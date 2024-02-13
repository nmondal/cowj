package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Scriptable;
import cowj.StorageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * A Minimal RAMA implementation - See RAMA.md in the manual section of the Git repo
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
    default EitherMonad<Boolean> put(String topic, String data){
        long curTime = System.currentTimeMillis();
        // More than good enough to be honest... for any scale > 50 calls per msec
        String randomSuffix = System.nanoTime() + "." + System.nanoTime() ;
        String fileName = directoryPrefix(curTime) + "/" + curTime + "_" + randomSuffix ;
        StorageWrapper<?,?,?> st = prefixedStorage();
        final boolean ret = st.safeBoolean( () -> st.dumps( topic, fileName, data)) ;
        return EitherMonad.value(ret);
    }

    /**
     * A Response structure for the message queue get
     */
    class Response{
        /**
         * List of data Strings, each string is string rep of the data object
         */
        public final List<String> data;

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
        Response(List<String> data, long offset){
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
            Stream<String> stream = st.allContent( topic, timePrefix );
            Iterator<String> skippedIterator = stream.skip( offsetToReadFrom ).iterator();
            long c = 0;
            List<String> data = new ArrayList<>();
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
     * Storage data source to use
     */
    String STORAGE = "storage" ;

    /**
     * A Creator for RAMA
     */
    DataSource.Creator RAMA = (name, config, parent) -> {
        final String storageName = config.getOrDefault(STORAGE, "").toString();
        logger.info("RAMA {} storage name specified : {}", name, storageName );
        if ( storageName.isEmpty() ) throw new IllegalArgumentException("RAMA Storage must not be empty!");
        final Object storage = Scriptable.DATA_SOURCES.get( storageName);
        if ( !(storage instanceof StorageWrapper<?,?,?>)) {
            throw new IllegalArgumentException("RAMA Storage is wrongly specified - Must be a  StorageWrapper!");
        }
        logger.info("RAMA {} storage type is found to be : {}", name, storage );

        JvmRAMA jvmRAMA = () -> (StorageWrapper<?,?,?>)storage;
        return DataSource.dataSource(name, jvmRAMA);
    };
}
