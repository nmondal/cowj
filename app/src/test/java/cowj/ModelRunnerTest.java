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

    @Test
    public void bootTest(){
        ModelRunner mr = runModel(hello);
        Assert.assertTrue( ping("http://localhost:1003", "/hello/z"));
        mr.stop();
        Assert.assertFalse( ping("http://localhost:1003", "/hello/z"));
    }
}
