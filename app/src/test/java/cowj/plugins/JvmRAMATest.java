package cowj.plugins;

import cowj.*;
import org.junit.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertThrows;

public class JvmRAMATest {

    static final String rama = "samples/rama/rama.yaml" ;
    private static ModelRunner mr ;

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
        mr = ModelRunnerTest.runModel( rama );
    }

    @AfterClass
    public static void afterClass() throws Exception {
        storage.deleteBucket(TOPIC);
        Scriptable.DATA_SOURCES.remove(MEM);
        if ( mr == null ) return;
        mr.stop();
        mr = null;
        System.gc(); // garbage collect trigger
        Thread.sleep(1500);
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
    public void incorrectConfigurationTests(){
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            JvmRAMA.RAMA.create( "rama", Map.of(), ()->"" );
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("empty"));

        exception = assertThrows(IllegalArgumentException.class, () -> {
            JvmRAMA.RAMA.create( "rama", Map.of("storage", "key_does_not_exists!"), ()->"" );
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("StorageWrapper"));

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

    @Test
    public void errorInGetTest(){
        final StorageWrapper<?,?,?> wrapper = new MemoryBackedStorage(){
            @Override
            public Stream<Map.Entry<String, String>> stream(String bucketName, String directoryPrefix) {
                throw new RuntimeException("Boom!");
            }
        };
        JvmRAMA rama = new JvmRAMA() {
            @Override
            public StorageWrapper<?, ?, ?> prefixedStorage() {
                return wrapper;
            }

            @Override
            public String suffix() {
                return "";
            }
        };
        EitherMonad<JvmRAMA.Response> mon =
                rama.get("whatever", "foobar", 10, 0 );
        Assert.assertTrue(mon.inError());
        Assert.assertTrue(mon.error().getMessage().contains("Boom!"));

        EitherMonad<Boolean> cp = rama.consumePrefix("whatever", "foobar", 100, (ec,eb)->{});
        Assert.assertTrue(mon.inError());
        Assert.assertTrue(mon.error().getMessage().contains("Boom!"));
    }

    @Test
    public void ramaCronTest() throws Exception {
        int max = 5;
        for ( int i=0; i < max; i++ ) {
            String body = "hey!" + i;
            String res = ModelRunnerTest.post("http://localhost:4202", "/event", body );
            Assert.assertNotNull(res);
        }
        int iter = 0;
        boolean has = false ;
        while ( !has && iter < 10 ) {
            has = Scriptable.SHARED_MEMORY.containsKey("cnt");
            iter ++;
            Thread.sleep(1300);
        }
        Assert.assertTrue(has);
        Assert.assertEquals( max, Scriptable.SHARED_MEMORY.get("cnt"));
    }
}
