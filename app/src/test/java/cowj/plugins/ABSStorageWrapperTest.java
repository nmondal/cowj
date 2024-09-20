package cowj.plugins;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import cowj.DataSource;

import org.junit.Assert;
import org.junit.Test;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobServiceClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ABSStorageWrapperTest {

    @Test
    public void creationTest(){
        DataSource ds = ABSStorageWrapper.STORAGE.create( "foo", Map.of("account", "foo-bar", "page-size", 42 ) , ()-> ".");
        ABSStorageWrapper abs = ds.any();
        Assert.assertNotNull( abs.client() );
        // check page size
        Assert.assertEquals(42, abs.pageSize());
        // Now test for bytes and utf8 
        final String data = "Hello!" ;
        BinaryData bd = BinaryData.fromString(data);
        Assert.assertArrayEquals( data.getBytes() ,  abs.bytes(  Map.entry("foo", bd) ) );
        Assert.assertEquals( data,  abs.utf8(  Map.entry("foo", bd) ) );
    }

    @Test
    public void failedCreationTest(){
        IllegalArgumentException ex = Assert.assertThrows(
                IllegalArgumentException.class, () -> ABSStorageWrapper.STORAGE.create( "foo", Map.of() , ()-> "."));
        Assert.assertTrue( ex.getMessage().contains("'account'"));
    }
    
    static ABSStorageWrapper creatAbsStorageWrapper( BlobServiceClient bc){
        return new ABSStorageWrapper() {
            @Override
            public BlobServiceClient client() {
                return bc;
            }

            @Override
            public int pageSize(){
                return 42;
            }
        };
    }

    @Test
    public void bucketCreationTests(){
        // Create Bucket 
        BlobServiceClient bc = mock(BlobServiceClient.class);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertTrue(abs.createBucket("foo", null, false));   
        // now failure 
        bc = mock(BlobServiceClient.class);
        when(bc.createBlobContainer(any())).thenThrow(new IllegalArgumentException());
        abs = creatAbsStorageWrapper(bc);
        Assert.assertFalse(abs.createBucket("foo", null, false));   
    }

    @Test
    public void bucketDeletionTests(){
        // Delete Bucket
        BlobServiceClient bc = mock(BlobServiceClient.class);
        when(bc.deleteBlobContainerIfExists(any())).thenReturn(true);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertTrue(abs.deleteBucket("foo"));
        // now failure
        bc = mock(BlobServiceClient.class);
        when(bc.deleteBlobContainerIfExists(any())).thenReturn(false);
        abs = creatAbsStorageWrapper(bc);
        Assert.assertFalse(abs.deleteBucket("foo"));
    }

    @Test
    public void pathDeletionTests(){
        // Delete Path
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        BlobClient blc = mock(BlobClient.class);
        when(blc.deleteIfExists()).thenReturn(true);
        when(bcc.getBlobClient(any())).thenReturn(blc);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertTrue(abs.delete("foo", "bar"));
        // now failure 
        bc = mock(BlobServiceClient.class);
        when(bc.getBlobContainerClient(any())).thenThrow( new IllegalArgumentException());
        abs = creatAbsStorageWrapper(bc);
        Assert.assertFalse(abs.delete("foo", "bar"));
    }

    @Test
    public void pathExistsTests(){
        // Exists Path
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        BlobClient blc = mock(BlobClient.class);
        when(blc.exists()).thenReturn(true);
        when(bcc.getBlobClient(any())).thenReturn(blc);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertTrue(abs.fileExist("foo", "bar"));

        // now failure
        bc = mock(BlobServiceClient.class);
        when(bc.getBlobContainerClient(any())).thenThrow( new IllegalArgumentException());
        abs = creatAbsStorageWrapper(bc);
        Assert.assertFalse(abs.fileExist("foo", "bar"));
    }

    @Test
    public void dataUploadTests(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        BlobClient blc = mock(BlobClient.class);
        when(bcc.getBlobClient(any())).thenReturn(blc);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertTrue(abs.dumps("foo", "bar", ""));

        bc = mock(BlobServiceClient.class);
        bcc = mock(BlobContainerClient.class);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        abs = creatAbsStorageWrapper(bc);
        Assert.assertFalse(abs.dumpb("foo", "bar", new byte[]{ 0, 1, 2 } ));
    }

    @Test
    public void dataReadTest(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        BlobClient blc = mock(BlobClient.class);
        BinaryData bd = BinaryData.fromString("Hello!") ;
        when(blc.downloadContent()).thenReturn( bd );
        when(bcc.getBlobClient(any())).thenReturn(blc);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        // now read
        Map.Entry<String,BinaryData> entry = abs.data( "foo", "bar");
        Assert.assertEquals( "bar", entry.getKey() );
        Assert.assertEquals( bd, entry.getValue() );
    }

    @Test
    public void dataStreamTest(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        PagedIterable<BlobItem> pi = mock(PagedIterable.class);
        List<BlobItem>  l =  IntStream.range(0,5).boxed().map(i -> {
            BlobItem b = mock(BlobItem.class);
            final String name = String.valueOf(i+1);
            when(b.getName()).thenReturn( name ) ;
            BlobClient blc = mock(BlobClient.class);
            BinaryData bd = BinaryData.fromString(name) ;
            when(blc.downloadContent()).thenReturn(bd);
            when(bcc.getBlobClient( name)).thenReturn(blc);
            return b;
        }).toList();
        when(pi.stream()).thenReturn( l.stream());
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        when(bcc.listBlobs( any(), any())).thenReturn( pi );
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        // now read
        List<Map.Entry<String,BinaryData>> ll = abs.stream( "foo", "bar").toList();
        Assert.assertEquals(l.size(), ll.size());
        ll.stream().forEach( e ->{
            Assert.assertEquals(e.getKey(), e.getValue().toString());
        });
    }

    @Test
    public void dataAtVersionTest(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        BlobClient blc = mock(BlobClient.class);
        BlobClient blv = mock(BlobClient.class);
        when(blc.getVersionClient( any())).thenReturn(blv);
        BinaryData bd = BinaryData.fromString("Hello!") ;
        when(blv.downloadContent()).thenReturn( bd );
        when(bcc.getBlobClient(any())).thenReturn(blc);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        Assert.assertEquals( bd, abs.dataAtVersion("foo", "bar", "v111"));
    }

    @Test
    public void dataVersionStreamNoFileExistsTest(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobClient ub = mock(BlobClient.class);
        BlobContainerClient bcc = mock(BlobContainerClient.class);
        when(bcc.getBlobClient(any())).thenReturn(ub);
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);

        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        // now read
        List<String> versions = abs.versions( "foo", "bar").toList();
        Assert.assertTrue(versions.isEmpty());
    }

    @Test
    public void dataVersionStreamTest(){
        BlobServiceClient bc = mock(BlobServiceClient.class);
        BlobClient ub = mock(BlobClient.class);
        when(ub.exists()).thenReturn(true);

        BlobContainerClient bcc = mock(BlobContainerClient.class);
        when(bcc.getBlobClient(any())).thenReturn(ub);

        PagedIterable<BlobItem> pi = mock(PagedIterable.class);
        List<BlobItem>  l =  IntStream.range(0,5).boxed().map(i -> {
            BlobItem b = mock(BlobItem.class);
            final String name = String.valueOf(i+1);
            when(b.getName()).thenReturn( "bar" ) ;
            when(b.getVersionId() ).thenReturn(name );
            BlobClient blc = mock(BlobClient.class);
            BinaryData bd = BinaryData.fromString(name) ;
            when(blc.downloadContent()).thenReturn(bd);
            when(bcc.getBlobClient( name)).thenReturn(blc);
            return b;
        }).toList();
        when(pi.stream()).thenReturn( l.stream());
        when(bc.getBlobContainerClient(any())).thenReturn(bcc);
        when(bcc.listBlobs( any(), any())).thenReturn( pi );
        ABSStorageWrapper abs = creatAbsStorageWrapper(bc);
        // now read
        List<String> versions = abs.versions( "foo", "bar").toList();
        Assert.assertEquals(l.size(), versions.size());
        // And these should be sorted with latest version first
        IntStream.range(0,5).boxed().forEach( i ->{
            String vId = String.valueOf(5-i);
            Assert.assertEquals( vId, versions.get(i));
        });
    }
}
