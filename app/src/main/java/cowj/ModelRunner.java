package cowj;

import spark.Route;
import spark.Spark;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static spark.Spark.port;
import static spark.Spark.staticFiles;

public interface ModelRunner extends Runnable {

    Model model();

    default RouteCreator routeCreator(){
        return RouteCreator.NOP;
    }

    @Override
    default void run(){
        final Map<String, BiConsumer<String,Route> > routeLoaderMap = new HashMap<>();
        routeLoaderMap.put( "get", Spark::get) ;
        routeLoaderMap.put( "put", Spark::put) ;
        routeLoaderMap.put( "post", Spark::post) ;
        routeLoaderMap.put( "delete", Spark::delete) ;
        routeLoaderMap.put( "head", Spark::head) ;
        routeLoaderMap.put( "connect", Spark::connect) ;
        routeLoaderMap.put( "options", Spark::options) ;
        routeLoaderMap.put( "trace", Spark::trace) ;
        routeLoaderMap.put( "patch", Spark::patch) ;

        final  Model m = model();
        // bind port
        port(m.port());
        // load static
        staticFiles.location(m.staticPath());
        // load routes
        Map<String,Map<String,String>> paths = m.routes();
        for ( String verb : paths.keySet() ){
            Map<String,String> verbRoutes = paths.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String,Route> bic = routeLoaderMap.get(verb);
            for ( Map.Entry<String,String> r: verbRoutes.entrySet() ){
                Route route = routeCreator().create(r.getKey(), r.getValue());
                bic.accept(r.getKey(), route);
            }
        }
        // set threading

    }


    static ModelRunner fromModel(String path){
        final Model model = Model.from(path);
        return () -> model;
    }
}
