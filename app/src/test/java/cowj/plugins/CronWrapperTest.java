package cowj.plugins;

import cowj.*;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.quartz.SchedulerException;
import zoomba.lang.core.io.ZWeb;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CronWrapperTest {

    final static String model = "samples/prog_cron/prog_cron.yaml" ;

    static ModelRunner mr;
    static CronWrapper cronWrapper;
    static String jobId;

    public static EitherMonad<String> put(String base, String path, String body ){
        return EitherMonad.call( () -> {
            ZWeb zWeb = new ZWeb(base);
            ZWeb.ZWebCom r = zWeb.send("put", path, Collections.emptyMap(), body);
            return r.body();
        });
    }

    @BeforeClass
    public static void beforeClass() throws SchedulerException {
        mr = ModelRunnerTest.runModel(model);
        cronWrapper = DataSource.dataSource( "my-prog-cron");
        Assert.assertNotNull(cronWrapper);
        Assert.assertTrue(cronWrapper.scheduler().isStarted());
    }

    @AfterClass
    public static void afterClass() throws SchedulerException {
        cronWrapper.scheduler().shutdown(true);
        mr.stop();
    }

    @Test
    public void createAndScheduleTest() throws Exception {
        Scriptable.SHARED_MEMORY.remove( "prog_cron");
        Map<String,String> payload = Map.of(  "storage" , "local-storage", "bucket" , "crons" , "path" , "hello.zm", "cron" , "0/5 * * * * ? *" );
        EitherMonad<String> resp = put( "http://localhost:5050", "/job", ZTypes.jsonString(payload));
        Assert.assertTrue(resp.isSuccessful());
        Thread.sleep(8000);
        Assert.assertTrue(Scriptable.SHARED_MEMORY.containsKey( "prog_cron") );
        Map m = (Map)ZTypes.json( resp.value() );
        Assert.assertTrue(m.containsKey("id") );
        jobId = m.get("id").toString();
    }

    @Test
    public void jobListingTest(){
        org.junit.Assume.assumeTrue( "This test needs running createAndScheduleTest() first in that order", Scriptable.SHARED_MEMORY.containsKey( "prog_cron"));
        String resp = ModelRunnerTest.get( "http://localhost:5050", "/job" );
        Object r = ZTypes.json(resp);
        Assert.assertTrue(r instanceof List);
        Assert.assertEquals(1, ((List<?>) r).size());
        Object j = ((List<?>) r).get(0);
        Assert.assertTrue(j instanceof Map);
        String respJobId = ((Map<?, ?>) j).get("id").toString();
        Assert.assertEquals(jobId, respJobId);
    }
}