package cowj;

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory ;
import org.python.core.Options;
import org.python.jsr223.PyScriptEngineFactory;
import spark.*;
import zoomba.lang.core.interpreter.ZContext;
import zoomba.lang.core.interpreter.ZScript;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZException;
import zoomba.lang.core.types.ZNumber;

import javax.script.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public interface Scriptable  {

    Object exec(Bindings bindings) throws Exception;

    default Object exec(Request request, Response response){
        SimpleBindings sb = new SimpleBindings();
        sb.put(REQUEST, request);
        sb.put(RESPONSE, response);
        try {
            return exec(sb);
        } catch ( Throwable t){
            if ( sb.containsKey(HALT_ERROR) ){
                Exception e = (Exception) sb.get(HALT_ERROR);
                if (e instanceof TestAsserter.HaltException) {
                    Spark.halt(((TestAsserter.HaltException) e).code, e.getMessage());
                } else {
                    Spark.halt(500, e.getMessage());
                }
            } else {
                response.status(500);
            }
            return t;
        }
    }

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

    /*
    * If the condition is true, halts, with message and error code
    * */
    interface TestAsserter{

        Bindings binding();

        class HaltException extends RuntimeException{
            public final int code;
            public HaltException( String message, int code ){
                super(message);
                this.code = code ;
            }
        }
        default boolean panic(boolean b){
            return panic(b, "Internal Error");
        }
        default boolean panic(boolean b, String message){
            return panic(b, message, 500);
        }
        default boolean panic(boolean b, String message, int code ){
            if ( b ) {
                RuntimeException ex = new HaltException(message, code);
                binding().put(HALT_ERROR, ex);
                throw ex;
            }
            return false;
        }
        default boolean expect(boolean b){
            return expect(b, "Internal Error");
        }
        default boolean expect(boolean b, String message){
            return expect(b, message, 500);
        }
        default boolean expect(boolean b, String message, int code ){
            return panic(!b, message, code);
        }

        String ASSERTER = "Test" ;
    }

    Map<String,String> ENGINES = Map.of(
            "js", "JavaScript",
            "groovy", "groovy",
            "py", "python"

    );

    Map<String,Object> DATA_SOURCES = new HashMap<>();


    String REQUEST = "req" ;
    String RESPONSE = "resp" ;
    String RESULT = "_res" ;

    String DATA_SOURCE = "_ds" ;

    String ENVIRON = "_env" ;

    String HALT_ERROR = "_ex" ;


    static String extension(String path){
        String[] arr = path.split("\\.");
        return arr[arr.length-1].toLowerCase(Locale.ROOT);
    }

    ScriptEngineManager MANAGER = new ScriptEngineManager();

    Runnable JythonLoad = new Runnable() {
        // https://stackoverflow.com/questions/52825426/jython-listed-by-getenginefactories-but-getenginebynamejython-is-null
        static {
            Options.importSite = false;
            // force load engines for fat-jar issues...
            MANAGER.registerEngineName( "JavaScript", new RhinoScriptEngineFactory());
            MANAGER.registerEngineName( "groovy", new GroovyScriptEngineFactory());
            MANAGER.registerEngineName( "python", new PyScriptEngineFactory());
        }
        @Override
        public void run() {}
    };

    static ScriptEngine getEngine(String path){
        String extension = extension(path);
        if ( !ENGINES.containsKey(extension) ) throw new RuntimeException("script type not registered : " + path);
        String engineName = ENGINES.get(extension);
        final ScriptEngine engine = MANAGER.getEngineByName(engineName);
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

    Creator NOP = (path, handler) -> bindings -> "";

    Creator JSR = (path, handler) -> (bindings) -> {
        CompiledScript cs = loadScript(handler);
        bindings.put(DATA_SOURCE, DATA_SOURCES);
        bindings.put(TestAsserter.ASSERTER, (TestAsserter) () -> bindings);
        bindings.put( ENVIRON, System.getenv());
        Object r =  cs.eval(bindings);
        if ( r != null ) return r;
        // Jython issue...
        if ( bindings.containsKey(RESULT) ){
            return bindings.get(RESULT);
        }
        return "";
    };

    Creator ZMB = (path, handler) -> (bindings) -> {
        ZScript zs = loadZScript(handler);
        ZContext.FunctionContext fc = new ZContext.FunctionContext( ZContext.EMPTY_CONTEXT , ZContext.ArgContext.EMPTY_ARGS_CONTEXT);
        bindings.put(DATA_SOURCE, DATA_SOURCES);
        bindings.put( ENVIRON, System.getenv());
        fc.putAll( bindings);
        zs.runContext(fc);
        Function.MonadicContainer mc = zs.execute();
        if ( mc.isNil() ) return "";
        if (mc.value() instanceof Throwable th){
            final Exception ex;
            if ( th instanceof ZException.ZRuntimeAssertion ){
                Object[] args = ((ZException.ZRuntimeAssertion) th).args;
                String message = ((Throwable)args[0]).getMessage();
                int status = 500;
                if ( args.length > 1 ){
                    status = ZNumber.integer( args[1], 500 ).intValue();
                }
                ex = new TestAsserter.HaltException(message, status);
            } else {
                ex = new RuntimeException(th);
            }
            bindings.put( HALT_ERROR, ex );
            throw ex;
        }
        return mc.value();
    };

    Map<String, Scriptable> binaryInstances = new HashMap<>(); // TODO ? Should be LRU? What?

    static Scriptable loadClass( String path){
        if ( binaryInstances.containsKey( path ) ) return binaryInstances.get(path);
        try {
            int inx = path.lastIndexOf(".class");
            String className = path.substring(0, inx);
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if ( !(instance instanceof  Scriptable) ) throw new RuntimeException("Not A Scriptable Implementation! " + clazz);
            binaryInstances.put(path, (Scriptable) instance);
            return (Scriptable) instance;
        }catch (Throwable t){
            System.err.println( "Error registering type for Scriptable... : " + t);
        }
        return NOP.create(path,path);
    }
    Creator BINARY = (path, handler) -> (bindings) -> {
        bindings.put(DATA_SOURCE, DATA_SOURCES);
        bindings.put( ENVIRON, System.getenv());
        Scriptable scriptable = loadClass(handler);
        return scriptable.exec(bindings);
    };

    Creator UNIVERSAL = (path, handler) -> {
        String extension = extension(handler);
        Creator r = switch (extension){
            case "zmb", "zm" -> ZMB;
            case  "js", "groovy", "py" -> JSR;
            case "class" -> BINARY;
            default -> NOP;
        };
        return r.create(path,handler);
    };
}
