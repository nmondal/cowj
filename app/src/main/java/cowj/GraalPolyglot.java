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
 *  - JS first - which conforms to the latest version of the ECMAScript
 *
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
    Context.Builder contextBuilder();

    @Override
    default Object exec(Bindings bindings) throws Exception {
        final Source src = source();
        final String lang = src.getLanguage() ;
        try( final Context ctx = contextBuilder().engine( threadedEngine.get() ).build() ) {
            final Value langBindings = ctx.getBindings(lang);
            bindings.forEach(langBindings::putMember);
            final Value res = ctx.eval(src);
            return res.as(Object.class);
        }
    }

    /**
     * Gets a JS Context along with  commonjs modules or not and enables it
     * @return a Context Builder
     */
    static Context.Builder graalContextBuilderWithCommonJSPath(){
        final String possibleCommonJSPath = ModuleManager.JS_MOD_MGR.modulePath() ;
        Context.Builder builder = Context.newBuilder("js").allowAllAccess(true);
        // https://docs.oracle.com/en/graalvm/jdk/22/docs/reference-manual/js/ScriptEngine/#setting-options-via-system-properties
        final boolean commonJSModule = Files.exists( Paths.get( possibleCommonJSPath ) );
        if ( commonJSModule ){
            builder.allowExperimentalOptions(true)
                    .option("js.commonjs-require", "true")
                    .option("js.commonjs-require-cwd", ModuleManager.JS_MOD_MGR.modulePath());
        }
        logger.info("Polyglot JavaScript CommonJS Module Enabled : {}", commonJSModule );
        return builder ;
    }

    /**
     * ThreadLocal Engine
     *
     * @see <a href="https://stackoverflow.com/questions/63451148/graalvm-polyglot-thread-issue-in-java-spring-boot-application"></a>
     * @see <a href="https://stackoverflow.com/questions/55893836/is-it-possible-to-store-and-load-precompiled-js-to-org-graalvm-polyglot-context"></a>
     */
    ThreadLocal<Engine> threadedEngine = ThreadLocal.withInitial(Engine::create);

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

        if (polyglotsMap.containsKey(path)) return polyglotsMap.get(path);

        // this now becomes a hack ... expression will be used with "2 + 2 //.js"
        final String content = INLINE.equals(directive) ? path : new String(Files.readAllBytes(Paths.get(path)));
        final String extension = Scriptable.extension(path);
        final String lang =  extension ; // TODO if mapping is indeed required
        if ( !"js".equals( lang ) ){ // find if we support or not
            throw new UnsupportedOperationException("Language not supported: " + lang );
        }
        // TODO this should be replaced with a factory - based on language
        final Context.Builder contextBuilder = graalContextBuilderWithCommonJSPath();
        try {
            final Source source = Source.newBuilder( lang, content, path ).build();
            logger.info("Polyglot Engine Language : {} ==> {}", path, lang);
            final GraalPolyglot polyglot = new GraalPolyglot() {
                @Override
                public Source source() {
                    return source;
                }
                @Override
                public Context.Builder contextBuilder() {
                    return contextBuilder;
                }
            };
            polyglotsMap.put(path,polyglot);
            return polyglot;
        } catch (Throwable th) {
            logger.error("Polyglot Script Load Error: {} ==> {}", th.getMessage(), path);
            throw new RuntimeException("Polyglot Script Loading Failed!");
        }
    }
}
