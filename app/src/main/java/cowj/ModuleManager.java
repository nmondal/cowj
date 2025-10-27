package cowj;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.python.jsr223.PyScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import javax.script.*;

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
     * Returns where modules are stored - in real
     * @return directory where modules are stored
     */
    default String modulePath(){ return null; }

    /**
     * <a href="https://langeress.medium.com/commonjs-for-jvm-over-jsr-223-b5e37d1ed572">...</a>
     * Sets up require() functionality for JavasScript
     * @param engine - for this engine
     */
    default void enable(ScriptEngine engine){}

    /**
     * In case updating binding is required
     * @param cs a CompiledScript for which we need to update binding
     * @param bindings Bindings which needs to be updated before calling
     */
    default void updateModuleBindings(CompiledScript cs, Bindings bindings){}

    /**
     * A JS Module Manager
     */
    ModuleManager JS_MOD_MGR = new ModuleManager() {

        String modulePath = "." ;

        @Override
        public void modulePath(String rootPath) {
            this.modulePath = rootPath + "/js";
        }

        @Override
        public String modulePath(){
            return modulePath ;
        }
    };

    /**
     * A Python Module Manager
     */
    ModuleManager PY_MOD_MGR = new ModuleManager() {

        String modulePath = "." ;
        @Override
        public void modulePath(String rootPath) {
            this.modulePath = rootPath + "/py/Lib/site-packages";
        }

        @Override
        public void enable(ScriptEngine engine) {
            try {
                final String template = "import sys; sys.path.append('%s');" ;
                final String script = String.format(template, modulePath);
                engine.eval( script );
                logger.info("Jython site-package success! Module path is : {}", modulePath );
            }catch (Throwable t){
                logger.error("Jython custom package installation will not be available : ", t );
            }
        }
        @Override
        public void updateModuleBindings(CompiledScript cs, Bindings bindings) {}
    };

    /**
     * A Universal Manager which wraps the underlying ones
     */
    ModuleManager UNIVERSAL = new ModuleManager() {
        @Override
        public void modulePath(String rootPath) {
            logger.info("Trying Setting up module path... {}", rootPath );
            JS_MOD_MGR.modulePath(rootPath );
            PY_MOD_MGR.modulePath(rootPath);
            logger.info("Library Exists and loaded? {}", ZTypes.loadJar(rootPath).value() );
        }

        @Override
        public void enable( ScriptEngine engine) {
            if (  engine instanceof PyScriptEngine ){
                PY_MOD_MGR.enable(engine);
            }
        }
        @Override
        public void updateModuleBindings(CompiledScript cs, Bindings bindings) {
            if ( cs.getEngine() instanceof  GraalJSScriptEngine ){
                JS_MOD_MGR.updateModuleBindings(cs, bindings );
            }
        }
    };
}
