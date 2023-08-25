package cowj;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;


import static cowj.Retry.STRATEGY;
import static cowj.Retry.INTERVAL;
import static cowj.Retry.MAX;
import static cowj.Retry.COUNTER;
import static cowj.Retry.RANDOM;
import static cowj.Retry.EXP;


import static org.junit.Assert.assertThrows;

public class RetryTest {

    Function<Integer,Integer> failUpToNTry(int n){
        return new Function<>() {
            int counter = 0;
            @Override
            public Integer apply(Integer integer) {
                if (counter < n) {
                    counter++;
                    throw new RuntimeException("Should Fail!");
                }
                return integer;
            }
        };
    }

    List<Long> delays(List<Retry.Failure> failures){
        long prev = failures.get(0).when();
        List<Long> delays = new ArrayList<>();
        for ( int i=1; i < failures.size(); i++){
            long d = (failures.get(i).when() - prev)/1000_000L;
            prev = failures.get(i).when();
            delays.add(d);
        }
        return delays;
    }

    @Test
    public void counterStrategyTest(){
        Retry retry = Retry.fromConfig(Map.of(STRATEGY, COUNTER, MAX, 2, INTERVAL, 100 ));
        Function<Integer,Integer> funcPass = retry.withRetry(failUpToNTry(2));
        Assert.assertEquals(42, funcPass.apply(42).intValue());
        Function<Integer,Integer> funcFail  = retry.withRetry(failUpToNTry(10));
        Retry.MaximumRetryExceededException exception = assertThrows(Retry.MaximumRetryExceededException.class, () -> {
            funcFail.apply(42);
        });
        Assert.assertNotNull(exception);
        Assert.assertEquals(3, retry.tries());
        Assert.assertEquals(3, exception.failures.size());
        List<Long> delays = delays(exception.failures);
        long offset = 10L;
        delays.forEach( d -> Assert.assertTrue( Math.abs( d - 100) <= offset ));
    }

    @Test
    public void randomStrategyTest(){
        Retry retry = Retry.fromConfig(Map.of(STRATEGY, RANDOM, MAX, 2, INTERVAL, 100 ));
        Function<Integer,Integer> funcPass = retry.withRetry(failUpToNTry(2));
        Assert.assertEquals(42, funcPass.apply(42).intValue());
        Function<Integer,Integer> funcFail  = retry.withRetry(failUpToNTry(10));
        Retry.MaximumRetryExceededException exception = assertThrows(Retry.MaximumRetryExceededException.class, () -> {
            funcFail.apply(42);
        });
        Assert.assertNotNull(exception);
        Assert.assertEquals(3, retry.tries());
        Assert.assertEquals(3, exception.failures.size());
        List<Long> delays = delays(exception.failures);
        delays.forEach( d -> Assert.assertTrue( d > 50 && d < 200 ) );
    }

    @Test
    public void exponentialBackOffTest(){
        Retry retry = Retry.fromConfig(Map.of(STRATEGY, EXP, MAX, 3, INTERVAL, 50 ));
        Function<Integer,Integer> funcPass = retry.withRetry(failUpToNTry(3));
        Assert.assertEquals(42, funcPass.apply(42).intValue());
        Function<Integer,Integer> funcFail  = retry.withRetry(failUpToNTry(10));
        Retry.MaximumRetryExceededException exception = assertThrows(Retry.MaximumRetryExceededException.class, () -> {
            funcFail.apply(42);
        });
        Assert.assertNotNull(exception);
        Assert.assertEquals(4, retry.tries());
        Assert.assertEquals(4, exception.failures.size());
    }

    @Test
    public void nopTest(){
        Assert.assertEquals( 0, Retry.NOP.tries());
        Assert.assertEquals( Long.MAX_VALUE, Retry.NOP.interval());
        Assert.assertFalse(Retry.NOP.can());
        Retry.NOP.numTries(-1000); // should be no op..
        Retry retry = Retry.fromConfig(Map.of(STRATEGY, "foobar", MAX, 2, INTERVAL, 100 ));
        Assert.assertEquals( Retry.NOP, retry);
        Assert.assertEquals( Retry.NOP, Retry.fromConfig(Collections.emptyMap()));
        Function<Integer,Integer> original = failUpToNTry(2);
        Function<Integer,Integer> funcPass = Retry.NOP.withRetry(original);
        Assert.assertEquals(original, funcPass);
    }

    @Test
    public void serializationTest(){
        Retry retry = new Retry.CounterStrategy(2, 99){};
        Function<Integer,Integer> funcFail  = retry.withRetry(failUpToNTry(10));
        Retry.MaximumRetryExceededException exception = assertThrows(Retry.MaximumRetryExceededException.class, () -> {
            funcFail.apply(42);
        });
        String ser = exception.getMessage();
        Assert.assertTrue(ser.contains("failures"));
        Assert.assertTrue(ser.contains("retry"));
        Assert.assertTrue(ser.contains("numTries"));
        Assert.assertTrue(ser.contains("RetryTest$"));
    }

    @Test
    public void interruptedTest() throws Exception{
        Retry retry = Retry.fromConfig(Map.of(STRATEGY, COUNTER, MAX, 2, INTERVAL, 2000 ));
        Function<Integer,Integer> funcFail  = retry.withRetry(failUpToNTry(10));
        Runnable r = () -> funcFail.apply(42);
        final Throwable[] tha = { null };
        Thread.UncaughtExceptionHandler h = (th, ex) -> tha[0] = ex ;
        Thread t = new Thread(r);
        t.setUncaughtExceptionHandler(h);
        t.start();
        while(t.isAlive()){
            Thread.sleep(100);
            t.interrupt();
        }
        Assert.assertNotNull(tha[0]);
        Assert.assertTrue(tha[0] instanceof RuntimeException);
        Assert.assertTrue(tha[0].getCause() instanceof TimeoutException);
    }
}
