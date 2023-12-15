package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Route;
import zoomba.lang.core.operations.ZJVMAccess;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static cowj.Scriptable.REQUEST;

/**
 * Handles Asynchronous requests
 */
public interface AsyncHandler {

    /**
     * Logger for the Cowj AsyncHandler
     */
    Logger logger = LoggerFactory.getLogger(AsyncHandler.class);

    /**
     * Key to the Async Handler instance in the Scriptable.DATA_SOURCE
     */
    String ASYNC_HANDLER = "__async__handler__" ;

    /**
     * Gets back the AsyncHandler instance
     * @return AsyncHandler instance
     */
    static AsyncHandler instance(){
        return (AsyncHandler) Scriptable.DATA_SOURCES.get(ASYNC_HANDLER);
    }

    /**
     * Executor to do the asynchronous computation
     * @return an ExecutorService to handle the async IO
     */
    ExecutorService executorService();

    /**
     * A storage to store the result of the asynchronous computation
     * Key to the map is task id which got generated by the call which was returned to the caller
     * @return a Map with task id pointing to result or failure
     */
    Map<String,Object> results();

    /**
     * A failure handler assigned to the failures for all async task
     * @return a Scriptable failure handler
     */
    Scriptable failureHandler();

    /**
     * Key to the Async Error in the Bindings
     */
    String ASYNC_ERROR = "_err" ;

    /**
     * Key to the Underlying Retry Object in the Bindings
     */
    String RETRY = "_retry" ;

    /**
     * The _async_ prefix for the routes, which deemed to be run in async mode
     */
    String ASYNC_ROUTE_PREFIX = "/_async_/";

    /**
     * A Wrap Around clone Request to handle Async IO
     */
    interface AsyncRequest {

        /**
         * URI of the original request
         *
         * @return URI of the original request
         */
        String uri();

        /**
         * Headers of the original request
         *
         * @return headers of the original request
         */
        Map<String, String> headers();

        /**
         * Body of the original request
         *
         * @return Body of the original request
         */
        String body();

        /**
         * Query parameters of the original request
         *
         * @return Query parameters of the original request
         */
        QueryParamsMap queryParams();

        /**
         * Path parameters of the original request
         *
         * @return Path parameters of the original request
         */
        Map<String, String> params();

        /**
         * Attributes of the original request
         *
         * @return Attributes of the original request
         */
        Map<String, Object> attributes();

        /**
         * Auto Generated Id for the async request
         * Which can be used to check the logs about the status of the request
         *
         * @return an automatically generated id which is almost sorted in invocation time
         */
        String id();

        /**
         * Creates an AsyncRequest from underlying spark.Request
         *
         * @param request from this spark.Request
         * @return an AsyncRequest
         */
        static AsyncRequest fromRequest(Request request) {
            final String uri = request.uri();
            // ensure that the time is understandable unix epoch time
            // nanoTime() does not give epoch time
            final String requestId = System.currentTimeMillis() + "." + System.nanoTime() + "." + System.nanoTime() + "|" + uri;

            final Map<String, String> headers = new HashMap<>();
            request.headers().forEach(h -> headers.put(h, request.headers(h)));

            final Map<String, Object> attributes = new HashMap<>();
            request.attributes().forEach(a -> attributes.put(a, request.attribute(a)));

            final Map<String, String> params = request.params();

            final String body = request.body() != null ? request.body() : "";
            final QueryParamsMap queryParamsMap = request.queryMap();
            return new AsyncRequest() {

                @Override
                public String uri() {
                    return uri;
                }

                @Override
                public Map<String, String> headers() {
                    return headers;
                }

                @Override
                public String body() {
                    return body;
                }

                @Override
                public QueryParamsMap queryParams() {
                    return queryParamsMap;
                }

                @Override
                public Map<String, String> params() {
                    return params;
                }

                @Override
                public Map<String, Object> attributes() {
                    return attributes;
                }

                @Override
                public String id() {
                    return requestId;
                }
            };
        }
    }

    /**
     * A map of uri mapped with Retry strategies
     * @return a map
     */
    Map<String,Retry> retries();

    /**
     * Creates an Asynchronous Route based on underlying scriptable
     * @param scriptable underling execution mechanism
     * @return a spark.Route
     */
    default Route route(Scriptable scriptable) {
        return (request, response) -> {
            final long startNano = System.nanoTime();
            AsyncRequest asyncRequest = AsyncRequest.fromRequest(request);
            final String retryKey = asyncRequest.uri().substring( ASYNC_ROUTE_PREFIX.length());
            final Bindings bindings = new SimpleBindings();
            bindings.put(REQUEST, asyncRequest);
            Runnable r = runnable(scriptable, retryKey, bindings, asyncRequest.id());
            executorService().submit(r);
            final long spentNano = System.nanoTime() - startNano;
            logger.info("{} took {} ns", asyncRequest.id(), spentNano);
            // return the task id ...
            return asyncRequest.id();
        };
    }

    /**
     * Create a Runnable using a Scriptable
     * @param scriptable underlying scriptable that would get called
     * @param retryKey using this one would find out the retry mechanism for the underlying Scriptable
     * @param bindings arguments for the Scriptable
     * @param uid unique id to identify the Runnable task that would be created
     * @return Runnable instance which would be created as the Task
     */
    default Runnable runnable(Scriptable scriptable, String retryKey, Bindings bindings, String uid){
        return () -> {
            try {
                final Retry retry = retries().getOrDefault( retryKey, Retry.NOP);
                bindings.put(RETRY, retry);
                final java.util.function.Function<Bindings,Object> withRetry = retry.withRetry( scriptable);
                final Object o = withRetry.apply(bindings);
                results().put(uid, o);
                logger.info("Async Task {} completed with result {}", uid, o);
            } catch (Throwable t) {
                results().put(uid, t);
                logger.error("Async Task {} failed with error {}", uid, t.toString());
                try {
                    bindings.put(ASYNC_ERROR, t);
                    Object fr = failureHandler().exec( bindings );
                    logger.info("Async Task Error Handler successfully executed with response : " + fr);
                }catch (Throwable handlerError){
                    logger.error("Async Task Error Handler itself failed (facepalm) with error : "+ handlerError);
                }
            }
        };
    }

    /**
     * Key to the Async IO Virtual Thread
     */
    String VIRTUAL_THREAD = "virtual" ;

    /**
     * Key to the Async IO Executor Service thread size
     */
    String THREAD_SIZE = "threads" ;

    /**
     * Key to the Async IO  Results max keep alive Memory size
     */
    String MEM_SIZE = "keep" ;

    /**
     * Key to the Async IO Execution fail Script Handler
     */
    String FAILURE_HANDLER = "fail" ;


    /**
     * Key to the Async IO Retry Configuration
     */
    String RETRY_CONFIG = "retries" ;

    /**
     * if Virtual threads are available returns the virtual thread executor
     * @return ExecutorService if available, else null
     */
    static ExecutorService getVirtualThreadExecutor(){
       Object o = ZJVMAccess.callMethod( Executors.class, "newVirtualThreadPerTaskExecutor", new Object[]{});
       if ( o instanceof ExecutorService ) return ((ExecutorService)o);
       return null;
    }

    /**
     * A field that has fixated virtual thread executor or null
     */
    ExecutorService  virtualThreadExecutor =  getVirtualThreadExecutor();

    /**
     * Check if Virtual threads are available
     * @return true if available, false otherwise
     */
    static boolean isVirtualThreadAvailable(){
        return virtualThreadExecutor != null ;
    }

    /**
     * Gets ExecutorService based on config
     * @param config a map configuration
     * @return an ExecutorService
     */
    static ExecutorService getExecutorService(Map<String,Object> config){
        final boolean useVThread = ZTypes.bool(config.getOrDefault(VIRTUAL_THREAD, false),false);
        logger.info("Async-IO use of virtual thread set to : {}", useVThread );
        if (useVThread){
            if ( isVirtualThreadAvailable() ){
                logger.error("Async-IO virtual threads AVAILABLE! will use...");
                return virtualThreadExecutor;
            } else {
                logger.error("Async-IO virtual threads are not available! will fallback on system threads...");
            }
        }
        if ( config.containsKey(THREAD_SIZE ) ){
            final int size = ZNumber.integer(config.get(THREAD_SIZE), 8).intValue();
            logger.info("Async-IO Thread pool size would be {}", size );
            return Executors.newFixedThreadPool( size );
        } else {
            logger.info("Async-IO Thread pool size would be expandable");
            return Executors.newCachedThreadPool();
        }
    }

    /**
     * Creates and inserts an AsyncHandler
     * @param config from this configuration
     * @param model using this parent model
     * @return an AsyncHandler
     */
    static AsyncHandler fromConfig(Map<String,Object> config, Model model){
        final ExecutorService executorService = getExecutorService(config);
        final int memSize = ZNumber.integer(config.get(MEM_SIZE), 1024).intValue();
        logger.info("Async memory size would be {}", memSize );
        final Map<String,Object> results = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > memSize;
            }
        });
        final Map<String,Map<String,Object>> retryConfig = (Map)config.getOrDefault( RETRY_CONFIG, Collections.emptyMap());
        final Map<String,Retry> retries = new HashMap<>();
        retryConfig.forEach( (uri,map) -> retries.put(uri, Retry.fromConfig(map)) );

        final Scriptable failureHandler;
        if ( config.containsKey( FAILURE_HANDLER ) ) {
            final String handlerPath = model.interpretPath( config.get(FAILURE_HANDLER).toString());
            logger.info("Async-IO Error Handler : {}", handlerPath);
            failureHandler = Scriptable.UNIVERSAL.create("_async_fail_", handlerPath);
        } else {
            logger.info("Async-IO Error Handler is set to NOP!");
            failureHandler = Scriptable.NOP.create("", "" );
        }
        AsyncHandler asyncHandler = new AsyncHandler() {
            @Override
            public ExecutorService executorService() {
                return executorService;
            }

            @Override
            public Map<String, Object> results() {
                return results;
            }

            @Override
            public Scriptable failureHandler() {
                return failureHandler;
            }

            @Override
            public Map<String, Retry> retries() {
                return retries;
            }
        };
        Scriptable.DATA_SOURCES.put(ASYNC_HANDLER, asyncHandler);
        return asyncHandler;
    }

    /**
     * Stops the Async IO Executor Service
     */
    static void stop(){
        instance().executorService().shutdown();
    }
}
