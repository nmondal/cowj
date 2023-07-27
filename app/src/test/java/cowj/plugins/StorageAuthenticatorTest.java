package cowj.plugins;

import cowj.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Request;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageAuthenticatorTest {

    static Authenticator NULL = request -> Authenticator.UserInfo.GUEST;

    @BeforeClass
    public static void beforeClass(){
        // register stuff
        DataSource.registerType("auth-jdbc", StorageAuthenticator.class.getName() + "::" + "JDBC");
        DataSource.registerType("auth-redis", StorageAuthenticator.class.getName() + "::" + "REDIS");
        DataSource.registerType("auth-gs", StorageAuthenticator.class.getName() + "::" + "GOOGLE_STORAGE");
        JDBCWrapper jdbcWrapper = mock(JDBCWrapper.class);
        Map<String,Object> data = Map.of("user", "foo", "expiry" , System.currentTimeMillis() + 100000);
        when(jdbcWrapper.select(any(),any())).thenReturn(EitherMonad.value(List.of(data)));
        Scriptable.DATA_SOURCES.put("__jdbc", jdbcWrapper );
    }

    @AfterClass
    public static void afterClass(){
        Scriptable.DATA_SOURCES.remove("__jdbc");
    }

    @Test
    public void jdbcAuthTest(){
        Map<String,Object> config = Map.of( "type", "auth-jdbc", "storage" , "__jdbc");
        Authenticator authenticator = AuthSystem.fromConfig(config, NULL);
        Assert.assertNotEquals(NULL, authenticator);
    }
}
