package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JDBCWrapperTest {

    static Model model = () -> ".";
    static JDBCWrapper derby = null;
    static final int UPTO = 5 ;

    @BeforeClass
    public static void boot() throws Exception {
        final String driverClassName = org.apache.derby.iapi.jdbc.AutoloadedDriver.class.getName();
        // in memory stuff...
        Map<String,Object> config = Map.of("connection_string", "jdbc:derby:memory:cowjdb;create=true" ,
                "driver", driverClassName );
        DataSource ds = JDBCWrapper.JDBC.create("derby", config, model);
        Assert.assertTrue( ds.proxy() instanceof  JDBCWrapper );
        derby = (JDBCWrapper) ds.proxy();
        Connection con = derby.connection();
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
            derby.connection().close();
        }
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
    public void injectError(){
        EitherMonad<List<Map<String,Object>>> resp = derby.select("select * from Data;" , Collections.emptyList());
        Assert.assertTrue(resp.inError());
        Assert.assertTrue( resp.error().getMessage().contains(";")); // error due to semi colon
    }
}
