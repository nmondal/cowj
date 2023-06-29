package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.quartz.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CronTest {

    private final List<JobExecutionException> exceptions = Collections.synchronizedList( new ArrayList<>(4));

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
            exceptions.add(jobException);
        }
    };

    @After
    public void cleanUp(){
        exceptions.clear();
    }

    @Test
    public void errorTest() throws Exception {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Object> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        CronModel.schedule(cronModel);
        CronModel.scheduler().getListenerManager().addJobListener(jobListener);
        Thread.sleep(15000);
        CronModel.stop();
        Assert.assertFalse( exceptions.isEmpty() );
        JobExecutionException je = exceptions.get(0);
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
        Map<String,Object> cron = Map.of("bar", cronJob);
        CronModel cronModel = CronModel.fromConfig( model, cron);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            CronModel.schedule(cronModel);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getCause().getCause() instanceof Scriptable.TestAsserter.HaltException );
    }

    @Test
    public void getSchedulerErrorTest() throws SchedulerException {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Object> cron = Map.of("bar", cronJob);
        final CronModel cronModel = CronModel.fromConfig( model, cron);
        final SchedulerFactory schedulerFactory = mock(SchedulerFactory.class);
        when( schedulerFactory.getScheduler()).thenThrow( new SchedulerException("bar") );
        final CronModel wrapped = new CronModel() {
            @Override
            public SchedulerFactory factory() {
                return schedulerFactory;
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
        Assert.assertTrue(exception.getCause() instanceof SchedulerException );
    }

    @Test
    public void setScheduleErrorTest() throws SchedulerException {
        Map<String,Object> cronJob = Map.of(
                CronModel.Task.EXEC, "samples/test_scripts/error_1_arg.zm",
                CronModel.Task.BOOT, false,
                CronModel.Task.SCHEDULE, "0/8 * * * * ? *"
        );
        Model model = () -> "." ;
        Map<String,Object> cron = Map.of("bar", cronJob);
        final CronModel cronModel = CronModel.fromConfig( model, cron);
        final SchedulerFactory schedulerFactory = mock(SchedulerFactory.class);
        final Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.scheduleJob(any(), any())).thenThrow( new SchedulerException("bar") );
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        final CronModel wrapped = new CronModel() {
            @Override
            public SchedulerFactory factory() {
                return schedulerFactory;
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

}
