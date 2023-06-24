package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;

import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public interface JDBCWrapper {

    Connection connection();

    String DRIVER = "driver" ;
    String CONNECTION = "connection" ;
    String SECRET_MANAGER = "secrets";
    String PROPERTIES = "properties" ;

    String CONNECTION_STRING = "connection_string" ;

    String ENV = "env" ;

    default Object getObject(Object value) {
        if (value instanceof java.sql.Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toEpochSecond() * 1000;
        }
        return value;
    }

    default EitherMonad<List<Map<String,Object>>> select(String query, List<Object> args) {
        List<Map<String,Object>> result = new ArrayList<>();
        final Connection con = connection();
        try (Statement stmt = con.createStatement()) {
            String q = query.formatted(args.toArray());
            System.out.println(q);
            ResultSet rs = stmt.executeQuery(q);
            ResultSetMetaData rsmd = rs.getMetaData();
            int count = rsmd.getColumnCount();

            while (rs.next()) {
                Map<String,Object> m = new LinkedHashMap<>(); // because of... order preserving
                for (int index = 1; index <= count; index++) {
                    String column = rsmd.getColumnName(index);
                    Object value = rs.getObject(column);
                    Object transformedValue = getObject(value);
                    m.put(column, transformedValue);
                }
                result.add(m);
            }
            return EitherMonad.value(result);
        } catch (SQLException e) {
            return EitherMonad.error(e);
        }
    }

    DataSource.Creator JDBC = (name, config, parent) -> {
        String driverName = config.getOrDefault(DRIVER, "").toString();
        Map<String, String> props = (Map<String, String>) config.getOrDefault(PROPERTIES, Collections.emptyMap());
        String conString = config.getOrDefault(CONNECTION_STRING, "").toString();

        String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
        SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.get(secretManagerName);

        Map<String, Object> env = (Map<String, Object>) config.getOrDefault(ENV, Collections.emptyMap());
        /// Mapping a Map in java is way too verbose. Imperative is much
        /// more readable in java
        Map<String, Object> substitutedEnv = new HashMap<>();
        for (Map.Entry<String, Object> entry : env.entrySet()) {
            String value = sm.getOrDefault(entry.getValue().toString(), "");
            if (value.isEmpty()) {
                System.out.printf("Warning: Value for env %s is empty or could not be found in secret manager %n", entry.getKey());
            }
            substitutedEnv.put(entry.getKey(), value);
        }
        String connectionString = parent.template(conString, substitutedEnv);

        Properties properties = new Properties();

        for (Map.Entry<String, String> entry : props.entrySet()) {
            String value = sm.getOrDefault(entry.getValue(), "");
            if (value.isEmpty()) {
                System.out.printf("Warning: Value for env %s is empty or could not be found in secret manager %n", entry.getKey());
            }
            properties.put(entry.getKey(), value);
        }

        try {
            Class.forName(driverName);
            final Connection con = DriverManager.getConnection(connectionString, properties);
            JDBCWrapper wrapper = () -> con;
            return new DataSource() {
                @Override
                public Object proxy() {
                    return wrapper;
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
