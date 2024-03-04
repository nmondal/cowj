package cowj.plugins;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import zoomba.lang.core.operations.ZRandom;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VersionedMapTest {

    static VersionedMap<Integer,Integer> vm = VersionedMap.versionedMap();

    static final int maxSize = 30;

    static final int maxVerSize = 10;

    @BeforeClass
    public static void beforeClass(){
        vm = VersionedMap.versionedMap();
        IntStream.range(0, maxSize).parallel().forEach( i -> {
            IntStream.range(0,maxVerSize).parallel().forEach( j -> {
                vm.put(i, j);
            });
        });
        Assert.assertEquals(maxSize, vm.size());
        IntStream.range(0, maxSize).parallel().forEach( i -> {
            Set<String> vs = vm.versions(i).collect(Collectors.toSet());
            Assert.assertEquals(maxVerSize, vs.size());
        });

        Set<Map.Entry<Integer, Integer>> entries = vm.entrySet();
        Assert.assertEquals(maxSize, entries.size());

        Set<Integer> keys = vm.keySet();
        Assert.assertEquals(maxSize, keys.size());

        Collection<Integer> values = vm.values();
        Assert.assertEquals(maxSize, values.size());

    }

    @AfterClass
    public static void afterClass(){
        IntStream.range(0, maxSize).parallel().forEach( i -> {
            int k = ZRandom.RANDOM.nextInt( 50);
            vm.remove(k);
            Assert.assertFalse(vm.containsKey(k));
        });

        vm.clear();
        Assert.assertEquals(0, vm.size());
        Assert.assertTrue(vm.isEmpty());
    }
    @Test
    public void parallelGetTest(){
        IntStream.range(0, maxSize).parallel().forEach( i -> {
            Assert.assertNotNull(vm.get(i));
            Assert.assertTrue( vm.containsKey(i));
        });

        IntStream.range(0, 10).parallel().forEach( i -> {
            Assert.assertNull(vm.get(9999999));
            Assert.assertFalse( vm.containsKey(9999999));
        });
        Assert.assertTrue( vm.containsValue(0)); // this may fail due to unknown reason
    }

    @Test
    public void dataAtVersionTest(){
        IntStream.range(0, maxSize).parallel().forEach( i -> {
            List<String> vs = vm.versions(i).toList();
            Set<Integer> allData = vs.stream().map( ver-> vm.dataAtVersion(i, ver)).collect(Collectors.toSet());
            Assert.assertEquals(maxVerSize, allData.size());
        });
    }

    @Test
    public void putAllTest(){
        VersionedMap<Integer,Integer> vMap = VersionedMap.versionedMap();
        Map<Integer,Integer> v1 = Map.of(1,2,3,4) ;
        vMap.putAll(v1);
        Assert.assertEquals(2, (int)vMap.get(1) ) ;
        Assert.assertEquals(4, (int)vMap.get(3) ) ;

        Map<Integer,Integer> v2 = Map.of(1,4,3,6) ;
        vMap.putAll(v2);
        Assert.assertEquals(4, (int)vMap.get(1) ) ;
        Assert.assertEquals(6, (int)vMap.get(3) ) ;
        
    }
}
