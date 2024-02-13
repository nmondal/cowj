package cowj.plugins;

import cowj.StorageWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

class MemoryBackedStorage implements StorageWrapper<Boolean,Boolean,String> {

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
    public String data(String bucketName, String fileName) {
        return dataMemory.getOrDefault( bucketName, Collections.emptyMap()).get(fileName);
    }

    @Override
    public byte[] bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String utf8(String input) {
        return input;
    }

    @Override
    public Stream<String> stream(String bucketName, String directoryPrefix) {
        Map<String,String> bucket = dataMemory.getOrDefault( bucketName, Collections.emptyMap());
        return bucket.entrySet().stream().filter( entry-> entry.getKey().startsWith(directoryPrefix)).map(Map.Entry::getValue);
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
}