package cowj.plugins;

import cowj.StorageWrapper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;

import cowj.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsIterable;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import zoomba.lang.core.types.ZNumber;

import java.util.stream.Stream;
import java.util.Map.Entry;

/**
 * Abstraction for AWS S3 Cloud Storage
 */
public interface S3StorageWrapper extends
        StorageWrapper.KeyValueStorage<Boolean, PutObjectResponse, ResponseBytes<GetObjectResponse>>,
        StorageWrapper.VersionedStorage<ResponseBytes<GetObjectResponse>> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(S3StorageWrapper.class);

    /**
     * Underlying S3Client instance
     *
     * @return S3Client
     */
    S3Client s3client();

    @Override
    default PutObjectResponse dumpb(String bucketName, String fileName, byte[] data) {
        return s3client().putObject(PutObjectRequest.builder().bucket(bucketName).key(fileName).build(),
                RequestBody.fromBytes(data));
    }

    /**
     * Dump String to S3 Cloud Storage
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a PutObjectResponse
     */
    @Override
    default PutObjectResponse dumps(String bucketName, String fileName, String data) {
        return s3client().putObject(PutObjectRequest.builder().bucket(bucketName).key(fileName).build(),
                RequestBody.fromString(data));
    }

    @Override
    default byte[] bytes(Entry<String, ResponseBytes<GetObjectResponse>> input){
        return (input == null || input.getValue() == null) ?
               null : input.getValue().asByteArray();
    }

    @Override
    default String utf8(Entry<String, ResponseBytes<GetObjectResponse>> input){
        return (input == null || input.getValue() == null) ?
                null : input.getValue().asUtf8String();
    }

    /**
     * In case file exists
     * @param bucketName in the bucket name
     * @param fileName having the name
     * @return true if it is a blob , false if it does not exist
     */
    @Override
    default boolean fileExist(String bucketName, String fileName) {
       return safeBoolean( () -> {
           HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                   .bucket(bucketName)
                   .key(fileName)
                   .build();

           s3client().headObject(headObjectRequest);
       });
    }

    @Override
    default Entry<String, ResponseBytes<GetObjectResponse>> data(String bucketName, String fileName){
        try {
            GetObjectRequest objectRequest = GetObjectRequest
                    .builder()
                    .key(fileName)
                    .bucket(bucketName)
                    .build();
            ResponseBytes<GetObjectResponse> responseBytes = s3client().getObjectAsBytes(objectRequest);
            return StorageWrapper.entry(fileName, responseBytes);
        }catch (Throwable th){
            logger.warn("Error loading data : " + th);
            return null;
        }
    }

    /**
     * Gets a Stream of Blob objects from a bucket
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Google Storage Blob
     */
    @Override
    default Stream<Entry<String, ResponseBytes<GetObjectResponse>>> stream(String bucketName, String directoryPrefix) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName )
                .maxKeys(pageSize()) // Set the maxKeys parameter to control the page size
                .prefix(directoryPrefix) // Set the prefix
                .build();

        ListObjectsV2Iterable listObjectsV2Iterable = s3client().listObjectsV2Paginator(listObjectsV2Request);
        Stream<Entry<String, ResponseBytes<GetObjectResponse>>> resultStream = Stream.of();
        for (ListObjectsV2Response page : listObjectsV2Iterable) {
            final Stream<Entry<String, ResponseBytes<GetObjectResponse>>> stream =
                    page.contents().stream().map( so -> data(bucketName, so.key()  ));
            resultStream = Stream.concat(resultStream, stream );
        }
        return resultStream;
    }


    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @param location  unused
     * @param preventPublicAccess unused
     * @return true if it was created, false otherwise
     */
    @Override
    default Boolean createBucket(String bucketName, String location, boolean preventPublicAccess ) {
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
    @Override
    default boolean deleteBucket(String bucketName) {
        return safeBoolean( () -> s3client().deleteBucket( DeleteBucketRequest.builder().bucket(bucketName).build()));
    }

    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if file was deleted, false if file does not exist
     */
    @Override
    default boolean delete(String bucketName, String path) {
        return safeBoolean( () ->
                s3client().deleteObject( DeleteObjectRequest.builder().bucket(bucketName).key(path).build()));
    }

    @Override
    default Stream<String> versions(String bucketName, String fileName){
        ListObjectVersionsRequest listRequest =  ListObjectVersionsRequest.builder()
                .bucket(bucketName).prefix(fileName).build();
        ListObjectVersionsIterable responses = s3client().listObjectVersionsPaginator(listRequest);
        // map only exact ones, not random prefixes
        return  responses.versions().stream().filter( v -> v.key().equals(fileName)).map(ObjectVersion::versionId);
    }

    @Override
    default ResponseBytes<GetObjectResponse> dataAtVersion(String bucketName, String fileName, String versionId){
        try {
            GetObjectRequest objectRequest = GetObjectRequest
                    .builder()
                    .key(fileName).versionId(versionId)
                    .bucket(bucketName)
                    .build();
            return s3client().getObjectAsBytes(objectRequest);
        }catch (Throwable th){
            logger.warn("Error reading versioned data : " + th);
            return null;
        }
    }

    @Override
    default String string(ResponseBytes<GetObjectResponse> input){
        return input == null? null : input.asUtf8String();
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
        Region region = Region.of( config.getOrDefault( REGION_ID, "ap-southeast-1" ).toString());
        logger.info("S3Storage [{}] region is [{}]", name,  region.id());
        final int pageSize = ZNumber.integer(config.getOrDefault( PAGE_SIZE, 1000 ) ).intValue();
        logger.info("S3Storage [{}] page size is [{}]", name,  pageSize);

        final S3Client s3Client = S3Client.builder()
                .httpClientBuilder(ApacheHttpClient.builder())
                .region(region)
                .build();
        final S3StorageWrapper s3sw = new S3StorageWrapper() {
            @Override
            public int pageSize() {
                return pageSize;
            }

            @Override
            public S3Client s3client() {
                return s3Client;
            }
        };
        return DataSource.dataSource(name, s3sw);
    };
}
