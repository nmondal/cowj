package cowj.plugins;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import zoomba.lang.core.types.ZTypes;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleStorageWrapperTest {

    @Test
    public void dumpWhenDoesExistTest(){
        Storage storage = mock( Storage.class);
        Blob b = mock(Blob.class);
        WriteChannel channel = mock(WriteChannel.class);
        when(b.getStorage()).thenReturn(storage);
        when(b.writer()).thenReturn(channel);
        when(storage.get((BlobId) ArgumentMatchers.any())).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Blob r = gsw.dump( "foo", "bar", Map.of("x",  42 ));
        Assert.assertEquals(b, r );
    }

    @Test
    public void dumpRaiseError(){
        Storage storage = mock( Storage.class);
        Blob b = mock(Blob.class);
        when(b.getStorage()).thenReturn(storage);
        when(storage.get((BlobId) ArgumentMatchers.any())).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Exception exception = assertThrows(RuntimeException.class, () -> {
            gsw.dump( "foo", "bar", Map.of("x",  42 ));
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void dumpWhenDoesNotExistTest(){
        Storage storage = mock( Storage.class);
        Blob b = mock(Blob.class);
        when(b.getStorage()).thenReturn(storage);
        when(storage.create(any(), (byte[]) any())).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Blob r = gsw.dump( "foo", "bar", Map.of("x",  42 ));
        Assert.assertEquals(b, r );
    }

    @Test
    public void loadObjectWhenNotPresent(){
        Storage storage = mock( Storage.class);
        GoogleStorageWrapper gsw = () -> storage;
        Object res = gsw.load("foo", "bar");
        Assert.assertNull(res);
    }

    @Test
    public void loadObjectWhenPresent(){
        Storage storage = mock( Storage.class);
        Map<String,Object> data = Map.of("x",  42 );
        String dataString = ZTypes.jsonString(data);
        Blob b = mock(Blob.class);
        when(b.getStorage()).thenReturn(storage);
        when(b.getContent()).thenReturn( dataString.getBytes( StandardCharsets.UTF_8 ));
        when(storage.get((BlobId) ArgumentMatchers.any())).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Object res = gsw.load("foo", "bar");
        Assert.assertNotNull(res);
        Assert.assertEquals( data, res );
        // now unstructured random data
        dataString = "foo,bar" ;
        when(b.getContent()).thenReturn( dataString.getBytes( StandardCharsets.UTF_8 ));
        res = gsw.load("foo", "bar");
        Assert.assertEquals( dataString, res );
    }
}
