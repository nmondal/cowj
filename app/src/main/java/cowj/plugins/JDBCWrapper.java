package cowj.plugins;

import cowj.CowjRuntime;
import cowj.DataSource;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public interface JDBCWrapper {

    Connection connection();

    String DRIVER = "driver";
    String ENV = "env" ;
    String SCHEME = "scheme";
    String DATABASE = "database";
    String USER = "user";
    String PASSWORD = "password";
    String HOST = "host";
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

    DataSource.Creator JDBC = (name, config, parent) -> {
        String driverName = config.getOrDefault(DRIVER, "").toString();
        String scheme = (String) config.getOrDefault(SCHEME, "");

        Map<String, Object> env = (Map<String, Object>) config.getOrDefault(ENV, Collections.emptyMap());
        String host = CowjRuntime.env.get((String) env.getOrDefault(HOST, ""));
        String database = CowjRuntime.env.get((String) env.getOrDefault(DATABASE, ""));
        String user = CowjRuntime.env.get((String) env.getOrDefault(USER, ""));
        String password = CowjRuntime.env.get((String) env.getOrDefault(PASSWORD, ""));

        String connection = "jdbc:%s://%s/%s?user=%s&password=%s".formatted(scheme, host, database, user, password);

        Map<String, Object> props = (Map<String, Object>) config.getOrDefault(PROPERTIES, Collections.emptyMap());
        Properties connectionProperties = new Properties();
        connectionProperties.putAll(props);
        try {
            Class.forName(driverName);
            final Connection con = DriverManager.getConnection(connection, connectionProperties);
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
