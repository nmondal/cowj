package cowj;

import zoomba.lang.core.io.ZWeb;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

public interface DataSource {

    Object proxy();

    String name();

    interface Creator {
        DataSource create(String name, Map<String, Object> config, Model parent);
    }

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

    static void registerType(String type, String path){
        String[] paths = path.split("::");
        try {
            Class<?> clazz = Class.forName(paths[0]);
            Field f = clazz.getDeclaredField(paths[1]);
            Object r = f.get(null);
            if ( !(r instanceof Creator) ){
                System.err.println( "Error registering type... not a creator object : " + r.getClass());
                return;
            }
            REGISTRY.put(type,(Creator) r);
        }catch (Throwable t){
            System.err.println( "Error registering type... : " + t);
        }
    }

    Creator UNIVERSAL = (name, config, parent) -> {
        String type = config.getOrDefault("type", "").toString();
        Creator creator = switch (type) {
            case "jdbc" -> JDBC;
            case "curl" -> CURL;
            default -> {
                Creator c = REGISTRY.get(type);
                if (c != null) yield c;
                throw new IllegalStateException("Unknown type of datasource -> value: " + type);
            }
        };
        return creator.create(name, config, parent);
    };
}
