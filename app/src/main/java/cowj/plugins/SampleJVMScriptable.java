package cowj.plugins;

import cowj.Scriptable;

import javax.script.Bindings;

public class SampleJVMScriptable implements Scriptable {
    @Override
    public Object exec(Bindings bindings) throws Exception {
        // we add the stuff we need to the binding...
        return "Hello world - from JVM Scriptable!";
    }
}
