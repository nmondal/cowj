package cowj;

import org.junit.Assert;
import org.junit.Test;

import javax.script.ScriptException;
import javax.script.SimpleBindings;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertThrows;

public class ScriptableTest {

    @Test
    public void eitherMonadTest(){
        EitherMonad<String> ems = EitherMonad.value("");
        Assert.assertTrue( ems.isSuccessful() );
        Assert.assertFalse( ems.inError() );
        ems = EitherMonad.error(new Throwable());
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

    @Test
    public void unrecognizedEngineTypeTest(){
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            Scriptable.getEngine("foo/bar.txt");
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("registered"));
    }

    @Test
    public void zmbRaiseErrorsDifferentArgsTest(){

        Scriptable.TestAsserter.HaltException exception = assertThrows(Scriptable.TestAsserter.HaltException.class, () -> {
            Scriptable sc = Scriptable.ZMB.create("",  "samples/test_scripts/error_1_arg.zm" );
            sc.exec(new SimpleBindings());
        });
        Assert.assertNotNull(exception);
        Assert.assertEquals(500, exception.code);

        exception = assertThrows(Scriptable.TestAsserter.HaltException.class, () -> {
            Scriptable sc = Scriptable.ZMB.create("",  "samples/test_scripts/error_2_arg.zm" );
            sc.exec(new SimpleBindings());
        });
        Assert.assertNotNull(exception);
        Assert.assertEquals("my error message", exception.getMessage());
        Assert.assertEquals(500, exception.code);
    }

    @Test
    public void globalVariableTest() throws Exception {
        // get variable
        int v = (int)Scriptable.SHARED_MEMORY.getOrDefault("hhgg", 0 );
        Scriptable sc = Scriptable.UNIVERSAL.create("hhgg", cowj.plugins.SampleJVMScriptable.class.getName()+ ".class");
        sc.exec( new SimpleBindings( ) );
        int n = (int)Scriptable.SHARED_MEMORY.getOrDefault("hhgg", 0 );
        Assert.assertTrue( v < n );
    }

    @Test
    public void expressionJSRTest() throws Exception {
        // JS
        Scriptable sc= Scriptable.JSR.create(Scriptable.INLINE, "2+2 //.js" );
        Object o = sc.exec( new SimpleBindings( ) );
        Assert.assertTrue( o instanceof Number);
        Assert.assertEquals( 4, ((Number) o).intValue() );

        // Python
        sc= Scriptable.JSR.create(Scriptable.INLINE, "2+2 #.py" );
        o = sc.exec( new SimpleBindings( ) );
        Assert.assertTrue( o instanceof Number);
        Assert.assertEquals( 4, ((Number) o).intValue() );

        // Groovy
        sc= Scriptable.JSR.create(Scriptable.INLINE, "2+2 //.groovy" );
        o = sc.exec( new SimpleBindings( ) );
        Assert.assertTrue( o instanceof Number);
        Assert.assertEquals( 4, ((Number) o).intValue() );

    }

    @Test
    public void rhinoPrintTest() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream( baos);
        PrintStream os = System.out;
        System.setOut(ps);

        ByteArrayOutputStream baes = new ByteArrayOutputStream();
        PrintStream pe = new PrintStream( baes);
        PrintStream es = System.err;
        System.setErr(pe);

        Scriptable sc= Scriptable.JSR.create(Scriptable.INLINE, "Test.print('hello, world!%n'); Test.printe('hello, world!%n'); //.js" );
        try {
            sc.exec( new SimpleBindings( ) );
            Assert.assertEquals( baos.toString(), "hello, world!\n");
            Assert.assertEquals( baes.toString(), "hello, world!\n");
        }finally {
            System.setOut(os);
            System.setOut(es);
        }
    }

    interface RunnableThrows {
        void run() throws Throwable;
    }

    public static void loadErrorTest( RunnableThrows r) {
        RuntimeException exception = assertThrows(RuntimeException.class, r::run);
        Assert.assertNotNull(exception);
    }

    @Test
    public void jsrScriptLoadErrorTest(){
        loadErrorTest(() -> Scriptable.loadScript("ignore", "samples/test_scripts/parse_err.js") );
    }

    @Test
    public void zmbScriptLoadErrorTest(){
        loadErrorTest(() -> Scriptable.loadZScript("ignore", "samples/test_scripts/parse_err.zm") );
    }
}
