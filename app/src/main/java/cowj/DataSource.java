package cowj;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import zoomba.lang.core.types.ZNumber;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

public interface DataSource {

    Object proxy();
    String name();

    interface Creator {
        DataSource create(String name, Map<String,Object> config);
    }

    Creator REDIS = (name, config) -> {
        List<String> urls = (List)config.getOrDefault("urls", Collections.emptyList());
        if ( urls.isEmpty() ) throw new RuntimeException("redis config must have 'urls' pointing to cluster!");
        Set<HostAndPort> jedisClusterNodes =
        urls.stream().map( s -> {
            String[] arr = s.split(":");
            return new HostAndPort( arr[0], ZNumber.integer(arr[1], 6379).intValue() );
        }).collect( Collectors.toSet());
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

    Creator JDBC = (name, config) -> {

        String driverName = config.getOrDefault("driver", "").toString();
        String connection = config.getOrDefault("connection", "").toString();
        try {
            Class.forName(driverName);
            Connection con = DriverManager.getConnection(connection);
            return new DataSource() {
                @Override
                public Object proxy() {
                    return con;
                }

                @Override
                public String name() {
                    return name;
                }
            };
        }catch (Throwable t){
             throw new RuntimeException(t);
        }
    };

    Creator G_STORAGE = (name, config) -> {

        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();
            return new DataSource() {
                @Override
                public Object proxy() {
                    return storage;
                }

                @Override
                public String name() {
                    return name;
                }
            };
        }catch (Throwable t){
            throw new RuntimeException(t);
        }
    };

    Creator UNIVERSAL = (name, config) -> {
        String type = config.getOrDefault("type", "").toString();
        Creator creator = switch (type){
            case "redis" -> REDIS ;
            case "jdbc" -> JDBC ;
            case "google" -> G_STORAGE ;
            default -> throw new IllegalStateException("Unknown type of datasource -> value: " + type);
        };
        return creator.create(name,config);
    };
}
