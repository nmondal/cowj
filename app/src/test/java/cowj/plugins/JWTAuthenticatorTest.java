package cowj.plugins;

import cowj.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import org.junit.Test;
import spark.Request;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.operations.ZJVMAccess;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class JWTAuthenticatorTest {
    Authenticator NULL = request -> Authenticator.UserInfo.GUEST;

    Model model = () -> "." ;

    JWTAuthenticator prov1 = new JWTAuthenticator() {
        @Override
        public String secretKey() {
            return "42";
        }

        @Override
        public String issuer() {
            return "dummy";
        }
    };

    @Before
    public void before(){
        // register stuff
        DataSource.registerType("auth-jwt", JWTAuthenticator.class.getName() + "::" + "JWT");
    }

    @After
    public void after(){
        DataSource.unregisterDataSource ("custom-secret");
    }

    @Test
    public void issueTokenTest() throws Exception {
        final long expiry = System.currentTimeMillis() + 4000 ;
        String tok = prov1.issueToken("foo", expiry );
        JWTAuthenticator.JWebToken jwt = (JWTAuthenticator.JWebToken)prov1.tryGetUserInfo(tok);
        Assert.assertEquals("foo", jwt.getSubject());
        // JWT works in secs, not on MS
        Assert.assertEquals(expiry/1000, jwt.expiry()/1000);

        tok = prov1.token("foo", expiry );
        jwt = (JWTAuthenticator.JWebToken)prov1.tryGetUserInfo(tok);
        Assert.assertEquals("foo", jwt.getSubject());
        // JWT works in secs, not on MS
        Assert.assertEquals(expiry/1000, jwt.expiry()/1000);
    }

    public void testSuccess(Map<String,Object> config){
        Authenticator authenticator = AuthSystem.fromConfig(config, model, NULL);
        Assert.assertNotEquals(NULL, authenticator);
        Assert.assertTrue( authenticator instanceof  JWTAuthenticator );
        JWTAuthenticator jwtAuthenticator = (JWTAuthenticator) authenticator;
        Assert.assertEquals("", jwtAuthenticator.tokenExpression()); // it supposed to be this way
        Assert.assertEquals("test", jwtAuthenticator.issuer());
        Assert.assertEquals("42", jwtAuthenticator.secretKey());
        Assert.assertEquals(0, jwtAuthenticator.cacheSize() );
        JWTAuthenticator.JWebToken jWebToken = jwtAuthenticator.jwt("foo");
        JWTAuthenticator.JWebToken jWebToken1 = jwtAuthenticator.jwt(Map.of("sub", "foo"));
        Assert.assertEquals( jWebToken1.getAudience(), jWebToken.getAudience() );
        Assert.assertEquals( jWebToken1.getSubject(), jWebToken.getSubject() );
        Assert.assertEquals( jWebToken1.expiry(), jWebToken.expiry(), 2 );
        Assert.assertEquals( jWebToken1.json().size(), jWebToken.json().size());
        Assert.assertTrue( jWebToken.isSignatureValid() );
        Assert.assertTrue( jWebToken.getAudience().isEmpty()); // this is what it supposed to be
        // Now create a request with auth stuff
        Request mockRequest = mock(Request.class);
        when(mockRequest.headers()).thenReturn( Set.of(JWTAuthenticator.AUTHENTICATION_HEADER));
        final String token = "Bearer " + jWebToken.token();
        when(mockRequest.headers(any())).thenReturn( token );
        final String uId = authenticator.authenticate( mockRequest);
        Assert.assertEquals("foo", uId );
    }

    @Test
    public void jwtAuthSuccessTest(){
        Map<String,Object> config = Map.of( "type", "auth-jwt", "secret-key" , "42", "issuer", "test" );
        testSuccess(config);
    }

    @Test
    public void jwtAuthWithSecretManagerTest(){

        Map<String,String> smMap = Map.of("secret-key", "42", "jwt-issuer", "test");
        SecretManager sm = () -> smMap; // create custom secret manager
        DataSource.registerDataSource ("custom-secret", sm );

        Map<String,Object> config = Map.of( "type", "auth-jwt",
                "secrets", "custom-secret",
                "secret-key" , "${secret-key}", "issuer", "${jwt-issuer}" );
        testSuccess(config);
    }


    @Test
    public void jwtInvalidCreationTests(){
        JWTAuthenticator dummy = new JWTAuthenticator() {
            @Override
            public String secretKey() {
                return "42";
            }

            @Override
            public String issuer() {
                return "dummy";
            }
        };
        Throwable throwable = assertThrows(IllegalArgumentException.class, () -> {
            dummy.new JWebToken("a.bc"); // invalid format
        });
        Assert.assertNotNull(throwable);

        throwable = assertThrows(NoSuchAlgorithmException.class, () -> {
            dummy.new JWebToken("a.b.c"); // invalid header - no such algo supported as of now
        });
        Assert.assertNotNull(throwable);
        // now craft and run through
        JWTAuthenticator.JWebToken jWebToken = dummy.jwt("foo");
        Function.MonadicContainer m = ZJVMAccess.getProperty(jWebToken,"payload");
        Assert.assertFalse(m.isNil());
        Assert.assertTrue( m.value() instanceof Map );
        ((Map)m.value()).remove("exp"); // remove expiry
        // Now create a token out of it
        final String craftedToken = jWebToken.toString();
        throwable = assertThrows(Exception.class, () -> {
            dummy.new JWebToken(craftedToken); // no expiry
        });
        Assert.assertNotNull(throwable);
    }

    @Test
    public void errorInCreationOfJWTTest(){
        JWTAuthenticator dummy = new JWTAuthenticator() {
            @Override
            public String secretKey() {
                return "";
            }

            @Override
            public String issuer() {
                return "";
            }
        };
        Throwable throwable = assertThrows(RuntimeException.class, () -> {
           dummy.jwt("foo"); // should throw exception
        });
        Assert.assertNotNull(throwable);
    }

    @Test
    public void crossJWTFailureTest(){

        JWTAuthenticator.JWebToken prov1Token = prov1.jwt("foo");
        // Now create a request with auth stuff
        final String token = prov1Token.token();
        JWTAuthenticator prov2 = new JWTAuthenticator() {
            @Override
            public String secretKey() {
                return "420";
            }

            @Override
            public String issuer() {
                return "dummy";
            }
        };
        Throwable throwable = assertThrows(Exception.class, () -> {
            prov2.tryGetUserInfo(token);
        });
        Assert.assertNotNull(throwable);
    }


    @Test
    public void noAuthHeaderTest(){
        Request mockRequest = mock(Request.class);
        when(mockRequest.headers()).thenReturn( Set.of());
        Throwable throwable = assertThrows(Exception.class, () -> {
            prov1.token(mockRequest);
        });
        Assert.assertNotNull(throwable);
        Assert.assertTrue( throwable.getMessage().contains("header"));
    }

    @Test
    public void noBearerTokenInAuthHeaderTest(){
        Request mockRequest = mock(Request.class);
        when(mockRequest.headers()).thenReturn( Set.of(JWTAuthenticator.AUTHENTICATION_HEADER));
        when(mockRequest.headers(any())).thenReturn( "boo hhaahah ");
        Throwable throwable = assertThrows(Exception.class, () -> {
            prov1.token(mockRequest);
        });
        Assert.assertNotNull(throwable);
        Assert.assertTrue( throwable.getMessage().contains("not found"));
    }

    @Test
    public void wrongBearerTokenInAuthHeaderTest(){
        Request mockRequest = mock(Request.class);
        when(mockRequest.headers()).thenReturn( Set.of(JWTAuthenticator.AUTHENTICATION_HEADER));
        when(mockRequest.headers(any())).thenReturn( "Bearer  foo bar 42");
        Throwable throwable = assertThrows(Exception.class, () -> {
            prov1.token(mockRequest);
        });
        Assert.assertNotNull(throwable);
        Assert.assertTrue( throwable.getMessage().contains("misconfigured"));
    }

    @Test
    public void invalidFieldsTest(){
        // test the param verifying function
        Assert.assertTrue( JWTAuthenticator.JWebToken.isInt(10));
        Assert.assertTrue( JWTAuthenticator.JWebToken.isInt(10L));
        Assert.assertFalse( JWTAuthenticator.JWebToken.isInt("10"));

        // now do rest
        IllegalArgumentException throwable = assertThrows(IllegalArgumentException.class, () -> {
            prov1.jwt(Map.of());
        });
        Assert.assertNotNull(throwable);
        Assert.assertTrue( throwable.getMessage().contains("no sub"));

        throwable = assertThrows(IllegalArgumentException.class, () -> {
            prov1.jwt(Map.of(
                    "sub", 10,
                    "aud", 42 ,
                    "exp", "X" ,
                    "iat", 42L,
                    "iss", 78,
                    "jti", false )
            );
        });
        Assert.assertNotNull(throwable);
        Assert.assertTrue( throwable.getMessage().contains("Type"));
    }
}
