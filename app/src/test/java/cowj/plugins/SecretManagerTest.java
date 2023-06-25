package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertThrows;

public class SecretManagerTest {

    final static Model model = () -> ".";

    @Test
    public void testLocal(){
        DataSource ds =  SecretManager.LOCAL.create("foo", Collections.emptyMap(), model  );
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name() );
        Assert.assertTrue( ds.proxy() instanceof SecretManager );
        SecretManager sm = (SecretManager)ds.proxy();
        Assert.assertFalse( sm.getOrDefault("PATH", "").isEmpty() );
    }

    @Test
    public void testGSM(){
        // TODO get this thing setup
        Exception exception = assertThrows(Exception.class, () -> {
            DataSource ds =  SecretManager.GSM.create("bar", Collections.emptyMap(), model  );
        });
        Assert.assertNotNull(exception);
    }
}
