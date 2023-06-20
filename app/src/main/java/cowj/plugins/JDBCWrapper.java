package cowj.plugins;

import cowj.DataSource;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public interface JDBCWrapper {

    Connection connection();

    String DRIVER = "driver" ;
    String CONNECTION = "connection" ;
    String CONNECTION_ENV = "connection-env" ;
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
        String conEnv = config.getOrDefault(CONNECTION_ENV, "").toString();
        String connection = "" ;
        if ( conEnv.isEmpty() ){
            connection = config.getOrDefault(CONNECTION, "").toString();
            if ( connection.isEmpty() ){
                System.err.printf(" Connection '%s' is empty! %n", name);
            }
        } else {
            connection = System.getenv().getOrDefault(conEnv, "");
            if ( connection.isEmpty() ){
                System.err.printf("Connection '%s' supposed to pick up from ENV variable '%s', but the variable is empty!%n", name, conEnv);
            }
        }

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
