package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Route;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static cowj.Scriptable.REQUEST;

/**
 * Asynchronous version of the request
 */
interface AsyncRequest {

    /**
     * Logger for the Cowj AsyncRequest
     */
    Logger logger = LoggerFactory.getLogger(AsyncRequest.class);

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
     * A storage to store the result of the asynchronous computation
     */
    Map<String, String> RESULTS = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 1024;
        }
    });

    static void asyncResult(Bindings bindings, Object result) {
        Object ar = bindings.get(REQUEST);
        if (ar instanceof AsyncRequest) {
            AsyncRequest.RESULTS.put(((AsyncRequest) ar).id(), String.valueOf(result));
            if (result instanceof Throwable) {
                logger.error("Async Exec Error! {} => {}", ((AsyncRequest) ar).id(), result);
            } else {
                logger.info("Async Exec Success! {} => {}", ((AsyncRequest) ar).id(), result);
            }
        }
    }

    /**
     * Creates an Asynchronous Route based on underlying scriptable
     * @param scriptable underling execution mechanism
     * @return a spark.Route
     */
    static Route route(Scriptable scriptable) {
        return (request, response) -> {
            AsyncRequest asyncRequest = AsyncRequest.fromRequest(request);
            final Bindings bindings = new SimpleBindings();
            bindings.put(REQUEST, asyncRequest);
            Runnable r = () -> {
                try {
                    Object o = scriptable.exec(bindings);
                    AsyncRequest.asyncResult(bindings, o);
                } catch (Throwable t) {
                    AsyncRequest.asyncResult(bindings, t);
                }
            };
            // TODO should check if we could do another async threadpool here...
            Thread t = new Thread(r);
            t.start();
            // return the task id ...
            return asyncRequest.id();
        };
    }

    /**
     * Creates an AsyncRequest from underlying spark.Request
     *
     * @param request from this spark.Request
     * @return an AsyncRequest
     */
    static AsyncRequest fromRequest(Request request) {
        final String requestId = System.nanoTime() + "." + System.nanoTime() + "." + System.nanoTime();

        final Map<String, String> headers = new HashMap<>();
        request.headers().forEach(h -> headers.put(h, request.headers(h)));

        final Map<String, Object> attributes = new HashMap<>();
        request.attributes().forEach(a -> attributes.put(a, request.attribute(a)));

        final Map<String, String> params = request.params();

        final String body = request.body() != null ? request.body() : "";
        final QueryParamsMap queryParamsMap = request.queryMap();
        return new AsyncRequest() {
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
