package cowj;


import org.graalvm.polyglot.Context;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GraalPolyglotTest {

    final String someFile = "samples/test_scripts/error_1_arg.zm" ;

    @Test
    public void python3SitePackagesTest() throws Exception {
        Path tmpProjectPath = Files.createTempDirectory( "proj_");
        Files.createDirectories(Paths.get( tmpProjectPath.toString(), "py/lib/python3.10/site-packages" ) );
        ModuleManager.PY3_MOD_MGR.modulePath( tmpProjectPath.toString() );
        Context.Builder builder = GraalPolyglot.python();
        Context ctx = builder.build();
        Object o = ctx.eval("python", "42").as(Object.class);
        assertEquals( 42, o );
    }

    @Test
    public void loadEngineTest() {
         assertThrows( UnsupportedOperationException.class , () -> GraalPolyglot.loadPolyglot("",  someFile) ) ;
    }
}
