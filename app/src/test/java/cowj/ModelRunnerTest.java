package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModelRunnerTest {

    final String hello = "samples/hello/hello.yaml" ;
    final String proxy = "samples/proxy/proxy.yaml" ;

    private ModelRunner mr ;

    @After
    public void stopSpark() throws Exception{
        if ( mr == null ) return;
        mr.stop();
        mr = null;
        Thread.sleep(1500);
    }

    static ModelRunner runModel(String modelPath){
        ModelRunner mr = ModelRunner.fromModel(modelPath) ;
        Thread server = new Thread(mr);
        server.start();
        try {
            Thread.sleep(5000);
        }catch (Exception ignored){}
        return mr;
    }

    static boolean ping( String base, String path ){
        ZWeb zWeb = new ZWeb(base);
        try {
            ZWeb.ZWebCom r = zWeb.get(path, Collections.emptyMap());
            return  r.bytes != null ;
        }catch (Exception ignored){}
        return false;
    }

    static String get( String base, String path ){
        ZWeb zWeb = new ZWeb(base);
        try {
            ZWeb.ZWebCom r = zWeb.get(path, Collections.emptyMap());
            return  r.body() ;
        }catch (Exception ignored){}
        return null;
    }

    static String post( String base, String path, String body ){
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
        Assert.assertTrue( ping("http://localhost:1003", "/hello/z"));
        mr.stop();
        Assert.assertFalse( ping("http://localhost:1003", "/hello/z"));
    }

    @Test
    public void routesTest(){
        mr = runModel(hello);
        final String expected = "hello, world!" ;
        // get routes
        // Java binary
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/b"));
        // Groovy
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/g"));
        // Javascript
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/j"));
        // python
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/p"));
        // zoomba
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/z"));
        // post routes
        Assert.assertEquals( expected, post("http://localhost:1003", "/hello", ""));
    }

    @Test
    public void errorCheck(){
        mr = runModel(hello);
        ZWeb zWeb = new ZWeb("http://localhost:1003");
        try {
            ZWeb.ZWebCom r = zWeb.get("/error", Collections.emptyMap());
            Assert.assertEquals( "boom!", r.body());
            Assert.assertEquals( 418, r.status);
        }catch (Exception ex){
            Assert.fail();
        }
    }

    @Test
    public void proxyTest(){
        mr = runModel(hello);
        // proxy route
        String resp = get("http://localhost:1003", "/users");
        Assert.assertNotNull(resp);
        try {
            Object r = ZTypes.json(resp);
            Assert.assertTrue( r instanceof List);
            Assert.assertFalse( ((List<?>) r).isEmpty());
        }catch (Exception ex){
            Assert.fail();
        }
    }

    @Test
    public void proxyForwardTransformTest(){
        mr = runModel(proxy);
        // proxy route
        String resp = post("http://localhost:1004", "/echo", "");
        Assert.assertNotNull(resp);
        try {
            Object r = ZTypes.json(resp);
            Assert.assertTrue( r instanceof Map);
            Object h = ((Map) r).get("headers");
            Assert.assertTrue( h instanceof Map);
            Assert.assertEquals( "42", ((Map<?, ?>) h).get("cowj"));
            Object b = ((Map<?, ?>) r).get("json");
            Assert.assertTrue( b instanceof Map);
            Object bs = ((Map<?, ?>) b).keySet().iterator().next();
            Assert.assertEquals( "hello,proxy!", bs);
        }catch (Exception ex){
            Assert.fail();
        }
    }

    @Test
    public void proxyTransformErrorCheck(){
        mr = runModel(proxy);
        ZWeb zWeb = new ZWeb("http://localhost:1004");
        try {
            ZWeb.ZWebCom r = zWeb.get("/error", Collections.emptyMap());
            Assert.assertEquals( "boom!", r.body());
            Assert.assertEquals( 500, r.status);
        }catch (Exception ex){
            Assert.fail();
        }
    }
}
