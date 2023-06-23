package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.List;

public class ModelRunnerTest {

    final String hello = "samples/hello/hello.yaml" ;
    final String proxy = "samples/proxy/proxy.yaml" ;

    private ModelRunner mr ;

    @After
    public void stopSpark(){
        if ( mr == null ) return;
        mr.stop();
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
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/g"));
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/j"));
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/p"));
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
}
