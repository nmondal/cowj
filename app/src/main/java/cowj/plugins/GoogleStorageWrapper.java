package cowj.plugins;

import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import cowj.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Abstraction for Google Cloud Storage
 */
public interface GoogleStorageWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(GoogleStorageWrapper.class);

    /**
     * Underlying Storage
     *
     * @return Storage
     */
    Storage storage();

    /**
     * Dump String to Google Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a Blob object
     */
    default Blob dumps(String bucketName, String fileName, String data) {
        Storage storage = storage();
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        final byte[] dataBytes = data.getBytes(UTF_8); // if done this way, the bug could have been avoided
        if (blob == null) {
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
            return storage.create(blobInfo, dataBytes);
        }
        try {
            WriteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap(dataBytes));
            channel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return blob;
    }

    /**
     * Dump Object to Google Cloud Storage after converting it to JSON String
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param obj        which to be dumped
     * @return a Blob object
     */
    default Blob dump(String bucketName, String fileName, Object obj) {
        String objJsonString = ZTypes.jsonString(obj);
        return dumps(bucketName, fileName, objJsonString);
    }

    /**
     * In case folder exists
     * @param bucketName in the bucket name
     * @param prefix having the prefix
     * @return true if is a folder like prefix, false if it does not exist or is not folder like
     */
    default boolean folderExists(String bucketName, String prefix){
        Page<Blob> blobs = storage().list(bucketName, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.pageSize(1));
        return  blobs != null && blobs.getValues().iterator().hasNext();
    }

    /**
     * In case file exists
     * @param bucketName in the bucket name
     * @param fileName having the name
     * @return true if it is a blob , false if it does not exist or is not simple blob
     */
    default boolean fileExist(String bucketName, String fileName) {
        Blob blob = storage().get(bucketName, fileName);
        return blob != null;
    }

    /**
     * Utility method to get content of a Blob
     * @param blob the Blob whose content we shall read
     * @return String content of the Blob imagining UTF-8 encoding
     */
    default String data(Blob blob){
        if (blob == null) {
            return "";
        }
        byte[] prevContent = blob.getContent();
        return new String(prevContent, UTF_8);
    }

    /**
     * Utility method to get content of a Blob as JSON Object
     * If not JSON object simply returns the String
     * @param blob the Blob whose content we shall read
     * @return JSON Object if possible - else String content
     */
    default Object json(Blob blob){
        String data = data(blob);
        try {
            return ZTypes.json(data);
        } catch (Throwable t) {
            return data;
        }
    }

    /**
     * Load data from Google Storage as String - encoding is UTF-8
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data string - content of the file
     */
    default String loads(String bucketName, String fileName) {
        Storage storage = storage();
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        return data(blob);
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
            return data;
        }
    }

    /**
     * Gets a Stream of Blob objects from a bucket
     * <a href="https://cloud.google.com/storage/docs/samples/storage-list-files-with-prefix#storage_list_files_with_prefix-java">See In Here.</a>
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Google Storage Blob
     */
    default Stream<Blob> all(String bucketName, String directoryPrefix) {
        Page<Blob> p = storage().list(bucketName,
                Storage.BlobListOption.prefix(directoryPrefix),
                Storage.BlobListOption.currentDirectory());
        Stream<Blob> resultStream = p.streamAll();
        while ( p.hasNextPage() ){
            p = p.getNextPage();
            // https://www.baeldung.com/java-merge-streams
            resultStream = Stream.concat(resultStream, p.streamAll());
        }
        return resultStream;
    }

    /**
     * Gets a Stream of String from a bucket
     *
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName, String directoryPrefix) {
        return all(bucketName,directoryPrefix).map(b -> new String(b.getContent(), UTF_8));
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
        return all(bucketName, directoryPrefix).map(this::json);
    }

    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @param location location of the bucket. Supported values https://cloud.google.com/storage/docs/locations
     * @param preventPublicAccess if true, sets up iam to prevent public access. Otherwise, does nothing
     * @return bucket
     */
    default Bucket createBucket(String bucketName, String location, boolean preventPublicAccess) {
        BucketInfo.Builder builder = BucketInfo.newBuilder(bucketName)
                .setLocation(location);
        if (preventPublicAccess) {
            builder.setIamConfiguration(
                    BucketInfo.IamConfiguration.newBuilder()
                            .setPublicAccessPrevention(BucketInfo.PublicAccessPrevention.ENFORCED)
                            .build()
            );
        }

        return storage().create(builder.build());
    }

    /**
     * Deletes the bucket
     * @param bucketName name of bucket
     * @return true if bucket was deleted false if bucket does not exist
     */
    default boolean deleteBucket(String bucketName) {
        return storage().delete(bucketName);
    }


    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if bucket was deleted, false if bucket does not exist
     */
    default boolean delete(String bucketName, String path) {
        return storage().delete(BlobId.of(bucketName, path));
    }

    /**
     * The key to be used to identify the storage id for the instance
     * In the configuration map
     */
    String PROJECT_ID = "project-id";

    /**
     * A DataSource.Creator for GoogleStorageWrapper
     */
    DataSource.Creator STORAGE = (name, config, parent) -> {
        HttpStorageOptions.Builder builder = HttpStorageOptions.newBuilder();
        String projectID = config.getOrDefault(PROJECT_ID, "").toString();
        logger.info("GoogleStorageWrapper {} project-id [{}]", name, projectID);
        if (!projectID.isEmpty()) {
            builder.setProjectId(projectID);
        }
        Storage storage = builder.build().getService();
        final GoogleStorageWrapper gw = () -> storage;
        return DataSource.dataSource(name, gw);
    };
}
