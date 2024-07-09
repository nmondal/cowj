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


    /**
     * A registry for path access schemas
     * A schema can be attached with one access pattern which should be specified in regex
     * Must entirely match
     */
    interface SchemaRegistry{

        /**
         * A map of path access patterns
         * @return map of path access patterns, key is the pattern, value is the schema
         */
        Map<Pattern,String> patternsToSchemas();

        /**
         * The path separator, defaulted to "/"
         * @return path seperator to be used
         */
        String pathSeperator();

        /**
         * Finds the schema given access pattern
         * @param bucketName name of the bucket
         * @param fileName name of the file
         * @return the schema if any pattern match to apply for the file access
         */
        default String schema(String bucketName, String fileName){
            String inputText = bucketName + pathSeperator() + fileName ;
            return patternsToSchemas().entrySet().stream()
                    .filter( entry -> entry.getKey().matcher(inputText).matches())
                    .findFirst().map(Map.Entry::getValue).orElse(null);
        }

        /**
         * Creates a SchemaRegistry
         * @param schemaMapping a map of pattern to schema
         * @param pathSeperator a path seperator to apply between bucket and file name
         * @return SchemaRegistry
         */
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

    /**
     * A custom error class to throw out access for invalid schema
     */
    class InvalidSchemaError extends RuntimeException{
        /**
         * Creates an instance of the InvalidSchemaError
         * @param cause the underlying cause of the error
         */
        public InvalidSchemaError(Throwable cause){
            super(cause);
        }
    }

    /**
     * A SchemaRegistry
     * @return SchemaRegistry for the instance
     */
    SchemaRegistry registry();

    /**
     * Underlying StorageWrapper which it would use to actually access data
     * @return StorageWrapper for the instance
     */
    StorageWrapper<B,R,I> storage();

    /**
     * Underlying TypeSystem which it would use to actually verify type
     * @return TypeSystem for the instance
     */
    TypeSystem typeSystem();

    /**
     * In case this returns true, system verifies each read with schema
     * Caution: Generally, please do not, please return false , it is default
     * If you write Type ful, you should be good
     * @return whether to enable schema verification even on read access
     */
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


    /**
     * Key for the Path Seperator in the Config
     */
    String PATH_SEPERATOR = "sep" ;

    /**
     * Key for the Path Pattern Map Config
     */
    String PATH_PATTERNS = "paths" ;

    /**
     * Key for the validate all read  in the Config
     */
    String VALIDATE_READING = "read" ;

    /**
     * Creates a TypedStorage from constituents
     * @param typeSystem TypeSystem it should use
     * @param schemaRegistry SchemaRegistry it should apply for access
     * @param storageWrapper StorageWrapper to be used under the hood
     * @param validateReading or the validate all read
     * @return a TypedStorage which is a proxy / decorator over the underlying StorageWrapper
     * @param <B> Type of response of bucket creation
     * @param <R> Type of response of writing to a key/file
     * @param <I> Type of item which is encountered in iteration within a bucket
     */
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

    /**
     * Attaches types to the storage pattern
     * @param typeSystem the underlying TypeSystem to be used
     */
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
