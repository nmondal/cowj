package cowj.plugins;

import cowj.*;
import org.junit.Assert;
import org.junit.Before;

import org.junit.Test;
import spark.Request;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class JWTAuthenticatorTest {
    Authenticator NULL = request -> Authenticator.UserInfo.GUEST;

    @Before
    public void before(){
        // register stuff
        DataSource.registerType("auth-jwt", JWTAuthenticator.class.getName() + "::" + "JWT");
    }

    @Test
    public void jwtAuthSuccessTest(){
        Map<String,Object> config = Map.of( "type", "auth-jwt", "secret-key" , "42", "issuer", "test" );
        Authenticator authenticator = AuthSystem.fromConfig(config, NULL);
        Assert.assertNotEquals(NULL, authenticator);
        Assert.assertTrue( authenticator instanceof  JWTAuthenticator );
        JWTAuthenticator jwtAuthenticator = (JWTAuthenticator) authenticator;
        Assert.assertEquals("test", jwtAuthenticator.issuer());
        Assert.assertEquals("42", jwtAuthenticator.secretKey());
        Assert.assertEquals(0, jwtAuthenticator.cacheSize() );
        JWTAuthenticator.JWebToken jWebToken = jwtAuthenticator.jwt("foo");
        Assert.assertTrue( jWebToken.isSignatureValid() );
        // Now create a request with auth stuff
        Request mockRequest = mock(Request.class);
        when(mockRequest.headers()).thenReturn( Set.of("Authentication"));
        final String token = "Bearer " + jWebToken.token();
        when(mockRequest.headers(any())).thenReturn( token );
        final String uId = authenticator.authenticate( mockRequest);
        Assert.assertEquals("foo", uId );
    }
}
