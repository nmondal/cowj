package cowj;

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;
import org.python.core.Options;
import org.python.jsr223.PyScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.*;
import zoomba.lang.core.interpreter.ZScript;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZException;
import zoomba.lang.core.types.ZNumber;

import javax.script.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scripting Abstraction for Cowj
 *
 * @see <a href="https://en.wikipedia.org/wiki/Scripting_for_the_Java_Platform">JSR-223</a>
 */
public interface Scriptable extends java.util.function.Function<Bindings, Object> {

    /**
     * Logger for the Cowj Scriptable
     */
    Logger logger = LoggerFactory.getLogger(Scriptable.class);

    /**
     * Basal method to run any scripts
     *
     * @param bindings Bindings of  javax.Scripting.Binding
     * @return result of the Script
     * @throws Exception any error happened while running the script
     * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.scripting/javax/script/Bindings.html">Bindings</a>
     */
    Object exec(Bindings bindings) throws Exception;

    @Override
    default Object apply(Bindings bindings) {
        try {
            return exec(bindings);
        } catch (Throwable t) {
            throw Function.runTimeException(t);
        }
    }

    /**
     * Abstraction to be used for spark.Route and spark.Filter
     *
     * @param request  a spark.Request object available as "req" in the script env
     * @param response a spark.Response object available as "resp" in the script env
     * @return result of the computation
     * @see <a href="https://sparkjava.com/documentation#filters">Filters</a>
     * @see <a href="https://sparkjava.com/documentation#request">Request</a>
     * @see <a href="https://sparkjava.com/documentation#response">Response</a>
     */
    default Object exec(Request request, Response response) {
        SimpleBindings sb = new SimpleBindings();
        sb.put(REQUEST, request);
        sb.put(RESPONSE, response);
        try {
            return exec(sb);
        } catch (Throwable t) {
            if (sb.containsKey(HALT_ERROR)) {
                TestAsserter.HaltException he = (TestAsserter.HaltException) sb.get(HALT_ERROR);
                logger.warn("checked error : " + he.getMessage());
                return Spark.halt(he.code, he.getMessage());
            }
            logger.error("unchecked error : " + t);
            final String message ;
            if (App.isProdMode()) {
                // in here, do not tell consumers what error happened
                message = "Internal Server Error!" ;
            } else {
                // in here tell consumers why we goofed up
                message = t.toString();
            }
            // now try respond...
            return Spark.halt(500, message);
        }
    }

    /**
     * Creator for the Scriptable
     */
    interface Creator {

        /**
         * Creates a Scriptable
         *
         * @param path    for this route path
         * @param handler from this script path
         * @return a Scriptable
         */
        Scriptable create(String path, String handler);

        /**
         * The _async_ prefix for the routes, which deemed to be run in async mode
         */
        String ASYNC_ROUTE_PREFIX = "/_async_/";

        /**
         * Creates a Scriptable as spark.Route
         *
         * @param path    for this route path
         * @param handler from this script path
         * @return a Scriptable as spark.Route
         * @see <a href="https://sparkjava.com/documentation#routes">Routes</a>
         */
        default Route createRoute(String path, String handler) {
            final boolean isAsync = path.startsWith(ASYNC_ROUTE_PREFIX);
            Scriptable scriptable = create(path, handler);
            if (isAsync) {
                logger.info("Async Route : {} ==> {}", path, handler);
                return AsyncHandler.instance().route(scriptable);
            }
            return scriptable::exec;
        }

        /**
         * Creates a Scriptable as spark.Filter
         *
         * @param path    for this route path
         * @param handler from this script path
         * @return a Scriptable as spark.Filter
         * @see <a href="https://sparkjava.com/documentation#filters">Filters</a>
         */
        default Filter createFilter(String path, String handler) {
            Scriptable scriptable = create(path, handler);
            return scriptable::exec;
        }
    }

    /**
     * Abstraction for Raising Errors which then propagates to Clients from Scriptable
     */
    interface TestAsserter {

        /**
         * Gets the underlying Bindings used
         *
         * @return the Bindings
         */
        Bindings binding();

        /**
         * prints to server console out stream
         *
         * @param format string to be formatted
         * @param args   to be used to format
         * @return underlying server console PrintStream
         */
        default PrintStream print(String format, Object... args) {
            return System.out.printf(format, args);
        }

        /**
         * prints to server console err stream
         *
         * @param format string to be formatted
         * @param args   to be used to format
         * @return underlying server console PrintStream
         */
        default PrintStream printe(String format, Object... args) {
            return System.err.printf(format, args);
        }

        /**
         * Abstraction to do proper error handling in Scripts
         * Exit Early is the idea
         */
        class HaltException extends RuntimeException {
            /**
             * Underlying status code which we would return to the client
             */
            public final int code;

            /**
             * Creates a HaltException
             *
             * @param message to be send back to erring  client
             * @param code    HTTP status code
             */
            public HaltException(String message, int code) {
                super(message);
                this.code = code;
            }
        }

        /**
         * Raises HaltException
         *
         * @param b if true raise HaltException
         * @return false if no error
         */
        default boolean panic(boolean b) {
            return panic(b, "Internal Error");
        }

        /**
         * Raises HaltException
         *
         * @param b       if true raise HaltException
         * @param message with this string as message
         * @return false if no error
         */
        default boolean panic(boolean b, String message) {
            return panic(b, message, 500);
        }

        /**
         * Raises HaltException
         *
         * @param b       if true raise HaltException
         * @param message with this string as message
         * @param code    with this HTTP status code
         * @return false if no error
         */
        default boolean panic(boolean b, String message, int code) {
            if (b) {
                RuntimeException ex = new HaltException(message, code);
                binding().put(HALT_ERROR, ex);
                throw ex;
            }
            return false;
        }

        /**
         * Raises HaltException
         *
         * @param b if false raise HaltException
         * @return false if no error
         */
        default boolean expect(boolean b) {
            return expect(b, "Internal Error");
        }


        /**
         * Raises HaltException
         *
         * @param b       if false raise HaltException
         * @param message with this string as message
         * @return false if no error
         */
        default boolean expect(boolean b, String message) {
            return expect(b, message, 500);
        }

        /**
         * Raises HaltException
         *
         * @param b       if false raise HaltException
         * @param message with this string as message
         * @param code    with this HTTP status code
         * @return false if no error
         */
        default boolean expect(boolean b, String message, int code) {
            return panic(!b, message, code);
        }

        /**
         * Key name for the TestAsserter instance inside a Scriptable script
         */
        String ASSERTER = "Test";

        /**
         * Gets a memory for a name space
         *
         * @param namespace the name space
         * @return a Map of string to object to store data
         */
        default Map<String, Object> shared(String namespace) {
            synchronized (SHARED_MEMORY) {
                if (SHARED_MEMORY.containsKey(namespace)) return (Map<String, Object>) SHARED_MEMORY.get(namespace);
                Map<String, Object> syncMap = new ConcurrentHashMap<>();
                SHARED_MEMORY.put(namespace, syncMap);
                return syncMap;
            }
        }
    }

    /**
     * Various Scripting Engines
     */
    Map<String, String> ENGINES = Map.of(
            "js", "JavaScript",
            "groovy", "groovy",
            "py", "python"

    );

    /**
     * Various data sources store as map
     * key - name of the DataSource
     * Value - the proxy() of the DataSource
     * Inside a Scriptable script this is accessible via _ds
     */
    Map<String, Object> DATA_SOURCES = new HashMap<>();

    /**
     * Shared Memory
     * Inside a Scriptable script this is accessible via _shared
     */
    Map<String, Object> SHARED_MEMORY = new ConcurrentHashMap<>();

    /**
     * Key name for the spark.Request parameter
     */
    String REQUEST = "req";

    /**
     * Key name for the spark.Response parameter
     */
    String RESPONSE = "resp";

    /**
     * Key name for the result for the script
     * Jython requires to set up _res = value
     */
    String RESULT = "_res";

    /**
     * Key name for the  DATA_SOURCES
     */
    String DATA_SOURCE = "_ds";

    /**
     * Key name for the  System.getenv()
     */
    String ENVIRON = "_env";


    /**
     * Key name for the  SHARED_MEMORY
     */
    String SHARED = "_shared";

    /**
     * Key name for the error in the script
     */
    String HALT_ERROR = "_ex";

    /**
     * Constant to be used to have the script inline
     */
    String INLINE = "(.)";

    /**
     * Method to find extension from a path
     *
     * @param path file path or string
     * @return extension for the file, string
     */
    static String extension(String path) {
        String[] arr = path.split("\\.");
        return arr[arr.length - 1].toLowerCase(Locale.ROOT);
    }

    /**
     * Default JSR-223 Engine Manager
     */
    ScriptEngineManager MANAGER = new ScriptEngineManager();

    /**
     * Cached map of all CompiledScript
     *
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/javax/script/CompiledScript.html">CompiledScript</a>
     * Key - location of the scripts
     * Value - CompiledScript
     */
    Map<String, CompiledScript> scripts = new HashMap<>(); // TODO ? Should be LRU? What?

    /**
     * Cached map of all ZScript
     *
     * @see <a href="https://gitlab.com/non.est.sacra/zoomba/-/blob/master/src/main/java/zoomba/lang/core/interpreter/ZScript.java">ZScript</a>
     * Key - location of the scripts
     * Value - ZScript
     */
    Map<String, ZScript> zScripts = new HashMap<>(); // TODO ? Should be LRU? What?

    /**
     * Basal hack to load Jython and other Engines
     */
    Serializable JythonLoad = new Serializable() { // simplest hack to load Jython ...
        // https://stackoverflow.com/questions/52825426/jython-listed-by-getenginefactories-but-getenginebynamejython-is-null
        static {
            Options.importSite = false;
            // force load engines for fat-jar issues...
            MANAGER.registerEngineName("JavaScript", new RhinoScriptEngineFactory());
            MANAGER.registerEngineName("groovy", new GroovyScriptEngineFactory());
            MANAGER.registerEngineName("python", new PyScriptEngineFactory());
            FileWatcher.ofCacheAndRegister(zScripts, (path) -> loadZScript("reload", path));
            FileWatcher.ofCacheAndRegister(scripts, (path) -> loadScript("reload", path));
        }
    };

    /**
     * Get engine from path
     *
     * @param path file location
     * @return a ScriptEngine
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/javax/script/ScriptEngine.html">ScriptEngine</a>
     */
    static ScriptEngine getEngine(String path) {
        String extension = extension(path);
        if (!ENGINES.containsKey(extension)) throw new IllegalArgumentException("script type not registered : " + path);
        String engineName = ENGINES.get(extension);
        final ScriptEngine engine = MANAGER.getEngineByName(engineName);
        return engine;
    }

    /**
     * Loads a script for JSR-223 Engine
     *
     * @param directive ignored unless it is INLINE, then use the path as executable string
     * @param path      file location from which script needs to be created, if INLINE then comment the extension in the end
     *                  example: "2+2; //.js" will load js engine
     * @return a CompiledScript
     * @throws IOException in case file not found
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/javax/script/CompiledScript.html">CompiledScript</a>
     */
    static CompiledScript loadScript(String directive, String path) throws IOException {
        // this now becomes a hack ... expression will be used with "2 + 2 //.js"
        // and this will load the engine
        if (scripts.containsKey(path)) return scripts.get(path);
        String content = INLINE.equals(directive) ? path : new String(Files.readAllBytes(Paths.get(path)));
        final ScriptEngine engine = getEngine(path);
        try {
            CompiledScript compiled = ((Compilable) engine).compile(content);
            scripts.put(path, compiled);
            return compiled;
        } catch (ScriptException sc) {
            logger.error("Script Load Error: {} ==> {}", sc.getMessage(), path);
            throw new RuntimeException("Script Loading Failed!");
        }
    }


    /**
     * Loads a script for ZoomBA Engine
     *
     * @param directive ignored unless it is INLINE, then use the path as executable string
     * @param path      file location from which script needs to be created
     * @return a ZScript
     * @see <a href="https://gitlab.com/non.est.sacra/zoomba/-/blob/master/src/main/java/zoomba/lang/core/interpreter/ZScript.java">ZScript</a>
     */
    static ZScript loadZScript(String directive, String path) {
        if (zScripts.containsKey(path)) return zScripts.get(path);
        try {
            final ZScript zScript = INLINE.equals(directive) ? new ZScript(path) : new ZScript(path, null); // no parent
            zScripts.put(path, zScript);
            return zScript;
        } catch (RuntimeException rt) {
            // zmb has support for detecting script path
            logger.error("Script Load Error: {}", rt.getMessage());
            throw new RuntimeException("Script Loading Failed!");
        }
    }

    /**
     * A No Operation Scriptable Creator
     */
    Creator NOP = (path, handler) -> bindings -> "";

    /**
     * Adds some parameters to the Bindings passed
     *
     * @param bindings a binding which is being prepared
     */
    static void prepareBinding(Bindings bindings) {
        bindings.put(DATA_SOURCE, DATA_SOURCES);
        bindings.put(TestAsserter.ASSERTER, (TestAsserter) () -> bindings);
        bindings.put(ENVIRON, System.getenv());
        bindings.put(SHARED, SHARED_MEMORY);
    }

    /**
     * JSR-223 Scriptable creator
     */
    Creator JSR = (path, handler) -> (bindings) -> {
        CompiledScript cs = loadScript(path, handler);
        prepareBinding(bindings);
        Object r = cs.eval(bindings);
        if (r != null) return r;
        // Jython issue...
        if (bindings.containsKey(RESULT)) {
            return bindings.get(RESULT);
        }
        return "";
    };

    /**
     * ZoomBA Scriptable creator
     */
    Creator ZMB = (path, handler) -> (bindings) -> {
        ZScript zs = loadZScript(path, handler);
        prepareBinding(bindings);
        // This ensures things are pure function
        Function.MonadicContainer mc = zs.eval(bindings);
        if (mc.isNil()) return "";
        if (mc.value() instanceof Throwable th) {
            final Exception ex;
            if (th instanceof ZException.ZRuntimeAssertion) {
                Object[] args = ((ZException.ZRuntimeAssertion) th).args;
                String message = th.toString();
                int status = 500;
                if (args.length > 0) {
                    message = ((Throwable) args[0]).getMessage();
                    if (args.length > 1) {
                        status = ZNumber.integer(args[1], 500).intValue();
                    }
                }
                ex = new TestAsserter.HaltException(message, status);
                bindings.put(HALT_ERROR, ex);
            } else {
                ex = new RuntimeException(th);
            }
            throw ex;
        }
        return mc.value();
    };

    /**
     * Underlying Cache for Binary Scriptable
     */
    Map<String, Scriptable> binaryInstances = new HashMap<>(); // TODO ? Should be LRU? What?

    /**
     * Loading a class as scriptable
     *
     * @param path full classname of the class with ".class" in the end
     * @return an instance of the class casted as Scriptable
     */
    static Scriptable loadClass(String path) {
        if (binaryInstances.containsKey(path)) return binaryInstances.get(path);
        try {
            int inx = path.lastIndexOf(".class");
            String className = path.substring(0, inx);
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Scriptable))
                throw new RuntimeException("Not A Scriptable Implementation! " + clazz);
            binaryInstances.put(path, (Scriptable) instance);
            return (Scriptable) instance;
        } catch (Throwable t) {
            logger.error("Error registering type for Scriptable... : " + t);
        }
        return NOP.create(path, path);
    }

    /**
     * Binary - class based creator
     */
    Creator BINARY = (path, handler) -> (bindings) -> {
        prepareBinding(bindings);
        Scriptable scriptable = loadClass(handler);
        return scriptable.exec(bindings);
    };

    /**
     * Universal Scriptable Creator
     * Merging 3 different types
     * ZoomBA, JSR, Binary
     */
    Creator UNIVERSAL = (path, handler) -> {
        String extension = extension(handler);
        Creator r = switch (extension) {
            case "zmb", "zm" -> ZMB;
            case "js", "groovy", "py" -> JSR;
            case "class" -> BINARY;
            default -> {
                logger.error("No pattern matched for path '{}' -> For handler '{}' Using NOP!", path, handler);
                yield NOP;
            }
        };
        return r.create(path, handler);
    };
}
