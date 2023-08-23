package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Route;
import zoomba.lang.core.types.ZNumber;

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
            final String requestId = System.nanoTime() + "." + System.nanoTime() + "." + System.nanoTime() + "|" + uri;

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
     * Creates an Asynchronous Route based on underlying scriptable
     * @param scriptable underling execution mechanism
     * @return a spark.Route
     */
    default Route route(Scriptable scriptable) {
        return (request, response) -> {
            final long startNano = System.nanoTime();
            AsyncRequest asyncRequest = AsyncRequest.fromRequest(request);
            final Bindings bindings = new SimpleBindings();
            bindings.put(REQUEST, asyncRequest);
            Runnable r = () -> {
                try {
                    Object o = scriptable.exec(bindings);
                    results().put(asyncRequest.id(), o);
                    logger.info("Async Task {} completed with result {}", asyncRequest.id(), o);
                } catch (Throwable t) {
                    results().put(asyncRequest.id(), t);
                    logger.error("Async Task {} failed with error {}", asyncRequest.id(), t.toString());
                    try {
                        bindings.put(ASYNC_ERROR, t);
                        failureHandler().exec( bindings );
                    }catch (Throwable ignore){}
                }
            };
            // TODO should check if we could do another async threadpool here...
            executorService().submit(r);
            final long spentNano = System.nanoTime() - startNano;
            logger.info("{} took {} ns", asyncRequest.id(), spentNano);
            // return the task id ...
            return asyncRequest.id();
        };
    }

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
     * Creates and inserts an AsyncHandler
     * @param config from this configuration
     * @param model using this parent model
     * @return an AsyncHandler
     */
    static AsyncHandler fromConfig(Map<String,Object> config, Model model){
        final ExecutorService executorService;
        if ( config.containsKey(THREAD_SIZE ) ){
            final int size = ZNumber.integer(config.get(THREAD_SIZE), 8).intValue();
            logger.info("Async-IO Threadpool size would be {}", size );
            executorService = Executors.newFixedThreadPool( size );
        } else {
            logger.info("Async-IO Threadpool size would be expandable");
            executorService = Executors.newCachedThreadPool();
        }
        final int memSize = ZNumber.integer(config.get(MEM_SIZE), 1024).intValue();
        logger.info("Async memory size would be {}", memSize );
        final Map<String,Object> results = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > memSize;
            }
        });
        final String handlerPath = model.interpretPath( config.getOrDefault(FAILURE_HANDLER, "").toString());
        logger.info("Async-IO Error Handler : {}", handlerPath );
        final Scriptable failureHandler = Scriptable.UNIVERSAL.create("_async_fail_", handlerPath);
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
