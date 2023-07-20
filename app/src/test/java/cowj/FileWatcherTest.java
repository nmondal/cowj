package cowj;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import zoomba.lang.core.io.ZFileSystem;
import zoomba.lang.core.operations.ZJVMAccess;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileWatcherTest {

    final static String FILE_MOD_DIR = "samples/test_scripts" ;
    final static Map<String,Long> contentMap = new HashMap<>();
    final static FileWatcher.FileResourceLoaderFunction<Long> loader = filePath -> System.currentTimeMillis();

    static String getAbsFilePath(String file){
        try {
            return new File(file).getCanonicalFile().getAbsolutePath();
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        ZFileSystem.write(FILE_MOD_DIR + "/foo.txt", "41");
        File dir = new File( FILE_MOD_DIR);
        File[] children = dir.listFiles();
        Assert.assertNotNull(children);
        Arrays.stream(children).forEach( c ->  {
                String absFile = getAbsFilePath(c.getAbsolutePath());
                contentMap.put(absFile, 0L);
        });
        FileWatcher.ofCacheAndRegister( contentMap, loader);
        ZJVMAccess.setProperty( new App(), "PROD_MODE", false);
        Assert.assertFalse( App.isProdMode() );
        final String baseDir = getAbsFilePath( FILE_MOD_DIR );
        FileWatcher.startWatchDog( baseDir );
        // give it sometime to setup...
        Thread.sleep(5000);
    }

    @AfterClass
    public static void afterClass(){
        ZJVMAccess.setProperty( new App(), "PROD_MODE", true);
        Assert.assertTrue( App.isProdMode() );
    }

    @Test
    public void fileWatcherNoErrorTest() throws Exception {
        String absPath = getAbsFilePath(FILE_MOD_DIR + "/foo.txt" );
        ZFileSystem.write(absPath, "42");
        FileWatcher.log("Modified File Externally!");
        // give it sometime to propagate...
        Thread.sleep(1500);
        final long val = contentMap.getOrDefault(absPath, 0L);
        Assert.assertTrue( val > 0L );
    }

    @Test
    public void simulateContainsKeyErrorTest(){
        Map<String,Long> cache = mock(Map.class);
        when(cache.containsKey(any())).thenThrow(new RuntimeException("boom!"));
        FileWatcher fileWatcher = FileWatcher.ofCache( cache, loader);
        Assert.assertFalse( fileWatcher.test("foo") );
    }

    @Test
    public void simulateLoaderErrorTest(){
        Map<String,Long> cache = mock(Map.class);
        when(cache.containsKey(any())).thenReturn(true);
        FileWatcher fileWatcher = FileWatcher.ofCache( cache,  (s) -> { throw new RuntimeException("boom!"); } );
        fileWatcher.accept("foo");
    }

}
