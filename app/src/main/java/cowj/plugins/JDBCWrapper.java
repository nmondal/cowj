package cowj.plugins;

import cowj.DataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class JDBCWrapper {
    public static DataSource.Creator JDBC = (name, config, parent) -> {

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
}
