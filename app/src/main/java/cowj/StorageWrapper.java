package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import java.util.stream.Stream;

/**
 * Wrapper to be used for underlying cloud storage
 * @param <B> Type of response of bucket creation
 * @param <R> Type of response of writing to a key/file
 * @param <I> Type of item which is encountered in iteration within a bucket
 */
public interface  StorageWrapper <B,R,I> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(StorageWrapper.class);


    /**
     * Runs a block to return a boolean
     * @param runnable the code block
     * @return true if block did not throw any exception, false otherwise
     */
    default boolean safeBoolean( Runnable runnable ){
        try {
            runnable.run();
            return true;
        }catch (Throwable th){
            logger.warn("Will return false - Error happened " + th);
            return false;
        }
    }

    /**
     * Dump String to Cloud Storage
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a R object
     */
    R dumps( String bucketName, String fileName, String data);
    /**
     * Dump Object to Storage after converting it to JSON String
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param obj        which to be dumped
     * @return a Blob object
     */
    default R dump(String bucketName, String fileName, Object obj) {
        String objJsonString = ZTypes.jsonString(obj);
        return dumps(bucketName, fileName, objJsonString);
    }

    /**
     * In case file exists
     * @param bucketName in the bucket name
     * @param fileName having the name
     * @return true if it is a blob , false if it does not exist
     */
    boolean fileExist(String bucketName, String fileName);

    /**
     * Get the input data type I
     * @param bucketName name of the bucket
     * @param fileName name of the file
     * @return data of type I
     */
    I data(String bucketName, String fileName);

    /**
     * Gets Bytes from the blob
     * @param input blob type I
     * @return byte array of the data
     */
    byte[] bytes(I input);

    /**
     * Gets UTF8 String from the blob
     * @param input blob type I
     * @return UTF8 String rep from the data
     */
    String utf8(I input);

    /**
     * Utility method to get content of a Blob as JSON Object
     * If not JSON object simply returns the String
     * @param input the Blob whose content we shall read
     * @return JSON Object if possible - else String content
     */
    default Object json(I input){
        String data = utf8(input);
        try {
            return ZTypes.json(data);
        } catch (Throwable t) {
            return data;
        }
    }

    /**
     * Dump String to Google Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped
     * @return an  object
     */
    R dumpb(String bucketName, String fileName, byte[] data) ;

    /**
     * Load data from Google Storage as bytes
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return byte[] - content of the file
     */
    default byte[] loadb(String bucketName, String fileName){
        return bytes(data(bucketName, fileName));
    }

    /**
     * Load data from Google Storage as String - encoding is UTF-8
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data string - content of the file
     */
    default String loads(String bucketName, String fileName) {
        return utf8( data(bucketName, fileName));
    }

    /**
     * Load data from Google Storage as Object
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data object - content of the file after parsing it as JSON
     */
    default Object load(String bucketName, String fileName) {
        String data = loads(bucketName, fileName);
        if (data.isEmpty()) return null;
        try {
            return ZTypes.json(data);
        } catch (Throwable t) {
            logger.warn("Error making json from binary data - will return raw data: " + t);
            return data;
        }
    }

    /**
     * Gets a Stream of objects from a bucket
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Blob of type I
     */
    Stream<I> stream(String bucketName, String directoryPrefix);

    /**
     * Gets a Stream of String from a bucket
     *
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName, String directoryPrefix) {
        return stream(bucketName,directoryPrefix).map( this::utf8 );
    }

    /**
     * Gets a Stream of Object from a bucket
     * after reading each Blob as String use UTF-8 encoding
     * In case it can parse it as JSON return that object, else return the string
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Object or String
     */
    default Stream<Object> allData(String bucketName, String directoryPrefix) {
        return stream(bucketName, directoryPrefix).map( this::json );
    }

    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @param location location of the bucket
     * @param preventPublicAccess if set to true, ensures global read access is disabled
     * @return a type B
     */
    B createBucket(String bucketName, String location, boolean preventPublicAccess);

    /**
     * Deletes the bucket
     * @param bucketName name of bucket
     * @return true if bucket was deleted false if bucket does not exist
     */
    boolean deleteBucket(String bucketName);

    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if file was deleted, false if file does not exist
     */
    boolean delete(String bucketName, String path);

}