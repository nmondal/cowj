package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class S3StorageWrapperTest {
    private static final Model model = () -> ".";

    @Test
    public void initTest(){
        DataSource ds = S3StorageWrapper.STORAGE.create("foo", Collections.emptyMap(), model);
        Assert.assertNotNull( ds.proxy() );
        Assert.assertTrue( ds.proxy() instanceof S3StorageWrapper );
        Assert.assertNotNull( ((S3StorageWrapper) ds.proxy()).s3client() );
    }

    @Test
    public void dumpTest(){
        S3Client s3Client = mock(S3Client.class);
        PutObjectResponse resp = mock(PutObjectResponse.class);
        when(s3Client.putObject( (PutObjectRequest) any(), (RequestBody) any())).thenReturn(resp);
        S3StorageWrapper s3 = () -> s3Client;
        PutObjectResponse actual= s3.dumps( "a","b", "c");
        Assert.assertEquals( resp, actual);
    }

    @Test
    public void responseTest(){
        S3Client s3Client = mock(S3Client.class);
        S3StorageWrapper s3 = () -> s3Client;
        final String hello = "hello!" ;
        final byte[] bytes = hello.getBytes(StandardCharsets.UTF_8) ;
        ResponseBytes<GetObjectResponse> resp = mock(ResponseBytes.class);
        when(resp.asByteArray()).thenReturn(bytes );
        when(resp.asUtf8String()).thenReturn( hello);
        Assert.assertEquals( hello, s3.utf8(resp));
        Assert.assertEquals( bytes, s3.bytes(resp));

        Assert.assertEquals( "", s3.utf8(null));
        Assert.assertEquals( 0, s3.bytes(null).length );
    }

}
