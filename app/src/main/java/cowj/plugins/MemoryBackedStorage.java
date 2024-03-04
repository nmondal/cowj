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
        return () -> Collections.synchronizedMap( new TreeMap<>());
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
        dataMemory().put(bucketName, bucketMemory().get());
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

        /**
         * Gets the underlying VersionedMap for the bucket
         * @param bucketName name of the bucket
         * @return a VersionedMap of entry type String, String
         */
        default VersionedMap<String,String> versionedMap(String bucketName){
            return (VersionedMap<String,String>)dataMemory()
                    .getOrDefault( bucketName , VersionedMap.versionedMap(Collections.emptyMap())) ;
        }

        @Override
        default Stream<String> versions(String bucketName, String fileName) {
            VersionedMap<String,String> vm = versionedMap(bucketName) ;
            return vm.versions(fileName);
        }

        @Override
        default String dataAtVersion(String bucketName, String fileName, String versionId) {
            VersionedMap<String,String> vm = versionedMap(bucketName) ;
            return vm.dataAtVersion(fileName, versionId);
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
        final  MemoryBackedStorage ms ;
        if ( versioned ){
            ms = (VersionedMemoryStorage) ( () -> memory); // a version backed store
        } else {
            ms = ( () -> memory); // regular one
        }
        return DataSource.dataSource(name, ms);
    };
}