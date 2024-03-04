package cowj.plugins;

import cowj.DataSource;
import cowj.StorageWrapper;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A Memory Backed Storage Reference Implementation
 * This can be used to simulate actual S3, Google Cloud Storage implementations
 */
public interface MemoryBackedStorage extends StorageWrapper.SimpleKeyValueStorage {

    /**
     * Underlying memory to use
     * @return a Map which would be used to store data
     */
    Map<String, Map<String,String>> dataMemory();

    default Supplier<Map<String,String>> bucketMemory(){
        // ensure that keys are sorted within a bucket
        return TreeMap::new;
    }

    @Override
    default Boolean dumpb(String bucketName, String fileName, byte[] data) {
        return dumps( bucketName, fileName, new String(data) );
    }

   @Override
    default Boolean dumps(String bucketName, String fileName, String data) {
        return safeBoolean( () ->{
            dataMemory().getOrDefault( bucketName, Collections.emptyMap()).put(fileName,data);
        });
    }

    @Override
    default boolean fileExist(String bucketName, String fileName) {
        return dataMemory().getOrDefault(bucketName, Collections.emptyMap()).containsKey(fileName);
    }

    @Override
    default Map.Entry<String,String> data(String bucketName, String fileName) {
        String val = dataMemory().getOrDefault( bucketName, Collections.emptyMap()).getOrDefault(fileName,null);
        return StorageWrapper.entry(bucketName,val);
    }

    @Override
    default Stream<Map.Entry<String,String>> stream(String bucketName, String directoryPrefix) {
        Map<String,String> bucket = dataMemory().getOrDefault( bucketName, Collections.emptyMap());
        return bucket.entrySet().stream().filter( entry-> entry.getKey().startsWith(directoryPrefix));
    }

    @Override
    default Boolean createBucket(String bucketName, String location, boolean preventdefaultAccess) {
        if ( dataMemory().containsKey(bucketName )) return false;
        dataMemory().put(bucketName, Collections.synchronizedMap(bucketMemory().get()));
        return true;
    }

    @Override
    default boolean deleteBucket(String bucketName) {
        if ( !dataMemory().containsKey(bucketName )) return false;
        dataMemory().remove(bucketName);
        return true;
    }

    @Override
    default boolean delete(String bucketName, String path) {
        if ( !dataMemory().containsKey(bucketName )) return false;
        Map<String,String> bucket = dataMemory().get(bucketName);
        if ( !bucket.containsKey(path )) return false;
        bucket.remove(path);
        return true;
    }

    /**
     * A basal Memory Backed Versioned Storage Implementation
     */
    interface VersionedMemoryStorage extends MemoryBackedStorage, StorageWrapper.VersionedStorage<String>{
        @Override
        default Supplier<Map<String,String>> bucketMemory(){
            // ensure that keys are sorted within a bucket
            return () -> VersionedMap.versionedMap( new TreeMap<>() );
        }


        @Override
        default Stream<String> versions(String bucketName, String fileName) {
            return null;
        }

        @Override
        default String dataAtVersion(String bucketName, String fileName, String versionId) {
            return null;
        }
    }

    /**
     * Key to whether or not use versioning
     */
    String VERSIONED = "versioned" ;

    /**
     * A Field to create the storage
     */
    DataSource.Creator STORAGE = (name, config, parent) -> {
        final boolean versioned = ZTypes.bool(config.getOrDefault( VERSIONED, false),false);
        logger.info("MemoryBackedStorage [{}]  versioned : {}", name, versioned);
        final Map<String,Map<String,String>> memory = new HashMap<>();
        final  MemoryBackedStorage ms = versioned ? (VersionedMemoryStorage) ( () -> memory) : ( () -> memory) ;
        return DataSource.dataSource(name, ms);
    };
}