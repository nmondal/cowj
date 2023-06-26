package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import cowj.Scriptable;
import org.junit.Assert;
import org.junit.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.embedded.RedisServer;
import zoomba.lang.core.types.ZNumber;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;


public class RedisWrapperTest {

    private final Model model = () -> ".";

    public UnifiedJedis boot(List<String> urls) {
        String[] arr = urls.get(0).split(":");
        String host = arr[0];
        int port = ZNumber.integer(arr[1], 6379).intValue();
        RedisServer redisServer = new RedisServer(port);
        Map<String, Object> config = Map.of("urls", urls);
        DataSource ds = RedisWrapper.REDIS.create("foo", config, model);
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name());
        Assert.assertTrue(ds.proxy() instanceof UnifiedJedis);
        return (UnifiedJedis) ds.proxy();
    }

    @Test
    public void bootSingleTest() {
        UnifiedJedis jedis = boot(List.of("localhost:4242"));
        Assert.assertTrue(jedis instanceof JedisPooled);
    }

    @Test
    public void invalidConfigTest() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DataSource ds = RedisWrapper.REDIS.create("foo", Collections.emptyMap(), model);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("empty"));
    }

    // Hack to get unused port https://stackoverflow.com/a/2675416/21970403
    private int randomUnusedPort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void useSecretManagerIfUrlsIsString() {
        /// start a redis server on a default port
        int port = randomUnusedPort();
        RedisServer server = new RedisServer(port);
        try {
            server.start();
            Map<String, Object> config = new HashMap<>();
            String urlKey = "test_secrets";
            /// Point urls to a string so Secret Manager is triggered
            config.put("urls", urlKey);

            /// Create a mock secret manager that returns the url
            /// for the above generated port
            SecretManager mockSecretManager = () -> Map.of(urlKey, "[\"localhost:" + port + "\"]");

            try {
                /// Create the data source and perform CRUD to check if that works
                Scriptable.DATA_SOURCES.put(RedisWrapper.SECRET_MANAGER, mockSecretManager);
                DataSource redis = RedisWrapper.REDIS.create("foo", config, model);
                Assert.assertTrue(redis.proxy() instanceof UnifiedJedis);
            } finally {
                Scriptable.DATA_SOURCES.remove(RedisWrapper.SECRET_MANAGER);
            }
        } finally {
            server.stop();
        }
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
            Scriptable.DATA_SOURCES.put(RedisWrapper.SECRET_MANAGER, mockSecretManager);
            /// we are setting urls to string but the secret manager
            /// does not contain the key. Also, default implementation
            /// uses environment variable which is not defined either.
            /// So, urls will be an empty list and will trigger
            /// IllegalArgumentException
            assertThrows(IllegalArgumentException.class, () -> {
                RedisWrapper.REDIS.create("foo", config, model);
            });
        } finally {
            Scriptable.DATA_SOURCES.remove(RedisWrapper.SECRET_MANAGER, mockSecretManager);
        }
    }
}
