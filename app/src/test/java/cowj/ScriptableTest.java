package cowj;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ScriptableTest {

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
            Assert.assertTrue( baos.toString().contains( "hello, world!" + System.lineSeparator()) );
            Assert.assertTrue( baes.toString().contains( "hello, world!" + System.lineSeparator() ) );
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

    @Test
    public void nameSpacedSharedMemTest(){
        final Bindings b = new SimpleBindings();
        Scriptable.TestAsserter ts = () -> b;
        Object o1 = ts.shared("_foo");
        Object o2 = ts.shared("_bar");
        Assert.assertNotSame(o1, o2);
        Object o3 = ts.shared("_bar");
        Assert.assertNotSame(o1, o3);
        Assert.assertEquals(o2,o3);
    }

    @Test
    public void prefixLoggerTest(){
        final int times = 100000 ;
        final Logger logger = Scriptable.logger ; // this may take a bit of time to get inited
        EitherMonad<Long> createMonad = TestUtils.timeIt( () ->{
            for ( int i =0; i < times; i++ ){
                Scriptable.prefixedLogger( logger, String.valueOf(i));
            }
        }).then( ns -> TimeUnit.NANOSECONDS.toMillis(ns) );
        Assert.assertTrue(createMonad.isSuccessful());
        System.out.printf("Time taken to create 100,000 Logger : %d ms %n", createMonad.value() );
        Assert.assertTrue( createMonad.value() < 500L );

        EitherMonad<Long> accessMonad = TestUtils.timeIt( () ->{
            for ( int i =0; i < times; i++ ){
                Scriptable.prefixedLogger( logger, String.valueOf(i));
            }
        }).then( ns -> TimeUnit.NANOSECONDS.toMillis(ns) );

        Assert.assertTrue(accessMonad.isSuccessful());
        System.out.printf("Time taken to access 100,000 Logger : %d ms %n", accessMonad.value() );
        Assert.assertTrue( accessMonad.value() < 40L );

        // now the access of methods which use marker
        Marker m = MarkerFactory.getMarker("marker");
        Logger l = Scriptable.prefixedLogger( logger, String.valueOf(42));
        l.info(m, "this is a test marker");
        Assert.assertTrue(true);
    }
}
