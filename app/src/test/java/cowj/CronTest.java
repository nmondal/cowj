package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Ignore
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
}
