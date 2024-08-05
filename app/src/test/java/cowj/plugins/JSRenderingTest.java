package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.ModelRunner;
import cowj.ModelRunnerTest;
import org.junit.*;

import javax.script.ScriptException;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;

public class JSRenderingTest {

    private static ModelRunner mr ;

    final static String ssr = "samples/ssr/ssr.yaml" ;

    @BeforeClass
    public static void startSpark() {
        mr = ModelRunnerTest.runModel(ssr);
    }

    @AfterClass
    public static void stopSpark() throws Exception{
        if ( mr == null ) return;
        mr.stop();
        mr = null;
        System.gc(); // garbage collect trigger
        Thread.sleep(1500);
    }

    @Test
    public void renderingReactTest(){
        String resp = ModelRunnerTest.get("http://localhost:5151", "/app");
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.contains("Fibonacci"));
    }

    @Test
    public void invalidRenderingTest() throws Exception {
        JSRendering rendering = DataSource.dataSource("react-ssr");
        Assert.assertNotNull(rendering);
        // get an error - in the script itself
        String absPath = new File( ssr ).getAbsoluteFile().getCanonicalPath() ;
        EitherMonad<String> em = rendering.render( absPath, "foo-bar");
        Assert.assertTrue( em.inError() );
        // run a script that does not return a map
        absPath = new File( "samples/test_scripts/null_return.js" ).getAbsoluteFile().getCanonicalPath() ;
        em = rendering.render( absPath, "foo-bar");
        Assert.assertTrue( em.inError() );
    }

    @Test
    public void invalidConfigTest(){
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            JSRendering.SSR.create( "foo-bar", Map.of( "context", List.of( ssr ) ) , () ->".");
        });
        Assert.assertTrue( exception.getCause() instanceof NoSuchFileException);

        exception = assertThrows(RuntimeException.class, () -> {
            String absPath = new File( ssr ).getAbsoluteFile().getCanonicalPath() ;
            JSRendering.SSR.create( "foo-bar", Map.of( "context", List.of( absPath ) ) , () ->".");
        });
        Assert.assertTrue( exception.getCause() instanceof ScriptException);
    }
}
