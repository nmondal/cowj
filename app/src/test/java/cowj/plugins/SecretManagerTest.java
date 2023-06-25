package cowj.plugins;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import cowj.DataSource;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

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
        MockedStatic<SecretManagerServiceClient> smscStatic =  mockStatic(SecretManagerServiceClient.class);
        SecretManagerServiceClient smsc = mock( SecretManagerServiceClient.class);
        smscStatic.when( SecretManagerServiceClient::create).thenReturn(smsc);
        AccessSecretVersionResponse resp = mock(AccessSecretVersionResponse.class);
        SecretPayload sp = mock(SecretPayload.class);
        ByteString bs = ByteString.copyFromUtf8( "{ \"a\" : \"42\" }");
        when(sp.getData()).thenReturn(bs);
        when(resp.getPayload()).thenReturn(sp);
        when(smsc.accessSecretVersion((SecretVersionName) any()) ).thenReturn(resp);
        DataSource ds =  SecretManager.GSM.create("foo", Collections.emptyMap(), model  );
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name() );
        Assert.assertTrue( ds.proxy() instanceof SecretManager );
        SecretManager sm = (SecretManager) ds.proxy();
        String r = sm.getOrDefault("a", "0" );
        Assert.assertEquals("42", r);
    }

    @Test
    public void testGSMOnError(){
        Exception exception = assertThrows(Exception.class, () -> {
            DataSource ds =  SecretManager.GSM.create("bar", Collections.emptyMap(), model  );
        });
        Assert.assertNotNull(exception);
    }
}
