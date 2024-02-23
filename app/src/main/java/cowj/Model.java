package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cowj Application Model Abstraction
 */
public interface Model {

    /**
     * Logger for the Cowj Model
     */
    Logger logger = LoggerFactory.getLogger(Model.class);

    /**
     * Pattern that defines ENV Substitution
     */
    Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(?<var>[^\\{\\}]+)\\}");

    /**
     * Constant that defines directory relative file location
     */
    String IN_THE_SAME_FOLDER_PREFIX  = "_/" ;

    /**
     * Suffix for binary Route
     */
    String BINARY_ROUTE_SUFFIX  = ".class" ;

    /**
     * Substitutes all ${xyz} from the context to produce string
     * @param templateString input string may containing ${xyz}
     * @param context input map whose key should be "xyz" so that we substitute
     * @return a string after substitution
     */
    default String template(String templateString, Map context){
        Matcher m = TEMPLATE_PATTERN.matcher(templateString);
        String ret = templateString;
        while ( m.find() ){
            String varName =  m.group("var");
            // may be evaluated? In that case the place is in Scriptable
            Object val = context.getOrDefault(varName, "?" + varName);
            ret = ret.replace("${" + varName + "}", val.toString());
        }
        return ret;
    }

    /**
     * Like the template() function but uses System.getenv() for the Map
     * @param templateString input string may containing ${env_var_name}
     * @return a string after substitution
     */
    default String envTemplate(String templateString){
        return template( templateString, Collections.unmodifiableMap(System.getenv()));
    }

    /**
     * Base directory of the model, which folder the model file is found
     * @return folder which model file was loaded from
     */
    String base();

    /**
     * Parameters for threading for Jetty
     * min : min no of threads
     * max : max no of threads
     * @return threading properties
     */
    default Map<String,Object> threading(){
        return Map.of(
                "min", 3,
                "max", 10,
                "timeout" , 30000,
                "virtual", false
        );
    }


    /**
     * Parameters for Async IO
     * threads : no of threads in the pool
     * keep : max no of tasks results to store in memory
     * fail : failure handler for all async task
     * @return Async IO properties
     */
    default Map<String,Object> async(){
        return Collections.emptyMap();
    }

    /**
     * Port of operation
     * @return port of the model
     */
    default int port(){
        return 8080;
    }

    /**
     * Location where all static files will be loaded
     * @return static file location - a folder
     */
    default String staticPath(){
        return interpretPath( "_/static" ) ;
    }

    /**
     * Location from where Type System - Schemas will be loaded
     * @return full path for Schema.yaml file
     */
    default String schemaPath(){
        return staticPath() + SCHEMA_DEFINITION ;
    }

    /**
     * Location where all extra jar files will be loaded
     * @return all jar/binary file location - a folder
     */
    default String libPath(){
        return "_/lib" ;
    }

    /**
     * Route definition
     * http_verb: [ path : script_path ]
     * @return a map returning the routes mapping
     */
    default Map<String, Map<String,String>> routes(){
        return Collections.emptyMap();
    }

    /**
     * WebSocket definition
     * path : script_path
     * @return a map returning the web socket mapping
     */
    default Map<String, String> sockets(){
        return Collections.emptyMap();
    }

    /**
     * Location from where Auth System will be loaded
     * @return full path for Auth.yaml file
     */
    default String auth(){
        return interpretPath( AUTH_DEFINITION ) ;
    }

    /**
     * Filter definition
     * [before|after|finally]: [ path : script_path ]
     * @return a map returning the filters mapping
     */
    default Map<String, Map<String,String>> filters(){
        return Collections.emptyMap();
    }

    /**
     * Proxy definition
     * http_verb: [ path : proxy_id/destination_Path ]
     * @return a map returning the proxy mapping
     */
    default Map<String, Map<String,String>> proxies(){
        return Collections.emptyMap();
    }

    /**
     * Plugin Registration definition
     * package_name : [ type_name : class_name::field_name ]
     * @return a map returning the plugin registrations
     */
    default Map<String, Map<String,String>> plugins(){
        return Collections.emptyMap();
    }


    /**
     * Plugin Creation definition
     * instance_name : [ prop_name : prop_value ]
     * @return a map returning the plugin creation
     */
    default Map<String, Map<String,Object>> dataSources(){
        return Collections.emptyMap();
    }

    /**
     * Gets back the Cron configuration
     * instance_name : [ prop_name : prop_value ]
     * @return a map of the cron jobs
     */
    default Map<String, Map<String,Object>> cron(){
        return Collections.emptyMap();
    }

    /**
     * Name for the key for port
     */
    String PORT = "port" ;

    /**
     * Name for the key for static folder
     */
    String STATIC = "static" ;

    /**
     * Name for the key for routes configuration
     */
    String ROUTES = "routes" ;

    /**
     * Name for the key for sockets configuration
     */
    String SOCKETS = "sockets" ;


    /**
     * Name for the key for threading configuration
     */
    String THREADING = "threading" ;


    /**
     * Name for the key for async IO configuration
     */
    String ASYNC = "async" ;

    /**
     * Name for the key for filters configuration
     */
    String FILTERS = "filters" ;

    /**
     * Name for the key for data sources configuration
     */
    String DATA_SOURCES = "data-sources" ;

    /**
     * Name for the key for proxy routes configuration
     */
    String PROXIES = "proxies" ;

    /**
     * Name for the key for plugin registration configuration
     */
    String PLUGINS = "plugins" ;

    /**
     * Name for the key for lib folder
     */
    String LIB_FOLDER = "lib" ;

    /**
     * Name for the key for cron job configuration
     */
    String CRON_JOBS = "cron" ;

    /**
     * Constant schema configuration location
     */
    String SCHEMA_DEFINITION = "/types/schema.yaml" ;

    /**
     * Constant auth configuration location
     */
    String AUTH_DEFINITION = "_/auth/auth.yaml" ;

    /**
     * Create a Model
     * @param map the properties map to be used to create the model
     * @param baseDir directory for the model
     * @return a Model
     */
    static Model from(final Map<String,Object> map, final String baseDir){

        return new Model() {

            @Override
            public String base(){ return baseDir; }
            @Override
            public int port() {
                Object p = map.getOrDefault(PORT, Model.super.port());
                if ( p instanceof Integer ) return (int)p;
                logger.info("Port is possibly redirected : '{}'", p );
                String ps = p.toString();
                String subP = envTemplate(ps);
                logger.info("Port is redirected to : '{}'", subP );
                return ZNumber.integer(subP, Model.super.port()).intValue();
            }

            @Override
            public String staticPath() {
                return interpretPath( map.getOrDefault(STATIC, Model.super.staticPath()).toString());
            }

            @Override
            public String libPath() {
                return interpretPath( map.getOrDefault(LIB_FOLDER, Model.super.libPath()).toString()) ;
            }

            @Override
            public Map<String, Map<String, String>> routes() {
                return (Map) map.getOrDefault( ROUTES, Model.super.routes());
            }

            @Override
            public Map<String,String> sockets() {
                return (Map) map.getOrDefault( SOCKETS, Model.super.sockets());
            }

            @Override
            public Map<String, Object> threading() {
                return (Map) map.getOrDefault( THREADING, Model.super.threading());
            }

            @Override
            public Map<String, Object> async() {
                return (Map) map.getOrDefault( ASYNC, Model.super.async());
            }

            @Override
            public Map<String, Map<String,Object>> cron() {
                return (Map) map.getOrDefault( CRON_JOBS, Model.super.cron());
            }

            @Override
            public Map<String, Map<String, String>> filters() {
                return (Map) map.getOrDefault( FILTERS, Model.super.filters());
            }

            @Override
            public Map<String, Map<String, String>> proxies() {
                return (Map) map.getOrDefault( PROXIES, Model.super.proxies());
            }

            @Override
            public Map<String, Map<String, String>> plugins() {
                return (Map) map.getOrDefault( PLUGINS, Model.super.plugins());
            }

            @Override
            public Map<String, Map<String, Object>> dataSources() {
                return (Map) map.getOrDefault( DATA_SOURCES, Model.super.dataSources());
            }
        };
    }

    /**
     * Create a Model from a yaml/json file
     * @param path - yaml,json file to be used to load as configuration
     * @return a Model
     */
    static Model from(String path){
        File f = new File(path);
        final String abs = f.getAbsolutePath();
        if ( !f.exists() ) throw new IllegalArgumentException(String.format("No file name : '%s' exists", abs ));
        final String lc = abs.toLowerCase(Locale.ROOT);
        final String baseDir = f.getAbsoluteFile().getParent();
        if ( lc.endsWith(".yaml") || lc.endsWith(".yml")){ //  yaml load
            Map m = (Map)ZTypes.yaml(abs,true);
            return from(m,baseDir);
        }
        if ( lc.endsWith(".json") ){ //  json load
            Map m = (Map)ZTypes.json(abs,true);
            return from(m,baseDir);
        }
        throw new IllegalArgumentException(String.format("Invalid Type of file : '%s' must be a json or yaml", abs ));
    }

    /**
     *  Central place to ask the model to interpret the path as it wishes.
     *  Doing this because a lot of plugins will need to do this. Best to
     *  have them all using the same function so any improvements are automatically
     *  propagated to everybody
     * @param path the input path perhaps containing _/ as prefix
     * @return the final normalized path in absolute form
     */
    default String interpretPath(String path) {
        if ( path.endsWith(BINARY_ROUTE_SUFFIX)) return path;
        final String input;
        if ( path.startsWith(IN_THE_SAME_FOLDER_PREFIX)) {
            input = base() + path.substring(1) ;
        } else {
            input = path;
        }
        try {
            return new File( input ).getCanonicalFile().getPath();
        } catch (Exception ignore){
            return input;
        }
    }
}
