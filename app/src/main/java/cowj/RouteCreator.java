package cowj;

import spark.Route;

import javax.script.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public interface RouteCreator {
    Route create(String path, String handler);

    Map<String,String> ENGINES = Map.of(
            "js", "JavaScript",
            "groovy", "groovy"
    );

    default ScriptEngine getEngine(String path){
       String[] arr = path.split("\\.");
       String extension = arr[arr.length-1].toLowerCase(Locale.ROOT);
       if ( !ENGINES.containsKey(extension) ) throw new RuntimeException("script type not registered : " + path);
       String engineName = ENGINES.get(extension);
       final ScriptEngine engine = new ScriptEngineManager().getEngineByName(engineName);
       return engine;
    }

    Map<String, CompiledScript> scripts = new HashMap<>(); // TODO ? Should be LRU? What?

    default CompiledScript loadScript(String path) throws IOException, ScriptException {
        if ( scripts.containsKey(path) ) return scripts.get(path);
        String content = new String(Files.readAllBytes(Paths.get(path)));
        final ScriptEngine engine = getEngine(path);
        CompiledScript compiled = ((Compilable) engine).compile(content);
        scripts.put(path,compiled);
        return compiled;
    }

    RouteCreator NOP = (path, handler) -> (Route) (request, response) -> ""; // default empty response

    RouteCreator JSR = new RouteCreator() {
        @Override
        public Route create(String path, String handler) {
            return (request, response) -> {
                CompiledScript cs = loadScript(handler);
                SimpleScriptContext sc = new SimpleScriptContext();
                sc.setAttribute("req", request, ScriptContext.ENGINE_SCOPE);
                sc.setAttribute("resp", response, ScriptContext.ENGINE_SCOPE);
                try {
                    return cs.eval(sc);
                } catch ( Throwable t){
                    response.status(500);
                    return t;
                }
            };
        }
    };
}
