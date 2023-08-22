package cowj.plugins;

import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import com.google.firebase.FirebaseApp;
import cowj.DataSource;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import zoomba.lang.core.types.ZTypes;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GoogleStorageWrapperTest {
    private static final Model model = () -> ".";
    @Test
    public void initTest(){
        DataSource ds = GoogleStorageWrapper.STORAGE.create("foo", Collections.emptyMap(), model);
        Assert.assertNotNull( ds.proxy() );
        Assert.assertTrue( ds.proxy() instanceof GoogleStorageWrapper );
        Assert.assertNotNull( ((GoogleStorageWrapper) ds.proxy()).storage() );
    }

    @Test
    public void initWithProjectID(){
        try (MockedStatic<HttpStorageOptions> storageOptions = mockStatic(HttpStorageOptions.class)) {
            HttpStorageOptions.Builder mockBuilder = mock(HttpStorageOptions.Builder.class);
            when(mockBuilder.setProjectId(ArgumentMatchers.eq("abdef"))).thenReturn(mockBuilder);

            HttpStorageOptions mockOptions = mock(HttpStorageOptions.class);
            Storage storage = mock(Storage.class);
            when(mockOptions.getService()).thenReturn(storage);
            when(mockBuilder.build()).thenReturn(mockOptions);

            storageOptions.when(HttpStorageOptions::newBuilder).thenReturn(mockBuilder);

            DataSource ds = GoogleStorageWrapper.STORAGE.create("foo", Map.of("project-id", "abcdef"), model);
            Assert.assertNotNull( ds.proxy() );
            Assert.assertTrue( ds.proxy() instanceof GoogleStorageWrapper );
            Assert.assertNotNull( ((GoogleStorageWrapper) ds.proxy()).storage() );

            verify(mockBuilder, times(1)).setProjectId("abcdef");
        }
    }

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

    @Test
    public void streamTest(){
        Storage storage = mock( Storage.class);
        Page<Blob> page = mock(Page.class);
        // two items
        Blob b1 = mock(Blob.class);
        when(b1.getStorage()).thenReturn(storage);

        final  String first = "hello, world!" ;
        final  String second = "{ \"a\" : 42 }" ;

        when(b1.getContent()).thenReturn( first.getBytes(StandardCharsets.UTF_8));

        Blob b2 = mock(Blob.class);
        when(b2.getStorage()).thenReturn(storage);
        when(b2.getContent()).thenReturn( second.getBytes(StandardCharsets.UTF_8));

        when(storage.list(anyString(), any(), any())).thenReturn(page);
        when(page.streamAll()).thenReturn(Stream.of(b1, b2));
        GoogleStorageWrapper gsw = () -> storage;
        // ensure stream has some stuff
        Stream<String> as = gsw.allContent("foo", "");
        Assert.assertNotNull(as);
        List<String> res = as.toList();
        Assert.assertEquals(2, res.size());
        Assert.assertEquals(first, res.get(0));
        Assert.assertEquals(second, res.get(1));

        when(page.streamAll()).thenReturn(Stream.of(b1, b2));
        Stream<Object> ao = gsw.allData("foo", "");
        List<Object> r = ao.toList();

        Assert.assertEquals(2, r.size());
        Assert.assertEquals(first, r.get(0));
        Assert.assertTrue( r.get(1) instanceof Map );
    }

    @Test
    public void createPrivateBucketTest() {
        Storage storage = mock(Storage.class);
        final String bucket = "foo-bar";
        final String location = "abc";
        Bucket b = mock(Bucket.class);

        ArgumentMatcher<BucketInfo> expectedBucketInfo = (BucketInfo bi) ->
                location.equals(bi.getLocation())
                        && bucket.equals(bi.getName())
                        /// public access prevention should be enforced for private buckets.
                        && bi.getIamConfiguration().getPublicAccessPrevention()
                        .equals(BucketInfo.PublicAccessPrevention.ENFORCED);
        when(storage.create(argThat(expectedBucketInfo))).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Assert.assertEquals(b, gsw.createBucket(bucket, location, true));
    }

    @Test
    public void createPublicBucketTest() {
        Storage storage = mock(Storage.class);
        final String bucket = "foo-bar";
        final String location = "abc";
        Bucket b = mock(Bucket.class);

        ArgumentMatcher<BucketInfo> expectedBucketInfo = (BucketInfo bi) ->
                location.equals(bi.getLocation())
                        && bucket.equals(bi.getName())
                        /// iam configuration should not be set for public buckets.
                        && bi.getIamConfiguration() == null;
        when(storage.create(argThat(expectedBucketInfo))).thenReturn(b);
        GoogleStorageWrapper gsw = () -> storage;
        Assert.assertEquals(b, gsw.createBucket(bucket, location, false));
    }

    @Test
    public void deleteBucketTest() {
        Storage storage = mock(Storage.class);
        final String bucket = "foo-bar";

        /// trying with both true and false both to check whether we
        /// actually return what storage.delete returns
        when(storage.delete(ArgumentMatchers.eq(bucket))).thenReturn(true);
        GoogleStorageWrapper gsw = () -> storage;
        Assert.assertTrue(gsw.deleteBucket(bucket));

        when(storage.delete(ArgumentMatchers.eq(bucket))).thenReturn(false);
        Assert.assertFalse(gsw.deleteBucket(bucket));
    }

    @Test
    public void deleteTest() {
        Storage storage = mock(Storage.class);
        final String bucket = "foo-bar";
        final String path = "abc/def";

        /// trying with both true and false both to check whether we
        /// actually return what storage.delete returns
        BlobId blob = BlobId.of(bucket, path);
        when(storage.delete(ArgumentMatchers.eq(blob))).thenReturn(true);
        GoogleStorageWrapper gsw = () -> storage;
        Assert.assertTrue(gsw.delete(bucket, path));

        when(storage.delete(ArgumentMatchers.eq(blob))).thenReturn(false);
        Assert.assertFalse(gsw.delete(bucket, path));
    }
}
