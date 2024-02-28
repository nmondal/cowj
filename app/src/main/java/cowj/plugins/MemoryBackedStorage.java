package cowj.plugins;

import cowj.DataSource;
import cowj.StorageWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A Memory Backed Storage Reference Implementation
 * This can be used to simulate actual S3, Google Cloud Storage implementations
 */
public class MemoryBackedStorage implements StorageWrapper<Boolean,Boolean, Map.Entry<String,String>> {
    @Override
    public Boolean dumpb(String bucketName, String fileName, byte[] data) {
        return dumps( bucketName, fileName, new String(data) );
    }

    Map<String, Map<String,String>> dataMemory = new HashMap<>();
    @Override
    public Boolean dumps(String bucketName, String fileName, String data) {
        return safeBoolean( () ->{
            dataMemory.getOrDefault( bucketName, Collections.emptyMap()).put(fileName,data);
        });
    }

    @Override
    public boolean fileExist(String bucketName, String fileName) {
        return dataMemory.getOrDefault(bucketName, Collections.emptyMap()).containsKey(fileName);
    }

    @Override
    public Map.Entry<String,String> data(String bucketName, String fileName) {
        String val = dataMemory.getOrDefault( bucketName, Collections.emptyMap()).getOrDefault(fileName,null);
        return StorageWrapper.entry(bucketName,val);
    }

    @Override
    public byte[] bytes(Map.Entry<String,String> input) {
        return input == null || input.getValue() == null ? null :
                input.getValue().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String utf8(Map.Entry<String,String> input) {
        return input == null ? null : input.getValue();
    }

    @Override
    public Stream<Map.Entry<String,String>> stream(String bucketName, String directoryPrefix) {
        Map<String,String> bucket = dataMemory.getOrDefault( bucketName, Collections.emptyMap());
        return bucket.entrySet().stream().filter( entry-> entry.getKey().startsWith(directoryPrefix));
    }

    @Override
    public Boolean createBucket(String bucketName, String location, boolean preventPublicAccess) {
        if ( dataMemory.containsKey(bucketName )) return false;
        dataMemory.put(bucketName, Collections.synchronizedMap( new TreeMap<>() )); // ensure that keys are sorted within a bucket
        return true;
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        if ( !dataMemory.containsKey(bucketName )) return false;
        dataMemory.remove(bucketName);
        return true;
    }

    @Override
    public boolean delete(String bucketName, String path) {
        if ( !dataMemory.containsKey(bucketName )) return false;
        Map<String,String> bucket = dataMemory.get(bucketName);
        if ( !bucket.containsKey(path )) return false;
        bucket.remove(path);
        return true;
    }

    /**
     * A Field to create the storage
     */
    public static final  DataSource.Creator STORAGE = (name, config, parent) -> {
        logger.info("MemoryBackedStorage [{}] ", name);
        return DataSource.dataSource(name, new MemoryBackedStorage());
    };
}