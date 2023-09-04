package cowj.plugins;

import cowj.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import zoomba.lang.core.operations.ZJVMAccess;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JDBCWrapperTest {

    static Model model = () -> ".";

    static JDBCWrapper derby = null;

    static final int UPTO = 5 ;

    static final String TEST_SECRET_MGR_NAME = "_test_" ;

    static Map<String,String> connectionBindings = Map.of(
             "schema", "schema",
            "host", "host", "user", "user",
            "db", "db", "pass", "pass" );

    static Map<String,String> smMap = Map.of(
            "schema", "jdbc:oracle:thin:@",
            "host", "localhost", "user", "u",
            "db", "local_db", "pass", "p" );

    static SecretManager SM = () -> smMap;

    @BeforeClass
    public static void boot() throws Exception {
        Scriptable.DATA_SOURCES.put(TEST_SECRET_MGR_NAME, SM );
        final String driverClassName = org.apache.derby.iapi.jdbc.AutoloadedDriver.class.getName();
        // in memory stuff...
        Map<String,Object> config = Map.of("connection", "jdbc:derby:memory:cowjdb;create=true" ,
                "driver", driverClassName , "stale", "values current_timestamp");
        DataSource ds = JDBCWrapper.JDBC.create("derby", config, model);
        Assert.assertTrue( ds.proxy() instanceof  JDBCWrapper );
        Assert.assertEquals( "derby", ds.name() );

        derby = (JDBCWrapper) ds.proxy();
        Connection con = derby.connection().value();
        Assert.assertNotNull( con );
        // create table
        String query = "CREATE TABLE Data( "
                + "Name VARCHAR(255), "
                + "Age INT NOT NULL) " ;
        Statement stmt = con.createStatement();
        stmt.execute(query);
        System.out.println("Table created");
        // insert 2 rows of data
        for ( int i = 0; i < UPTO ; i ++ ){
            String sql = String.format( "INSERT into Data values ( 'n_%d' , %d )", i+1, i+1 )  ;
            stmt.executeUpdate(sql);
            System.out.println("Row Inserted...: " + i );
        }
    }

    @AfterClass
    public static void shutDown() throws Exception {
        if ( derby != null ) {
            derby.connection().value().close();
        }
        Scriptable.DATA_SOURCES.remove(TEST_SECRET_MGR_NAME);
    }

    @Test
    public void queryTest(){
       EitherMonad<List<Map<String,Object>>> resp = derby.select("select * from Data" , Collections.emptyList());
       Assert.assertTrue(resp.isSuccessful());
       List<Map<String,Object>> rows = resp.value();
       Assert.assertEquals( UPTO, rows.size() );
       // SQL has poor sense of casing...
       rows.forEach( m -> {
           Object name = m.get("NAME");
           Assert.assertTrue( name instanceof String );
           Object age = m.get("AGE");
           Assert.assertTrue( age instanceof Integer );
       });
    }

    @Test
    public void queryTestWithInvalidConnection() throws SQLException {
        // Check validity
        Assert.assertEquals( "values current_timestamp", derby.staleCheckQuery() );
        EitherMonad<Connection> connectionEitherMonad = derby.connection();
        // destroy it
        connectionEitherMonad.value().close();
        // now let's do a query
        EitherMonad<List<Map<String,Object>>> resp = derby.select("select * from Data" , Collections.emptyList());
        Assert.assertTrue(resp.isSuccessful());
        List<Map<String,Object>> rows = resp.value();
        Assert.assertEquals( UPTO, rows.size() );
        // SQL has poor sense of casing...
        rows.forEach( m -> {
            Object name = m.get("NAME");
            Assert.assertTrue( name instanceof String );
            Object age = m.get("AGE");
            Assert.assertTrue( age instanceof Integer );
        });
    }


    @Test
    public void injectError(){
        EitherMonad<List<Map<String,Object>>> resp = derby.select("select * from Data;" , Collections.emptyList());
        Assert.assertTrue(resp.inError());
        Assert.assertTrue( resp.error().getMessage().contains(";")); // error due to semi colon
    }

    @Test
    public void invalidConfigTest(){
        Map<String,Object> config = Map.of("connection", "jdbc:foo:memory:cowjdb;create=true" ,
                "driver", "" );
        Exception exception = assertThrows(RuntimeException.class, () -> {
            DataSource ds = JDBCWrapper.JDBC.create("foo", config, model);
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void getObjectTest(){
        java.util.Date o = new java.util.Date();
        java.sql.Date d = new java.sql.Date( o.getTime());
        LocalDateTime ld = o.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        Object r = JDBCWrapper.getObject(d);
        Assert.assertTrue( r instanceof Long);
        Assert.assertEquals(o.getTime(), r);
        r = JDBCWrapper.getObject(ld);
        Assert.assertTrue( r instanceof Long);
        // now timestamp
        java.sql.Timestamp ts = new java.sql.Timestamp( o.getTime());
        r = JDBCWrapper.getObject(ts);
        Assert.assertEquals(o.getTime(), r);
        // Now date itself
        r = JDBCWrapper.getObject(o);
        Assert.assertEquals(o.getTime(), r);
    }

    @Test
    public void jdbcWithSecretTestNoConnectionStringPassed(){
        final Map<String,Object> config = Map.of( "secrets" , TEST_SECRET_MGR_NAME, "bindings", connectionBindings);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            DataSource ds = JDBCWrapper.JDBC.create("foo", config, model);
        });
        // TODO make it run properly, not throw exception
        Assert.assertNotNull(exception);
    }

    @Test
    public void jdbcWithSecretTestConnectionStringPassed(){
        final Map<String,Object> config = Map.of( "connection" , "@" + JDBCWrapper.DEFAULT_CONNECTION_STRING,
                "secrets" , TEST_SECRET_MGR_NAME, "bindings", connectionBindings);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            DataSource ds = JDBCWrapper.JDBC.create("foo", config, model);
        });
        // TODO make it run properly, not throw exception
        Assert.assertNotNull(exception);
    }

    @Test
    public void rawConnectionStringIsNotSubstituted() {
        final Map<String, String> properties = Map.of("foo", "bar");
        final Map<String,Object> config = Map.of( "connection" , "jdbc:derby:memory:cowjdb",
                "properties", properties);
        JDBCWrapper derbyClient = (JDBCWrapper) JDBCWrapper.JDBC.create("foo", config, model).proxy();
        EitherMonad<List<Map<String,Object>>> resp = derbyClient.select("select count(*) as count from Data", Collections.emptyList());
        assertTrue(resp.isSuccessful());

        /// Ensure only 1 row and 1 column are returned
        List<Map<String,Object>> rows = resp.value();
        assertEquals(rows.get(0).get("COUNT"), UPTO);
    }

    @Test
    public void connectionStringIsSubstituted() {
        String TEST_SM = "__temp_sm__";
        final Map<String,Object> config = Map.of( "connection" , "${bar}:${baz}", "secrets", TEST_SM);

        SecretManager sm = () -> Map.of("bar", "jdbc:derby", "baz", "memory:cowjdb");

        try {
            Scriptable.DATA_SOURCES.put(TEST_SM, sm);
            JDBCWrapper derbyClient = (JDBCWrapper) JDBCWrapper.JDBC.create("foo", config, model).proxy();
            EitherMonad<List<Map<String,Object>>> resp = derbyClient.select("select count(*) as count from Data", Collections.emptyList());
            assertTrue(resp.isSuccessful());

            /// Ensure only 1 row and 1 column are returned
            List<Map<String,Object>> rows = resp.value();
            assertEquals(rows.get(0).get("COUNT"), UPTO);
        } finally {
            Scriptable.DATA_SOURCES.remove(TEST_SM);
        }
    }
    @Test
    public void testPooling(){
        EitherMonad<Connection> db1 = derby.connection();
        EitherMonad<Connection> db2 = derby.connection();
        // same thread, they should be same
        Assert.assertEquals(db1.value(), db2.value());
        // Let's create another thread
        EitherMonad<?>[] dbT = new EitherMonad[1];
        Runnable r = () ->{
            Assert.assertFalse( derby.isValid() );
            dbT[0] = derby.connection();
        };
        Thread t = new Thread(r);
        t.start();
        while(t.isAlive()){
            try {
                Thread.sleep(300);
            }catch (Exception ignore){}
        }
        Assert.assertNotNull(dbT[0]);
        Assert.assertTrue(dbT[0].isSuccessful());
        Assert.assertNotEquals( db1, dbT[0]);
    }

    @Test
    public void underStressNullConnectionCheckTest(){
        final EitherMonad<Connection> errorCon = EitherMonad.error(new Throwable("Too much load!"));
        JDBCWrapper broken = new JDBCWrapper() {
            @Override
            public EitherMonad<Connection> connection() {
                return errorCon;
            }
            @Override
            public EitherMonad<Connection> create() {
                return errorCon;
            }
            @Override
            public boolean isValid() {
                return false;
            }
            @Override
            public long timeout() { return Long.MAX_VALUE; }
        };
        Assert.assertFalse( broken.noCrashOnBoot() ); // this should be defaulted to false...
        EitherMonad<?> res = broken.select("whatever", List.of());
        Assert.assertTrue( res.inError());
        Assert.assertEquals(errorCon.error() , res.error() );
        // now generic error test on static
        res = JDBCWrapper.selectWithConnection(null, "whatever", List.of());
        Assert.assertTrue( res.inError());
        Assert.assertTrue( res.error() instanceof NullPointerException );
    }

    @Test
    public void testPerformance() throws Exception{
        final int numThreads = 5 ;
        final int totalQueries = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<Callable<EitherMonad<?>>> list = new ArrayList<>();
        for ( int i=0; i < totalQueries; i++ ){
            list.add( () -> derby.select("select count(*) as count from Data", Collections.emptyList()) );
        }
        List<Future<EitherMonad<?>>> futures = executorService.invokeAll(list);
        EitherMonad<Long> tm = TestUtils.timeIt( () -> {
            executorService.shutdown();
            try {
                executorService.awaitTermination(1 , TimeUnit.MILLISECONDS) ;
            }catch (InterruptedException ie){
                throw new RuntimeException(ie);
            }
        });
        Assert.assertTrue(tm.isSuccessful());
        futures.forEach( f -> {
            Assert.assertTrue(f.isDone());
            try {
                Assert.assertTrue(f.get().isSuccessful());
            }catch (Throwable t){
                Assert.fail();
            }
        });
        double tms = tm.value()/1000000.0;
        System.out.printf( "With %d threads running over %d queries on derby took %.2f milli secs! %n", numThreads, totalQueries, tms );
        Assert.assertTrue( tms <= 1.5 );
    }

    @Test
    public void noCrashOnBootTest(){
        final Map<String,Object> config = Map.of( "no-crash-boot", true );
        JDBCWrapper jdbcWrapper = (JDBCWrapper) JDBCWrapper.JDBC.create("bar", config, model).proxy();
        Assert.assertNotNull(jdbcWrapper);
        Connection con = derby.connection().value();
        // set the connection borrowed from derby
        ZJVMAccess.callMethod( jdbcWrapper, "threadLocalConnection", new Object[] { con });
        // setup done, now ask for the stuff back
        Assert.assertTrue(jdbcWrapper.connection().isSuccessful());
        Assert.assertEquals( con, jdbcWrapper.connection().value());
    }

    @Test
    public void connectionAutoCloseSuccessTest() throws Exception {
        final Map<String,Object> config = Map.of( "no-crash-boot", true , "timeout", 100L );
        JDBCWrapper jdbcWrapper = (JDBCWrapper) JDBCWrapper.JDBC.create("bar", config, model).proxy();
        Assert.assertNotNull(jdbcWrapper);
        Assert.assertEquals( 100L, jdbcWrapper.timeout());
        Connection con = mock(Connection.class);
        ZJVMAccess.callMethod( jdbcWrapper, "threadLocalConnection", new Object[] { con });
        // this should not be closed, yet
        verify(con, times(0)).close();
        // wait for some time.. more than 200 ms
        Thread.sleep(200);
        verify(con, times(1)).close();
    }

    @Test
    public void connectionAutoCloseFailureTest() throws Exception {
        final Map<String,Object> config = Map.of( "no-crash-boot", true , "timeout", 100L );
        JDBCWrapper jdbcWrapper = (JDBCWrapper) JDBCWrapper.JDBC.create("bar", config, model).proxy();
        Assert.assertNotNull(jdbcWrapper);
        Assert.assertEquals( 100L, jdbcWrapper.timeout());
        Connection con = mock(Connection.class);
        doThrow(new SQLException("boom!")).when(con).close();
        ZJVMAccess.callMethod( jdbcWrapper, "threadLocalConnection", new Object[] { con });
        // this should not be closed, yet
        verify(con, times(0)).close();
        // wait for some time.. more than 200 ms
        Thread.sleep(200);
        verify(con, times(1)).close();
    }
}
