package cowj.plugins;

import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import cowj.DataSource;
import cowj.StorageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Abstraction for Google Cloud Storage
 */
public interface GoogleStorageWrapper extends StorageWrapper<Bucket, Blob, Blob>,
        StorageWrapper.VersionedStorage<Blob> {

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
     * <a href="https://cloud.google.com/storage/docs/metadata#generation-number">...</a>
     * Creates a unique increasing sorted ID for Blobs
     * @param b a Blob in Google Storage
     * @return a unique increasing sorted ID for Blob b of the format : metaGenNo#GenNo
     */
    static String blobVersion(Blob b){
        return  b.getMetageneration() + "#" + b.getGeneration();
    }

    @Override
    default Stream<String> versions(String bucketName, String fileName) {
        Bucket bucket = storage().get(bucketName);
        Page<Blob> blobs = bucket.list(Storage.BlobListOption.versions(true));
        return blobs.streamAll()
                .filter( b ->  key(b).equals(fileName)) // bad design
                .map(GoogleStorageWrapper::blobVersion) // this is the way
                .sorted( Comparator.reverseOrder()); // latest should be first
    }

    @Override
    default Blob dataAtVersion(String bucketName, String fileName, String versionId) {
        Bucket bucket = storage().get(bucketName);
        String[] arr = versionId.split("#");
        long metaGen = Long.parseLong(arr[0]);
        long gen = Long.parseLong(arr[0]);
        return bucket.get( fileName, Storage.BlobGetOption.metagenerationMatch( metaGen),
                Storage.BlobGetOption.generationMatch( gen) );
    }

    @Override
    default String string(Blob input) {
        return utf8(input);
    }

    @Override
    default String key(Blob input) {
        return input.getBlobId().getName();
    }

    /**
     * Dump String to Google Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped
     * @return a Blob object
     */
    @Override
    default Blob dumpb(String bucketName, String fileName, byte[] data) {
        Storage storage = storage();
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
            return storage.create(blobInfo, data);
        }
        try {
            WriteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap(data));
            channel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return blob;
    }

    /**
     * Dump String to Google Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a Blob object
     */
    @Override
    default Blob dumps(String bucketName, String fileName, String data) {
        final byte[] dataBytes = data.getBytes(UTF_8); // if done this way, the bug could have been avoided
        return dumpb(bucketName, fileName, dataBytes);
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
    @Override
    default boolean fileExist(String bucketName, String fileName) {
        Blob blob = data(bucketName, fileName);
        return blob != null;
    }

    @Override
    default Blob data(String bucketName, String fileName) {
        return storage().get(bucketName, fileName);
    }

    @Override
    default byte[] bytes(Blob input) {
        try {
            return input.getContent();
        }catch (Throwable t){
            return null;
        }
    }

    @Override
    default String utf8(Blob input) {
        try {
            return new String( bytes(input), UTF_8);
        }catch (Throwable t){
            return null;
        }
    }

    /**
     * Gets a Stream of Blob objects from a bucket
     * <a href="https://cloud.google.com/storage/docs/samples/storage-list-files-with-prefix#storage_list_files_with_prefix-java">See In Here.</a>
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @param recurse should we go down to the subdirectories
     * @return a Stream of Google Storage Blob
     */
    default Stream<Blob> stream(String bucketName, String directoryPrefix, boolean recurse) {
        Page<Blob> p ;
                if ( recurse ) {
                    p = storage().list(bucketName,
                            Storage.BlobListOption.prefix(directoryPrefix));
                } else {
                    p = storage().list(bucketName,
                            Storage.BlobListOption.prefix(directoryPrefix),
                            Storage.BlobListOption.currentDirectory());
                }
        Stream<Blob> resultStream = p.streamAll();
        while ( p.hasNextPage() ){
            p = p.getNextPage();
            // https://www.baeldung.com/java-merge-streams
            resultStream = Stream.concat(resultStream, p.streamAll());
        }
        return resultStream;
    }

    @Override
    default Stream<Blob> stream(String bucketName, String directoryPrefix) {
        return stream(bucketName, directoryPrefix, true);
    }

    /**
     * Gets a Stream of String from a bucket
     *
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @param recurse should we go down to the subdirectories
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName, String directoryPrefix, boolean recurse) {
        return stream(bucketName,directoryPrefix, recurse).map(this::utf8);
    }

    /**
     * Gets a Stream of Object from a bucket
     * after reading each Blob as String use UTF-8 encoding
     * In case it can parse it as JSON return that object, else return the string
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @param recurse should we go down to the subdirectories
     * @return a Stream of Object or String
     */
    default Stream<Object> allData(String bucketName, String directoryPrefix, boolean recurse) {
        return stream(bucketName, directoryPrefix, recurse).map(this::json);
    }

    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @param location location of the bucket. Supported values <a href="https://cloud.google.com/storage/docs/locations">...</a>
     * @param preventPublicAccess if true, sets up iam to prevent public access. Otherwise, does nothing
     * @return bucket
     */
    @Override
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
    @Override
    default boolean deleteBucket(String bucketName) {
        return storage().delete(bucketName);
    }


    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if file was deleted, false if file does not exist
     */
    @Override
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
