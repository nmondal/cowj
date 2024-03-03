package cowj.plugins;

import cowj.DataSource;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VersionedStorageTest {
    static final String BUCKET_NAME = "fs-bar-versioned";

    static final String MOUNT_PT = "./build" ;

    static VersionedFileStorage fs;

    @BeforeClass
    public static void beforeClass() {
        VersionedFileStorage.deleteRecurse(Paths.get(MOUNT_PT + "/" + BUCKET_NAME) );
        DataSource ds  = VersionedFileStorage.STORAGE.create( "_vfs_", Map.of(VersionedFileStorage.MOUNT_POINT, MOUNT_PT ), () ->"");
        Assert.assertTrue( ds.proxy() instanceof  VersionedFileStorage );
        fs = (VersionedFileStorage)ds.proxy();
        Assert.assertNotNull(fs.absMountPoint);
        Assert.assertTrue( fs.createBucket(BUCKET_NAME, "", false ));
        Assert.assertFalse( fs.createBucket(BUCKET_NAME, "", false ));
        Assert.assertFalse( VersionedFileStorage.deleteRecurse( Paths.get( "/cdrom") ));
    }

    @AfterClass
    public static void afterClass(){
        fs.deleteBucket(BUCKET_NAME);
        Assert.assertFalse( fs.deleteBucket(BUCKET_NAME));
    }

    public void readWriteTest(String keyName){
        Assert.assertNull( VersionedFileStorage.readString( Paths.get("/xxx/xxx/")));
        Assert.assertFalse( fs.dumps("xxxx", "xxx", "xxx"));
        final String data = "Hello!" ;
        Assert.assertTrue( fs.dumps( BUCKET_NAME, keyName, data ));
        String res = fs.data(BUCKET_NAME, keyName).getValue();
        Assert.assertEquals( data, res);
        Assert.assertTrue( fs.fileExist( BUCKET_NAME, keyName));
        Assert.assertTrue( fs.delete( BUCKET_NAME, keyName));
        Assert.assertFalse( fs.delete( BUCKET_NAME, keyName));
        Assert.assertFalse( fs.fileExist( BUCKET_NAME, keyName));
    }

    @Test
    public void readWriteNonSlashKeyTest(){
        readWriteTest("foo");
    }

    @Test
    public void readWritSlashKeyTest(){
        readWriteTest("a/b/c");
    }

    @Test
    public void versionedTest(){
        final String keyName = "ver_path" ;
        IntStream.range(0,10).parallel().forEach( (i) ->{
            Assert.assertTrue( fs.dumps( BUCKET_NAME, keyName, String.valueOf(i) ));
        });
        Set<String> allData = fs.versions( BUCKET_NAME, keyName)
                .map( ver -> fs.dataAtVersion(BUCKET_NAME, keyName,ver )).collect(Collectors.toSet());
        Assert.assertEquals(10, allData.size() );
    }

    @Test
    public void prefixedStreamTest(){
        List<String> l = fs.allContent("xxxx", "xxx").toList();
        Assert.assertTrue(l.isEmpty());

        for ( int i = 0; i < 20 ; i++ ){
            String key =  i /10 + "/" + i % 10 ;
            Assert.assertTrue( fs.dumps(BUCKET_NAME, key, String.valueOf(i)) );
        }
        // Now read say "1/"
        Set<String> set = fs.stream(BUCKET_NAME, "1/").filter( s -> !s.getValue().isEmpty()).map(Map.Entry::getValue).collect(Collectors.toSet());
        Assert.assertEquals(10, set.size() );
        set = fs.stream(BUCKET_NAME, "0/").filter( s -> !s.getValue().isEmpty()).map(Map.Entry::getValue).collect(Collectors.toSet());
        Assert.assertEquals(10, set.size() );
    }
}
