package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import cowj.Scriptable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.embedded.RedisServer;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockConstruction;


public class RedisWrapperTest {

    private final Model model = () -> ".";
    private RedisServer redisServer;

    @Before
    public void bootServer(){
        redisServer = new RedisServer(4242);
    }

    public UnifiedJedis boot(Object urls) {
        Map<String, Object> config = Map.of("urls", urls, "secrets", "__bar");
        SecretManager sm = () -> Map.of("redis-urls", "[ \"localhost:4242\" ]");
        DataSource.registerDataSource("__bar", sm );
        DataSource ds = RedisWrapper.REDIS.create("foo", config, model);
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name());
        Assert.assertTrue(ds.proxy() instanceof UnifiedJedis);
        return (UnifiedJedis) ds.proxy();
    }

    @After
    public void shutdownRedis(){
        if ( redisServer == null ) return;
        redisServer.stop();
        DataSource.unregisterDataSource("__bar");
    }

    @Test
    public void bootSingleTest() {
        UnifiedJedis jedis = boot(List.of("localhost:4242"));
        Assert.assertTrue(jedis instanceof JedisPooled);
        jedis = RedisWrapper.Config.jedis(
                Set.of( new HostAndPort("localhost" , 4242) ),
                Map.of("foobar", "foo-bar" ) );
        Assert.assertTrue(jedis instanceof JedisPooled);
    }

    @Test
    public void bootMultipleTest(){
        MockedConstruction<JedisCluster> mock = mockConstruction(JedisCluster.class);
        UnifiedJedis jedis = boot(List.of("localhost:4242", "localhost:5555" ));
        Assert.assertTrue(jedis instanceof JedisCluster);

        jedis =   RedisWrapper.Config.jedis(
                Set.of(new HostAndPort("localhost", 4242), new HostAndPort("localhost", 5555)),
                Map.of("foobar", "foo-bar"));
        Assert.assertTrue(jedis instanceof JedisCluster);
    }

    @Test
    public void invalidConfigTest() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DataSource ds = RedisWrapper.REDIS.create("foo", Collections.emptyMap(), model);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("empty"));

        exception = assertThrows(IllegalArgumentException.class, () -> {
            DataSource ds = RedisWrapper.REDIS.create("foo", Map.of("urls", false), model);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("urls"));
    }

    @Test
    public void useSecretManagerIfUrlsIsString() {
        UnifiedJedis jedis = boot( "${redis-urls}");
        Assert.assertTrue(jedis instanceof JedisPooled);
    }

    @Test
    public void useSecretManagerDefaultIfSecretManagerIsNotSpecified() {
        Map<String, Object> config = new HashMap<>();
        String secretManager = "test_secrets";
        config.put("urls", secretManager);

        /// we are setting urls to string but the secret manager
        /// is not present in Scriptable. Also, default implementation
        /// uses environment variable which is not defined either.
        /// So, urls will be an empty list and will trigger
        /// IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            RedisWrapper.REDIS.create("foo", config, model);
        });
    }

    @Test
    public void throwExceptionIfSecretManagerDoesNotContainKey() {
        Map<String, Object> config = new HashMap<>();
        String secretManager = "test_secrets";
        /// Point urls to a string so Secret Manager is triggered
        config.put("urls", secretManager);

        /// Create a mock secret manager that returns the defaultVal
        SecretManager mockSecretManager = Collections::emptyMap;

        try {
            DataSource.registerDataSource(SecretManager.SECRET_MANAGER, mockSecretManager);
            /// we are setting urls to string but the secret manager
            /// does not contain the key. Also, default implementation
            /// uses environment variable which is not defined either.
            /// So, urls will be an empty list and will trigger
            /// IllegalArgumentException
            assertThrows(IllegalArgumentException.class, () -> {
                RedisWrapper.REDIS.create("foo", config, model);
            });
        } finally {
            DataSource.unregisterDataSource(SecretManager.SECRET_MANAGER);
        }
    }

    @Test
    public void utilFunctionsTest(){
        assertEquals( 42, (int)DataSource.INTEGER_CONVERTER.apply( "42", 0 ) );
        assertEquals( 0, (int)DataSource.INTEGER_CONVERTER.apply( "xxxx", 0 ) );
        assertEquals( "x", DataSource.STRING_CONVERTER.apply( "x", "" ) );
        assertEquals( "null", DataSource.STRING_CONVERTER.apply( null, "" ) );
        final String[] vars = new String[] { null } ;
        Consumer<String> consumer = (s) -> vars[0] = s ;
        assertNull(vars[0]);
        DataSource.computeIfPresent( Map.of("f", 42), "f", consumer );
        assertEquals("42", vars[0] );
    }
}
