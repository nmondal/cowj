package cowj;


import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class TimeSeriesStorageTest {

    static Map<String,String> mem = Collections.synchronizedMap( new TreeMap<>() );

    static StorageWrapper<Boolean, Boolean, Map.Entry<String, String>> wrapper = new StorageWrapper.SimpleKeyValueStorage() {

        @Override
        public Boolean dumps(String bucketName, String fileName, String data) {
            mem.put(fileName,data);
            return true;
        }

        @Override
        public boolean fileExist(String bucketName, String fileName) {
            return mem.containsKey(fileName);
        }

        @Override
        public Map.Entry<String, String> data(String bucketName, String fileName) {
            String val = mem.get(fileName);
            return Map.entry(fileName,val);
        }

        @Override
        public Boolean dumpb(String bucketName, String fileName, byte[] data) {
            final String sData = new String(data, StandardCharsets.UTF_8);
            mem.put(fileName,sData);
            return true;
        }

        @Override
        public Stream<Map.Entry<String, String>> stream(String bucketName, String directoryPrefix) {
            return mem.entrySet().stream().filter( e -> e.getKey().startsWith(directoryPrefix) );
        }

        @Override
        public Boolean createBucket(String bucketName, String location, boolean preventPublicAccess) {
            return true;
        }

        @Override
        public boolean deleteBucket(String bucketName) {
            return false;
        }

        @Override
        public boolean delete(String bucketName, String path) {
            return false;
        }
    } ;

    static TimeSeriesStorage timeSeriesStorage = wrapper.timeSeries( "ts", "foo", "MS") ;

    @After
    public void after(){
        mem.clear();
    }

    @Test
    public void stepTest(){
        TemporalAmount d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.MS );
        Assert.assertEquals( Duration.ofMillis(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.SEC );
        Assert.assertEquals( Duration.ofSeconds(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.MIN );
        Assert.assertEquals( Duration.ofMinutes(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.HOUR );
        Assert.assertEquals( Duration.ofHours(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.DAY );
        Assert.assertEquals( Duration.ofDays(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.MONTH );
        Assert.assertEquals(Period.ofMonths(1), d);
        d = TimeSeriesStorage.step( TimeSeriesStorage.Precision.YEAR );
        Assert.assertEquals( Period.ofYears(1), d);
    }

    @Test
    public void dataTest(){
        final String sData = "hello" ;
        final byte[] raw = sData.getBytes(StandardCharsets.UTF_8);
        final List<?> ll = List.of( sData, raw );
        final Map<?,?> map = Map.of( "a" , 42 );
        long startTime = System.currentTimeMillis() ;
        IntStream.range(0,5).parallel().forEach( (i) -> {
            timeSeriesStorage.put(".txt", sData);
            timeSeriesStorage.put(".bin", raw);
            timeSeriesStorage.put(".json", ll);
            timeSeriesStorage.put(".json", map);
        });
        Assert.assertEquals(20, mem.size() );
        Set<String> s = new HashSet<>(mem.values());
        Assert.assertEquals(3, s.size());
        // now list over the data
        // get the timestamp ms
        long stMS = startTime - 1000 ;
        List<Map.Entry<String,Object>> l = timeSeriesStorage.list( stMS, stMS - 1 );
        Assert.assertTrue(l.isEmpty());
        l = timeSeriesStorage.list( stMS, stMS );
        Assert.assertTrue(l.isEmpty());
        l = timeSeriesStorage.list( stMS, stMS + 5000 );
        Assert.assertEquals(20, l.size() );
    }
}
