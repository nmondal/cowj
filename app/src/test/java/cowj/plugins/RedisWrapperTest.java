package cowj.plugins;

import cowj.DataSource;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.embedded.RedisServer;
import zoomba.lang.core.types.ZNumber;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;


public class RedisWrapperTest {

    private final Model model = () -> ".";

    public UnifiedJedis boot( List<String> urls ){
        String[] arr = urls.get(0).split(":");
        String host = arr[0];
        int port = ZNumber.integer(arr[1], 6379).intValue();
        RedisServer redisServer = new RedisServer( port);
        Map<String,Object> config = Map.of( "urls", urls );
        DataSource ds = RedisWrapper.REDIS.create("foo", config, model);
        Assert.assertNotNull(ds);
        Assert.assertEquals( "foo", ds.name() );
        Assert.assertTrue( ds.proxy() instanceof UnifiedJedis);
        return (UnifiedJedis) ds.proxy();
    }

    @Test
    public void bootSingleTest(){
        UnifiedJedis jedis = boot( List.of( "localhost:4242" ));
        Assert.assertTrue( jedis instanceof JedisPooled);
    }

    @Test
    public void invalidConfig(){
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DataSource ds = RedisWrapper.REDIS.create("foo", Collections.emptyMap(), model);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("empty"));
    }
}
