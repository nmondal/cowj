package cowj.plugins;

import cowj.DataSource;
import org.junit.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Currently no support for windows, need to think to give support
 */
public class VersionedStorageTest {
    static final String BUCKET_NAME = "fs-bar-versioned";

    static final String MOUNT_PT = "./build" ;

    static VersionedFileStorage fs;

    @BeforeClass
    public static void beforeClass() {
        Assume.assumeTrue(File.separator.equals("/") );
        VersionedFileStorage.deleteRecurse(Paths.get(MOUNT_PT + "/" + BUCKET_NAME) );
        DataSource ds  = VersionedFileStorage.STORAGE.create( "_vfs_", Map.of(VersionedFileStorage.MOUNT_POINT, MOUNT_PT ), () ->"");
        Assert.assertTrue( ds.proxy() instanceof  VersionedFileStorage );
        fs = (VersionedFileStorage)ds.proxy();
        Assert.assertNotNull(fs.absMountPoint);
        Assert.assertTrue( fs.createBucket(BUCKET_NAME, "", false ));
        Assert.assertFalse( fs.createBucket(BUCKET_NAME, "", false ));
        Assert.assertFalse( VersionedFileStorage.deleteRecurse( Paths.get( "/bla/bla/bla") ));
        Assert.assertFalse( VersionedFileStorage.deleteRecurse( Paths.get( "/opt") ));
    }

    @AfterClass
    public static void afterClass(){
        if ( fs == null ) return;
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
    public void nonExistentRead(){
        // nonexistent read ???
        Assert.assertNull( fs.load( BUCKET_NAME, "asaadadada") );
    }

    @Test
    public void specialKeyLoadingTest(){
        final String[] data = new String[] { "Crazy!1" , "Crazy!2" } ;
        // first time
        Assert.assertTrue( fs.dumps( BUCKET_NAME, "a/__latest__/b/1", data[0] ));
        Assert.assertTrue( fs.dumps( BUCKET_NAME, "a/__latest__/b/2", data[1] ));

        // Now check
        List<String> allData = fs.stream( BUCKET_NAME, "a" ).map(Map.Entry::getValue).toList();
        Assert.assertEquals(2, allData.size() );
        Assert.assertEquals( data[0], allData.get(0));
        Assert.assertEquals( data[1], allData.get(1));

        final String[] data_v2 = new String[] { "Crazy!12" , "Crazy!22" } ;

        // then again
        Assert.assertTrue( fs.dumps( BUCKET_NAME, "a/__latest__/b/1", data_v2[0] ));
        Assert.assertTrue( fs.dumps( BUCKET_NAME, "a/__latest__/b/2", data_v2[1] ));
        // test again

        allData = fs.stream( BUCKET_NAME, "a" ).map(Map.Entry::getValue).toList();
        Assert.assertEquals(2, allData.size() );
        Assert.assertEquals( data_v2[0], allData.get(0));
        Assert.assertEquals( data_v2[1], allData.get(1));
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
        Set<String> set = fs.stream(BUCKET_NAME, "1/").map(Map.Entry::getValue).collect(Collectors.toSet());
        Assert.assertEquals(10, set.size() );
        set = fs.stream(BUCKET_NAME, "0/").map(Map.Entry::getValue).collect(Collectors.toSet());
        Assert.assertEquals(10, set.size() );
    }
}
