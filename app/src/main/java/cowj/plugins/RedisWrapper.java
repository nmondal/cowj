package cowj.plugins;

import cowj.DataSource;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import zoomba.lang.core.types.ZNumber;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisWrapper {
    public static DataSource.Creator REDIS = (name, config, parent) -> {
        List<String> urls = (List) config.getOrDefault("urls", Collections.emptyList());
        if (urls.isEmpty()) throw new RuntimeException("redis config must have 'urls' pointing to cluster!");
        Set<HostAndPort> jedisClusterNodes =
                urls.stream().map(s -> {
                    String[] arr = s.split(":");
                    return new HostAndPort(arr[0], ZNumber.integer(arr[1], 6379).intValue());
                }).collect(Collectors.toSet());
        final JedisCluster jedis = new JedisCluster(jedisClusterNodes);
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
