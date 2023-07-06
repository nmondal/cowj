package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import zoomba.lang.core.operations.Function;

import java.sql.*;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;

public interface JDBCWrapper {

    EitherMonad<Connection> connection();

    default String staleCheckQuery(){
        /*
        * Druid, MySQL, PGSQL ,AuroraDB
        * Oracle will not work SELECT 1 FROM DUAL
        * */
        return "SELECT 1";
    }

    String DRIVER = "driver" ;

    String SECRET_MANAGER = "secrets";

    String PROPERTIES = "properties" ;

    String CONNECTION_STRING = "connection" ;

    String STALE_CHECK_TIMEOUT_QUERY = "stale" ;

    String DEFAULT_CONNECTION_STRING = "${schema}//${host}/${db}?user=${user}&password=${pass}" ;

    default Object getObject(Object value) {
        if (value instanceof java.util.Date) {
            /*
            * This here, catches everything derived from this.
            * Which automatically solves the problem for sql.Date, sql.Timestamp
            *
            * */
            return ((java.util.Date) value).getTime();
        }
        if (value instanceof ChronoLocalDateTime) {
            return ((ChronoLocalDateTime<?>) value).atZone(ZoneId.systemDefault()).toEpochSecond() * 1000;
        }
        return value;
    }

    default EitherMonad<List<Map<String,Object>>> select(String query, List<Object> args) {
        List<Map<String,Object>> result = new ArrayList<>();
        EitherMonad<Connection> em = connection();
        if ( em.inError() ) return EitherMonad.error(em.error());
        final Connection con = em.value();

        try (Statement stmt = con.createStatement() ) {
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

        JDBCWrapper jdbcWrapper = new JDBCWrapper() {
            final String staleCheckQuery = config.getOrDefault(STALE_CHECK_TIMEOUT_QUERY, JDBCWrapper.super.staleCheckQuery()).toString() ;
            final ThreadLocal<Connection> connectionThreadLocal = ThreadLocal.withInitial(() -> null);

            boolean isValid(){
                final Connection _connection = connectionThreadLocal.get();
                if ( _connection == null) return false;

                try (Statement st = _connection.createStatement()) {
                     st.execute( staleCheckQuery );
                     return true;
                }catch (Exception ignore){
                    connectionThreadLocal.set(null);
                    System.err.printf("'%s' db Connection was stale, will try creating one! %n", name );
                    return false;
                }
            }
            EitherMonad<Connection> create(){
                String driverName = config.getOrDefault(DRIVER, "").toString();
                String conString = config.getOrDefault(CONNECTION_STRING, "").toString();
                String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
                SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(secretManagerName, SecretManager.DEFAULT);

                String substitutedConString = parent.template(conString, sm.env());

                Map<String, String> props = (Map<String, String>) config.getOrDefault(PROPERTIES, Collections.emptyMap());
                Properties properties = new Properties();
                for (Map.Entry<String, String> entry : props.entrySet()) {
                    properties.put(entry.getKey(), parent.template(entry.getValue(), sm.env()));
                }
                try {
                    /// Most modern drivers register themselves on startup.
                    /// We usually don't need to do this
                    if (!driverName.isEmpty()) {
                        Class.forName(driverName);
                    }
                    final Connection _connection = DriverManager.getConnection(substitutedConString, properties);
                    connectionThreadLocal.set(_connection);
                    System.out.printf("'%s' db Connection got created from thread.%n", name );
                    return EitherMonad.value(_connection);
                } catch ( Throwable th){
                    return EitherMonad.error(th);
                }
            }

            @Override
            public String staleCheckQuery() {
                return staleCheckQuery;
            }

            @Override
            public EitherMonad<Connection> connection() {
                if ( isValid() ) return EitherMonad.value(connectionThreadLocal.get());
                return create();
            }
        };
        EitherMonad<Connection> em = jdbcWrapper.connection();
        if ( em.inError() ) {
           throw  Function.runTimeException(em.error());
        }
        return new DataSource() {
            @Override
            public Object proxy() {
                return jdbcWrapper;
            }

            @Override
            public String name() {
                return name;
            }
        };
    };
}
