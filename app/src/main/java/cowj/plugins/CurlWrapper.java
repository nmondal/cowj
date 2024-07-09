package cowj.plugins;

import cowj.AsyncHandler;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZNumber;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static cowj.AsyncHandler.ASYNC_ROUTE_PREFIX;

/**
 * Abstraction for a HTTP[s] web call
 */
public interface CurlWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(CurlWrapper.class);

    /**
     * Sends a payload to a remote server
     *
     * @param verb    HTTP verb ( get, post, etc )
     * @param path    non server portion of the URI, e.g. <a href="http://localhost:8080/foo/bar">...</a>
     *                /foo/bar is the path
     * @param headers to be sent
     * @param params  to be sent
     * @param body    to be sent
     * @return EitherMonad of type ZWeb.ZWebCom
     */
    EitherMonad<ZWeb.ZWebCom> send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body);

    /**
     * Sends a payload to a remote server Asynchronously
     *
     * @param verb    HTTP verb ( get, post, etc )
     * @param path    non server portion of the URI, e.g. <a href="http://localhost:8080/foo/bar">...</a>
     *                /foo/bar is the path
     * @param headers to be sent
     * @param params  to be sent
     * @param body    to be sent
     * @return a Future
     */
    default Future<EitherMonad<ZWeb.ZWebCom>> sendAsync(String verb, String path, Map<String, String> headers, Map<String, String> params, String body){
        Callable<EitherMonad<ZWeb.ZWebCom>> runnable = () -> send(verb,path, headers, params, body);
        return AsyncHandler.instance().executorService().submit(runnable);
    }

    /**
     * Key for the query to be used in the proxy payload
     */
    String QUERY = "query";

    /**
     * Key for the headers to be used in the proxy payload
     */
    String HEADER = "headers";

    /**
     * Key for the body to be used in the proxy payload
     */
    String BODY = "body";

    /**
     * Key for the base url for the CurlWrapper
     * for : <a href="http://localhost:8080/foo/bar">...</a>
     * it is  <a href="http://localhost:8080">...</a>
     */
    String DESTINATION_URL = "url";

    /**
     * Key for the timeout url for the CurlWrapper
     */
    String TIMEOUT = "timeout";

    /**
     * Key for the proxy payload to be added on the spark.Request.attribute()
     */
    String PROXY_ATTRIBUTE = "_proxy";

    /**
     * Create a payload for proxy from spark.Request
     *
     * @param request the spark.Request
     * @return a map of the form
     * { "query" : { }, "headers" : {} , "body" : ""  }
     */
    static Map<String, Map<String, String>> payload(Request request) {
        final Map<String, Map<String, String>> forwardPayload = new HashMap<>();
        // add request
        Map<String, String> headers = new HashMap<>();
        request.headers().forEach(h -> headers.put(h, request.headers(h)));
        forwardPayload.put(HEADER, headers);
        Map<String, String> queryParams = new HashMap<>();
        request.queryParams().forEach(q -> queryParams.put(q, request.queryParams(q)));
        forwardPayload.put(QUERY, queryParams);
        return forwardPayload;
    }

    /**
     * Constant string prefix for error when proxy destination route failed to execute
     */
    String PROXY_ROUTE_FAILED_ERROR_PREFIX = "Proxy route failed executing!";

    /**
     * Method for forward proxy
     *
     * @param async    should we call the destination in async mode i.e. drop the data and get back
     * @param verb     HTTP verb
     * @param destPath destination path
     * @param request  spark.Request
     * @param response spark.Response
     * @return response body of the proxy request
     */
    default String proxy(boolean async, String verb, String destPath, Request request, Response response) {
        final long startTime = System.nanoTime();
        Objects.requireNonNull(request.body()); // this, according to code, can never be bull
        String body = request.body();
        Object proxyPayload = request.attribute(PROXY_ATTRIBUTE);
        // now here...
        final Map<String, Object> resp;
        if (proxyPayload instanceof Map) {
            resp = (Map<String, Object>) proxyPayload;
        } else {
            resp = Collections.emptyMap();
        }
        Map<String, Map<String, String>> originalPayload = payload(request);
        final Map<String, String> queryMap = (Map) resp.getOrDefault(QUERY, originalPayload.getOrDefault(QUERY, Collections.emptyMap()));
        final Map<String, String> headerMap = (Map) resp.getOrDefault(HEADER, originalPayload.getOrDefault(HEADER, Collections.emptyMap()));
        final String bodyString = resp.getOrDefault(BODY, body).toString();
        if (async) {
            final String uri = request.uri();
            final String uid = System.nanoTime() + "." + System.nanoTime() + "." + System.nanoTime() + "p" + uri ;
            final String retryKey = uri.substring( ASYNC_ROUTE_PREFIX.length());
            Scriptable scriptable = (b) ->{
                EitherMonad<ZWeb.ZWebCom> curlResponse = send(verb, destPath, headerMap, queryMap, bodyString);
                if ( curlResponse.inError() ){
                    return new Throwable( PROXY_ROUTE_FAILED_ERROR_PREFIX + " => " + uri, curlResponse.error() );
                }
                return Map.of("body", curlResponse.value().body(), "status", curlResponse.value().status) ;
            };
            Bindings bindings = new SimpleBindings();
            Runnable runnable = AsyncHandler.instance().runnable(scriptable, retryKey, bindings, uid );
            AsyncHandler.instance().executorService().submit(runnable);
            final long spentNano = System.nanoTime() - startTime;
            logger.info("{} took {} ns", uid, spentNano);
            return uid;
        }
        // This is the regular path sync call
        EitherMonad<ZWeb.ZWebCom> curlResponse = send(verb, destPath, headerMap, queryMap, bodyString);
        if (curlResponse.inError()) {
            Spark.halt(500, PROXY_ROUTE_FAILED_ERROR_PREFIX + "\n" + curlResponse.error());
        }
        response.status(curlResponse.value().status);
        // no mapping of response headers from destination forward...
        return curlResponse.value().body();
    }

    /**
     * Creates a Route
     *
     * @param verb      with the HTTP verb
     * @param localPath uri path in the local server system
     * @param destPath  uri path of the remote proxy server system
     * @return a spark.Route instance
     */
    default Route route(String verb, String localPath, String destPath) {
        return (request, response) -> {
            final boolean async = localPath.startsWith(ASYNC_ROUTE_PREFIX);
            return proxy(async, verb, "*".equals(destPath) ? request.pathInfo() : destPath, request, response);
        };
    }

    /**
     * A DataSource.Creator for the CurlWrapper
     */
    DataSource.Creator CURL = (name, config, parent) -> {
        String baseUrlKey = config.getOrDefault(DESTINATION_URL, "").toString();
        logger.info("{} : base url key [{}]", name, baseUrlKey );
        String baseUrl = parent.envTemplate( baseUrlKey );
        logger.info("{} : base url [{}]", name, baseUrl );
        final int timeout = ZNumber.integer(config.getOrDefault(TIMEOUT, 42000L),42000).intValue() ;
        logger.info("{} : connection timeout [{}]", name, timeout );

        final CurlWrapper curlWrapper = (verb, path, headers, params, body) -> {
            try {
                ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                zWeb.conTO = timeout; // connection timeout
                zWeb.readTO = timeout; // read timeout
                zWeb.headers.putAll(headers);
                final ZWeb.ZWebCom com = zWeb.send(verb, path, params, body);
                if ( com.status >= 400 ){
                    logger.warn("{} : Non OK Response : [{}]  body : [{}]", name, com.status,
                            com.bytes == null ? "(null)" : com.body());
                }
                return EitherMonad.value(com);
            } catch (Throwable t) {
                logger.error("{} : Error while Sending Request : {}", name,  t.toString() );
                return EitherMonad.error(t);
            }
        };
        return DataSource.dataSource(name, curlWrapper);
    };
}
