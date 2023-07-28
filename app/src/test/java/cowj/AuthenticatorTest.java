package cowj;

import org.junit.Assert;
import org.junit.Test;
import spark.HaltException;
import spark.Request;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthenticatorTest {

    @Test
    public void headerTokenAuthTest(){
        Request request = mock(Request.class);
        when(request.headers("foo")).thenReturn("bar");
        int[] counters = { 0 };
        Authenticator authenticator = new Authenticator.TokenAuthenticator.CachedAuthenticator() {
            @Override
            protected UserInfo tryGetUserInfo(String token) throws Exception {
                counters[0] ++;
                return UserInfo.userInfo("id","bar", Long.MAX_VALUE);
            }
            @Override
            public String tokenExpression() {
                return "header:foo" ;
            }
        };
        String user = authenticator.authenticate(request);
        Assert.assertEquals("id", user);
        user = authenticator.authenticate(request);
        // cache
        Assert.assertEquals(1, counters[0]);
    }

    @Test
    public void unsupportedAuthenticatorExpressionTest(){
        Request request = mock(Request.class);
        Authenticator authenticator = new Authenticator.TokenAuthenticator.CachedAuthenticator() {
            @Override
            protected UserInfo tryGetUserInfo(String token) throws Exception {
                return UserInfo.userInfo("id","bar", Long.MAX_VALUE);
            }
            @Override
            public String tokenExpression() {
                return "proto:foo" ;
            }
        };
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authenticator.authenticate(request);
        });
        Assert.assertTrue( exception instanceof HaltException );
        Assert.assertEquals( 401, ((HaltException)exception).statusCode() );
    }
}
