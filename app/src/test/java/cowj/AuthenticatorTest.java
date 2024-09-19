package cowj;

import org.junit.Assert;
import org.junit.Test;
import spark.HaltException;
import spark.Request;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthenticatorTest {
    static class CachedAuthTestImpl extends Authenticator.TokenAuthenticator.CachedAuthenticator{
        int[] counters;
        public CachedAuthTestImpl(int[] counters, int cacheSize){
            super();
            super.maxCapacity = cacheSize;
            this.counters = counters;
        }
        @Override
        protected UserInfo tryGetUserInfo(String token) throws Exception {
            counters[0] ++;
            return UserInfo.userInfo("id","bar", Long.MAX_VALUE, Collections.emptyMap());
        }
        @Override
        public String tokenExpression() {
            return "header:foo" ;
        }

        @Override
        public Set<String> risks() {
            return Set.of("foo/bar");
        }
    }

    @Test
    public void headerTokenAuthTest(){
        Request request = mock(Request.class);
        when(request.headers("foo")).thenReturn("bar");
        int[] counters = { 0 };
        Authenticator authenticator = new CachedAuthTestImpl(counters,1);
        String user = authenticator.authenticate(request);
        Assert.assertEquals("id", user);
        user = authenticator.authenticate(request);
        // cache
        Assert.assertEquals(1, counters[0]);
        // Cache test with another request...
        Request request2 = mock(Request.class);
        when(request2.headers("foo")).thenReturn("bar2");
        user = authenticator.authenticate(request2);
        Assert.assertEquals("id", user);
    }

    @Test
    public void unsupportedAuthenticatorExpressionTest(){
        Request request = mock(Request.class);
        Authenticator authenticator = new Authenticator.TokenAuthenticator.CachedAuthenticator() {
            @Override
            protected UserInfo tryGetUserInfo(String token) throws Exception {
                return UserInfo.userInfo("id","bar", Long.MAX_VALUE, Collections.emptyMap());
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


    @Test
    public void riskyRouteTest(){
        Request request = mock(Request.class);
        when(request.pathInfo()).thenReturn("foo/bar");
        int[] counters = { 0 };
        Authenticator authenticator = new CachedAuthTestImpl(counters,1);
        String user = authenticator.authenticate(request);
        Assert.assertEquals(0, counters[0]);
        Assert.assertEquals("", Authenticator.UserInfo.GUEST.id());
    }

    @Test
    public void riskyRouteErrorTest(){
        Request request = mock(Request.class);
        when(request.pathInfo()).thenReturn("foo/whatever");
        int[] counters = { 0 };
        Authenticator authenticator = new CachedAuthTestImpl(counters,1);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authenticator.authenticate(request);
        });
    }

    @Test
    public void nullUserInfoTest(){
        final Request request = mock(Request.class);
        when(request.pathInfo()).thenReturn("/") ;
        Authenticator auth = request1 -> null;
        HaltException exception = assertThrows(HaltException.class, () -> {
            auth.authenticate(request);
        });
        Assert.assertEquals(401, exception.statusCode() );
    }


    @Test
    public void sessionExpiredTest(){
        final Request request = mock(Request.class);
        when(request.pathInfo()).thenReturn("/") ;
        final Authenticator.UserInfo userInfo = mock(Authenticator.UserInfo.class);
        when(userInfo.expiry()).thenReturn( System.currentTimeMillis() - 10000L );
        Authenticator auth = request1 -> userInfo;
        HaltException exception = assertThrows(HaltException.class, () -> {
            auth.authenticate(request);
        });
        Assert.assertEquals(401, exception.statusCode() );
    }

    @Test
    public void tokenAuthEmptyTokenTest(){
        Request request = mock(Request.class);
        when(request.pathInfo()).thenReturn("/") ;
        Authenticator.TokenAuthenticator auth = new Authenticator.TokenAuthenticator() {
            @Override
            public String tokenExpression() {
                return "";
            }

            @Override
            public String token(Request request) {
                return "";
            }

            @Override
            public UserInfo userFromToken(String token) {
                return null;
            }
        };
        HaltException exception = assertThrows(HaltException.class, () -> {
            auth.authenticate(request);
        });
        Assert.assertEquals(401, exception.statusCode() );
    }
}
