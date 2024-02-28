package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import cowj.StorageWrapper;
import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class S3StorageWrapperTest {
    private static final Model model = () -> ".";

    @Test
    public void initTest(){
        DataSource ds = S3StorageWrapper.STORAGE.create("foo", Map.of("page-size", 50), model);
        Assert.assertNotNull( ds.proxy() );
        Assert.assertTrue( ds.proxy() instanceof S3StorageWrapper );
        Assert.assertNotNull( ((S3StorageWrapper) ds.proxy()).s3client() );
        Assert.assertEquals(50, ((S3StorageWrapper)ds.proxy()).pageSize() );
        Entry<String,String> es = StorageWrapper.entry("1","2");
        // check that the Entries also works out well
        Assert.assertEquals("1", es.getKey());
        Assert.assertEquals("2", es.getValue());
        Assert.assertEquals("2", es.setValue("42"));
    }

    @Test
    public void dumpTest(){
        S3Client s3Client = mock(S3Client.class);
        PutObjectResponse resp = mock(PutObjectResponse.class);
        when(s3Client.putObject( (PutObjectRequest) any(), (RequestBody) any())).thenReturn(resp);
        S3StorageWrapper s3 = () -> s3Client;
        PutObjectResponse actual= s3.dumps( "a","b", "c");
        Assert.assertEquals( resp, actual);
        actual= s3.dumpb( "a","b", "c".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals( resp, actual);

        Assert.assertEquals(1000, s3.pageSize() );
    }

    @Test
    public void createBucketTest(){
        S3Client s3Client = mock(S3Client.class);
        S3Waiter s3Waiter = mock(S3Waiter.class);
        when(s3Waiter.waitUntilBucketExists( (HeadBucketRequest) any())).thenReturn(null);
        when(s3Client.createBucket((CreateBucketRequest) any())).thenReturn(null);
        when(s3Client.waiter()).thenReturn(s3Waiter);

        S3StorageWrapper s3 = () -> s3Client;
        Assert.assertTrue( s3.createBucket("a","b", false ));
    }

    @Test
    public void responseOkTest(){
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject((HeadObjectRequest) any())).thenReturn(null);
        when(s3Client.deleteBucket((DeleteBucketRequest) any())).thenReturn(null);
        when(s3Client.deleteObject((DeleteObjectRequest) any())).thenReturn(null);

        final String hello = "hello!" ;
        final byte[] bytes = hello.getBytes(StandardCharsets.UTF_8) ;
        ResponseBytes<GetObjectResponse> resp = mock(ResponseBytes.class);

        when(resp.asByteArray()).thenReturn(bytes );
        when(resp.asUtf8String()).thenReturn( hello);
        when(s3Client.getObjectAsBytes((GetObjectRequest) any())).thenReturn(resp);
        final Entry<String,ResponseBytes<GetObjectResponse>> data = StorageWrapper.entry("b", resp);

        S3StorageWrapper s3 = () -> s3Client;

        Assert.assertEquals( hello, s3.utf8(data));
        Assert.assertEquals( bytes, s3.bytes(data));

        Assert.assertNull(s3.utf8(null));
        Assert.assertNull(s3.bytes(null));
        Entry<String,ResponseBytes<GetObjectResponse>> nullResp = StorageWrapper.entry("", null);
        Assert.assertNull(s3.utf8(nullResp));
        Assert.assertNull(s3.bytes(nullResp));

        Assert.assertTrue( s3.delete("a","b"));
        Assert.assertTrue( s3.deleteBucket("a"));

        Assert.assertTrue( s3.fileExist("a","b"));
        Assert.assertEquals( data.getValue(), s3.data("a","b" ).getValue());

    }

    @Test
    public void errorInLoadingData(){
        S3Client s3Client = mock(S3Client.class);
        given(s3Client.getObjectAsBytes((GetObjectRequest) any()))
                .willAnswer( invocation -> { throw new Exception("Boom!"); });

        S3StorageWrapper s3 = () -> s3Client;
        Assert.assertNull( s3.data("a","b" ));
    }

    @Test
    public void paginationTest(){
        S3Client s3Client = mock(S3Client.class);
        ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
        ListObjectsV2Response resp = mock(ListObjectsV2Response.class);
        final String hello = "hello!" ;
        final byte[] bytes = hello.getBytes(StandardCharsets.UTF_8) ;
        ResponseBytes<GetObjectResponse> respBytes = mock(ResponseBytes.class);

        when(respBytes.asByteArray()).thenReturn(bytes );
        when(respBytes.asUtf8String()).thenReturn( hello);
        when(s3Client.getObjectAsBytes((GetObjectRequest) any())).thenReturn(respBytes);

        S3Object s3Object = mock(S3Object.class);
        when(resp.contents()).thenReturn( List.of(s3Object, s3Object, s3Object ));

        Iterator<ListObjectsV2Response> iterator = List.of(resp,resp).iterator();

        when( iterable.iterator() ).thenReturn(iterator);
        when(s3Client.listObjectsV2Paginator( (ListObjectsV2Request) any())).thenReturn(iterable);

        S3StorageWrapper s3 = () -> s3Client;
        List<?> list = s3.stream("a", "abc").toList();
        Assert.assertNotNull(list);
        Assert.assertEquals(6, list.size());
    }
}
