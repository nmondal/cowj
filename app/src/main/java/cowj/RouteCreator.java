package cowj;

import spark.Route;
import zoomba.lang.core.interpreter.ZContext;
import zoomba.lang.core.interpreter.ZScript;
import zoomba.lang.core.operations.Function;

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

    default String extension(String path){
        String[] arr = path.split("\\.");
        return arr[arr.length-1].toLowerCase(Locale.ROOT);
    }

    default ScriptEngine getEngine(String path){
       String extension = extension(path);
       if ( !ENGINES.containsKey(extension) ) throw new RuntimeException("script type not registered : " + path);
       String engineName = ENGINES.get(extension);
       final ScriptEngine engine = new ScriptEngineManager().getEngineByName(engineName);
       return engine;
    }

    Map<String, CompiledScript> scripts = new HashMap<>(); // TODO ? Should be LRU? What?

    Map<String, ZScript> zScripts = new HashMap<>(); // TODO ? Should be LRU? What?

    default CompiledScript loadScript(String path) throws IOException, ScriptException {
        if ( scripts.containsKey(path) ) return scripts.get(path);
        String content = new String(Files.readAllBytes(Paths.get(path)));
        final ScriptEngine engine = getEngine(path);
        CompiledScript compiled = ((Compilable) engine).compile(content);
        scripts.put(path,compiled);
        return compiled;
    }

    default ZScript loadZScript(String path)  {
        if ( zScripts.containsKey(path) ) return zScripts.get(path);
        final ZScript zScript = new ZScript(path, null); // no parent
        zScripts.put(path,zScript);
        return zScript;
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

    RouteCreator ZMB = new RouteCreator() {
        @Override
        public Route create(String path, String handler) {
            return (request, response) -> {
                ZScript zs = loadZScript(handler);
                ZContext.FunctionContext fc = new ZContext.FunctionContext( ZContext.EMPTY_CONTEXT , ZContext.ArgContext.EMPTY_ARGS_CONTEXT);
                fc.set("req", request);
                fc.set("resp", response);
                zs.runContext(fc);
                Function.MonadicContainer mc = zs.execute();
                if ( mc.isNil() ) return "";
                if ( mc.value() instanceof Throwable ){
                    response.status(500);
                }
                return mc.value();
            };
        }
    };

    RouteCreator UNIVERSAL = new RouteCreator() {
        @Override
        public Route create(String path, String handler) {
            String extension = extension(handler);
            RouteCreator r = switch (extension){
                case "zmb", "zm" -> ZMB;
                case "js", "groovy" -> JSR;
                default -> NOP;
            };
            return r.create(path,handler);
        }
    };
}
