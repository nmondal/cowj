package cowj.plugins;

import cowj.DataSource;
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

    ZWeb.ZWebCom send(String verb, String path, Map<String,String> headers, Map<String,String> params, String body);

    Function<Request, Map<String,Object>> proxyTransformation();

    String QUERY = "query" ;
    String HEADER = "headers" ;
    String BODY = "body" ;


    default String proxy(String verb, String destPath, Request request, Response response){

        Map<String,Object> trObject = proxyTransformation().apply(request);
        String body = request.body() != null ? request.body() : "" ;
        Map<String,Object> resp = (Map<String, Object>)trObject;
        Map<String,String> queryMap = (Map)resp.getOrDefault(QUERY, Collections.emptyMap());
        Map<String,String> headerMap = (Map)resp.getOrDefault(HEADER, Collections.emptyMap());
        body = resp.getOrDefault(BODY, body).toString();
        ZWeb.ZWebCom com = send(verb, destPath, headerMap, queryMap, body );
        if ( com == null ){
            Spark.halt(500, "Proxy route failed executing!");
        }
        response.status(com.status);
        // no mapping of response headers from destination forward...
        return com.body();
    }

    DataSource.Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();
            String proxy = config.getOrDefault( "proxy", "").toString();
            final Function<Request,Map<String,Object>> transformation;
            if ( proxy.isEmpty() ){
                transformation = (m) -> Collections.emptyMap();
            } else {
                final String absPath = parent.interpretPath(proxy);
                Scriptable sc  = Scriptable.UNIVERSAL.create("proxy." + name, absPath);
                transformation = request -> {
                    SimpleBindings bindings = new SimpleBindings();
                    // add request
                    bindings.put(Scriptable.REQUEST, request);
                    Map<String,String> headers = new HashMap<>();
                    request.headers().forEach( h ->  headers.put(h, request.headers(h)) );
                    bindings.put(HEADER, headers);
                    Map<String,String> queryParams = new HashMap<>();
                    request.queryParams().forEach( q -> queryParams.put( q, request.queryParams(q)) );
                    bindings.put(QUERY, queryParams);
                    bindings.put(BODY, request.body());
                    bindings.put(Scriptable.DATA_SOURCE, Scriptable.DATA_SOURCES);
                    // TODO hemil should think more here...
                    try {
                       Object r = sc.exec( bindings);
                       if ( r instanceof  Map ) return (Map)r;
                       return Map.of( BODY, bindings.get(BODY), HEADER, bindings.get(HEADER), QUERY, bindings.get(QUERY));
                    }catch (Exception e){
                        System.err.println(e);
                        return Collections.emptyMap();
                    }
                };
            }

            final CurlWrapper curlWrapper = new CurlWrapper() {
                @Override
                public ZWeb.ZWebCom send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body) {
                    ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                    zWeb.headers.putAll(headers);
                    try {
                        return zWeb.send(verb, path, params, body);
                    }catch (Throwable t){
                        t.printStackTrace();
                        return null;
                    }
                }
                @Override
                public Function<Request, Map<String, Object>> proxyTransformation() {
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
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    };

}
