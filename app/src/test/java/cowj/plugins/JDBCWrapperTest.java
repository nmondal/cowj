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
        /*
        stmt = con.createStatement();
        String sql = "INSERT into Data values"+"("+ "'"+ id +"'"+ ","+ name + ","+ "'"+ marks+"'"+")";
        stmt.executeUpdate(sql);
         */
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
    }
}
