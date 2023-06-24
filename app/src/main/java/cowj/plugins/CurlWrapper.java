package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import spark.Request;
import spark.Response;
import spark.Spark;
import zoomba.lang.core.io.ZWeb;

import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public interface CurlWrapper {

    EitherMonad<ZWeb.ZWebCom> send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body);

    Function<Request, EitherMonad<Map<String, Object>>> proxyTransformation();

    String QUERY = "query";
    String HEADER = "headers";
    String BODY = "body";

    String DESTINATION_URL = "url";
    String PROXY_TRANSFORMER = "proxy";

    default String proxy(String verb, String destPath, Request request, Response response) {
        String body = request.body() != null ? request.body() : "";
        EitherMonad<Map<String, Object>> transformResult = proxyTransformation().apply(request);
        if (transformResult.inError()) {
            Spark.halt(500, transformResult.error().getMessage());
            return "";
        }
        // now here...
        Map<String, Object> resp = transformResult.value();
        Map<String, String> queryMap = (Map) resp.getOrDefault(QUERY, Collections.emptyMap());
        Map<String, String> headerMap = (Map) resp.getOrDefault(HEADER, Collections.emptyMap());
        body = resp.getOrDefault(BODY, body).toString();
        EitherMonad<ZWeb.ZWebCom> curlResponse = send(verb, destPath, headerMap, queryMap, body);
        if (curlResponse.inError()) {
            Spark.halt(500, "Proxy route failed executing!\n" + curlResponse.error());
        }
        response.status(curlResponse.value().status);
        // no mapping of response headers from destination forward...
        return curlResponse.value().body();
    }

    DataSource.Creator CURL = (name, config, parent) -> {
        String baseUrl = config.getOrDefault(DESTINATION_URL, "").toString();
        String proxy = config.getOrDefault(PROXY_TRANSFORMER, "").toString();
        final Function<Request, EitherMonad<Map<String, Object>>> transformation;
        if (proxy.isEmpty()) {
            transformation = (m) -> EitherMonad.value(Collections.emptyMap());
        } else {
            final String absPath = parent.interpretPath(proxy);
            Scriptable sc = Scriptable.UNIVERSAL.create("proxy." + name, absPath);
            transformation = request -> {
                SimpleBindings bindings = new SimpleBindings();
                // add request
                bindings.put(Scriptable.REQUEST, request);
                Map<String, String> headers = new HashMap<>();
                request.headers().forEach(h -> headers.put(h, request.headers(h)));
                bindings.put(HEADER, headers);
                Map<String, String> queryParams = new HashMap<>();
                request.queryParams().forEach(q -> queryParams.put(q, request.queryParams(q)));
                bindings.put(QUERY, queryParams);
                bindings.put(BODY, request.body());
                bindings.put(Scriptable.DATA_SOURCE, Scriptable.DATA_SOURCES);

                try {
                    Object r = sc.exec(bindings);
                    final Map<String, Object> m;
                    if (r instanceof Map) {
                        m = (Map) r;
                    } else {
                        m = Map.of(BODY, bindings.get(BODY), HEADER, bindings.get(HEADER), QUERY, bindings.get(QUERY));
                    }
                    return EitherMonad.value(m);
                } catch (Throwable e) {
                    System.err.printf("Proxy Transform threw error for the proxy [%s] at script '%s' %n", name, absPath);
                    return EitherMonad.error(e);
                }
            };
        }

        final CurlWrapper curlWrapper = new CurlWrapper() {
            @Override
            public EitherMonad<ZWeb.ZWebCom> send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body) {
                try {
                    ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                    zWeb.headers.putAll(headers);
                    final ZWeb.ZWebCom com = zWeb.send(verb, path, params, body);
                    return EitherMonad.value(com);
                } catch (Throwable t) {
                    return EitherMonad.error(t);
                }
            }

            @Override
            public Function<Request, EitherMonad<Map<String, Object>>> proxyTransformation() {
                return transformation;
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
