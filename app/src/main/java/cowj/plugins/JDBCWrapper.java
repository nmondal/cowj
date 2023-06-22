package cowj.plugins;

import cowj.DataSource;
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


    default Object getObject(Object value) {
        if (value instanceof java.sql.Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toEpochSecond() * 1000;
        }
        return value;
    }

    default List<Map<String,Object>> select(String query, List<Object> args) {
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
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    static String connectionString(Map<String, String> map, SecretManager secretManager) {
        String scheme = map.getOrDefault(CONNECTION, "");
        String hostKey = map.getOrDefault("host", "");
        String userKey = map.getOrDefault("user", "");
        String dbKey = map.getOrDefault("db", "");
        String passKey = map.getOrDefault("pass", "");

        String host = secretManager.getOrDefault(hostKey, "");
        String user = secretManager.getOrDefault(userKey, "");
        String db = secretManager.getOrDefault(dbKey, "");
        String pass = secretManager.getOrDefault(passKey, "");

        /// TODO(Hemil): Each db has a different format
        return String.format("%s://%s/%s?user=%s&password=%s", scheme, host, db, user, pass);
    }

    DataSource.Creator JDBC = (name, config, parent) -> {
        String driverName = config.getOrDefault(DRIVER, "").toString();

        String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
        SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.get(secretManagerName);

        Map<String, String> props = (Map<String, String>) config.getOrDefault(PROPERTIES, Collections.emptyMap());
        String connection = connectionString(props, sm);
        try {
            Class.forName(driverName);
            final Connection con = DriverManager.getConnection(connection);
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
