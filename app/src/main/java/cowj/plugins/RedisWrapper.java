package cowj.plugins;

import cowj.DataSource;
import cowj.Scriptable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface RedisWrapper {
    String SECRET_MANAGER = "secrets";
    DataSource.Creator REDIS = (name, config, parent) -> {
        String urlString = config.getOrDefault("urls", "").toString();
        String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
        SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.get(secretManagerName);

        List<String> urls = (List<String>) ZTypes.json(sm.getOrDefault(urlString, "").toString());
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
