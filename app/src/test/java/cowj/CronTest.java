package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.quartz.*;

import javax.script.Bindings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cowj.CronModel.SCHEDULER;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CronTest {

    private final List<EitherMonad<Object>> results = Collections.synchronizedList( new ArrayList<>());

    private final JobListener jobListener = new JobListener() {
        @Override
        public String getName() {
            return "cowj.test";
        }
        @Override
        public void jobToBeExecuted(JobExecutionContext context) {}

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {}

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
            if ( jobException != null ){
                results.add( EitherMonad.error(jobException));
            } else {
                results.add( EitherMonad.value( context.getResult() ));
            }
        }
    };

    @After
    public void cleanUp(){
        CronModel.stop();
        results.clear();
    }

    @Test
    public void noCronConfigTest(){
        CronModel model = CronModel.fromConfig( () -> ".", Collections.emptyMap() );
        Assert.assertEquals( CronModel.NULL, model);
        Assert.assertNull( model.scheduler() );
    }

    @Test
    public void runTest() throws Exception {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/auth_demo/fetch.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        CronModel.schedule(cronModel);
        cronModel.scheduler().getListenerManager().addJobListener(jobListener);
        Thread.sleep(15000);
        CronModel.stop();
        Assert.assertFalse( results.isEmpty() );
        Assert.assertTrue( results.get(0).isSuccessful() );
        Assert.assertEquals("fetch done!", results.get(0).value() );
    }

    @Test
    public void normalErrorTest() throws Exception {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        CronModel.schedule(cronModel);
        cronModel.scheduler().getListenerManager().addJobListener(jobListener);
        Thread.sleep(15000);
        CronModel.stop();
        Assert.assertFalse( results.isEmpty() );
        Assert.assertTrue( results.get(0).inError() );
        JobExecutionException je = (JobExecutionException)results.get(0).error();
        Assert.assertTrue(je.getCause() instanceof Scriptable.TestAsserter.HaltException);
    }

    @Test
    public void errorAtBootTest(){
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, true,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            CronModel.schedule(cronModel);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getCause().getCause() instanceof Scriptable.TestAsserter.HaltException );
    }

    @Test
    public void errorAtBootTestWithMultipleTries(){
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, true,
                CronModel.Task.RETRY, Map.of("strategy", "counter", "max" ,  3 , "interval", 100 ),
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            CronModel.schedule(cronModel);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getCause().getCause() instanceof Retry.MaximumRetryExceededException );
    }

    public static class Dummy implements  Scriptable{
        static int counter = 0;
        public Dummy(){}
        @Override
        public Object exec(Bindings bindings) throws Exception {
            if ( counter <= 0 ){
                counter ++;
                throw new Exception("Boom!");
            }
            return counter;
        }
    }

    @Test
    public void successAtBootTestWithMultipleTries(){
        final String execClass = Dummy.class.getName() + ".class" ;
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, execClass,
                CronModel.Task.BOOT, true,
                CronModel.Task.RETRY, Map.of("strategy", "counter", "max" ,  2 , "interval", 100 ),
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar-success", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        CronModel.schedule(cronModel);
        Assert.assertEquals(1, Dummy.counter );
    }

    @Test
    public void setScheduleErrorTest() throws SchedulerException {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Map<String,Object>> cron = Map.of("bar", cronJob);
        final CronModel cronModel = CronModel.fromConfig( model, cron);
        final Scheduler scheduler = mock(Scheduler.class);
        when( scheduler.scheduleJob(any(), any())).thenThrow( new SchedulerException("bar") );
        final CronModel wrapped = new CronModel() {
            @Override
            public Scheduler scheduler() {
                return scheduler;
            }
            @Override
            public Map<String, Task> tasks() {
                return cronModel.tasks();
            }
        };
        Exception exception = assertThrows(RuntimeException.class, () -> {
            CronModel.schedule(wrapped);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getCause().getCause() instanceof SchedulerException );
    }

    @Test
    public void noErrorRaiseOnStopTest(){
        DataSource.unregisterDataSource(SCHEDULER);
        CronModel.stop();
    }
}
