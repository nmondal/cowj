package cowj;

import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Model {

    Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(?<var>[^\\{\\}]+)\\}");

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

    default String envTemplate(String templateString){
        return template( templateString, Collections.unmodifiableMap(System.getenv()));
    }

    String base();

    default Map<String,Integer> threading(){
        return Map.of(
                "min", 3,
                "max", 10,
                "timeout" , 30000
        );
    }

    default int port(){
        return 8080;
    }

    default String staticPath(){
        return "_/static" ;
    }

    default String libPath(){
        return "_/lib" ;
    }

    default Map<String, Map<String,String>> routes(){
        return Collections.emptyMap();
    }

    default Map<String,String> auth(){
        return Collections.emptyMap();
    }

    default Map<String, Map<String,String>> filters(){
        return Collections.emptyMap();
    }

    default Map<String, Map<String,String>> proxies(){
        return Collections.emptyMap();
    }

    default Map<String, Map<String,String>> plugins(){
        return Collections.emptyMap();
    }

    default Map<String, Map<String,Object>> dataSources(){
        return Collections.emptyMap();
    }

    default Map<String, Map<String,Object>> cron(){
        return Collections.emptyMap();
    }

    String PORT = "port" ;

    String STATIC = "static" ;

    String ROUTES = "routes" ;

    String THREADING = "threading" ;

    String AUTH = "auth" ;

    String FILTERS = "filters" ;

    String DATA_SOURCES = "data-sources" ;

    String PROXIES = "proxies" ;

    String PLUGINS = "plugins" ;

    String LIB_FOLDER = "lib" ;

    String CRON_JOBS = "cron" ;

    static Model from(final Map<String,Object> map, final String baseDir){

        return new Model() {

            @Override
            public String base(){ return baseDir; }
            @Override
            public int port() {
                Object p = map.getOrDefault(PORT, Model.super.port());
                if ( p instanceof Integer ) return (int)p;
                System.out.printf("Port is possibly redirected : '%s' %n", p );
                String ps = p.toString();
                String subP = envTemplate(ps);
                System.out.printf("Port is redirected to : '%s' %n", subP );
                return ZNumber.integer(subP, Model.super.port()).intValue();
            }

            @Override
            public String staticPath() {
                return (String) map.getOrDefault(STATIC, Model.super.staticPath());
            }

            @Override
            public String libPath() {
                return (String) map.getOrDefault(LIB_FOLDER, Model.super.libPath());
            }

            @Override
            public Map<String, Map<String, String>> routes() {
                return (Map) map.getOrDefault( ROUTES, Model.super.routes());
            }

            @Override
            public Map<String, Integer> threading() {
                return (Map) map.getOrDefault( THREADING, Model.super.threading());
            }

            @Override
            public Map<String, String> auth() {
                return (Map) map.getOrDefault( AUTH, Model.super.auth());
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

    /// Central place to ask the model to interpret the path as it wishes.
    /// Doing this because a lot of plugins will need to do this. Best to
    /// have them all using the same function so any improvements are automatically
    /// propagated to everybody
    default String interpretPath(String path) {
        if (!path.startsWith("_/")) {
            return path;
        }

        return base() + path.substring(1);
    }
}
