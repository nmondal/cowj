package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import cowj.Scriptable;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.sql.*;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction for JDBC Connection
 */
public interface JDBCWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(JDBCWrapper.class);

    /**
     * Gets the EitherMonad for Connection
     * @return EitherMonad for Connection
     */
    EitherMonad<Connection> connection();

    /**
     * Creates the Connection
     * In case of failure, puts the creation error into EitherMonad
     * @return EitherMonad for Connection
     */
    EitherMonad<Connection> create();

    /**
     * Checks if the underlying Connection is valid or not
     * @return true if Connection is ok, false otherwise
     */
    boolean isValid();

    /**
     * Gets the idle timeout  for the underlying Connection
     * @return timeout in ms
     */
    long timeout();

    /**
     * This is the query which used to check if the underlying Connection is valid or not
     * Druid, MySQL, PGSQL ,AuroraDB :: SELECT 1
*    * Oracle ::  SELECT 1 FROM DUAL
     * @return  a query which can be used to check underlying db connection health
     */
    default String staleCheckQuery(){
        return "SELECT 1";
    }


    /**
     * States if the wrapper is set to expect crash on booting up
     * Perhaps JDBC ds will be available later,
     * So in case no Connection can be made, still continue w/o throwing error
     * @return true if boot up connection can fail, false if we should throw error
     */
    default boolean noCrashOnBoot(){
        return false;
    }

    /**
     * Key for the driver class
     */
    String DRIVER = "driver" ;

    /**
     * Key for the SecretManager
     */
    String SECRET_MANAGER = "secrets";

    /**
     * Key for the JDBC Connection Properties
     */
    String PROPERTIES = "properties" ;

    /**
     * Key for the JDBC Connection String
     */
    String CONNECTION_STRING = "connection" ;

    /**
     * Key for the staleCheckQuery() query string
     */
    String STALE_CHECK_TIMEOUT_QUERY = "stale" ;


    /**
     * Key for Connection idle timeout after which connection would be closed by the server
     */
    String CONNECTION_TIMEOUT = "timeout" ;

    /**
     * Constant for the default JDBC Connection String
     */
    String DEFAULT_CONNECTION_STRING = "${schema}//${host}/${db}?user=${user}&password=${pass}" ;


    /**
     * Constant for the system not to crash on boot if connection failed on creation
     */
    String NO_CRASH_ON_BOOT_CONNECTION  = "no-crash-boot" ;


    /**
     * Converts java.sql.* object into java object
     * Essentially take temporal SQL Column values and convert them into numeric
     * @param value java.sql.* object
     * @return normal java object
     */
    static Object getObject(Object value) {
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

    /**
     * Formats the query
     * @param query a String query which needs to be formatted
     * @param args arguments, either a list or Map
     * @return formatted query as String
     */
    static String format(String query, Object args){
        if ( args instanceof List){
            return query.formatted(((List<?>)args).toArray());
        }
        if ( args instanceof Map<?,?>){
            return Model.formatParams( query, (Map<?,?>)args );
        }
        logger.warn("Query '{}' argument is passed non list/map, will return as is!",query);
        return query;
    }

    /**
     * Runs Select Query using connection and args
     * @param con the Connection to use
     * @param query to be executed
     * @param args the arguments to be passed, List or Map
     * @return an EitherMonad of type List of Map - rather list of json objects
     */
    static EitherMonad<List<Map<String,Object>>> selectWithConnection( Connection con, String query, Object args) {
        try (Statement stmt = con.createStatement() ) {
            List<Map<String,Object>> result = new ArrayList<>();
            String q =  format(query,args);
            logger.info("[S] query : [{}]", q);
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
        } catch (Throwable e) {
            logger.error("Error in query : " + e );
            return EitherMonad.error(e);
        }
    }


    /**
     * Runs Insert, Delete, Update Query using connection and args
     * @param con the Connection to use
     * @param query to be executed
     * @param args the arguments to be passed, List or Map
     * @return an EitherMonad of type Integer returned from the connection
     */
    static EitherMonad<Integer> updateWithConnection( Connection con, String query, Object args) {
        try (Statement stmt = con.createStatement() ) {
            String q = format(query,args);
            logger.info("[CUID] query : [{}]", q);
            Integer result = stmt.executeUpdate(q);
            return EitherMonad.value(result);
        } catch (Throwable e) {
            logger.error("Error in query : " + e );
            return EitherMonad.error(e);
        }
    }

    /**
     * Wrapper function to retry things using connection
     * @param function java.util.function.Function which takes Connection and returns EitherMonad
     * @return final result which is EitherMonad
     * @param <T> type of the EitherMonad
     */
    default  <T> EitherMonad<T>  queryWithOnceRetry( java.util.function.Function <Connection, EitherMonad<T>> function ){
        EitherMonad<Connection> em = connection();
        if ( em.inError() ) {
            logger.error("Connection is in error - returning the error, will not do query!");
            return EitherMonad.error(em.error());
        }
        EitherMonad<T> res = function.apply(em.value());
        if ( res.isSuccessful() || isValid() ) return res;
        logger.error("Somehow the query failed, hence will retry once after creating connection again!");
        em  = create();
        return function.apply(em.value());
    }

    /**
     * Runs Select Query using underlying connection and args
     * Does automatic retry for stale/invalid connection
     * @param query to be executed
     * @param args the arguments to be passed,  List or Map
     * @return an EitherMonad of type List of Map - rather list of json objects
     */
    default EitherMonad<List<Map<String,Object>>> select(String query,Object args) {
        return queryWithOnceRetry( (connection ->  selectWithConnection(connection, query, args)));
    }

    /**
     * Runs Insert, Update, Delete Query using underlying connection and args
     * Does automatic retry for stale/invalid connection
     * @param query to be executed
     * @param args the arguments to be passed, List or Map
     * @return a EitherMonad of integer defining no of rows updated
     */
    default EitherMonad<Integer> update(String query, Object args) {
        return queryWithOnceRetry( (connection ->  updateWithConnection(connection, query, args)));
    }

    /**
     * DataSource.Creator for JDBCWrapper type
     */
    DataSource.Creator JDBC = (name, config, parent) -> {

        JDBCWrapper jdbcWrapper = new JDBCWrapper() {
            final String staleCheckQuery = config.getOrDefault(STALE_CHECK_TIMEOUT_QUERY, JDBCWrapper.super.staleCheckQuery()).toString() ;

            final long connectionTimeout = ZNumber.integer(config.getOrDefault( CONNECTION_TIMEOUT, 30 * 60000L ), 30 * 60000L).longValue();

            @Override
            public long timeout() {
                return connectionTimeout;
            }

            // get a unique id to each thread...
            final ThreadLocal<String> uuidThreadLocal = ThreadLocal.withInitial(() ->  UUID.randomUUID().toString());

            final ExpirationListener<String,Connection> closeConnection = (uuid, con ) -> {
                try {
                    logger.info("On Thread {} trying closing JDBC connection due to inactivity", uuidThreadLocal.get());
                    con.close();
                    logger.info("On Thread {} closed JDBC connection due to inactivity", uuidThreadLocal.get());
                }catch (Throwable t){
                    logger.error("Error happened while closing connection : " +  t);
                }
            };

            final ExpiringMap<String,Connection> conMap = ExpiringMap.builder()
                    .expirationPolicy( ExpirationPolicy.ACCESSED)
                    .expiration( connectionTimeout, TimeUnit.MILLISECONDS)
                    .asyncExpirationListener(closeConnection)
                    .build();

            final boolean noCrashOnBoot = ZTypes.bool(config.getOrDefault(NO_CRASH_ON_BOOT_CONNECTION, false),false) ;

            void threadLocalConnection(Connection connection){
                final String uuid = uuidThreadLocal.get();
                conMap.put(uuid, connection);
            }

            Connection threadLocalConnection(){
                final String uuid = uuidThreadLocal.get();
                return conMap.get(uuid);
            }

            @Override
            public boolean noCrashOnBoot() {
                return noCrashOnBoot;
            }

            @Override
            public boolean isValid(){
                final Connection _connection = threadLocalConnection();
                if ( _connection == null) {
                    logger.warn("Connection is null!");
                    return false;
                }

                try (Statement st = _connection.createStatement()) {
                     st.execute( staleCheckQuery );
                     return true;
                }catch (Exception ignore){
                    threadLocalConnection(null);
                    logger.error("'{}' db Connection was stale, will try creating one!", name );
                    return false;
                }
            }
            @Override
            public EitherMonad<Connection> create(){
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
                    Objects.requireNonNull(_connection);
                    threadLocalConnection(_connection);
                    logger.info("'{}' db Connection got created from thread.", name );
                    return EitherMonad.value(_connection);
                } catch ( Throwable th){
                    logger.error("Connection stays null for the thread due to : " + th);
                    return EitherMonad.error(th);
                }
            }

            @Override
            public String staleCheckQuery() {
                return staleCheckQuery;
            }

            @Override
            public EitherMonad<Connection> connection() {
                Connection connection = threadLocalConnection();
                if ( connection != null ) return EitherMonad.value(connection);
                return create();
            }
        };
        EitherMonad<Connection> em = jdbcWrapper.connection();
        if ( em.inError() ) {
            // This is where we essentially claim that later threads might be successful
            if (jdbcWrapper.noCrashOnBoot()) {
                logger.error("Booting DS Connection '{}' Creation Failed: {}", name , em.error().toString());
                logger.error("noCrashOnBoot() is true, creating DS, may later result in unexpected issues !!!");
                logger.error("Please consider booting the dependencies properly.");
            } else {
                throw Function.runTimeException(em.error());
            }
        }
        return DataSource.dataSource(name, jdbcWrapper);
    };
}
