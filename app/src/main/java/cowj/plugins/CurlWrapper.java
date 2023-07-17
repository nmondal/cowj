package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import spark.Request;
import spark.Response;
import spark.Spark;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction for a HTTP[s] web call
 */
public interface CurlWrapper {

    /**
     * Sends a payload to a remote server
     * @param verb HTTP verb ( get, post, etc )
     * @param path non server portion of the URI, e.g. <a href="http://localhost:8080/foo/bar">...</a>
     *             /foo/bar is the path
     * @param headers to be sent
     * @param params to be sent
     * @param body to be sent
     * @return EitherMonad of type ZWeb.ZWebCom
     */
    EitherMonad<ZWeb.ZWebCom> send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body);

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
     *
     */
    String DESTINATION_URL = "url";

    /**
     * Key for the proxy payload to be added on the spark.Request.attribute()
     */
    String PROXY_ATTRIBUTE = "_proxy";

    /**
     * Create a payload for proxy from spark.Request
     * @param request the spark.Request
     * @return a map of the form
     *  { "query" : { }, "headers" : {} , "body" : ""  }
     */
    static Map<String,Map<String,String>> payload(Request request){
        final Map<String,Map<String,String>> forwardPayload = new HashMap<>();
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
     * Method for forward proxy
     * @param verb HTTP verb
     * @param destPath destination path
     * @param request spark.Request
     * @param response spark.Response
     * @return response body of the proxy request
     */
    default String proxy(String verb, String destPath, Request request, Response response) {
        String body = request.body() != null ? request.body() : "";
        Object proxyPayload = request.attribute( PROXY_ATTRIBUTE ) ;
        // now here...
        final Map<String, Object> resp ;
        if ( proxyPayload instanceof  Map){
            resp = (Map<String, Object>) proxyPayload;
        } else {
            resp = Collections.emptyMap();
        }
        Map<String,Map<String,String>> originalPayload = payload(request);
        Map<String, String> queryMap = (Map) resp.getOrDefault(QUERY, originalPayload.getOrDefault( QUERY, Collections.emptyMap()));
        Map<String, String> headerMap = (Map) resp.getOrDefault(HEADER, originalPayload.getOrDefault(HEADER, Collections.emptyMap()));
        body = resp.getOrDefault(BODY, body).toString();
        EitherMonad<ZWeb.ZWebCom> curlResponse = send(verb, destPath, headerMap, queryMap, body);
        if (curlResponse.inError()) {
            Spark.halt(500, "Proxy route failed executing!\n" + curlResponse.error());
        }
        response.status(curlResponse.value().status);
        // no mapping of response headers from destination forward...
        return curlResponse.value().body();
    }

    /**
     * A DataSource.Creator for the CurlWrapper
     */
    DataSource.Creator CURL = (name, config, parent) -> {
        String baseUrl = config.getOrDefault(DESTINATION_URL, "").toString();

        final CurlWrapper curlWrapper = (verb, path, headers, params, body) -> {
            try {
                ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                zWeb.headers.putAll(headers);
                final ZWeb.ZWebCom com = zWeb.send(verb, path, params, body);
                return EitherMonad.value(com);
            } catch (Throwable t) {
                return EitherMonad.error(t);
            }
        };

        return new DataSource() {
            @Override
            public Object proxy() {
                return curlWrapper;
            }

            @Override
            public String name() {
                return name;
            }
        };
    };

}
