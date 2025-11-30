package cowj.plugins;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import cowj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is the Plugin for JavaScript Server Side Rendering
 * It loads a bunch of contexts, and then you can simply render any page via render() call
 */
public interface JSRendering {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(JSRendering.class);

    /**
     * Renders to String from data accumulated from the script file using the template file
     * @param scriptPath JavaScript file that would be run to produce the data ( actual SSR )
     * @param htmlTemplatePath template file that would be returned after populating the data
     * @return some HTML rendered on Server Side
     */
    default EitherMonad<String> render(String scriptPath, String htmlTemplatePath){
        EitherMonad<Object> em = evalScript( engine(), scriptPath, model().staticPath() );
        if ( em.inError() ) {
            final String message = "Error in Script :" + scriptPath ;
            logger.error(message, em.error());
            return EitherMonad.error( em.error() );
        }
        if ( em.value() instanceof Map<?,?> ){
            return EitherMonad.call( () ->{
                final Map<?,?> context = (Map<?,?>)em.value();
                final String templateBody = relocAndLoadFileData( htmlTemplatePath , model().staticPath()) ;
                return model().template( templateBody, context );
            });
        }
        return EitherMonad.error(new IllegalStateException("Renderer Function did not return a Map!"));
    }

    /**
     * Underlying Scripting Engine
     * @return A JSR223 JavaScript Engine
     */
    ScriptEngine engine();

    /**
     * Underlying Model
     * @return a Model
     */
    Model model();

    private static String relocAndLoadFileData( String path, String modelStaticPath) throws Exception {
        final String localPath;
        if ( new File(path).isAbsolute() ){
            localPath =  path;
        } else {
            localPath =  modelStaticPath + File.separator + path ;
        }
        logger.info("Reading script locally : {}", localPath);
        return  Files.readString(Paths.get(localPath));
    }

    /**
     * Evaluates the script using the engine
     * @param engine JSR223 JavaScript Engine
     * @param scriptPath path of the script ( either absolute, or relative to the modelStaticPath )
     * @param modelStaticPath the static path of the model
     * @return result of the script execution
     */
    static EitherMonad<Object> evalScript(ScriptEngine engine, String scriptPath, String modelStaticPath){
        return EitherMonad.call( () -> {
            final String scriptBody;
            if (scriptPath.startsWith("http")) {
                URL u = new URL(scriptPath);
                logger.info("Reading script url : {}", scriptPath);
                try (InputStream in = u.openStream()) {
                    scriptBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                scriptBody = relocAndLoadFileData( scriptPath, modelStaticPath );
            }
           return engine.eval(scriptBody);
        });
    }

    /**
     * Key for the context scripts
     */
    String CONTEXT = "context" ;


    /**
     * A DataSource.Creator for Generic JS Server Side Rendering
     */
    DataSource.Creator SSR = (name, config, parent) -> {
        try {
            final GraalJSScriptEngine engine = GraalJSScriptEngine.create( null,
                    GraalPolyglot.javaScriptWithCommonJSPath() );
            List<String> scripts = (List<String>)config.getOrDefault( CONTEXT , Collections.emptyList());
            for ( String path : scripts ){
               EitherMonad<Object> em = evalScript(engine, path, parent.staticPath() );
               if ( em.inError() ){
                   final String message = "Error in Script :" + path ;
                   logger.error(message, em.error());
                   throw em.error();
               }
            }

            final JSRendering renderer = new JSRendering() {
                @Override
                public ScriptEngine engine() {
                    return engine;
                }
                @Override
                public Model model() {
                    return parent;
                }
            };
            return DataSource.dataSource(name, renderer);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    };
}
