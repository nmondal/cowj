package cowj.plugins;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.StorageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Azure Blob Storage Wrapper
 * Bucket is the Azure Container
 * Data loaded as BinaryData
 */
public interface ABSStorageWrapper extends StorageWrapper.KeyValueStorage<Boolean, Boolean, BinaryData>, StorageWrapper.VersionedStorage<BinaryData> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(ABSStorageWrapper.class);

    BlobServiceClient client();

    @Override
    default Boolean dumps(String containerName, String fileName, String data) {
        return safeBoolean( () -> {
            BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
            BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
            blobClient.upload(BinaryData.fromString(data));
        });
    }

    @Override
    default Map.Entry<String, BinaryData> data(String containerName, String fileName) {
        BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
        return Map.entry(fileName , blobClient.downloadContent() );
    }

    @Override
    default byte[] bytes(Map.Entry<String, BinaryData> input) {
        return input.getValue().toBytes() ;
    }

    @Override
    default String utf8(Map.Entry<String, BinaryData> input) {
        return input.getValue().toString();
    }

    @Override
    default Boolean dumpb(String containerName, String fileName, byte[] data) {
        return safeBoolean( () -> {
            BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
            BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
            blobClient.upload(BinaryData.fromBytes(data));
        });
    }

    @Override
    default Stream<Map.Entry<String, BinaryData>> stream(String containerName, String directoryPrefix) {
        BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
        ListBlobsOptions listBlobsOptions = new ListBlobsOptions();
        listBlobsOptions.setMaxResultsPerPage( pageSize() );
        listBlobsOptions.setPrefix(directoryPrefix);
        return blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(5))
                .stream().map( blobItem -> {
                    final String fileName = blobItem.getName() ;
                    BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
                    return Map.entry(fileName , blobClient.downloadContent() );
                });
    }

    @Override
    default Boolean createBucket(String containerName, String location, boolean preventPublicAccess) {
        return safeBoolean( () ->  client().createBlobContainer(containerName)) ;
    }

    @Override
    default boolean fileExist(String containerName, String fileName) {
        BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
        return blobClient.exists();
    }


    @Override
    default boolean deleteBucket(String containerName) {
        return client().deleteBlobContainerIfExists(containerName);
    }

    @Override
    default boolean delete(String containerName, String path) {
        BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(path);
        return blobClient.deleteIfExists();
    }

    /////////////////////// Versioning /////////////////////


    @Override
    default BinaryData dataAtVersion(String containerName, String fileName, String versionId) {
        return EitherMonad.orNull( () -> {
            // when and if things go bonkers, this helps straight away
            BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
            BlobClient blobClient = blobContainerClient.getBlobClient(fileName).getVersionClient(versionId);
            return blobClient.downloadContent();
        });
    }

    @Override
    default Stream<String> versions(String containerName, String fileName) {
        BlobContainerClient blobContainerClient = client().getBlobContainerClient(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
        if ( blobClient.exists() ){
            // https://stackoverflow.com/questions/67214642/azure-blobs-java-sdk-can-not-return-the-list-of-versionids-of-an-object
            ListBlobsOptions listBlobsOptions = new ListBlobsOptions();
            listBlobsOptions.setMaxResultsPerPage( pageSize() );
            listBlobsOptions.setPrefix(fileName);
            listBlobsOptions.setDetails(new BlobListDetails().setRetrieveVersions(true));
            return blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(1))
                    .stream().filter( blobItem -> blobItem.getName().equals(fileName) ).map(BlobItem::getVersionId);
        }
        return Stream.empty();
    }

    String ACCOUNT = "account" ;


    /**
     * A DataSource.Creator for ABSStorageWrapper
     */
    DataSource.Creator STORAGE = (name, config, parent) -> {
        String storageAccountName = config.getOrDefault(ACCOUNT, "" ).toString();
        if ( storageAccountName.isEmpty() ) throw new IllegalArgumentException("storage 'account' name must be present");
        logger.info("ABSStorage [{}] acc name [{}]", name,  storageAccountName);
        storageAccountName = parent.envTemplate(storageAccountName);
        logger.info("ABSStorage [{}] post transform acc name [{}]", name,  storageAccountName);

        final int pageSize = ZNumber.integer(config.getOrDefault( PAGE_SIZE, 1000 ) ).intValue();
        logger.info("ABSStorage [{}] page size is [{}]", name,  pageSize);

        /*
         * The default credential first checks environment variables for configuration
         * If environment configuration is incomplete, it will try managed identity
         */
        DefaultAzureCredential defaultCredential = new DefaultAzureCredentialBuilder().build();

        final String endPoint = String.format( "https://%s.blob.core.windows.net/" , storageAccountName );
        // Azure SDK client builders accept the credential as a parameter
        final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(endPoint)
                .credential(defaultCredential)
                .buildClient();

        final ABSStorageWrapper wrapper = new ABSStorageWrapper() {
            @Override
            public BlobServiceClient client() {
                return blobServiceClient;
            }

            @Override
            public int pageSize(){
                return pageSize;
            }
        };
        return DataSource.dataSource(name, wrapper);
    };
}
