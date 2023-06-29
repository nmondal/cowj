package cowj;

import cowj.plugins.CurlWrapper;
import spark.*;
import zoomba.lang.core.types.ZTypes;

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
    default void run() {
        final Map<String, BiConsumer<String, Route>> routeLoaderMap = new HashMap<>();
        routeLoaderMap.put("get", Spark::get);
        routeLoaderMap.put("put", Spark::put);
        routeLoaderMap.put("post", Spark::post);
        routeLoaderMap.put("delete", Spark::delete);
        routeLoaderMap.put("head", Spark::head);
        routeLoaderMap.put("connect", Spark::connect);
        routeLoaderMap.put("options", Spark::options);
        routeLoaderMap.put("trace", Spark::trace);
        routeLoaderMap.put("patch", Spark::patch);

        final Model m = model();
        // bind port
        port(m.port());
        // set threading
        Map<String, Integer> tAct = model().threading();
        int min = tAct.getOrDefault("min", 3);
        int max = tAct.getOrDefault("max", 10);
        int timeout = tAct.getOrDefault("timeout", 30000);
        threadPool(max, min, timeout);
        Scriptable.Creator creator = scriptCreator();
        // load static
        staticFiles.location(m.staticPath());
        final String baseDir = m.base();
        // load routes
        System.out.println("Base Directory : " + baseDir);
        final String libDir = m.interpretPath( m.libPath() );
        // load libraries
        System.out.println("Library Directory : " +libDir);
        ZTypes.loadJar(libDir);

        // loading plugins...
        System.out.println("Loading plugins now...");
        Map<String, Map<String, String>> plugins = m.plugins();
        for ( String packageName : plugins.keySet() ){
            Map<String,String> providers = plugins.get(packageName);
            providers.forEach( (type, identifier) -> {
                String fullProviderName = packageName + "." + identifier ;
                DataSource.registerType(type, fullProviderName);
            } );
        }

        // load data sources ...
        System.out.println("DataSources mapping are as follows...");
        Map<String, Map<String, Object>> dataSources = m.dataSources();
        DataSource.Creator dsCreator = dsCreator();
        for (String dsName : dataSources.keySet()) {
            Map<String,Object> dsConfig = dataSources.get(dsName);
            try{
                DataSource dataSource = dsCreator.create(dsName, dsConfig, model());
                System.out.printf("DS '%s' created! %n", dataSource.name());
                Scriptable.DATA_SOURCES.put(dsName, dataSource.proxy());
            }catch (Throwable t){
                System.err.printf("DS '%s' failed to create! %s %n", dsName, t);
            }
        }

        System.out.println("Routes mapping are as follows...");
        Map<String, Map<String, String>> paths = m.routes();
        for (String verb : paths.keySet()) {
            Map<String, String> verbRoutes = paths.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String, Route> bic = routeLoaderMap.get(verb);
            for (Map.Entry<String, String> r : verbRoutes.entrySet()) {
                String scriptPath = m.interpretPath(r.getValue());
                Route route = creator.createRoute(r.getKey(), scriptPath);
                bic.accept(r.getKey(), route);
                System.out.printf("%s -> %s -> %s %n", verb, r.getKey(), scriptPath);
            }
        }
        // proxies, if any...
        System.out.println("Proxies mapping are as follows...");
        Map<String, Map<String, String>> proxies = m.proxies();
        for (String verb : proxies.keySet()) {
            Map<String, String> verbProxies = proxies.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String, Route> bic = routeLoaderMap.get(verb);
            for (Map.Entry<String, String> r : verbProxies.entrySet()) {
                String proxyPath = r.getValue();
                String[] arr = proxyPath.split("/");
                String curlKey = arr[0];
                Object o = Scriptable.DATA_SOURCES.get(curlKey);
                if (!(o instanceof CurlWrapper )){
                    System.err.printf("route does not have any base curl data source : %s->%s %n", r.getKey(), r.getValue());
                    continue;
                }
                final String destPath = proxyPath.replace(curlKey + "/", "");
                Route route = (request, response) -> {
                    final CurlWrapper cw = (CurlWrapper) o;
                    return cw.proxy(verb, "*".equals(destPath) ? request.pathInfo() : destPath, request, response);
                };
                bic.accept(r.getKey(), route);
                System.out.printf("%s -> %s -> %s %n", verb, r.getKey(), r.getValue());
            }
        }

        // load filters
        System.out.println("Filters mapping are as follows...");
        Map<String, Map<String, String>> filters = m.filters();
        final Map<String, BiConsumer<String, Filter>> filterMap = new HashMap<>();
        filterMap.put("before", Spark::before);
        filterMap.put("after", Spark::after);
        filterMap.put("finally", Spark::afterAfter);

        for (String filterType : filters.keySet()) {
            Map<String, String> filterRoutes = filters.getOrDefault(filterType, Collections.emptyMap());
            BiConsumer<String, Filter> bic = filterMap.get(filterType);
            for (Map.Entry<String, String> r : filterRoutes.entrySet()) {
                String scriptPath = m.interpretPath(r.getValue());
                Filter filter = creator.createFilter(r.getKey(), scriptPath);
                bic.accept(r.getKey(), filter);
                System.out.printf("%s -> %s -> %s %n", filterType, r.getKey(), scriptPath);
            }
        }
        awaitInitialization();
        // now everything is done, run cron...
        CronModel cronModel = CronModel.fromConfig(m, m.cron());
        CronModel.schedule(cronModel);
    }

    default void stop(){
        Spark.stop();
        awaitStop();
        CronModel.stop();
    }

    static ModelRunner fromModel(String path){
        final Model model = Model.from(path);
        return () -> model;
    }
}
