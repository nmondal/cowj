package cowj.plugins;

import cowj.*;
import org.junit.*;
import redis.clients.jedis.UnifiedJedis;
import spark.HaltException;
import spark.Request;
import zoomba.lang.core.types.ZTypes;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageAuthenticatorTest {

    Authenticator NULL = request -> Authenticator.UserInfo.GUEST;

    Model model = () -> "." ;

    Request mockRequest;

    JDBCWrapper jdbcWrapper;

    UnifiedJedis unifiedJedis;

    GoogleStorageWrapper googleStorageWrapper;

    @Before
    public void before(){
        // register stuff
        DataSource.registerType("auth-jdbc", StorageAuthenticator.class.getName() + "::" + "JDBC");
        DataSource.registerType("auth-redis", StorageAuthenticator.class.getName() + "::" + "REDIS");
        DataSource.registerType("auth-gs", StorageAuthenticator.class.getName() + "::" + "CLOUD_STORAGE");
        jdbcWrapper = mock(JDBCWrapper.class);
        Map<String,Object> data = Map.of("user", "foo", "expiry" , System.currentTimeMillis() + 100000);
        when(jdbcWrapper.select(any(),(List)any())).thenReturn(EitherMonad.value(List.of(data)));
        DataSource.registerDataSource("__jdbc", jdbcWrapper );
        mockRequest = mock(Request.class);
        when(mockRequest.body()).thenReturn("{ \"token\" : \"foo-bar\" }");
        // redis...
        unifiedJedis = mock(UnifiedJedis.class);
        DataSource.registerDataSource("__redis", unifiedJedis );
        String jsonData = ZTypes.jsonString(data);
        when(unifiedJedis.hget((String) any(), (String)any() )).thenReturn( jsonData );
        // Google Storage
        googleStorageWrapper = mock(GoogleStorageWrapper.class);
        DataSource.registerDataSource("__gs", googleStorageWrapper );
        when(googleStorageWrapper.load(any(), any())).thenReturn(data);
    }

    @After
    public void after(){
        DataSource.unregisterDataSource("__jdbc");
        DataSource.unregisterDataSource("__redis");
        DataSource.unregisterDataSource("__gs");
    }

    @Test
    public void jdbcAuthTest(){
        Map<String,Object> config = Map.of( "type", "auth-jdbc", "storage" , "__jdbc", "cache", 8 );
        Authenticator authenticator = AuthSystem.fromConfig(config, model, NULL);
        Assert.assertNotEquals(NULL, authenticator);
        Assert.assertEquals(8, ((Authenticator.TokenAuthenticator.CachedAuthenticator)authenticator).cacheSize()  );
        final String userId = authenticator.authenticate(mockRequest);
        Assert.assertEquals("foo", userId);
    }

    @Test
    public void jdbcAuthFailureTest(){
        Map<String,Object> config = Map.of( "type", "auth-jdbc", "storage" , "__jdbc", "cache", 8 );
        Throwable th = new Throwable("boom!");
        // ensure jdbc throws ...
        when(jdbcWrapper.select(any(),(List)any())).thenReturn(EitherMonad.error(th));
        Authenticator authenticator = AuthSystem.fromConfig(config, model, NULL);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authenticator.authenticate(mockRequest);
        });
        Assert.assertTrue( exception instanceof HaltException );
        Assert.assertEquals( 401, ((HaltException)exception).statusCode() );
        // ensure jdbc responds empty
        when(jdbcWrapper.select(any(),(List)any())).thenReturn(EitherMonad.value( List.of()));
        exception = assertThrows(RuntimeException.class, () -> {
            authenticator.authenticate(mockRequest);
        });
        Assert.assertTrue( exception instanceof HaltException );
        Assert.assertEquals( 401, ((HaltException)exception).statusCode() );
        // jdbc token issue test
        exception = assertThrows(UnsupportedOperationException.class, () -> {
            ((Authenticator.TokenAuthenticator.TokenIssuer)authenticator).issueToken( "foo", System.currentTimeMillis() + 4000) ;
        });
        Assert.assertTrue( exception.getMessage().contains("creating token") );
    }

    @Test
    public void redisAuthTest() throws Exception {
        Map<String,Object> config = Map.of( "type", "auth-redis", "storage" , "__redis",
                "cache", 8 );
        Authenticator authenticator = AuthSystem.fromConfig(config, model, NULL);
        Assert.assertNotEquals(NULL, authenticator);
        final String userId = authenticator.authenticate(mockRequest);
        Assert.assertEquals("foo", userId);

        // Issuer test
        String token = ((Authenticator.TokenAuthenticator.TokenIssuer)authenticator).issueToken( "foo", System.currentTimeMillis() + 4000) ;
        Assert.assertFalse( token.isEmpty() );
    }

    @Test
    public void googleAuthTest() throws Exception{
        Map<String,Object> config = Map.of( "type", "auth-gs", "storage" , "__gs",
                "query", "bucket_name/file_name" );
        Authenticator authenticator = AuthSystem.fromConfig(config, model, NULL);
        Assert.assertNotEquals(NULL, authenticator);
        final String userId = authenticator.authenticate(mockRequest);
        Assert.assertEquals("foo", userId);

        // Issuer test
        String token = ((Authenticator.TokenAuthenticator.TokenIssuer)authenticator).issueToken( "foo", System.currentTimeMillis() + 4000) ;
        Assert.assertFalse( token.isEmpty() );
    }

    @Test
    public void googleAuthFailureTest(){
        when(googleStorageWrapper.load(any(), any())).thenReturn(42);
        Map<String,Object> config = Map.of( "type", "auth-gs", "storage" , "__gs",
                "query", "bucket_name/file_name" );
        Authenticator authenticator = AuthSystem.fromConfig(config, model,  NULL);
        Assert.assertNotEquals(NULL, authenticator);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authenticator.authenticate(mockRequest);
        });
        Assert.assertTrue( exception instanceof HaltException );
        Assert.assertEquals( 401, ((HaltException)exception).statusCode() );
    }
}
