package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import cowj.Scriptable;
import org.junit.Assert;
import org.junit.Test;

import javax.script.Bindings;
import java.util.Map;

public class ExpiryMapWrapperTest {
    public static Model model = () -> ".";

    public static class Handler implements Scriptable {
        public static boolean wasCalled = false;

        public static boolean err = false;
        @Override
        public Object exec(Bindings bindings) throws Exception {
            wasCalled = true;
            if ( err ) throw new Exception("boom!");
            return wasCalled;
        }
    }

    static ExpiryMapWrapper createFromConfig(Map<String,Object> config){
        DataSource ds = ExpiryMapWrapper.CACHE.create("foo", config, model);
        Object o = ds.proxy();
        Assert.assertTrue( o instanceof ExpiryMapWrapper );
        ExpiryMapWrapper expiryMapWrapper = (ExpiryMapWrapper)o;
        Assert.assertNotNull(expiryMapWrapper.map());
        Assert.assertNotNull(expiryMapWrapper.shared());
        return expiryMapWrapper;
    }

    @Test
    public void expiryTest() throws Exception {
        Map<String,Object> config = Map.of("expiry", 100 );
        ExpiryMapWrapper expiryMapWrapper = createFromConfig(config);
        expiryMapWrapper.map().put("x", 42);
        Assert.assertTrue( expiryMapWrapper.map().containsKey("x"));
        Thread.sleep(300);
        Assert.assertFalse( expiryMapWrapper.map().containsKey("x"));
    }

    private void handleExpiry( boolean err ) throws Exception{
        Handler.err = err;
        Handler.wasCalled = false;
        final String myHandlerClass = ExpiryMapWrapperTest.Handler.class.getName() + ".class";
        Map<String,Object> config = Map.of("expiry", 100, "listener", myHandlerClass );
        ExpiryMapWrapper expiryMapWrapper = createFromConfig(config);
        expiryMapWrapper.map().put("x", 42);
        Assert.assertTrue( expiryMapWrapper.map().containsKey("x"));
        Thread.sleep(300);
        Assert.assertFalse( expiryMapWrapper.map().containsKey("x"));
        Assert.assertTrue( Handler.wasCalled);
    }
    @Test
    public void expiryCustomHandlerSuccessTest() throws Exception {
        handleExpiry(false);
    }

    @Test
    public void expiryCustomHandlerFailureTest() throws Exception {
        handleExpiry(true);
    }

    @Test
    public void multiThreadedTest() throws Exception {
        Map<String,Object> config = Map.of("expiry", 1500 );
        ExpiryMapWrapper expiryMapWrapper = createFromConfig(config);
        final Map<String,Object> shared = expiryMapWrapper.shared();
        Thread t1  = new Thread( () -> {
           shared.put("t1", 0);
           shared.put("t2", -1);
        });
        t1.start();
        Thread t2  = new Thread( () -> {
            shared.put("t2", 0);
            shared.put("t1", -1);
        });
        t2.start();
        t1.join();
        t2.join();
        Assert.assertTrue( shared.containsKey("t1"));
        Assert.assertTrue( shared.containsKey("t2"));
    }
}
