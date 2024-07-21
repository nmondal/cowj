package cowj;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZTypes;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.quartz.JobBuilder.newJob;

/**
 * Integrates Quartz Scheduler into Cowj
 * @see <a href="http://www.quartz-scheduler.org/documentation/quartz-2.3.0/quick-start.html#starting-a-sample-application">Quartz Scheduler</a>
 */
public interface CronModel {

    /**
     * Logger for the Cron
     */
    Logger logger = LoggerFactory.getLogger(CronModel.class);

    /**
     * A Cowj Task
     */
    interface Task {

        /**
         * Name of the boot key
         * If true, task gets executed in system startup
         */
        String BOOT = "boot";

        /**
         * Name of the retry key
         * If exists, if the task is running on system startup, failure will be automatically retried
         * If not, it would be still wrapped around one Retry which will not retry
         * See more from @{Retry}
         */
        String RETRY = "retries";

        /**
         * Name of the exec key
         * This points to the executable script - which is the cron job
         */
        String EXEC = "exec";

        /**
         * Name of the cron expression key
         * This points to the cron expression
         * @see <a href="https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm">Oracle : Cron Expression</a>
         * @see <a href="http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html">Quartz: Cron Expression</a>
         */
        String SCHEDULE = "at";

        /**
         * Scriptable Context key for the JobContext
         */
        String JOB_EXEC_CONTEXT = "_ctx";

        /**
         * Concrete Job Class for the Scriptable Support
         * @see <a href="https://www.javadoc.io/doc/org.quartz-scheduler/quartz/2.3.0/org/quartz/Job.html">Job</a>
         */
        final class CronJob implements Job {

            @Override
            public void execute(JobExecutionContext context) throws JobExecutionException {
                JobDetail jobDetail = context.getJobDetail();
                JobDataMap map = context.getJobDetail().getJobDataMap();
                final String scriptFile = map.get(EXEC).toString();
                final String jobName = jobDetail.getKey().getName();
                Scriptable scriptable = Scriptable.UNIVERSAL.create("cron:" + jobName, scriptFile);
                logger.info("Starting job '{}' ==> {}", jobName, scriptFile);
                try {
                    Bindings b = new SimpleBindings();
                    b.put(JOB_EXEC_CONTEXT, context);
                    Object result = scriptable.exec(b);
                    context.setResult(result); // set the result
                    logger.info("Completed job '{}' ==> {}", jobName, scriptFile);
                } catch (Throwable t) {
                    logger.error("Error in job '{}' ==> {} : {}", jobName, scriptFile, t.toString());
                    throw new JobExecutionException(t);
                }
            }
        }

        /**
         * Name of the Task
         * @return name of the Task
         */
        String name();

        /**
         * Should the task be run at boot time for Cowj ?
         * @return true if task needs to run on boot also, false if no
         */
        boolean boot();

        /**
         * Details for the Job
         * @see <a href="https://www.quartz-scheduler.org/api/2.3.0/org/quartz/JobDetail.html">JobDetail</a>
         * @return a JobDetail object
         */
        JobDetail jobDetail();

        /**
         * Trigger for the Job
         * @see <a href="https://www.javadoc.io/doc/org.quartz-scheduler/quartz/2.3.0/org/quartz/Trigger.html">Trigger</a>
         * @return a Trigger object
         */
        Trigger trigger();

        /**
         * Underlying Scriptable responsible for the Job
         * @return a Scriptable object
         */
        Scriptable scriptable();

        /**
         * Retry  for the Job
         * @return a Retry object
         */
        Retry retry();

        /**
         * A Function that is decorator with  Retry for the Job
         * @return a java.util.function.Function wrapping scriptable
         */
        default java.util.function.Function<Bindings,Object> withRetry(){
            return retry().withRetry(scriptable());
        }

        /**
         * Creates a task from config
         * @param model Cowj Data Model
         * @param name of the task
         * @param config actual configuration
         * @return a Task
         */
        static Task fromConfig(Model model, String name, Map<String, Object> config) {
            final boolean boot = ZTypes.bool(config.getOrDefault(BOOT, "false"), false);
            final String scriptPath = model.interpretPath(config.getOrDefault(EXEC, "").toString());
            // https://stackoverflow.com/questions/8324306/a-cron-job-that-will-never-execute
            final String cronString = config.getOrDefault(SCHEDULE, "0 0 5 31 2 ?").toString();
            final Scriptable scriptable = Scriptable.UNIVERSAL.create("cron:" + name,  scriptPath);
            final JobDetail jobDetail = newJob(Task.CronJob.class).withIdentity(name)
                    .usingJobData(EXEC, scriptPath).build();
            final Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(name)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronString))
                    .forJob(name).build();
            final Retry retry = Retry.fromConfig( (Map)config.getOrDefault( RETRY, Collections.emptyMap() ));

            return new Task() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public boolean boot() {
                    return boot;
                }

                @Override
                public Scriptable scriptable() {
                    return scriptable;
                }

                @Override
                public JobDetail jobDetail() {
                    return jobDetail;
                }

                @Override
                public Trigger trigger() {
                    return trigger;
                }

                @Override
                public Retry retry() {
                    return retry;
                }
            };
        }
    }

    /**
     * Gets all the Tasks
     * @return a Map of Tasks, keyed by their name
     */
    Map<String, Task> tasks();

    /**
     * Name of the key via which Scheduler is accessible inside any Scriptable
     */
    String SCHEDULER = "_sched";


    /**
     * This is how one can run multiple scheduler in a single JVM
     * Creates  new Scheduler based on the name and the no of threads
     * One can get the scheduler any time post creation via
     * new StdSchedulerFactory().getScheduler(String)
     * @param schedulerName name of the scheduler to be created
     * @param numThreads no of threads it should have,
     *                   if less than 5 threads are given it would clamp it to 5
     * @return a Quartz Scheduler
     */
    static Scheduler schedulerByName(String schedulerName, int numThreads ){
        Map<String,Object> data = Map.of(
                "org.quartz.scheduler.instanceName", schedulerName,
                "org.quartz.scheduler.instanceId", schedulerName.hashCode(),
                "org.quartz.scheduler.rmi.export", false,
                "org.quartz.scheduler.rmi.proxy",  false,
                "org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool",
                "org.quartz.threadPool.threadCount", String.valueOf(Math.max(numThreads, 5)),
                "org.quartz.context.key.QuartzTopic", "QuartzProperties"
        );
        Properties p =  new Properties();
        p.putAll(data);
        return EitherMonad.runUnsafe( () -> new StdSchedulerFactory( p ).getScheduler());
    }

    /**
     * A specific NULL CronModel to ensure that we have a fallback model when there are no cron
     */
    CronModel NULL = new CronModel() {
        @Override
        public Map<String, Task> tasks() {
            return Collections.emptyMap();
        }

        @Override
        public Scheduler scheduler() {
            return null;
        }
    };

    /**
     * Creates a CronModel
     * In case config is empty, returns NULL CronModel
     * @param model the Cowj data model
     * @param config actual configuration
     * @return a CronModel
     */
    static CronModel fromConfig(Model model, Map<String, Map<String,Object>> config) {
        if ( config.isEmpty() ) return NULL ; // this takes care of the fact that no scheduler gets created
        // now that it is not empty, continue working
        final Map<String, Task> tasks = new LinkedHashMap<>();
        config.forEach((name, conf) -> {
            tasks.put(name, Task.fromConfig(model, name, (Map) conf));
        });
        final String schedulerName =  CronModel.class.getName() + ".cron" ;
        final int numThreads = tasks.size();
        final Scheduler scheduler = schedulerByName( schedulerName, numThreads );
        return new CronModel() {
            @Override
            public Map<String, Task> tasks() {
                return tasks;
            }

            @Override
            public Scheduler scheduler() {
                return scheduler;
            }
        };
    }

    /**
     * Schedules the model
     * @param cronModel to be scheduled
     */
    static void schedule(CronModel cronModel) {
        // do not bother if empty
        if (cronModel.tasks().isEmpty()) return;
        try {
            final Scheduler scheduler = cronModel.scheduler();
            // now the rest of the problem...
            logger.info("Cron Jobs are...");
            cronModel.tasks().forEach((name, task) -> {
                logger.info( "{} --> {}", task.name(), task.trigger());
                if ( task.boot() ){ // run immediately...
                    try {
                        logger.info("Running immediate : " + name);
                        try {
                            task.withRetry().apply( new SimpleBindings());
                            logger.info("Successfully ran: " + name);
                        } catch (Throwable t) {
                            logger.error("Error Running Task {} => {}", name, t.toString());
                            logger.error("This will terminate/hang the instance...");
                            throw t;
                        }
                    }catch (Throwable e){
                       throw new RuntimeException(e);
                    }
                }

                try {
                    scheduler.scheduleJob(task.jobDetail(), task.trigger());
                } catch (SchedulerException e) {
                    throw new RuntimeException(e);
                }
            });
            scheduler.start();
            DataSource.registerDataSource(SCHEDULER, scheduler);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Quick Method to get the underlying Scheduler
     * @see <a href="https://www.javadoc.io/doc/org.quartz-scheduler/quartz/2.3.0/org/quartz/Scheduler.html">Scheduler</a>
     * @return Underlying Scheduler
     */
    Scheduler scheduler();

    /**
     * Stops the Cron Jobs
     */
    static void stop(){
        try {
            Scheduler scheduler = DataSource.dataSource(SCHEDULER);
            if ( scheduler == null ) return;
            scheduler.clear();
            scheduler.shutdown(true);
        }catch (Exception ignore){}
    }
}
