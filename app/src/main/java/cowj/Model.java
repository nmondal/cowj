package cowj;

import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public interface Model {

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
        return "static" ;
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

    String PORT = "port" ;
    String STATIC = "static" ;
    String ROUTES = "routes" ;

    String THREADING = "threading" ;

    String AUTH = "auth" ;

    String FILTERS = "filters" ;


    static Model from(final Map<String,Object> map, final String baseDir){

        return new Model() {

            @Override
            public String base(){ return baseDir; }
            @Override
            public int port() {
                return (int)map.getOrDefault(PORT, Model.super.port());
            }

            @Override
            public String staticPath() {
                return (String) map.getOrDefault(STATIC, Model.super.staticPath());
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
            public Map<String, Map<String, String>> filters() {
                return (Map) map.getOrDefault( ROUTES, Model.super.filters());
            }
        };
    }

    static Model from(String path){
        File f = new File(path);
        final String abs = f.getAbsolutePath();
        if ( !f.exists() ) throw new RuntimeException(String.format("No file name : '%s' ", abs ));
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
        throw new RuntimeException(String.format("file '%s' must be a json or yaml", abs ));
    }
}
