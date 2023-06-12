package cowj;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZNumber;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;
import cowj.plugins.FCMWrapper;

public interface DataSource {

    Object proxy();

    String name();

    interface Creator {
        DataSource create(String name, Map<String, Object> config, Model parent);
    }

    Creator REDIS = (name, config, parent) -> {
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

    Creator JDBC = (name, config, parent) -> {

        String driverName = config.getOrDefault("driver", "").toString();
        String connection = config.getOrDefault("connection", "").toString();

        Map<String, Object> props = (Map<String, Object>) config.getOrDefault("properties", Collections.emptyMap());
        Properties connectionProperties = new Properties();
        connectionProperties.putAll(props);
        try {
            Class.forName(driverName);
            Connection con = DriverManager.getConnection(connection, connectionProperties);
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
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    };

    Creator G_STORAGE = (name, config, parent) -> {

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
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    };

    Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();
            Map<String, String> headers = (Map) config.getOrDefault("headers", Collections.emptyMap());
            ZWeb zWeb = new ZWeb(baseUrl);
            if (!headers.isEmpty()) {
                zWeb.headers.putAll(headers);
            }
            return new DataSource() {
                @Override
                public Object proxy() {
                    return zWeb;
                }

                @Override
                public String name() {
                    return name;
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    };

    Map<String,Creator> REGISTRY = new HashMap<>();

    Creator UNIVERSAL = new Creator() {
        static {
            DataSource.REGISTRY.put("fcm", FCMWrapper.FCM);
        }

        @Override
        public DataSource create(String name, Map<String, Object> config, Model parent) {
            String type = config.getOrDefault("type", "").toString();
            Creator creator = switch (type) {
                case "redis" -> REDIS;
                case "jdbc" -> JDBC;
                case "google" -> G_STORAGE;
                case "curl" -> CURL;
                default -> {
                    Creator c = REGISTRY.get(type);
                    if (c != null) yield c;
                    throw new IllegalStateException("Unknown type of datasource -> value: " + type);
                }
            };
            return creator.create(name, config, parent);
        }
    };
}
