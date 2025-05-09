package cowj;

import cowj.plugins.CurlWrapper;
import cowj.plugins.JvmRAMA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.*;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.util.*;
import java.util.function.BiConsumer;

import static spark.Spark.*;

/**
 * This is where Cowj really runs
 */
public interface ModelRunner extends Runnable {

    /**
     * Logger for the Cowj ModelRunner
     */
    Logger logger = LoggerFactory.getLogger(ModelRunner.class);

    /**
     * Underlying model
     * @return a Model, which is running currently
     */
    Model model();

    /**
     * Underlying Creator for Scriptable
     * @return a Scriptable.Creator
     */
    default Scriptable.Creator scriptCreator(){
        return Scriptable.UNIVERSAL;
    }

    /**
     * Underlying Creator for DataSource
     * @return a DataSource.Creator
     */
    default DataSource.Creator dsCreator(){
        return DataSource.UNIVERSAL;
    }

    /**
     * Checks in case the verb, path combo is unique or not
     * Raises IllegalStateException in case a repeat route is seen
     * @param verb GET, SET etc HTTP verb
     * @param path path of the route
     * @param myRoutes a Set of strings
     */
    default void checkUniqueRoute(String verb, String path, Set<String> myRoutes){
        final String routeKey = verb + "::" + path ;
        if ( myRoutes.contains(routeKey ) ) throw new IllegalStateException("route already exists : " + routeKey );
        myRoutes.add(routeKey);
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
        Map<String, Object> tAct = m.threading();
        final boolean useVirtualThread = ZTypes.bool(tAct.getOrDefault("virtual", false), false);
        logger.info("threading: virtual threading supported: {}, specified using virtual threads: {}, will use: {}",
                AsyncHandler.HAS_VIRTUAL_THREAD_SUPPORT, useVirtualThread, useVirtualThread && AsyncHandler.HAS_VIRTUAL_THREAD_SUPPORT );
        useVirtualThread(useVirtualThread && AsyncHandler.HAS_VIRTUAL_THREAD_SUPPORT); // Java 21 virtual threads

        int min = ZNumber.integer(tAct.getOrDefault("min", 3),3).intValue();
        int max = ZNumber.integer(tAct.getOrDefault("max", 10),10).intValue();
        int timeout = ZNumber.integer(tAct.getOrDefault("timeout", 30000), 30000).intValue();
        logger.info("threading: min {}, max {}, timeout(ms) {}", min, max, timeout);
        threadPool(max, min, timeout);
        // Set Async IO
        Map<String, Object> asyncConfig = m.async();
        AsyncHandler.fromConfig(asyncConfig, m);
        // Now go start creating creators
        Scriptable.Creator creator = scriptCreator();
        // load static
        staticFiles.externalLocation(m.staticPath());
        final String baseDir = m.base();
        // load routes
        logger.info("Base Directory : " + baseDir);
        final String libDir = m.interpretPath( m.libPath() );
        // load libraries
        logger.info("Library Directory : " +libDir);
        // Setup modular scripting
        ModuleManager.UNIVERSAL.modulePath(libDir);

        // load type system ... other folks may depend on this
        TypeSystem typeSystem = TypeSystem.fromFile( m.schemaPath());

        // loading plugins...
        logger.info("Loading plugins now...");
        Map<String, Map<String, String>> plugins = m.plugins();
        for ( String packageName : plugins.keySet() ){
            Map<String,String> providers = plugins.get(packageName);
            providers.forEach( (type, identifier) -> {
                String fullProviderName = packageName + "." + identifier ;
                DataSource.registerType(type, fullProviderName);
            } );
        }

        // load data sources ...
        logger.info("DataSources mapping are as follows...");
        Map<String, Map<String, Object>> dataSources = m.dataSources();
        DataSource.Creator dsCreator = dsCreator();
        for (String dsName : dataSources.keySet()) {
            Map<String,Object> dsConfig = dataSources.get(dsName);
            try{
                DataSource dataSource = dsCreator.create(dsName, dsConfig, model());
                logger.info("DS '{}' created!", dataSource.name());
                DataSource.registerDataSource(dsName, dataSource.proxy());
            }catch (Throwable t){
                final String message = String.format("DS '%s' failed to create!", dsName);
                logger.error(message, t);
            }
        }
        // now everything is done, run cron...
        CronModel cronModel = CronModel.fromConfig(m, m.cron());
        CronModel.schedule(cronModel);

        // web sockets, if any...
        logger.info("WebSocket mapping are as follows...");
        Map<String, String> sockets = m.sockets();
        for ( Map.Entry<String,String> r : sockets.entrySet() ){
            final String scriptPath = m.interpretPath(r.getValue());
            ScriptableSocket socketInstance = ScriptableSocket.socket(r.getKey(), scriptPath);
            webSocket(r.getKey(), socketInstance);
            logger.info("scriptable websocket: {} -> {}", r.getKey(), scriptPath);
        }

        Set<String> myRoutes = new HashSet<>();

        logger.info("Routes mapping are as follows...");
        Map<String, Map<String, String>> paths = m.routes();
        for (String verb : paths.keySet()) {
            Map<String, String> verbRoutes = paths.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String, Route> bic = routeLoaderMap.get(verb);
            for (Map.Entry<String, String> r : verbRoutes.entrySet()) {
                checkUniqueRoute( verb, r.getKey(), myRoutes);
                String scriptPath = m.interpretPath(r.getValue());
                Route route = creator.createRoute(r.getKey(), scriptPath);
                bic.accept(r.getKey(), route);
                logger.info("scriptable route: {} -> {} -> {}", verb, r.getKey(), scriptPath);
            }
        }
        // proxies, if any...
        logger.info("Proxies mapping are as follows...");
        Map<String, Map<String, String>> proxies = m.proxies();
        for (String verb : proxies.keySet()) {
            Map<String, String> verbProxies = proxies.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String, Route> bic = routeLoaderMap.get(verb);
            for (Map.Entry<String, String> r : verbProxies.entrySet()) {
                checkUniqueRoute( verb, r.getKey(), myRoutes);
                String proxyPath = r.getValue();
                String[] arr = proxyPath.split("/");
                String curlKey = arr[0];
                Object o = DataSource.dataSource(curlKey);
                if (!(o instanceof CurlWrapper cw)){
                    logger.error("route does not have any base curl data source : {}->{}", r.getKey(), r.getValue());
                    continue;
                }
                final String destPath = proxyPath.replace(curlKey + "/", "");
                Route route = cw.route(verb, r.getKey(), destPath);
                bic.accept(r.getKey(), route);
                logger.info("proxy route: {} -> {} -> {}", verb, r.getKey(), r.getValue());
            }
        }

        // load auth...
        AuthSystem authSystem = AuthSystem.fromFile(m.auth(), m );
        authSystem.attach();
        // Attach input before any before filter
        typeSystem.attachInput();
        // load filters
        logger.info("Filters mapping are as follows...");
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
                logger.info("{} -> {} -> {}", filterType, r.getKey(), scriptPath);
            }
        }
        FileWatcher.startWatchDog( baseDir );
        // Attach TypeSystem output schema verification after any after filter
        typeSystem.attachOutput();
        // now if type system has typed storage, replace all storages via typed storage mechanism
        TypedStorage.attach(typeSystem);
        try {
            awaitInitialization();
        }catch (IllegalStateException ex){
            logger.info("Perhaps init() was not called... ");
            if ( ex.getMessage().contains("properly")){
                // this is only possible when we are just using cowj to do static web server
                logger.info("init() was not called for sure, calling and waiting");
                Spark.init();
                awaitInitialization();
            } else{
                throw ex; // should not hold it, throw it back
            }
        }
        logger.info("Cowj is initialized!");
    }

    /**
     * Stops the Cowj Instance from Running
     */
    default void stop(){
        Spark.stop();
        awaitStop();
        CronModel.stop();
        AsyncHandler.stop();
        JvmRAMA.RAMAConsumerJob.stop();
    }

    /**
     * Creates a ModelRunner instance from a file
     * @param path the file yaml, json
     * @return a ModelRunner
     */
    static ModelRunner fromModel(String path){
        final Model model = Model.from(path);
        return () -> model;
    }
}
