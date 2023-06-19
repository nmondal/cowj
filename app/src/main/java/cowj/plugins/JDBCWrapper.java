package cowj.plugins;

import cowj.DataSource;
import jnr.constants.platform.Local;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public interface JDBCWrapper {

    List<Map> select(String query, List<Object> args);

    DataSource.Creator JDBC = (name, config, parent) -> {
        String driverName = config.getOrDefault("driver", "").toString();
        String connection = config.getOrDefault("connection", "").toString();

        Map<String, Object> props = (Map<String, Object>) config.getOrDefault("properties", Collections.emptyMap());
        Properties connectionProperties = new Properties();
        connectionProperties.putAll(props);
        try {
            Class.forName(driverName);
            Connection con = DriverManager.getConnection(connection, connectionProperties);
            JDBCWrapper wrapper = (query, args) -> {
                List<Map> result = new ArrayList<>();
                try (Statement stmt = con.createStatement()){
                    String q = query.formatted(args.toArray());
                    System.out.println(q);
                    ResultSet rs = stmt.executeQuery(q);
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int count = rsmd.getColumnCount();

                    while (rs.next()) {
                        Map m = new HashMap();
                        for (int index = 1; index <= count; index++)
                        {
                            String column = rsmd.getColumnName(index);
                            Object value = rs.getObject(column);
                            if (value == null)
                            {
                                m.put(column, null);
                            } else if (value instanceof Integer) {
                                m.put(column, value);
                            } else if (value instanceof String) {
                                m.put(column, value);
                            } else if (value instanceof Boolean) {
                                m.put(column, value);
                            } else if (value instanceof Date) {
                                m.put(column, ((Date) value).getTime());
                            } else if (value instanceof LocalDateTime) {
                                /// convert to milliseconds since epoch
                                m.put(column, ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toEpochSecond() * 1000);
                            } else if (value instanceof Long) {
                                m.put(column, value);
                            } else if (value instanceof Double) {
                                m.put(column, value);
                            } else if (value instanceof Float) {
                                m.put(column, value);
                            } else if (value instanceof BigDecimal) {
                                m.put(column, value);
                            } else if (value instanceof Byte) {
                                m.put(column, value);
                            } else if (value instanceof byte[]) {
                                m.put(column, value);
                            } else {
                                throw new IllegalArgumentException("Unmappable object type: " + value.getClass());
                            }
                        }
                        result.add(m);
                    }

                    return result;
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }
            };
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
