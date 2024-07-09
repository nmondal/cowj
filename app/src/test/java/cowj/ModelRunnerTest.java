package cowj;

import cowj.plugins.CurlWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.operations.ZJVMAccess;
import zoomba.lang.core.types.ZTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class ModelRunnerTest {

    final String hello = "samples/hello/hello.yaml" ;
    final String proxy = "samples/proxy/proxy.yaml" ;
    final String test = "samples/test_scripts/test.yml" ;
    final String jython = "samples/jython/jython.yaml" ;

    private ModelRunner mr ;

    @After
    public void stopSpark() throws Exception{
        if ( mr == null ) return;
        mr.stop();
        mr = null;
        System.gc(); // garbage collect trigger
        Thread.sleep(1500);
    }

    public static ModelRunner runModel(String modelPath){
        ModelRunner mr = ModelRunner.fromModel(modelPath) ;
        mr.run();
        try {
            Thread.sleep(1000);
        }catch (Exception ignored){}
        return mr;
    }

    public static boolean ping( String base, String path ){
        ZWeb zWeb = new ZWeb(base);
        try {
            ZWeb.ZWebCom r = zWeb.get(path, Collections.emptyMap());
            return  r.bytes != null ;
        }catch (Exception ignored){}
        return false;
    }

    public static String get( String base, String path ){
        ZWeb zWeb = new ZWeb(base);
        try {
            ZWeb.ZWebCom r = zWeb.get(path, Collections.emptyMap());
            return  r.body() ;
        }catch (Exception ignored){}
        return null;
    }

    public static String post( String base, String path, String body ){
        ZWeb zWeb = new ZWeb(base);
        try {
            ZWeb.ZWebCom r = zWeb.post(path, Collections.emptyMap(), body);
            return  r.body() ;
        }catch (Exception ignored){}
        return null;
    }
    @Test
    public void bootTest(){
        ModelRunner mr = runModel(hello);
        Assert.assertTrue( ping("http://localhost:5003", "/hello/z"));
        mr.stop();
        Assert.assertFalse( ping("http://localhost:5003", "/hello/z"));
    }

    @Test
    public void routesTest(){
        mr = runModel(hello);
        final String expected = "hello, world!" ;
        // get routes
        // Java binary
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/b"));
        // cache run
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/b"));

        // Groovy
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/g"));
        // Javascript
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/j"));
        // python
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/p"));
        // kotlin
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/k"));
        // zoomba
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/z"));
        // cache run
        Assert.assertEquals( expected, get("http://localhost:5003", "/hello/j"));
        // post routes
        Assert.assertEquals( expected, post("http://localhost:5003", "/hello", ""));

        // routes returning null
        Assert.assertEquals( "", get("http://localhost:5003", "/null"));
    }

    @Test
    public void jythonIntegrationTest(){
        mr = runModel(jython);
        final String expected = "Hello :42!" ;
        final String body = ZTypes.jsonString( Map.of("name", "42"));
        Assert.assertEquals( expected, post("http://localhost:5009", "/python", body ));
        Assert.assertEquals( expected, post("http://localhost:5009", "/zmb", body ));
        Assert.assertEquals( expected, post("http://localhost:5009", "/jackson", body ));
    }
    @Test
    public void multiThreadedPostTest() throws Exception{
        mr = runModel(hello);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final int times = 10;
        final String payLoad1 = ZTypes.jsonString( Map.of("x", 42 ));
        Thread t1 = new Thread( () ->{
            for ( int i=0;  i < times; i++ ){
                try {
                    String res = post("http://localhost:5003", "/identity", payLoad1 );
                    Assert.assertEquals(payLoad1, res);
                }catch (Throwable t){
                    System.err.println(t);
                    errors.add(t);
                }
            }
        });
        final String payLoad2 = ZTypes.jsonString( Map.of("y", 42 ));
        Thread t2 = new Thread( () ->{
            for ( int i=0;  i < times; i++ ){
                try {
                    String res = post("http://localhost:5003", "/identity", payLoad2 );
                    Assert.assertEquals(payLoad2, res);
                }catch (Throwable t){
                    System.err.println(t);
                    errors.add(t);
                }
            }
        });
        t1.start();
        t2.start();
        Thread.sleep(1500);
        // Nothing should be erring out...
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void errorCheckZMB() throws Exception {
        mr = runModel(hello);
        ZWeb zWeb = new ZWeb("http://localhost:5003");
        ZWeb.ZWebCom r = zWeb.get("/error/z", Collections.emptyMap());
        Assert.assertEquals( "boom!", r.body());
        Assert.assertEquals( 418, r.status);
    }

    @Test
    public void errorCheckJSR() throws Exception {
        mr = runModel(hello);
        ZWeb zWeb = new ZWeb("http://localhost:5003");
        ZWeb.ZWebCom r = zWeb.get("/error/j", Collections.emptyMap());
        Assert.assertEquals( "boom!", r.body());
        Assert.assertEquals( 418, r.status);
    }

    @Test
    public void runTimeErrorJSR() throws Exception {
        mr = runModel(hello);
        try {
            ZJVMAccess.setProperty( new App(), "PROD_MODE", false);
            ZWeb zWeb = new ZWeb("http://localhost:5003");
            ZWeb.ZWebCom r = zWeb.get("/runtime_error", Collections.emptyMap());
            // this should show up in non prod mode
            Assert.assertTrue(r.body().contains("bar"));
            Assert.assertEquals(500, r.status);
        }finally {
            ZJVMAccess.setProperty( new App(), "PROD_MODE", true);
        }
        ZWeb zWeb = new ZWeb("http://localhost:5003");
        ZWeb.ZWebCom r = zWeb.get("/runtime_error", Collections.emptyMap());
        // On prod mode, this should point to right error in the script
        // Are we literally re-inventing PHP? May be.
        Assert.assertTrue(r.body().contains("Internal Server Error!"));
        Assert.assertEquals(500, r.status);
    }

    @Test
    public void proxyTest() {
        mr = runModel(hello);
        // proxy route
        String resp = get("http://localhost:5003", "/users?id=1");
        Assert.assertNotNull(resp);
        Object r = ZTypes.json(resp);
        Assert.assertTrue( r instanceof List);
        Assert.assertEquals( 1, ((List<?>) r).size());
    }

    @Test
    public void proxyForwardTransformTest(){
        mr = runModel(proxy);
        // proxy route
        String resp = post("http://localhost:5004", "/echo", "");
        Assert.assertNotNull(resp);
        Assume.assumeFalse( "We are in a troubling network, or postman-echo got a problem!",
                resp.contains("timed out")); // this can happen with bad network
        // if it does happen, it should skip
        Object r = ZTypes.json(resp);
        Assert.assertTrue( r instanceof Map);
        Object h = ((Map) r).get("headers");
        Assert.assertTrue( h instanceof Map);
        Assert.assertEquals( "42", ((Map<?, ?>) h).get("cowj"));
        Object b = ((Map<?, ?>) r).get("json");
        Assert.assertTrue( b instanceof Map);
        Object bs = ((Map<?, ?>) b).keySet().iterator().next();
        Assert.assertEquals( "hello,proxy!", bs);
    }

    @Test
    public void proxyTransformErrorCheck() throws Exception {
        mr = runModel(proxy);
        ZWeb zWeb = new ZWeb("http://localhost:5004");
        ZWeb.ZWebCom r = zWeb.get("/error", Collections.emptyMap());
        Assert.assertEquals( "proxy transform boom!", r.body());
        Assert.assertEquals( 500, r.status);
    }

    @Test
    public void proxyDestinationErrorCheck() throws Exception {
        try {
            ZJVMAccess.setProperty( new App(), "PROD_MODE", false);
            mr = runModel(proxy);
            ZWeb zWeb = new ZWeb("http://localhost:5004");
            ZWeb.ZWebCom r = zWeb.get("/wp", Collections.emptyMap());
            Assert.assertEquals(500, r.status);
            Assert.assertNotNull(r.body());
            Assert.assertTrue(r.body().contains(CurlWrapper.PROXY_ROUTE_FAILED_ERROR_PREFIX));
        }finally {
            ZJVMAccess.setProperty( new App(), "PROD_MODE", true);
        }
    }

    @Test
    public void pathRelativeScriptTest() throws Exception {
        mr = runModel(test);
        ZWeb zWeb = new ZWeb("http://localhost:5234");
        ZWeb.ZWebCom r = zWeb.get("/call", Collections.emptyMap());
        Assert.assertFalse( r.body().isEmpty() );
        Assert.assertEquals( 500, r.status);
    }
}
