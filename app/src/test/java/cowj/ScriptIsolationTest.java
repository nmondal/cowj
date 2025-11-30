package cowj;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import zoomba.lang.core.types.ZTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cowj.ModelRunnerTest.post;
import static cowj.ModelRunnerTest.runModel;
import static org.junit.Assert.assertTrue;

/**
 * These tests are about script isolation
 * so that every request should be a "pure" function call of sorts
 */
public class ScriptIsolationTest {

    static final String isolationTestFile = "samples/isolation/isolation.yaml" ;

    private static ModelRunner mr ;

    @BeforeClass
    public static void startSpark(){
        mr = runModel(isolationTestFile);
    }

    @AfterClass
    public static void stopSpark() throws Exception{
        if ( mr == null ) return;
        mr.stop();
        mr = null;
        System.gc(); // garbage collect trigger
        Thread.sleep(1500);
    }

    void runIsolation(String path) throws Exception {

        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService es = Executors.newFixedThreadPool(10);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++ ){
            final String payLoad = ZTypes.jsonString( Map.of(String.format("v_%d",i), i ));
            es.submit( ( ) ->{
                try {
                    String res = post("http://localhost:5342", path, payLoad );
                    Assert.assertEquals(payLoad, res);
                }catch (Throwable t){
                    //System.err.println(t);
                    errors.add(t);
                }
            });
        }
        es.shutdown();
        assertTrue( es.awaitTermination( 100, TimeUnit.SECONDS ) );
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("for path [%s] time taken (ms) : %d %n", path, elapsed );
        // Nothing should be erring out...
        assertTrue(errors.isEmpty());
    }

    @Test
    public void isolationZMBTest() throws Exception{
        runIsolation("/z");
    }

    @Test
    public void isolationGraalJSTest() throws Exception{
        runIsolation("/j");
    }

    @Test
    public void isolationJythonTest() throws Exception{
        runIsolation("/p2");
    }

    @Test
    public void isolationKotlinTest() throws Exception{
        runIsolation("/k");
    }

    @Test
    public void isolationGroovyTest() throws Exception{
        runIsolation("/g");
    }

    @Test
    public void isolationGraalPython() throws Exception{
        runIsolation("/p3");
    }
}
