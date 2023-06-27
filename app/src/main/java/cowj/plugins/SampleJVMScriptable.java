package cowj.plugins;

import cowj.Scriptable;

import javax.script.Bindings;
import java.util.Map;

public class SampleJVMScriptable implements Scriptable {
    @Override
    public Object exec(Bindings bindings) throws Exception {
        // get the shared data an increase
        Map<String,Object> shared = (Map)bindings.get( Scriptable.SHARED );
        int i = (int)shared.getOrDefault("hhgg", 0);
        i++; // increment this
        shared.put("hhgg", i);
        // we add the stuff we need to the binding...
        return "hello, world!";
    }
}
