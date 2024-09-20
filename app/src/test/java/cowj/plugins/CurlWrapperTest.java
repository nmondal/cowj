package cowj.plugins;

import cowj.AsyncHandler;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import cowj.ModelRunner;
import cowj.ModelRunnerTest;

import org.junit.*;
import spark.Request;
import spark.Response;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.io.ZWeb.ZWebCom;
import zoomba.lang.core.types.ZTypes;

import java.net.ConnectException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Future;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CurlWrapperTest {

    final String proxy = "samples/proxy/proxy.yaml" ;
    
    public static Model model = () -> ".";

    static ModelRunner mr ;

    @BeforeClass
    public static void boot(){
        mr = null;
        AsyncHandler.fromConfig( Collections.emptyMap(), model);
    }

    @AfterClass
    public static void shutDown(){
        AsyncHandler.instance().results().clear();
        AsyncHandler.stop();
        if ( mr != null ){
            mr.stop();
        }
        mr = null;
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

    @Test
    public void curlStatusWarnTest(){
        DataSource dataSource = CurlWrapper.CURL.create( "foo", Map.of("url", "https://google.co.in"), model);
        Assert.assertEquals( "foo", dataSource.name());
        Object cw = dataSource.proxy();
        Assert.assertTrue( cw instanceof  CurlWrapper );
        EitherMonad<ZWeb.ZWebCom> res = ((CurlWrapper) cw).send("get", "/foobar", Collections.emptyMap(), Collections.emptyMap(), "");
        Assert.assertFalse( res.inError() );
        Assert.assertEquals( 404, res.value().status);
    }

    @Test
    public void asyncSendTest() throws Exception {
        DataSource dataSource = CurlWrapper.CURL.create( "foo", Map.of("url", "https://google.co.in"), model);
        Assert.assertEquals( "foo", dataSource.name());
        Object cw = dataSource.proxy();
        Assert.assertTrue( cw instanceof  CurlWrapper );
        Future<EitherMonad<ZWeb.ZWebCom>> future = ((CurlWrapper) cw).sendAsync("get", "/", Collections.emptyMap(), Collections.emptyMap(), "");
        EitherMonad<ZWeb.ZWebCom> res = future.get();
        Assert.assertEquals(200, res.value().status);
        Assert.assertFalse(res.value().body().isEmpty());
    }

    static String  callAsynchProxy(String url, String destinationPath) throws Exception {
        DataSource dataSource = CurlWrapper.CURL.create( "dummy",
                Map.of("url", url, "timeout", 6000 ), model);
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

    public static boolean isTimeOut(Object res){
        return res instanceof Throwable && ((Throwable) res).getCause().getMessage().contains("timed out");
    }

    @Test
    public void asyncProxySuccessTest() throws Exception {
        final String resp = callAsynchProxy("https://postman-echo.com","/post");
        // this can take significant time.. so...
        waitAsynchOn(5,resp);
        Object res = AsyncHandler.instance().results().get(resp);
        // check if there is a timeout ?
        Assume.assumeFalse( isTimeOut(res) );
        // then only
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

    @Test
    public void emptyBodyWithErrorTest(){
        mr = ModelRunnerTest.runModel(proxy);
        DataSource dataSource = CurlWrapper.CURL.create( "foo-bar",
                Map.of("url", "http://localhost:5004", "timeout", 6000 ), model);
        CurlWrapper cw = dataSource.any();
        EitherMonad<ZWebCom> em = cw.send("get", "/empty", Collections.emptyMap(), Collections.emptyMap(), "");
        Assert.assertTrue( em.isSuccessful() );
        Assert.assertEquals(405,  em.value().status);
    }
}
