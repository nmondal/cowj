package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Typed storage wrapper
 * This takes in a type system, a schema registry
 * and reads and writes according to the schema
 * @param <B> Type of response of bucket creation
 * @param <R> Type of response of writing to a key/file
 * @param <I> Type of item which is encountered in iteration within a bucket
 */
public interface  TypedStorage<B,R,I> extends StorageWrapper<B,R,I> {

    /**
     * Logger for the Cowj TypedStorage
     */
    Logger logger = LoggerFactory.getLogger(TypedStorage.class);


    interface SchemaRegistry{

        Map<Pattern,String> patternsToSchemas();

        String pathSeperator();

        default String schema(String bucketName, String fileName){
            String inputText = bucketName + pathSeperator() + fileName ;
            return patternsToSchemas().entrySet().stream()
                    .filter( entry -> entry.getKey().matcher(inputText).matches())
                    .findFirst().map(Map.Entry::getValue).orElse(null);
        }

        static SchemaRegistry fromConfig(Map<String,String> schemaMapping, String pathSeperator){
            final Map<Pattern,String> patternStringMap = new LinkedHashMap<>();
            schemaMapping.forEach((key, value) -> {
                Pattern p = Pattern.compile(key);
                patternStringMap.put(p,value);
                logger.info("pattern: '{}' <-> {} schema", key, value);
            });
            return new SchemaRegistry() {
                @Override
                public Map<Pattern, String> patternsToSchemas() {
                    return patternStringMap;
                }

                @Override
                public String pathSeperator() {
                    return pathSeperator;
                }
            };
        }
    }

    class InvalidSchemaError extends RuntimeException{
        public InvalidSchemaError(Throwable cause){
            super(cause);
        }
    }

    SchemaRegistry registry();

    StorageWrapper<B,R,I> storage();

    TypeSystem typeSystem();

    boolean verifyRead();

    @Override
    default R dumpb(String bucketName, String fileName, byte[] data) {
        return storage().dumpb(bucketName, fileName, data);
    }

    @Override
    default R dumps(String bucketName, String fileName, String data) {
        final String schemaPath = registry().schema(bucketName,fileName);
        if ( schemaPath != null ) {
            EitherMonad<Object> parsed = typeSystem().json(schemaPath, data);
            if (parsed.inError()) throw new InvalidSchemaError(parsed.error());
        } else {
            logger.warn("[write] No schema attached with access pattern : {}{}{}",  bucketName, registry().pathSeperator() , fileName);
        }
        return storage().dumps( bucketName, fileName, data);
    }

    @Override
    default boolean fileExist(String bucketName, String fileName) {
        return storage().fileExist(bucketName, fileName);
    }

    @Override
    default I data(String bucketName, String fileName) {
        final I data =  storage().data(bucketName,fileName);
        if ( verifyRead()){ // turning it on, makes the system very, very, slow...
            final String schemaPath = registry().schema(bucketName,fileName);
            if  ( schemaPath != null ){
                final String dataBody = storage().utf8(data);
                EitherMonad<Object> parsed = typeSystem().json( schemaPath, dataBody);
                if ( parsed.inError() ) throw new InvalidSchemaError( parsed.error() );
            }else {
                logger.warn("[read] No schema attached with access pattern : {}{}{}",  bucketName, registry().pathSeperator() , fileName);
            }
        }
        return data;
    }

    @Override
    default byte[] bytes(I input) {
        return storage().bytes(input);
    }

    @Override
    default String utf8(I input) {
        return storage().utf8(input);
    }

    @Override
    default String key(I input) {
        return storage().key(input);
    }

    @Override
    default Stream<I> stream(String bucketName, String directoryPrefix) {
        return storage().stream(bucketName, directoryPrefix);
    }

    @Override
    default B createBucket(String bucketName, String location, boolean preventPublicAccess) {
        return storage().createBucket(bucketName, location, preventPublicAccess);
    }

    @Override
    default boolean deleteBucket(String bucketName) {
        return storage().deleteBucket(bucketName);
    }

    @Override
    default boolean delete(String bucketName, String path) {
        return storage().delete(bucketName, path);
    }


    String PATH_SEPERATOR = "sep" ;

    String PATH_PATTERNS = "paths" ;

    String VALIDATE_READING = "read" ;

    static <B,R,I> TypedStorage<B,R,I> typedStorage(TypeSystem typeSystem, SchemaRegistry schemaRegistry,
                                                    StorageWrapper<B,R,I> storageWrapper,  boolean  validateReading){
        return new TypedStorage<>() {
            @Override
            public SchemaRegistry registry() {
                return schemaRegistry;
            }

            @Override
            public StorageWrapper<B,R,I> storage() {
                return storageWrapper;
            }

            @Override
            public TypeSystem typeSystem() {
                return typeSystem;
            }

            @Override
            public boolean verifyRead() {
                return validateReading;
            }
        };
    }

    static void attach(TypeSystem typeSystem){
        typeSystem.storages().forEach( (storageName, stringObjectMap) -> {
            logger.info("start : storage '{}' is being converted", storageName);
            final String pathSep = stringObjectMap.getOrDefault( PATH_SEPERATOR, "/").toString();
            final boolean validateReading = ZTypes.bool(stringObjectMap.getOrDefault( VALIDATE_READING, false), false);
            final Map<String,String> patterns = (Map)stringObjectMap.getOrDefault( PATH_PATTERNS, Collections.emptyMap());
            final StorageWrapper<?,?,?> storageWrapper = DataSource.dataSource(storageName);
            final SchemaRegistry schemaRegistry = SchemaRegistry.fromConfig( patterns, pathSep);
            final TypedStorage<?,?,?> typedStorage = typedStorage(typeSystem, schemaRegistry, storageWrapper, validateReading);
            DataSource.registerDataSource(storageName, typedStorage);
            logger.info("end : storage '{}' was converted with read validation: {}, path seperator '{}'", storageName, validateReading, pathSep);
        });
    }
}
