package cowj;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Graal Polyglot Scripting Abstraction for Cowj
 * We start supporting
 *  - JS  - which conforms to the latest version of the ECMAScript - this is default JS engine
 *  - Python - which conforms to the latest version of Python 3 - to use this, use extension py3
 * @see  <a href="https://www.graalvm.org/jdk25/reference-manual/polyglot-programming"></a>
 */
public interface GraalPolyglot extends Scriptable{

    /**
     * Logger for the Cowj GraalPolyglot
     */
    Logger logger = LoggerFactory.getLogger(GraalPolyglot.class);

    /**
     * Gets Source of the script
     * @return Source of the script
     */
    Source source();

    /**
     * Given every script runs within its own Context
     * This gets the Cached builder associated with the Context
     * @return gets a Cached Builder for the Context
     */
    default Context.Builder contextBuilder(){
        return Context.newBuilder();
    }

    /**
     * Given every script runs within its own Context
     * This gets the Cached Context or new Context
     * @return gets a Context
     */
    default Context context(){
        return contextBuilder().engine( threadedEngine.get() ).build() ;
    }

    @Override
    default Object exec(Bindings bindings) throws Exception {
        final Source src = source();
        final String lang = src.getLanguage() ;
        final Context ctx = context() ;
        final Value langBindings = ctx.getBindings(lang);
        bindings.forEach(langBindings::putMember);
        final Value res = ctx.eval(src);
        return res.as(Object.class);
    }

    /**
     * Gets a JS Context along with  commonjs modules or not and enables it
     * @return a Context Builder
     */
    static Context.Builder javaScriptWithCommonJSPath(){
        final String possibleCommonJSPath = ModuleManager.JS_MOD_MGR.modulePath() ;
        Context.Builder builder = Context.newBuilder("js").allowAllAccess(true);
        // https://docs.oracle.com/en/graalvm/jdk/22/docs/reference-manual/js/ScriptEngine/#setting-options-via-system-properties
        final boolean commonJSModule = Files.exists( Paths.get( possibleCommonJSPath ) );
        logger.info("Polyglot JavaScript CommonJS Module Enabled : {}", commonJSModule );
        if ( commonJSModule ){
            builder.allowExperimentalOptions(true)
                    .option("js.commonjs-require", "true")
                    .option("js.commonjs-require-cwd", ModuleManager.JS_MOD_MGR.modulePath());
            logger.info("Polyglot JavaScript CommonJS Module Location : {}", ModuleManager.JS_MOD_MGR.modulePath() );

        }
        return builder ;
    }

    /**
     * Build a JavaScript GraalPolyglot
     * Per request a new context gets created
     * @param content of this character content
     * @param filePath using this as name - the file path of the content
     * @return a GraalPolyglot
     * @throws IOException in case source building fails
     */
    static GraalPolyglot js( CharSequence content, String filePath) throws IOException {
        final Source source = Source.newBuilder( "js", content, filePath ).build();
        return new GraalPolyglot() {
            final Context.Builder builder = javaScriptWithCommonJSPath();
            @Override
            public Source source() {
                return source;
            }
            @Override
            public Context.Builder contextBuilder() {
                return builder;
            }
        };
    }

    /**
     * Gets a Python3 Context along with  site packages or not and enables it
     * @return a Context Builder
     */
    static Context.Builder python(){
        final String possiblePythonSitePath = ModuleManager.PY3_MOD_MGR.modulePath();
        Context.Builder builder = Context.newBuilder("python").allowAllAccess(true);
        // https://docs.oracle.com/en/graalvm/jdk/22/docs/reference-manual/js/ScriptEngine/#setting-options-via-system-properties
        final boolean sitePackage = Files.exists( Paths.get( possiblePythonSitePath ) );
        logger.info("Polyglot Python Site Package Module Enabled : {}", sitePackage );
        if ( sitePackage ){
            builder.option("python.ForceImportSite", "true")
                    .option("python.PythonPath", possiblePythonSitePath);
            logger.info("Polyglot Python Site Package Module Location : {}", possiblePythonSitePath );
        }
        return builder ;
    }

    /**
     * Build a Python GraalPolyglot
     * Per thread a new Context is created
     * @param content of this character content
     * @param filePath using this as name - the file path of the content
     * @return a GraalPolyglot
     * @throws IOException in case source building fails
     */
    static GraalPolyglot python( CharSequence content, String filePath) throws IOException {
        final Source source = Source.newBuilder( "python", content, filePath ).build();
        return new GraalPolyglot() {
            @Override
            public Source source() {
                return source;
            }
            @Override
            public Context context() {
                return threadedPythonContext.get();
            }
        };
    }

    /**
     * ThreadLocal Engine
     *
     * @see <a href="https://stackoverflow.com/questions/63451148/graalvm-polyglot-thread-issue-in-java-spring-boot-application"></a>
     * @see <a href="https://stackoverflow.com/questions/55893836/is-it-possible-to-store-and-load-precompiled-js-to-org-graalvm-polyglot-context"></a>
     */
    ThreadLocal<Engine> threadedEngine = ThreadLocal.withInitial(Engine::create);

    /**
     * ThreadLocal Graal Python Context
     * This improves the speed by at least 40% as we have tested it - still a far cry from Jython
     * @see <a href="https://stackoverflow.com/questions/63451148/graalvm-polyglot-thread-issue-in-java-spring-boot-application"></a>
     * @see <a href="https://github.com/oracle/graalpython/issues/564"></a>
     */
    ThreadLocal<Context> threadedPythonContext = ThreadLocal.withInitial(() -> python().engine( threadedEngine.get() ).build());

    /**
     * A map of path to GraalPolyglot map for each script sources
     */
    Map<String, GraalPolyglot> polyglotsMap = new HashMap<>();

    /**
     * Loads a polyglot Scriptable from various params thread safe
     * @param directive a directive
     * @param path path to the script
     * @return a GraalPolyglot
     * @throws IOException in case script can not be loaded/found
     */
    static GraalPolyglot loadPolyglot(String directive, String path) throws IOException {

        GraalPolyglot polyglot =  polyglotsMap.get(path);
        if (  polyglot != null ) return polyglot;

        // this now becomes a hack ... expression will be used with "2 + 2 //.js"
        final String content = INLINE.equals(directive) ? path : new String(Files.readAllBytes(Paths.get(path)));
        final String extension = Scriptable.extension(path);

        if ( "py3".equals( extension ) ){
            polyglot = python(content,path);
        } else if ( "js".equals( extension ) ){
            polyglot = js(content,path);
        }
        else { // find if we support or not
            throw new UnsupportedOperationException("Graal Language not identified by extension : " + extension );
        }
        logger.info("Polyglot Engine Language : {} ==> {}", path, extension );
        // put it up
        polyglotsMap.put(path,polyglot);
        return polyglot;
    }
}
