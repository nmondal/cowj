package cowj.plugins;

import cowj.AsyncHandler;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Request;
import spark.Response;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZTypes;

import java.net.ConnectException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CurlWrapperTest {
    public static Model model = () -> ".";

    @BeforeClass
    public static void boot(){
        AsyncHandler.fromConfig( Collections.emptyMap(), model);
    }

    @AfterClass
    public static void shutDown(){
        AsyncHandler.instance().results().clear();
        AsyncHandler.stop();
    }

    @Test
    public void curlErrorTest(){
        DataSource dataSource = CurlWrapper.CURL.create( "foo", Map.of(), model);
        Assert.assertEquals( "foo", dataSource.name());
        Object cw = dataSource.proxy();
        Assert.assertTrue( cw instanceof  CurlWrapper );
        EitherMonad<ZWeb.ZWebCom> res = ((CurlWrapper) cw).send("get", "/", Collections.emptyMap(), Collections.emptyMap(), "");
        Assert.assertTrue( res.inError() );
        Assert.assertTrue( res.error().getMessage().contains("no protocol") );
    }

    static String  callAsynchProxy(String url, String destinationPath) throws Exception {
        DataSource dataSource = CurlWrapper.CURL.create( "dummy",
                Map.of("url", url), model);
        CurlWrapper cw = (CurlWrapper) dataSource.proxy();
        Request request = mock(Request.class);
        final String body = ZTypes.jsonString( Map.of("x" , 42 ));
        when(request.body()).thenReturn( body);
        when(request.uri()).thenReturn( "/_async_/post" );
        when(request.headers()).thenReturn( Collections.emptySet() );
        when(request.queryParams()).thenReturn( Collections.emptySet() );
        Response response = mock(Response.class);
        String resp = cw.proxy(true, "post", destinationPath, request, response );
        Assert.assertTrue( resp.contains("p"));
        Thread.sleep(3000);
        return resp;
    }

    static void waitAsynchOn(int maxTimes, String resp) throws Exception{
        int tries = 0;
        while( tries < maxTimes && !AsyncHandler.instance().results().containsKey(resp) ){
            tries ++;
            System.err.println("Waiting... " + tries);
            Thread.sleep(1500);
        }
    }

    @Test
    public void asyncProxySuccessTest() throws Exception {
        final String resp = callAsynchProxy("https://postman-echo.com","/post");
        // this can take significant time.. so...
        waitAsynchOn(5,resp);
        Object res = AsyncHandler.instance().results().get(resp);
        Assert.assertTrue( res instanceof Map<?,?>);
        Assert.assertEquals( 200, ((Map<?, ?>) res).get("status"));
        Assert.assertTrue( ((Map<?, ?>) res).containsKey("body"));
    }

    @Test
    public void asyncProxyFailureTest() throws Exception {
        final String resp = callAsynchProxy("http://localhost:9999", "/blablabla");
        waitAsynchOn(2,resp);
        Object res = AsyncHandler.instance().results().get(resp);
        Assert.assertTrue( res instanceof Throwable);
        Assert.assertTrue( ((Throwable) res).getCause() instanceof ConnectException );
    }
}
