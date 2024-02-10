package cowj;

import com.github.alanger.commonjs.FilesystemFolder;
import com.github.alanger.commonjs.Require;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import javax.script.*;
import java.io.File;

/**
 * Manages modules
 */
interface ModuleManager{

    /**
     * Logger for the Cowj ModuleManager
     */
    Logger logger = LoggerFactory.getLogger(ModuleManager.class);

    /**
     * Sets the root path for modules
     * @param rootPath root path of the module
     */
    void modulePath(String rootPath);
    /**
     * <a href="https://langeress.medium.com/commonjs-for-jvm-over-jsr-223-b5e37d1ed572">...</a>
     * Sets up require() functionality for JavasScript
     * @param engine - for this engine
     */
    void enable(ScriptEngine engine);

    /**
     * In case updating binding is required
     * @param cs a CompiledScript for which we need to update binding
     * @param bindings Bindings which needs to be updated before calling
     */
    void updateModuleBindings(CompiledScript cs, Bindings bindings);

    /**
     * A JS Module Manager
     */
    ModuleManager JS_MOD_MGR = new ModuleManager() {

        static {
            System.setProperty(Require.MODULE_NAME, Require.RHINO );
        }
        String modulePath = "." ;
        final Bindings moduleBindings = new SimpleBindings();

        @Override
        public void modulePath(String rootPath) {
            this.modulePath = rootPath + "/js";
        }

        @Override
        public void enable(ScriptEngine engine) {
            final String jsModuleRoot = modulePath;
            FilesystemFolder rootFolder = FilesystemFolder.create(new File(jsModuleRoot), "UTF-8");
            try {
                Require.enable(engine, rootFolder, moduleBindings );
                logger.info("JS Require success! Module path is : {}", jsModuleRoot );
            }catch (Throwable t){
                logger.error("JS Require will not be available : ", t );
            }
        }

        @Override
        public void updateModuleBindings(CompiledScript cs, Bindings bindings) {
            bindings.putAll(moduleBindings);
        }
    };

    /**
     * A Universal Manager which wraps the underlying ones
     */
    ModuleManager UNIVERSAL = new ModuleManager() {
        @Override
        public void modulePath(String rootPath) {
            logger.info("Trying Setting up module path... {}", rootPath );
            JS_MOD_MGR.modulePath(rootPath );
            logger.info("Library Exists and loaded? {}", ZTypes.loadJar(rootPath).value() );
        }

        @Override
        public void enable( ScriptEngine engine) {
            final String engineName = engine.getClass().getName().toLowerCase() ;
            if ( engineName.contains("javascript")){
                JS_MOD_MGR.enable(engine );
            }
        }
        @Override
        public void updateModuleBindings(CompiledScript cs, Bindings bindings) {
            final String engineName = cs.getEngine().getClass().getName().toLowerCase() ;
            if ( engineName.contains("javascript")){
                JS_MOD_MGR.updateModuleBindings(cs, bindings );
            }
        }
    };
}
