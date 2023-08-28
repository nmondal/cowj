package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZTypes;

import java.sql.*;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;

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
     * Runs Select Query using connection and args
     * @param con the Connection to use
     * @param query to be executed
     * @param args the arguments to be passed
     * @return a EitherMonad of type List of Map - rather list of json objects
     */
    static EitherMonad<List<Map<String,Object>>> selectWithConnection( Connection con, String query, List<Object> args) {
        if ( con == null ){
            final String errorMessage = "Connection was passed as null, why?" ;
            logger.error(errorMessage);
            return EitherMonad.error( new NullPointerException(errorMessage));
        }
        try (Statement stmt = con.createStatement() ) {
            List<Map<String,Object>> result = new ArrayList<>();
            String q = query.formatted(args.toArray());
            logger.info(q);
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
            return EitherMonad.error(e);
        }
    }

    /**
     * Runs Select Query using underlying connection and args
     * Does automatic retry for stale/invalid connection
     * @param query to be executed
     * @param args the arguments to be passed
     * @return a EitherMonad of type List of Map - rather list of json objects
     */
    default EitherMonad<List<Map<String,Object>>> select(String query, List<Object> args) {
        EitherMonad<Connection> em = connection();
        if ( em.inError() ) {
            logger.error("Connection creation error - returning the error, will not do query!");
            return EitherMonad.error(em.error());
        }
        EitherMonad<List<Map<String,Object>>> res = selectWithConnection(connection().value(), query, args);
        if ( res.isSuccessful() || isValid() ) return res;
        em  = create();
        return selectWithConnection(em.value(), query, args);
    }

    /**
     * DataSource.Creator for JDBCWrapper type
     */
    DataSource.Creator JDBC = (name, config, parent) -> {

        JDBCWrapper jdbcWrapper = new JDBCWrapper() {
            final String staleCheckQuery = config.getOrDefault(STALE_CHECK_TIMEOUT_QUERY, JDBCWrapper.super.staleCheckQuery()).toString() ;
            final ThreadLocal<Connection> connectionThreadLocal = ThreadLocal.withInitial(() -> null);

            final boolean noCrashOnBoot = ZTypes.bool(config.getOrDefault(NO_CRASH_ON_BOOT_CONNECTION, false),false) ;

            @Override
            public boolean noCrashOnBoot() {
                return noCrashOnBoot;
            }

            @Override
            public boolean isValid(){
                final Connection _connection = connectionThreadLocal.get();
                if ( _connection == null) {
                    logger.warn("Connection is null!");
                    return false;
                }

                try (Statement st = _connection.createStatement()) {
                     st.execute( staleCheckQuery );
                     return true;
                }catch (Exception ignore){
                    connectionThreadLocal.set(null);
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
                    connectionThreadLocal.set(_connection);
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
                Connection connection = connectionThreadLocal.get();
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
