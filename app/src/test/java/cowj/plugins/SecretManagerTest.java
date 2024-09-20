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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import zoomba.lang.core.types.ZTypes;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    public void genericCreationTest(){
        final Function<String,String> fetcherJSON = s -> ZTypes.jsonString( Map.of("x", "42" ));
        final Function<String,String> fetcherDirect = s -> s;

        // Creation Failure Test
        IllegalArgumentException ex = Assert.assertThrows( IllegalArgumentException.class, () ->{
            SecretManager.secretManager("foo", Map.of(), model, fetcherDirect);
        });
        Assert.assertTrue(ex.getMessage().contains("'config'") );

        // now run through -- default case, config points to a key which points to JSON String
        SecretManager sm = SecretManager.secretManager("foo", Map.of("config", "foo-bar"), model, fetcherJSON);
        Assert.assertEquals( "42", sm.getOrDefault("x", "1000") );

        // config points to a List of elements
        List<String> l = List.of("a", "b") ;
        final SecretManager smList = SecretManager.secretManager("foo", Map.of("config", l) , model, fetcherDirect);
        l.forEach( x -> Assert.assertEquals( x, smList.getOrDefault(x, "")));

        // config points to a Map
        Map<String,String> keys = Map.of("a", "some a", "b", "some b") ;
        final SecretManager smMap = SecretManager.secretManager("foo", Map.of("config", keys) , model, fetcherDirect);
        keys.keySet().forEach( x -> Assert.assertEquals( x, smMap.getOrDefault(x, "")));
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
        DataSource ds =  SecretManager.GSM.create("foo", Map.of("config", "bar") , model  );
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name() );
        Assert.assertTrue( ds.proxy() instanceof SecretManager );
        SecretManager sm = (SecretManager) ds.proxy();
        String r = sm.getOrDefault("a", "0" );
        Assert.assertEquals("42", r);
        // Now check IO Error
        smscStatic.when( SecretManagerServiceClient::create).thenThrow(new IOException("Boom!"));
        Exception exception = assertThrows(RuntimeException.class, () -> {
            SecretManager.GSM.create("bar", Collections.emptyMap(), model  );
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void testASM(){
        MockedStatic<SecretsManagerClient> smscStatic =  mockStatic(SecretsManagerClient.class);
        SecretsManagerClient smsc = mock( SecretsManagerClient.class);
        smscStatic.when( SecretsManagerClient::create).thenReturn(smsc);

        when(smsc.getSecretValue(argThat((GetSecretValueRequest request) ->
                "bar".equals(request.secretId())
        ))).thenReturn(GetSecretValueResponse.builder().secretString("{ \"a\" : \"423\" }").build());

        Map<String, Object> config = Map.of("config", "bar");
        DataSource ds =  SecretManager.ASM.create("foo", config, model  );
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name() );
        Assert.assertTrue( ds.proxy() instanceof SecretManager );
        SecretManager sm = (SecretManager) ds.proxy();
        String r = sm.getOrDefault("a", "0" );
        Assert.assertEquals("423", r);

        smscStatic.when( SecretsManagerClient::create).thenThrow(new IllegalStateException("Boom!"));
        Exception exception = assertThrows(RuntimeException.class, () -> {
            SecretManager.ASM.create("bar", Collections.emptyMap(), model  );
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void testGSMOnError(){
        Exception exception = assertThrows(Exception.class, () -> {
            DataSource ds =  SecretManager.GSM.create("bar", Collections.emptyMap(), model  );
        });
        Assert.assertNotNull(exception);
    }
}
