package cowj.plugins;

import cowj.DataSource;
import cowj.Scriptable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstraction for REDIS via JEDIS
 */
public interface RedisWrapper {

    /**
     * Key for the SecretManager to be used
     */
    String SECRET_MANAGER = "secrets";

    /**
     * Key for the hosted REDIS urls to be used
     * They should be of host:port notation
     */
    String URLS = "urls";

    /**
     * A DataSource.Creator which returns proxy() as redis.clients.jedis.UnifiedJedis
     */
    DataSource.Creator REDIS = (name, config, parent) -> {
        Object urlObject = config.getOrDefault(URLS, "");
        List<String> urls = new ArrayList<>();
        if (urlObject instanceof List) {
            urls = (List<String>) urlObject;
        } else if (urlObject instanceof String) {
            SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(SECRET_MANAGER, SecretManager.DEFAULT);
            String urlJson = sm.getOrDefault(urlObject.toString(), "[]");
            urls = (List<String>) ZTypes.json(urlJson);
        }

        if (urls.isEmpty()) throw new IllegalArgumentException("url list is empty!");
        Set<HostAndPort> jedisClusterNodes =
                urls.stream().map(s -> {
                    String[] arr = s.split(":");
                    return new HostAndPort(arr[0], ZNumber.integer(arr[1], 6379).intValue());
                }).collect(Collectors.toSet());
        final UnifiedJedis jedis;
        if ( jedisClusterNodes.size() > 1 ){
            jedis =  new JedisCluster(jedisClusterNodes);
        } else {
            HostAndPort hp = jedisClusterNodes.iterator().next();
            jedis = new JedisPooled(hp.getHost(), hp.getPort());
        }
        return new DataSource() {
            @Override
            public Object proxy() {
                return jedis;
            }

            @Override
            public String name() {
                return name;
            }
        };
    };
}
