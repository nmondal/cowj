package cowj;

import spark.Filter;
import spark.Route;
import spark.Spark;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static spark.Spark.*;

public interface ModelRunner extends Runnable {

    Model model();

    default Scriptable.Creator scriptCreator(){
        return Scriptable.UNIVERSAL;
    }

    default DataSource.Creator dsCreator(){
        return DataSource.UNIVERSAL;
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
        Scriptable.Creator creator = scriptCreator();
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
                Route route = creator.createRoute(r.getKey(), scriptPath);
                bic.accept(r.getKey(), route);
                System.out.printf("%s -> %s -> %s %n", verb, r.getKey(), scriptPath);
            }
        }
        // load filters
        System.out.println("Filters mapping are as follows...");
        Map<String,Map<String,String>> filters = m.filters();
        final Map<String, BiConsumer<String,Filter> > filterMap = new HashMap<>();
        filterMap.put("before", Spark::before);
        filterMap.put("after", Spark::after);
        filterMap.put("finally", Spark::afterAfter);

        for ( String filterType : filters.keySet() ){
            Map<String,String> filterRoutes = filters.getOrDefault(filterType, Collections.emptyMap());
            BiConsumer<String,Filter> bic = filterMap.get(filterType);
            for ( Map.Entry<String,String> r: filterRoutes.entrySet() ){
                String scriptPath = r.getValue();
                if ( scriptPath.startsWith("_/")){
                    scriptPath = baseDir + scriptPath.substring(1);
                }
                Filter filter = creator.createFilter(r.getKey(), scriptPath);
                bic.accept(r.getKey(), filter);
                System.out.printf("%s -> %s -> %s %n", filterType, r.getKey(), scriptPath);
            }
        }
    }


    static ModelRunner fromModel(String path){
        final Model model = Model.from(path);
        return () -> model;
    }
}
