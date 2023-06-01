package cowj;

import org.python.core.Options;
import spark.Filter;
import spark.Request;
import spark.Response;
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

public interface Scriptable  {

    Object exec(Request request, Response response) throws Exception;

    interface Creator {
        Scriptable create(String path, String handler);
        default Route createRoute( String path, String handler ){
            Scriptable scriptable = create(path, handler);
            return scriptable::exec;
        }
        default Filter createFilter( String path, String handler ){
            Scriptable scriptable = create(path, handler);
            return scriptable::exec;
        }
    }
    Map<String,String> ENGINES = Map.of(
            "js", "JavaScript",
            "groovy", "groovy",
            "py", "python"

    );

    String REQUEST = "req" ;
    String RESPONSE = "resp" ;
    String RESULT = "_res" ;

    static String extension(String path){
        String[] arr = path.split("\\.");
        return arr[arr.length-1].toLowerCase(Locale.ROOT);
    }

    Runnable JythonLoad = new Runnable() {
        // https://stackoverflow.com/questions/52825426/jython-listed-by-getenginefactories-but-getenginebynamejython-is-null
        static {
            Options.importSite = false;
        }
        @Override
        public void run() {}
    };

    static ScriptEngine getEngine(String path){
        String extension = extension(path);
        if ( !ENGINES.containsKey(extension) ) throw new RuntimeException("script type not registered : " + path);
        String engineName = ENGINES.get(extension);
        final ScriptEngine engine = new ScriptEngineManager().getEngineByName(engineName);
        return engine;
    }

    Map<String, CompiledScript> scripts = new HashMap<>(); // TODO ? Should be LRU? What?

    Map<String, ZScript> zScripts = new HashMap<>(); // TODO ? Should be LRU? What?

    static CompiledScript loadScript(String path) throws IOException, ScriptException {
        if ( scripts.containsKey(path) ) return scripts.get(path);
        String content = new String(Files.readAllBytes(Paths.get(path)));
        final ScriptEngine engine = getEngine(path);
        CompiledScript compiled = ((Compilable) engine).compile(content);
        scripts.put(path,compiled);
        return compiled;
    }

    static ZScript loadZScript(String path)  {
        if ( zScripts.containsKey(path) ) return zScripts.get(path);
        final ZScript zScript = new ZScript(path, null); // no parent
        zScripts.put(path,zScript);
        return zScript;
    }

    Creator NOP = (path, handler) -> (request, response) -> "";

    Creator JSR = (path, handler) -> (request, response) -> {
        CompiledScript cs = loadScript(handler);
        SimpleBindings sb = new SimpleBindings();
        sb.put(REQUEST, request);
        sb.put(RESPONSE, response);
        try {
            Object r =  cs.eval(sb);
            if ( r != null ) return r;
            // Jython issue...
            if ( sb.containsKey(RESULT) ){
                return sb.get(RESULT);
            }
            return "";

        } catch ( Throwable t){
            response.status(500);
            return t;
        }
    };

    Creator ZMB = (path, handler) -> (request, response) -> {
        ZScript zs = loadZScript(handler);
        ZContext.FunctionContext fc = new ZContext.FunctionContext( ZContext.EMPTY_CONTEXT , ZContext.ArgContext.EMPTY_ARGS_CONTEXT);
        fc.set(REQUEST, request);
        fc.set(RESPONSE, response);
        zs.runContext(fc);
        Function.MonadicContainer mc = zs.execute();
        if ( mc.isNil() ) return "";
        if ( mc.value() instanceof Throwable ){
            response.status(500);
        }
        return mc.value();
    };

    Creator UNIVERSAL = (path, handler) -> {
        String extension = extension(handler);
        Creator r = switch (extension){
            case "zmb", "zm" -> ZMB;
            case  "js", "groovy", "py" -> JSR;
            default -> NOP;
        };
        return r.create(path,handler);
    };
}
