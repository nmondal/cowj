package cowj.plugins;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;

import cowj.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import zoomba.lang.core.types.ZTypes;

import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
/**
 * Abstraction for AWS S3 Cloud Storage
 */
public interface S3StorageWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(S3StorageWrapper.class);

    static boolean safeBoolean( Runnable runnable ){
        try {
            runnable.run();
            return true;
        }catch (Throwable th){
            logger.warn("Will return false - Error happened " + th);
            return false;
        }
    }

    /**
     * Underlying S3Client instance
     *
     * @return S3Client
     */
    S3Client s3client();

    /**
     * Dump String to Google Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a PutObjectResponse
     */
    default PutObjectResponse dumps(String bucketName, String fileName, String data) {
        return s3client().putObject(PutObjectRequest.builder().bucket(bucketName).key(fileName).build(),
                RequestBody.fromString(data));
    }

    /**
     * Dump Object to Google Cloud Storage after converting it to JSON String
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param obj        which to be dumped
     * @return a Blob object
     */
    default PutObjectResponse dump(String bucketName, String fileName, Object obj) {
        String objJsonString = ZTypes.jsonString(obj);
        return dumps(bucketName, fileName, objJsonString);
    }


    /**
     * In case file exists
     * @param bucketName in the bucket name
     * @param fileName having the name
     * @return true if it is a blob , false if it does not exist
     */
    default boolean fileExist(String bucketName, String fileName) {
       return safeBoolean( () -> {
           HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                   .bucket(bucketName)
                   .key(fileName)
                   .build();

           s3client().headObject(headObjectRequest);
       });
    }

    /**
     * Load data from Google Storage as String - encoding is UTF-8
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data string - content of the file
     */
    default String loads(String bucketName, String fileName) {
        byte[] blob = loadb(bucketName, fileName);
        if (blob == null) {
            return "";
        }
        return new String(blob, UTF_8);
    }

    /**
     * Load data from Google Storage as bytes
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return byte[] - content of the file
     */
    default byte[] loadb(String bucketName, String fileName) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest
                    .builder()
                    .key(fileName)
                    .bucket(bucketName)
                    .build();
            ResponseBytes<GetObjectResponse> objectBytes = s3client().getObjectAsBytes(objectRequest);
            return objectBytes.asByteArray();
        }catch (Throwable th){
            logger.warn("Error loading data : " + th);
            return null;
        }
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
            logger.warn("Error jsonifying object data - will return raw data: " + t);
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
    default Stream<S3Object> stream(String bucketName, String directoryPrefix) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName )
                .maxKeys(pageSize()) // Set the maxKeys parameter to control the page size
                .prefix(directoryPrefix) // Set the prefix
                .build();

        ListObjectsV2Iterable listObjectsV2Iterable = s3client().listObjectsV2Paginator(listObjectsV2Request);
        Stream<S3Object> resultStream = Stream.of();
        for (ListObjectsV2Response page : listObjectsV2Iterable) {
            final Stream<S3Object> stream = page.contents().stream();
            resultStream = Stream.concat(resultStream, stream );
        }
        return resultStream;
    }

    default int pageSize(){ return  1000 ; }

    /**
     * Gets a Stream of String from a bucket
     *
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName, String directoryPrefix) {
        return stream(bucketName,directoryPrefix).map(so -> loads(bucketName, so.key()));
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
        return stream(bucketName, directoryPrefix).map( so -> load(bucketName, so.key()));
    }

    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @return true if it was created, false otherwise
     */
    default boolean createBucket(String bucketName) {
        final S3Client s3Client = s3client();
        return safeBoolean( () -> {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .build());
            logger.info("Creating bucket: " + bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            logger.info("Successfully Created bucket: " + bucketName);
        });
    }

    /**
     * Deletes the bucket
     * @param bucketName name of bucket
     * @return true if bucket was deleted false if bucket does not exist
     */
    default boolean deleteBucket(String bucketName) {
        return safeBoolean( () -> s3client().deleteBucket( DeleteBucketRequest.builder().bucket(bucketName).build()));
    }

    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if file was deleted, false if file does not exist
     */
    default boolean delete(String bucketName, String path) {
        return safeBoolean( () ->
                s3client().deleteObject( DeleteObjectRequest.builder().bucket(bucketName).key(path).build()));
    }

    /**
     * The key to be used to identify the storage id for the instance
     * In the configuration map
     */
    String REGION_ID = "region-id";

    /**
     * A DataSource.Creator for GoogleStorageWrapper
     */
    DataSource.Creator STORAGE = (name, config, parent) -> {
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        Region region = Region.of( config.getOrDefault( REGION_ID, "ap-southeast-1" ).toString());
        logger.info("[{}] region is [{}]", name,  region.id());
        final S3Client s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(ApacheHttpClient.builder())
                .region(region)
                .build();
        final S3StorageWrapper s3sw = () -> s3Client;
        return DataSource.dataSource(name, s3sw);
    };
}
