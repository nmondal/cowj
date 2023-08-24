package cowj;

import cowj.plugins.CurlWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.*;
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
        Map<String, Integer> tAct = m.threading();
        int min = tAct.getOrDefault("min", 3);
        int max = tAct.getOrDefault("max", 10);
        int timeout = tAct.getOrDefault("timeout", 30000);
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
        ZTypes.loadJar(libDir);

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
                Scriptable.DATA_SOURCES.put(dsName, dataSource.proxy());
            }catch (Throwable t){
                logger.error("DS '{}' failed to create! {}", dsName, t.toString());
            }
        }

        // now everything is done, run cron...
        CronModel cronModel = CronModel.fromConfig(m, m.cron());
        CronModel.schedule(cronModel);

        logger.info("Routes mapping are as follows...");
        Map<String, Map<String, String>> paths = m.routes();
        for (String verb : paths.keySet()) {
            Map<String, String> verbRoutes = paths.getOrDefault(verb, Collections.emptyMap());
            BiConsumer<String, Route> bic = routeLoaderMap.get(verb);
            for (Map.Entry<String, String> r : verbRoutes.entrySet()) {
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
                String proxyPath = r.getValue();
                String[] arr = proxyPath.split("/");
                String curlKey = arr[0];
                Object o = Scriptable.DATA_SOURCES.get(curlKey);
                if (!(o instanceof CurlWrapper )){
                    logger.error("route does not have any base curl data source : {}->{}", r.getKey(), r.getValue());
                    continue;
                }
                final String destPath = proxyPath.replace(curlKey + "/", "");
                Route route = (request, response) -> {
                    final CurlWrapper cw = (CurlWrapper) o;
                    return cw.proxy(verb, "*".equals(destPath) ? request.pathInfo() : destPath, request, response);
                };
                bic.accept(r.getKey(), route);
                logger.info("proxy route: {} -> {} -> {}", verb, r.getKey(), r.getValue());
            }
        }
        // load auth...
        AuthSystem authSystem = AuthSystem.fromFile(m.auth());
        authSystem.attach();
        // load type system ...
        TypeSystem typeSystem = TypeSystem.fromFile( m.schemaPath());
        typeSystem.attach();
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
        awaitInitialization();
        FileWatcher.startWatchDog( baseDir );
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
