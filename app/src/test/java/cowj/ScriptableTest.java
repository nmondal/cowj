package cowj;

import org.junit.Assert;
import org.junit.Test;

import javax.script.ScriptException;
import javax.script.SimpleBindings;

import static org.junit.Assert.assertThrows;

public class ScriptableTest {

    @Test
    public void eitherMonadTest(){
        EitherMonad<String> ems = EitherMonad.value("");
        Assert.assertTrue( ems.isValid() );
        Assert.assertTrue( ems.isSuccessful() );
        Assert.assertFalse( ems.inError() );
        ems = EitherMonad.error(new Throwable());
        Assert.assertTrue( ems.isValid() );
        Assert.assertFalse( ems.isSuccessful() );
        Assert.assertTrue( ems.inError() );

    }
    @Test
    public void loadClassErrorTest() throws Exception {
        Scriptable sc = Scriptable.loadClass("java.lang.String.class");
        Assert.assertNotNull(sc);
        Object r = sc.exec(new SimpleBindings());
        Assert.assertNotNull(r);
        Assert.assertTrue(r.toString().isEmpty());
    }

    @Test
    public void nopScriptableTest() throws Exception {
       Scriptable sc = Scriptable.UNIVERSAL.create("foo", "bar");
       Object o = sc.exec(new SimpleBindings());
       Assert.assertEquals("", o);
    }

    @Test
    public void jsrRuntimeErrorTest(){
        Scriptable sc = Scriptable.JSR.create("",  "samples/test_scripts/runtime_error.js" );
        Exception exception = assertThrows(ScriptException.class, () -> {
            sc.exec(new SimpleBindings());
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("bar"));
    }

    @Test
    public void jsrNullReturnTest() throws Exception {
        Scriptable sc = Scriptable.JSR.create("",  "samples/test_scripts/null_return.js" );
        Object o = sc.exec(new SimpleBindings());
        Assert.assertEquals("", o);
    }

    @Test
    public void zmbRuntimeErrorTest(){
        Scriptable sc = Scriptable.ZMB.create("",  "samples/test_scripts/runtime_error.zmb" );
        Exception exception = assertThrows(RuntimeException.class, () -> {
            sc.exec(new SimpleBindings());
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("bar"));
    }
}
