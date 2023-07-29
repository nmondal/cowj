package cowj.plugins;

import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import cowj.DataSource;
import zoomba.lang.core.types.ZTypes;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Abstraction for Google Cloud Storage
 */
public interface GoogleStorageWrapper {

    /**
     * Underlying Storage
     * @return Storage
     */
    Storage storage();

    /**
     * Dump String to Google Cloud Storage
     * @param bucketName the bucket
     * @param fileName the file
     * @param data which to be dumped encoding used is UTF-8
     * @return a Blob object
     */
    default Blob dumps(String bucketName, String fileName, String data) {
        Storage storage = storage();
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
            return storage.create(blobInfo, data.getBytes(UTF_8));
        }
        try {
            WriteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap("Updated content".getBytes(UTF_8)));
            channel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return blob;
    }

    /**
     * Dump Object to Google Cloud Storage after converting it to JSON String
     * @param bucketName the bucket
     * @param fileName the file
     * @param obj which to be dumped
     * @return a Blob object
     */
    default Blob dump(String bucketName, String fileName, Object obj) {
        String objJsonString = ZTypes.jsonString(obj);
        return dumps(bucketName, fileName, objJsonString);
    }

    /**
     * Load data from Google Storage as String - encoding is UTF-8
     * @param bucketName from this bucket name
     * @param fileName from this file
     * @return data string - content of the file
     */
    default String loads(String bucketName, String fileName) {
        Storage storage = storage();
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            return "";
        }
        byte[] prevContent = blob.getContent();
        return new String(prevContent, UTF_8);
    }

    /**
     * Load data from Google Storage as Object
     * @param bucketName from this bucket name
     * @param fileName from this file
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
     * @param bucketName name of the bucket
     * @return a Stream of Google Storage Blob
     */
    default Stream<Blob> all(String bucketName) {
        Page<Blob> p = storage().list(bucketName);
        return p.streamAll();
    }

    /**
     * Gets a Stream of String from a bucket
     * @param bucketName name of the bucket
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName) {
        return all(bucketName).map(b -> new String(b.getContent(), UTF_8));
    }

    /**
     * Gets a Stream of Object from a bucket
     * after reading each Blob as String use UTF-8 encoding
     * In case it can parse it as JSON return that object, else return the string
     * @param bucketName name of the bucket
     * @return a Stream of Object or String
     */
    default Stream<Object> allData(String bucketName) {
        return all(bucketName).map(b -> {
            final String data = new String(b.getContent(), UTF_8);
            try {
                return ZTypes.json(data);
            } catch (Throwable e) {
                return data;
            }
        });
    }

    /**
     * A DataSource.Creator for GoogleStorageWrapper
     */
    DataSource.Creator STORAGE = (name, config, parent) -> {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        final GoogleStorageWrapper gw = () -> storage;
        return DataSource.dataSource(name, gw);
    };
}
