package cowj.plugins;

import cowj.DataSource;
import cowj.Scriptable;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;
import java.util.Map;

public interface CurlWrapper {

    ZWeb.ZWebCom send(String verb, String path, Map<String,String> headers, Map<String,String> params, String body);

    Route transformation();
    default String proxy(String verb, String destPath, Request request, Response response){

        Object trObject ;
        Route transform = transformation();
        try {
            trObject = transform.handle(request, response);
        } catch (Throwable t){
            System.err.println("proxy transform failed! " + t);
            trObject = Collections.emptyMap();
        }

        if ( !(trObject instanceof Map) ) {
            trObject = Collections.emptyMap();
            System.err.println("proxy transform returned non map!");
        }
        String body = request.body() != null ? request.body() : "" ;
        Map<String,Object> resp = (Map<String, Object>)trObject;
        Map<String,String> queryMap = (Map)resp.getOrDefault("query", Collections.emptyMap());
        Map<String,String> headerMap = (Map)resp.getOrDefault("header", Collections.emptyMap());
        body = resp.getOrDefault("body", body).toString();
        ZWeb.ZWebCom com = send(verb, destPath, headerMap, queryMap, body );
        if ( com == null ){
            Spark.halt(500, "Proxy rout failed executing!");
        }
        response.status(com.status);
        // no mapping of response headers from destination forward...
        return com.body();
    }

    DataSource.Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();
            String proxy = config.getOrDefault( "proxy", "").toString();
            final Route transformation;
            if ( proxy.isEmpty() ){
                transformation = (request, response) -> Collections.emptyMap();
            } else {
                final String absPath = parent.interpretPath(proxy);
                transformation = Scriptable.UNIVERSAL.createRoute("proxy." + name, absPath);
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
                public Route transformation() {
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
