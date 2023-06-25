package cowj.plugins;

import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import cowj.DataSource;
import zoomba.lang.core.types.ZTypes;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface GoogleStorageWrapper {

    Storage storage();

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

    default Blob dump(String bucketName, String fileName, Object obj) {
        String objJsonString = ZTypes.jsonString(obj);
        return dumps(bucketName, fileName, objJsonString);
    }

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

    default Object load(String bucketName, String fileName) {
        String data = loads(bucketName, fileName);
        if (data.isEmpty()) return null;
        try {
            return ZTypes.json(data);
        } catch (Throwable t) {
            return data;
        }
    }

    default Stream<Blob> all(String bucketName) {
        Page<Blob> p = storage().list(bucketName);
        return p.streamAll();
    }

    default Stream<String> allContent(String bucketName) {
        return all(bucketName).map(b -> new String(b.getContent(), UTF_8));
    }

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

    DataSource.Creator STORAGE = (name, config, parent) -> {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        final GoogleStorageWrapper gw = () -> storage;
        return new DataSource() {
            @Override
            public Object proxy() {
                return gw;
            }

            @Override
            public String name() {
                return name;
            }
        };
    };
}
