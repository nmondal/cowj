package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import cowj.Scriptable;

import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public interface JDBCWrapper {

    Connection connection();

    String DRIVER = "driver" ;

    String SECRET_MANAGER = "secrets";

    String BINDINGS = "bindings";

    String PROPERTIES = "properties" ;

    String CONNECTION_STRING = "connection" ;

    String REDIRECTION = "@" ;

    String DEFAULT_CONNECTION_STRING = "${schema}//${host}/${db}?user=${user}&password=${pass}" ;

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

    static String connectionString(Model parent, final String original, Map<String, String> keyMap, SecretManager secretManager) {
        final String formattedReturn;
        if ( original.isEmpty() ){
            formattedReturn = DEFAULT_CONNECTION_STRING ; // create a default one which works...
        } else {
            // trim the 1st char at begin
            formattedReturn = original.substring(1);
        }

        Map<String,Object> passedThroughEnvMap = new HashMap<>() {
            @Override
            public String getOrDefault(Object key, Object def) {
                String refKey = keyMap.get(key.toString());
                if (refKey == null) return null;
                return secretManager.getOrDefault(refKey, def.toString());
            }
        };
        return parent.template( formattedReturn, passedThroughEnvMap );
    }


    DataSource.Creator JDBC = (name, config, parent) -> {
        String driverName = config.getOrDefault(DRIVER, "").toString();
        Map<String, String> bindings = (Map<String, String>) config.getOrDefault(BINDINGS, Collections.emptyMap());

        String conString = config.getOrDefault(CONNECTION_STRING, "").toString();
        String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
        SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(secretManagerName, SecretManager.DEFAULT);

        if ( conString.startsWith(REDIRECTION) || conString.isEmpty() ) {
            System.out.println("We shall redirect the string to secret manager...");
            conString = connectionString(parent, conString, bindings, sm);
        } else {
            System.out.println("We shall use the connection string as is...");
        }

        Map<String, String> props = (Map<String, String>) config.getOrDefault(PROPERTIES, Collections.emptyMap());
        Properties properties = new Properties();
        properties.putAll(props);

        try {
            Class.forName(driverName);
            final Connection con = DriverManager.getConnection(conString, properties);
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
