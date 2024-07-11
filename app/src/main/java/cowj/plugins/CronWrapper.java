package cowj.plugins;

import cowj.*;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.quartz.JobBuilder.newJob;

/**
 * Abstraction for Runtime Cron Wrapper for a Cowj App
 */
public interface CronWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(CronWrapper.class);

    /**
     * Constant to pass name of the underlying storage data source which will be used to fetch the script
     */
    String STORAGE = "storage" ;

    /**
     * Constant to pass bucket name of the underlying storage data source which will be used to fetch the script
     */
    String BUCKET = "bucket" ;

    /**
     * Constant to pass path under the bucket of the underlying storage data source
     * which will be used to fetch the script body
     */
    String PATH = "path" ;

    /**
     * Constant to pass cron expression
     */
    String CRON_EXPR = "cron" ;

    /**
     * Creates and schedules a job using the wrapper
     *  ( storage : underlying storage which hosts the script body ,
     *  bucket, path : inside the bucket which path, cron : expression )
     * @param jobPayload payload map as described above
     * @return EitherMonad of String depicting the created job id
     */
    default EitherMonad<String> create(Map<String,String> jobPayload) {
        return EitherMonad.call( () -> {
            final String requestId = System.currentTimeMillis() + "." + System.nanoTime() ;
            final String storage = jobPayload.getOrDefault(STORAGE, "");
            StorageWrapper<?,?,?> sw = DataSource.dataSource(storage);
            final String bucket = jobPayload.getOrDefault(BUCKET, "");
            final String path = jobPayload.getOrDefault(PATH, "");
            final String cronString = jobPayload.getOrDefault(CRON_EXPR, "");
            final String scriptData = sw.loads( bucket, path);

            File tempFile = File.createTempFile(requestId, path);
            // ensure they are not permanent
            tempFile.deleteOnExit();
            Files.writeString( tempFile.toPath(), scriptData);
            final String scriptPath = tempFile.getAbsolutePath();
            JobDataMap jobDataMap  = new JobDataMap();
            jobDataMap.putAll(jobPayload);
            jobDataMap.put("id", requestId);

            final JobDetail jobDetail = newJob(CronModel.Task.CronJob.class).withIdentity(requestId)
                    .usingJobData(jobDataMap)
                    .usingJobData(CronModel.Task.EXEC, scriptPath)
                    .build();
            final Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(requestId)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronString))
                    .forJob(requestId).build();
            scheduler().scheduleJob( jobDetail, trigger) ;
            return requestId;
        });
    }

    /**
     * Returns a list of jobs scheduled and running in the system
     * Has all the elements from input payload which was used to create this job,
     * Additionally has
     *  id : job id
     *  exec : actual temp file path which gets used to execute the job
     * @return List of Map described above
     */
    default EitherMonad< List<Map<String,String>> > list() {
        return EitherMonad.call(() -> {
            List<Map<String,String>> jobs = new ArrayList<>();
            Scheduler scheduler = scheduler();
            for (String groupName : scheduler.getJobGroupNames()) {

                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                     Map<String,String> payload = new HashMap<>();
                     jobDetail.getJobDataMap().forEach( (k,v) -> payload.put(k, v.toString()));
                     jobs.add(payload);
                }
            }
            return jobs;
        });
    }

    /**
     * Underlying Scheduler
     */
    Scheduler scheduler();

    /**
     * Key for number of threads to be used for the schedular
     */
    String NUM_THREADS = "threads" ;

    /**
     * A creator for the CronWrapper
     */
    DataSource.Creator CRON = (name, config, parent) -> {
        final int numThreads = (ZNumber.integer(config.getOrDefault( NUM_THREADS, 5),5)).intValue();
        logger.info( "[{}] num threads {}", name, numThreads );
        final String schedularName = "prog_" + name ;
        logger.info( "[{}] schedular name [{}]", name, schedularName );
        final Scheduler scheduler = CronModel.schedulerByName( schedularName, numThreads );
        EitherMonad.runUnsafe( () -> { scheduler.start() ; return true ; } );
        logger.info( "[{}] schedular started...", name );
        final CronWrapper cronWrapper = () -> scheduler ;
        logger.info( "[{}] CronWrapper created successfully!", name );
        return DataSource.dataSource(name, cronWrapper);
    };
}
