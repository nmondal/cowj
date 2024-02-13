package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import org.junit.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.IntStream;

public class JvmRAMATest {
    static final MemoryBackedStorage storage = new MemoryBackedStorage();
    static final String MEM = "__mem__" ;

    static final String TOPIC = "__foo__" ;

    static final String CURRENT_MIN_PREFIX = "yyyy/MM/dd/HH/mm";

    static final int TOT_EVENTS = 10000;

    static String directoryPrefix(long timeStamp){
        SimpleDateFormat sdf = new SimpleDateFormat( CURRENT_MIN_PREFIX );
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date dt = new Date(timeStamp);
        return sdf.format(dt);
    }
    @BeforeClass
    public static void beforeClass(){
        storage.createBucket(TOPIC, "", false);
        Scriptable.DATA_SOURCES.put( MEM , storage);
    }

    @AfterClass
    public static void afterClass(){
        storage.deleteBucket(TOPIC);
        Scriptable.DATA_SOURCES.remove(MEM);
    }

    @Before
    public void cleanTopic(){
        storage.dataMemory.get( TOPIC).clear();
    }

    long appendEvent(JvmRAMA rama )  {
        final long ts = System.currentTimeMillis();
        IntStream.range(0,TOT_EVENTS).parallel().forEach( i ->{
            long t = System.currentTimeMillis();
            rama.put( TOPIC, String.valueOf(t));
        });

        Assert.assertEquals( TOT_EVENTS, storage.dataMemory.get(TOPIC).size());
        return ts;
    }

    @Test
    public void ramaWriteRead() {
        DataSource ds = JvmRAMA.RAMA.create( "rama", Map.of("storage", MEM), ()->"" );
        Assert.assertTrue( ds.proxy() instanceof JvmRAMA );
        JvmRAMA rama = (JvmRAMA) ds.proxy();
        long start = System.currentTimeMillis();
        long ts = appendEvent( rama);
        EitherMonad<JvmRAMA.Response> mon =
                rama.get(TOPIC, directoryPrefix( ts ), TOT_EVENTS + 100, 0);
        Assert.assertFalse(mon.inError());

        long delta = System.currentTimeMillis() - start;
        System.out.printf("Time taken for %d message write/read is %d (ms)%n", TOT_EVENTS, delta );

        int readSize = mon.value().data.size();
        if ( readSize == TOT_EVENTS ){
            Assert.assertFalse( mon.value().hasMoreData );
            return;
        }
        // there are gaps, so
        int gap = TOT_EVENTS - mon.value().data.size() ;
        // Move to Next minute ...
        mon = rama.get(TOPIC, directoryPrefix( System.currentTimeMillis() ), TOT_EVENTS, 0);


        Assert.assertEquals( gap, mon.value().data.size() );
        Assert.assertFalse( mon.value().hasMoreData );

    }

    @Test
    public void batchedRead(){
        DataSource ds = JvmRAMA.RAMA.create( "rama", Map.of("storage", MEM), ()->"" );
        JvmRAMA rama = (JvmRAMA) ds.proxy();
        long ts = appendEvent( rama);
        EitherMonad<JvmRAMA.Response> mon =
                rama.get(TOPIC, directoryPrefix( ts ), 5000, 0);
        Assert.assertFalse(mon.inError());
        Assert.assertEquals( 5000, mon.value().data.size());
        Assert.assertEquals( 5000, mon.value().readOffset);
        Assert.assertTrue( mon.value().hasMoreData);
        mon = rama.get(TOPIC, directoryPrefix( ts ), 5000, mon.value().readOffset);
        Assert.assertFalse(mon.inError());
        Assert.assertEquals( 5000, mon.value().data.size());
        Assert.assertFalse( mon.value().hasMoreData);
    }
}
