package cowj;

import spark.Route;
import spark.Spark;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static spark.Spark.*;

public interface ModelRunner extends Runnable {

    Model model();

    default RouteCreator routeCreator(){
        return RouteCreator.UNIVERSAL;
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
        // set threading
        Map<String,Integer> tAct = model().threading();
        int min = tAct.getOrDefault("min", 3);
        int max = tAct.getOrDefault("max", 10);
        int timeout = tAct.getOrDefault("timeout", 30000);
        threadPool( max, min,timeout);

        // load static
        staticFiles.location(m.staticPath());
        final String baseDir = model().base();
        // load routes
        System.out.println("Base Directory : " + baseDir);
        System.out.println("Routes mapping are as follows...");
        Map<String,Map<String,String>> paths = m.routes();
        for ( String verb : paths.keySet() ){
            Map<String,String> verbRoutes = paths.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String,Route> bic = routeLoaderMap.get(verb);
            for ( Map.Entry<String,String> r: verbRoutes.entrySet() ){
                String scriptPath = r.getValue();
                if ( scriptPath.startsWith("_/")){
                    scriptPath = baseDir + scriptPath.substring(1);
                }
                Route route = routeCreator().create(r.getKey(), scriptPath);
                bic.accept(r.getKey(), route);
                System.out.printf("%s -> %s -> %s %n", verb, r.getKey(), scriptPath);
            }
        }
    }


    static ModelRunner fromModel(String path){
        final Model model = Model.from(path);
        return () -> model;
    }
}
