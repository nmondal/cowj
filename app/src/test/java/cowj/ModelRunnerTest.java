package cowj;

import org.junit.Assert;
import org.junit.Test;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;

public class ModelRunnerTest {

    final String hello = "samples/hello/hello.yaml" ;
    final String proxy = "samples/proxy/proxy.yaml" ;

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

    @Test
    public void bootTest(){
        ModelRunner mr = runModel(hello);
        Assert.assertTrue( ping("http://localhost:1003", "/hello/z"));
        mr.stop();
        Assert.assertFalse( ping("http://localhost:1003", "/hello/z"));
    }

    @Test
    public void routesTest(){
        ModelRunner mr = runModel(hello);
        final String expected = "hello, world!" ;
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/g"));
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/j"));
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/p"));
        Assert.assertEquals( expected, get("http://localhost:1003", "/hello/z"));
        mr.stop();
    }
}
