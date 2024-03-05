package cowj.plugins;

import cowj.DataSource;
import cowj.StorageWrapper;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VersionedMemoryStorageTest {
    static final String BUCKET_NAME = "foo";

    static final Map<String,Map<String,String>> dataMemory = new HashMap<>();

    static MemoryBackedStorage.VersionedMemoryStorage ms = () -> dataMemory; // crazy, is not it? Hmm...

    @BeforeClass
    public static void beforeClass() {
        Assert.assertFalse(ms.delete(BUCKET_NAME, "bar"));
        Assert.assertTrue(ms.createBucket(BUCKET_NAME, "", false));
        Assert.assertFalse(ms.createBucket(BUCKET_NAME, "", false));
    }

    @AfterClass
    public static void afterClass() {
        Assert.assertTrue(ms.deleteBucket(BUCKET_NAME));
        Assert.assertFalse(ms.deleteBucket(BUCKET_NAME));
        dataMemory.clear();
    }

    @Test
    public void storageTest() {
        DataSource ds = MemoryBackedStorage.STORAGE.create("bar", Map.of(MemoryBackedStorage.VERSIONED, true), () -> "");
        Assert.assertNotNull(ds);
        Assert.assertTrue(ds.proxy() instanceof MemoryBackedStorage.VersionedMemoryStorage);
    }

    @Test
    public void safeBooleanTest() {
        Assert.assertTrue(ms.safeBoolean(() -> {
        }));
        Assert.assertFalse(ms.safeBoolean(() -> {
            throw new RuntimeException();
        }));
    }

    @Test
    public void creationDeletionTest() {
        Assert.assertTrue(ms.dumpb(BUCKET_NAME, "bar", "hello!".getBytes(StandardCharsets.UTF_8)));
        Assert.assertEquals("hello!", ms.loads(BUCKET_NAME, "bar"));
        byte[] bytes = ms.loadb(BUCKET_NAME, "bar");
        Assert.assertNotNull(bytes);
        String s = new String(bytes);
        Assert.assertEquals("hello!", s);
        Assert.assertTrue(ms.fileExist(BUCKET_NAME, "bar"));
        Assert.assertTrue(ms.delete(BUCKET_NAME, "bar"));
        Assert.assertFalse(ms.delete(BUCKET_NAME, "bar"));
    }

    @Test
    public void otherFunctionsTest(){
        Assert.assertNull(ms.bytes(null));
        Assert.assertNull(ms.bytes(StorageWrapper.entry(null, null)));
        Assert.assertNull(ms.utf8(null));
        Assert.assertNull(ms.utf8(StorageWrapper.entry(null, null)));
    }

    @Test
    public void streamTest(){
        final String streamBucket = "stream" ;
        Assert.assertTrue( ms.createBucket(streamBucket, "", false));
        final int max = 10;
        for ( int i=0; i< max; i++ ){
            Object data = Map.of("x", i );
            Assert.assertTrue( ms.dump(streamBucket, "x/" + i, data ));
        }
        Set<Integer> set = ms.allData( streamBucket , "x/" )
                .map( o -> (int)((Map)o).get("x") ).collect(Collectors.toSet());
        Assert.assertEquals( max, set.size() );
        Set<String> setS = ms.allContent( streamBucket , "x/" ).collect(Collectors.toSet());
        Assert.assertEquals( max, setS.size() );
        Set<Map.Entry<?,?>> setE = ms.entriesData( streamBucket , "x/" ).collect(Collectors.toSet());
        Assert.assertEquals( max, setE.size() );
    }

    @Test
    public void versionsTest(){

        IntStream.range(0,10).parallel().forEach( (i) ->{
            IntStream.range(0,10).parallel().forEach( (j) -> {
                ms.dumps(BUCKET_NAME, String.valueOf(i), String.valueOf(j));
            });
        } );
        IntStream.range(0,10).parallel().forEach( (i) ->{
            final String keyName = String.valueOf(i) ;
            Set<String> versions = ms.versions(BUCKET_NAME, keyName).collect(Collectors.toSet());
            Assert.assertEquals(10, versions.size() );
            Set<String> values = versions.stream().map( ver -> ms.dataAtVersion(BUCKET_NAME, keyName, ver)).collect(Collectors.toSet());
            Assert.assertEquals(10, values.size() );
            Assert.assertEquals( String.valueOf(0), ms.latestVersion(BUCKET_NAME, keyName));
            List<String> vl = ms.listVersions(BUCKET_NAME, keyName, 0, 3);
            Assert.assertEquals( List.of("0","1","2"), vl);
        } );
        // test something that would not have anything
        Assert.assertEquals(Collections.emptyList(), ms.listVersions( BUCKET_NAME, "shooh!", 0, 1000) );
        Assert.assertNull(ms.latestVersion( BUCKET_NAME, "shooh!"));
        final String s = ms.stringAtVersion( BUCKET_NAME, "0", "0");
        Assert.assertNotNull(s);
    }
}
