package cowj;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import zoomba.lang.core.types.ZTypes;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.quartz.JobBuilder.newJob;

public interface CronModel {

    interface Task {

        String BOOT = "boot";

        String EXEC = "exec";

        String SCHEDULE = "at";

        String JOB_EXEC_CONTEXT = "_ctx";

        final class CronJob implements Job {

            @Override
            public void execute(JobExecutionContext context) throws JobExecutionException {
                JobDetail jobDetail = context.getJobDetail();
                JobDataMap map = context.getJobDetail().getJobDataMap();
                final String scriptFile = map.get(EXEC).toString();
                Scriptable scriptable = Scriptable.UNIVERSAL.create("cron:" + jobDetail.getKey().getName(), scriptFile);
                try {
                    Bindings b = new SimpleBindings();
                    b.put(JOB_EXEC_CONTEXT, context);
                    scriptable.exec(b);
                } catch (Throwable t) {
                    throw new JobExecutionException(t);
                }
            }
        }

        String name();

        boolean boot();

        JobDetail jobDetail();

        Trigger trigger();

        Scriptable scriptable();

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
            };
        }
    }

    Map<String, Task> tasks();

    String SCHEDULER = "_sched";

    SchedulerFactory SCHEDULER_FACTORY = new StdSchedulerFactory();

    static CronModel fromConfig(Model model, Map<String, Map<String,Object>> config) {
        final Map<String, Task> tasks = new LinkedHashMap<>();
        config.forEach((name, conf) -> {
            tasks.put(name, Task.fromConfig(model, name, (Map) conf));
        });
        return () -> tasks;
    }

    default SchedulerFactory factory(){
        return SCHEDULER_FACTORY ;
    }

    static void schedule(CronModel cronModel) {
        // do not bother if empty
        if (cronModel.tasks().isEmpty()) return;
        // only loaded, then...
        SchedulerFactory schedulerFactory = cronModel.factory();
        try {
            final Scheduler scheduler = schedulerFactory.getScheduler();
            // now the rest of the problem...
            System.out.println("Cron Jobs are...");
            cronModel.tasks().forEach((name, task) -> {
                System.out.printf("%s --> %s %n", task.name(), task.trigger());
                if ( task.boot() ){ // run immediately...
                    try {
                        System.out.println("Running immediate ... " + name);
                        task.scriptable().exec( new SimpleBindings());
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
            Scriptable.DATA_SOURCES.put(SCHEDULER, scheduler);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static Scheduler scheduler(){
        return (Scheduler)Scriptable.DATA_SOURCES.get(SCHEDULER);
    }

    static void stop(){
        try {
            Scheduler scheduler = scheduler();
            if ( scheduler == null ) return;
            scheduler.clear();
            scheduler.shutdown(true);
        }catch (Exception ignore){}
    }
}
