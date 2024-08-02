package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import cowj.Scriptable;

import javax.script.ScriptEngine;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface JSRendering {

    default EitherMonad<String> render(String scriptPath, String htmlTemplatePath){
        EitherMonad<Object> em = evalScript( engine(), scriptPath, model().staticPath() );
        if ( em.inError() ) return EitherMonad.error( new IllegalStateException("Error in Script : " + scriptPath, em.error()));
        if ( em.value() instanceof Map<?,?> ){
            return EitherMonad.call( () ->{
                final Map<?,?> context = (Map<?,?>)em.value();
                final String templateBody = relocAndLoadFileData( htmlTemplatePath , model().staticPath()) ;
                return model().template( templateBody, context );
            });
        }
        return EitherMonad.error(new IllegalStateException("Renderer Function did not return a Map!"));
    }

    ScriptEngine engine();

    Model model();

    private static String relocAndLoadFileData( String path, String modelStaticPath) throws Exception {
        final String localPath;
        if ( path.startsWith("/")){
            localPath =  path;
        } else {
            localPath =  modelStaticPath + "/" + path ;
        }
        return  Files.readString(Paths.get(localPath));
    }

    static EitherMonad<Object> evalScript(ScriptEngine engine, String scriptPath, String modelStaticPath){
        return EitherMonad.call( () -> {
            final String scriptBody;
            if (scriptPath.startsWith("http")) {
                URL u = new URL(scriptPath);
                try (InputStream in = u.openStream()) {
                    scriptBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                scriptBody = relocAndLoadFileData( scriptPath, modelStaticPath );
            }
           return engine.eval(scriptBody);
        });
    }

    String CONTEXT = "context" ;


    /**
     * A DataSource.Creator for Generic JS Server Side Rendering
     */
    DataSource.Creator SSR = (name, config, parent) -> {
        try {
            final ScriptEngine engine = Scriptable.getEngine("__.js");
            List<String> scripts = (List<String>)config.getOrDefault( CONTEXT , Collections.emptyList());
            for ( String path : scripts ){
               EitherMonad<Object> em = evalScript(engine, path, parent.staticPath() );
               if ( em.inError() ) throw new IllegalStateException( "Error in Script : " + path, em.error() );
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
