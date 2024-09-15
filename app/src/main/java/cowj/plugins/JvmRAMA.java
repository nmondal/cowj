package cowj.plugins;

import cowj.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZDate;
import zoomba.lang.core.types.ZRange;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.quartz.JobBuilder.newJob;

/**
 * A Minimal RAMA implementation - See RAMA.md in the manual section of the Git repo
 * <a href="https://github.com/nmondal/cowj/blob/main/manual/RAMA.md">...</a>
 * Essentially a throwaway poor mens Kafka substitute running over Cloud Storage
 */
public interface JvmRAMA {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(JvmRAMA.class);

    /**
     * A Prefixed Storage - StorageWrapper
     * @return a StorageWrapper
     */
    StorageWrapper<?,?,?> prefixedStorage();

    /**
     * Micro Batching - this is the format of the prefix in the cloud storage
     */
    String TIME_BUCKET_FORMAT = "yyyy/MM/dd/HH/mm/ss";

    /**
     * Given a timestamp produces a directory prefix
     * @param timeStamp in milli sec
     * @return a prefix to be used by the storage
     */
    default String directoryPrefix(long timeStamp){
        SimpleDateFormat sdf = new SimpleDateFormat( TIME_BUCKET_FORMAT );
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date dt = new Date(timeStamp);
        return sdf.format(dt);
    }


    /**
     * Poor mans Kafka publish
     * @param topic to the topic, it is a bucket essentially
     * @param data the data we need to dump as event or whatever
     * @return EitherMonad for success or failure
     */
    default EitherMonad<EitherMonad.Nothing> put(String topic, String data){
        long curTime = System.currentTimeMillis();
        // More than good enough to be honest... for any scale > 50 calls per msec
        String randomSuffix = suffix() ;
        String fileName = directoryPrefix(curTime) + "/" + curTime + "_" + randomSuffix ;
        logger.debug("{} - {}", topic, fileName);
        StorageWrapper<?,?,?> st = prefixedStorage();
        return EitherMonad.run( () -> {
            st.dumps( topic, fileName, data);
        } );
    }

    /**
     * A suffix that guarantees the following:
     * 1. Multiple caller would have different suffix
     * 2. Universally unique
     * @return a suffix to be used as suffix of the key
     */
    String suffix();

    /**
     * A Response structure for the message queue get
     */
    class Response{
        /**
         * List of pair, (key, data String) , each data string is string rep of the data object
         */
        public final List<Map.Entry<String,String>> data;

        /**
         * Till how much it read in the current get call
         * Greater than 0 implies we have more data for same prefix
         * Less than or equal to zero implies all data has been read
         */
        public final long readOffset;

        /**
         * Does the prefix has more data to read? Then true, else false
         */
        public final boolean hasMoreData;
        Response(List<Map.Entry<String,String>> data, long offset){
            this.data = data;
            this.readOffset = offset;
            this.hasMoreData = this.readOffset > 0;
        }
    }

    /**
     * Poor Mans Kafka subscribe and get
     * @param topic essentially the bucket from which to read
     * @param timePrefix this is where things become interesting
     *                   Due to the structure we can have yearly, monthly, day, hour, min, sec wise precision
     *                   Examples:
     *                   2024 will gather all 2024 events
     *                   2024/01/ will gather all January 2024 events
     *                   2024/01/23/21/00/ will gather all 23rd January 2024 events
     *                   which happened in exactly 9PM 00 Min
     *
     * @param numRecordsToRead how many records to read
     * @param offsetToReadFrom number of records to skip, useless to send less than or equal 0 here
     * @return a Response type object encoding the read state
     */
    default EitherMonad<Response> get(String topic, String timePrefix,
                                      long numRecordsToRead, long offsetToReadFrom ){
        StorageWrapper<?,?,?> st = prefixedStorage();
        try {
            Stream<Map.Entry<String,String>> stream = st.entries( topic, timePrefix );
            Iterator<Map.Entry<String,String>> skippedIterator = stream.skip( offsetToReadFrom ).iterator();
            long c = 0;
            List<Map.Entry<String,String>> data = new ArrayList<>();
            while ( c < numRecordsToRead && skippedIterator.hasNext() ){
                data.add(skippedIterator.next());
                c++;
            }
            final Response response;
            if ( skippedIterator.hasNext() ){
                response = new Response( data, offsetToReadFrom + numRecordsToRead );
            } else {
                response = new Response( data, -1 );
            }
            return EitherMonad.value(response);
        }catch (Throwable t){
            return EitherMonad.error(t);
        }
    }

    /**
     * Consumes an entire Prefix of a topic
     * @param topic the topic to consume
     * @param prefix the prefix to consume
     * @param pageSize size of the page to read each time
     * @param consumer a BiConsumer, which consumes, first arg is the topic itself, second is a Map.Entry of key, and value
     * @return EitherMonad of Nothing, if successful with no error
     */
    default EitherMonad<EitherMonad.Nothing> consumePrefix(String topic, String prefix, long pageSize, BiConsumer<String, Map.Entry<String,String>> consumer){
        long offset = 0;
        boolean hasMoreData = true;
        long total = 0;
        while ( hasMoreData ){
            EitherMonad<Response> em = get(topic, prefix, pageSize, offset);
            if ( em.inError() ) return EitherMonad.error(em.error());
            final Response response = em.value();
            hasMoreData = response.hasMoreData;
            EitherMonad<EitherMonad.Nothing> consume = EitherMonad.run( () -> {
                response.data.forEach( (s) -> consumer.accept(topic,s) );
            });
            if ( consume.inError() ) return consume;
            offset = response.readOffset ;
            total += response.data.size();
        }
        logger.info("Topic '{}' , Prefix '{}', Total: {} event processed", topic, prefix, total );
        return EitherMonad.value(EitherMonad.Nothing.SUCCESS);
    }

    /**
     * Consumes an entire Prefix of a topic
     * @param topic the topic to consume
     * @param prefix the prefix to consume
     * @param pageSize size of the page to read each time
     * @param scriptable working as a BiConsumer, which consumes,
     *                   'event' arg is the topic itself,
     *                  'body' is a Map.Entry of key, and value
     * @return EitherMonad of boolean, if successful with no error
     */

    default EitherMonad<EitherMonad.Nothing> consumePrefix(String topic, String prefix, long pageSize, Scriptable scriptable){
        return consumePrefix( topic, prefix, pageSize, (eventClass, event) -> {
            Bindings bindings = new SimpleBindings();
            bindings.put("event", eventClass );
            bindings.put("body", event );
            try {
                scriptable.exec(bindings);
            } catch (Throwable e) {
                final String msg = String.format("Event %s : %s Failed!", eventClass, event);
                logger.error(msg, e );
                throw Function.runTimeException(e);
            }
        } );
    }

    /**
     * key for Storage data source to use
     */
    String STORAGE = "storage" ;

    /**
     * Key for Unique Identifier for per node processor
     * This would be used to suffix to key to avoid key collision at scale
     * Default is "-"
     */
    String NODE_UNIQUE_IDENTIFIER = "uuid" ;

    /**
     * A RAMA Cron Job to Auto consume Events
     */
    final class RAMAConsumerJob implements Job {

        private static Scheduler scheduler;

        /**
         * Stops the underlying Job Scheduler,
         * Waits for all jobs to terminate/end first
         */
        public static void stop(){
            EitherMonad.call( () -> {
                scheduler.clear();
                scheduler.shutdown(true);
                return 0;
            });
        }

        private static final Map<String,RAMAConsumerJobPayload> payloadMap = new ConcurrentHashMap<>();

        private static boolean attachListenerHandler(JvmRAMA rama, Map<String,Object> topicConfig, Model model) throws SchedulerException {
            if ( topicConfig.isEmpty() ) return false;
            logger.info("RAMA topic config found will now attach listeners!");
            // each topic gets one thread for sure
            scheduler = CronModel.schedulerByName( JvmRAMA.RAMAConsumerJob.class.getName() + ".cron", topicConfig.size());
            for ( Map.Entry<String,Object> entry : topicConfig.entrySet() ){
                RAMAConsumerJobPayload jobPayload = new RAMAConsumerJobPayload( entry.getKey(), (Map<String, Object>) entry.getValue(), rama, model);
                payloadMap.put(jobPayload.name, jobPayload);
                if ( jobPayload.create ){ // should create bucket...
                    logger.info("{} : Create is set to true, will create bucket!", jobPayload.topic);
                    rama.prefixedStorage().createBucket( jobPayload.topic, "", true);
                }
                final JobDetail jobDetail = newJob( RAMAConsumerJob.class).withIdentity(jobPayload.name).build();
                final Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(jobPayload.name)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobPayload.at))
                        .forJob(jobPayload.name).build();
                scheduler.scheduleJob( jobDetail, trigger );
            }
            scheduler.start();
            return true;
        }

        final static class  RAMAConsumerJobPayload{
            public static final String AT = "at" ;
            public static final String CREATE = "create" ;

            public static final String PREFIX = "prefix" ;
            public static final String OFFSET = "offset" ;
            public static final String STEP = "step" ;
            public static final String PAGE_SIZE = "page-size" ;
            public static final String CONSUMERS = "consumers" ;
            final List<Scriptable> handlers;
            final Duration offset;
            final Duration step;
            final SimpleDateFormat df;
            final long pageSize ;
            final String topic;
            final String at;

            final String name;

            final JvmRAMA rama ;

            final boolean create;

            private RAMAConsumerJobPayload(String topic, Map<String,Object> consumerConfig, JvmRAMA rama, Model model){
                this.topic = topic ;
                create = (Boolean) consumerConfig.getOrDefault(CREATE,  false);
                at = consumerConfig.getOrDefault(AT,  "0 0 5 31 2 ?").toString();
                offset = Duration.parse( consumerConfig.getOrDefault(OFFSET, "PT-1m" ).toString());
                step =  Duration.parse(consumerConfig.getOrDefault(STEP, "PT-1m" ).toString());
                String prefix = consumerConfig.getOrDefault(PREFIX,  "yyyy/MM/dd/HH/mm").toString();
                df = new SimpleDateFormat(prefix);
                pageSize = ((Number)consumerConfig.getOrDefault(PAGE_SIZE,  100L)).longValue();
                this.rama = rama ;
                name = "rama:event:" + topic ;
                final List<String> handlers = (List)consumerConfig.getOrDefault(CONSUMERS, Collections.emptyList());
                this.handlers = handlers.stream().map( s-> {
                    final String handler = model.interpretPath(s);
                    logger.info("RAMA {} handler set to -> {}", topic, handler);
                    return Scriptable.UNIVERSAL.create(name, handler);
                }).toList();

            }
        }


        @Override
        public void execute(JobExecutionContext context) {
            JobDetail jobDetail = context.getJobDetail();
            String jobName = jobDetail.getKey().getName() ;
            RAMAConsumerJobPayload jobPayload = payloadMap.get(jobName);
            logger.info("Starting RAMA listener job '{}'", jobPayload.name );
            ZDate zDate = new ZDate().at("UTC");
            ZDate begin = (ZDate) zDate._add_( jobPayload.offset );
            ZRange.DateRange dateRange = new ZRange.DateRange( begin, zDate, jobPayload.step );
            while ( dateRange.hasNext() ){
                final Date item = (Date)dateRange.next();
                String datePrefix = jobPayload.df.format( item );
                logger.info("consumer reading topic: '{}' with prefix '{}'", jobPayload.topic, datePrefix );
                jobPayload.handlers.stream().parallel().forEach( handler -> {
                   EitherMonad<?> em = jobPayload.rama.consumePrefix( jobPayload.topic, datePrefix, jobPayload.pageSize, handler);
                   if ( em.inError() ){
                       final String err = String.format("From %s Error reading!!!", jobPayload.name);
                       logger.error(err , em.error() );
                   }
                });
            }
            logger.info("Ending RAMA listener job '{}'", jobPayload.name );
        }
    }

    /**
     * A Creator for RAMA
     */
    DataSource.Creator RAMA = (name, config, parent) -> {
        final String storageName = config.getOrDefault(STORAGE, "").toString();
        logger.info("RAMA {} storage name specified : '{}'", name, storageName );
        if ( storageName.isEmpty() ) throw new IllegalArgumentException("RAMA Storage must not be empty!");
        final Object storage = DataSource.dataSource(storageName);
        if ( !(storage instanceof StorageWrapper<?,?,?>)) {
            throw new IllegalArgumentException("RAMA Storage is wrongly specified - Must be a  StorageWrapper!");
        }
        logger.info("RAMA {} storage type is found to be : '{}'", name, storage );
        final String uuid = config.getOrDefault(NODE_UNIQUE_IDENTIFIER, "-").toString();
        logger.info("RAMA {} uuid is set to be : [{}]", name, uuid );

        final JvmRAMA jvmRAMA = new JvmRAMA() {
            @Override
            public StorageWrapper<?, ?, ?> prefixedStorage() {
                return (StorageWrapper<?, ?, ?>)storage;
            }

            @Override
            public String suffix() {
                return uuid + Thread.currentThread().getId() + "." + System.nanoTime() ;
            }
        };
        Map<String,Object> topicConfig = (Map)config.getOrDefault( "topics", Collections.emptyMap());
        EitherMonad.runUnsafe( () -> RAMAConsumerJob.attachListenerHandler( jvmRAMA, topicConfig, parent) );
        return DataSource.dataSource(name, jvmRAMA);
    };
}
