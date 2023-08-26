package cowj;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.Assert;
import org.junit.Test;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncTest {

    final String uri = "/_async_/foo" ;
    final HttpServletRequest servletRequest = mock(HttpServletRequest.class);

    final Map<String,String> headers = Map.of("a","b");
    final Map<String,String> params = Map.of("c","d");
    final QueryParamsMap queryParamsMap = new QueryParamsMap(servletRequest);

    private Request mockRequest(){
        Request request = mock(Request.class);
        when(request.uri()).thenReturn(uri);
        when(request.headers()).thenReturn(headers.keySet());
        when(request.headers("a")).thenReturn(headers.get("a"));
        when(request.params()).thenReturn(params);
        when(request.queryMap()).thenReturn(queryParamsMap);
        when(request.body()).thenReturn("42");
        when(request.attributes()).thenReturn(Set.of("foo"));
        when(request.attribute("foo")).thenReturn("bar");
        return request;
    }

    @Test
    public void asyncRequestTest(){
        Request request = mockRequest();
        AsyncHandler.AsyncRequest asyncRequest = AsyncHandler.AsyncRequest.fromRequest(request);
        Assert.assertEquals( uri, asyncRequest.uri());
        Assert.assertEquals( headers, asyncRequest.headers());
        Assert.assertEquals( queryParamsMap, asyncRequest.queryParams());
        Assert.assertEquals( "42", asyncRequest.body());
        Assert.assertEquals( params, asyncRequest.params());

        Assert.assertEquals( Map.of("foo","bar"), asyncRequest.attributes());
        Assert.assertNotNull( asyncRequest.id() );
    }

    @Test
    public void asyncHandlerSuccessTest() throws Exception {
        final Model m = () -> "" ;
        AsyncHandler asyncHandler = AsyncHandler.fromConfig(Collections.emptyMap(), m);
        Assert.assertTrue( asyncHandler.executorService() instanceof ThreadPoolExecutor );
        Assert.assertEquals( Collections.emptyMap(), asyncHandler.retries()  );
        // Now trigger a cool job
        Scriptable pass  = Scriptable.UNIVERSAL.create("foo", "samples/test_scripts/null_return.js");
        Route r = asyncHandler.route(pass);

        // pass case
        Request request = mockRequest();
        Response response = mock(Response.class);
        r.handle(request,response);
        Thread.sleep(3000);
        Assert.assertEquals(1, asyncHandler.results().size());
        // in the end...
        AsyncHandler.stop();
    }

    @Test
    public void asyncHandlerErrorTest() throws Exception {
        final Model m = () -> "" ;
        AsyncHandler asyncHandler = AsyncHandler.fromConfig(Collections.emptyMap(), m);
        Assert.assertTrue( asyncHandler.executorService() instanceof ThreadPoolExecutor );
        Assert.assertEquals( Collections.emptyMap(), asyncHandler.retries()  );
        // Now trigger a cool job
        Scriptable pass  = Scriptable.UNIVERSAL.create("foo", "samples/test_scripts/error_1_arg.zm");
        Route r = asyncHandler.route(pass);

        // pass case
        Request request = mockRequest();
        when(request.body()).thenReturn(null);
        Response response = mock(Response.class);
        r.handle(request,response);
        Thread.sleep(3000);
        Assert.assertEquals(1, asyncHandler.results().size());
        Assert.assertTrue(asyncHandler.results().entrySet().iterator().next().getValue() instanceof Throwable);

        // in the end...
        AsyncHandler.stop();
    }

    @Test
    public void lruCacheWithSizeTest(){
        final Model m = () -> "" ;
        AsyncHandler asyncHandler = AsyncHandler.fromConfig(Map.of("keep", 1), m);
        for( int i=0; i < 10; i++) {
            asyncHandler.results().put(String.valueOf(i), i);
        }
        Assert.assertEquals(1, asyncHandler.results().size());
        Assert.assertEquals(9, asyncHandler.results().entrySet().iterator().next().getValue() );
        // in the end...
        AsyncHandler.stop();
    }

    @Test
    public void lruCacheDisableTest(){
        final Model m = () -> "" ;
        AsyncHandler asyncHandler = AsyncHandler.fromConfig(Map.of("keep", 0), m);
        for( int i=0; i < 10; i++) {
            asyncHandler.results().put(String.valueOf(i), i);
        }
        Assert.assertTrue( asyncHandler.results().isEmpty());
        // in the end...
        AsyncHandler.stop();
    }
}
