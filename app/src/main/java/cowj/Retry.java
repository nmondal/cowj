package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of things which are retry-able
 */
public interface Retry {


    /**
     * Logging for the Retry
     */
    Logger logger = LoggerFactory.getLogger(Retry.class);

    /**
     * Can we retry again?
     * This MUST be immutable function
     * @return true if we can , false if we can not
     */
    boolean can();

    /**
     * This is where one must update the state of the Retry
     * @param currentNumberOfFailures current no of retries to be set by the decorator
     */
    void numTries(int currentNumberOfFailures);

    /**
     * Gets the current retry state
     * -1 : Not initialized
     *  0 : First try
     *  1,2,... : Retry count
     * @return Retry state
     */
    default int tries(){
        return 0;
    }

    /**
     * interval of wait between two successive tries
     * @return interval of time in ms
     */
    long interval();

    /**
     * A retry to ensure no retry
     */
    Retry NOP = new Retry() {
        @Override
        public boolean can() {
            return false;
        }
        @Override
        public void numTries(int currentNumberOfFailures) {
        }
        @Override
        public long interval() {
            return Long.MAX_VALUE;
        }
    } ;


    /**
     * A Failure in Retry
     */
    interface Failure{
        /**
         * Gets the time stamp of the failure time
         * @return time stamp in nano sec
         */
        long when();

        /**
         * Gets the underlying error
         * @return a Throwable error
         */
        Throwable error();

        /**
         * Creates a Failure instance from error
         * @param t underlying Throwable error
         * @return Failure instance
         */
        static Failure failure(Throwable t){
            return new Failure() {
                final long ts = System.nanoTime();
                @Override
                public long when() {
                    return ts;
                }
                @Override
                public Throwable error() {
                    return t;
                }
            };
        }
    }

    /**
     * A Special Exception with some special properties for failure in Retry
     */
    class MaximumRetryExceededException extends RuntimeException {

        /**
         * Various failures which happened during the course of tries
         */
        public final List<Failure> failures;

        /**
         * Underlying Retry which failed
         */
        public final Retry retry;

        /**
         * Creates MaximumRetryExceededException from
         * @param retry underlying retry
         * @param causes underlying Failure encountered during the execution
         */
        public MaximumRetryExceededException( Retry retry, List<Failure> causes){
            super();
            failures = Collections.unmodifiableList(causes);
            this.retry = retry;
        }

        @Override
        public String toString() {
            Map<String,Object> map = Map.of( "failures",
                    failures.stream().map( f -> Map.of("t", f.when(), "e" , f.error() ) ).collect(Collectors.toList()),
                    "retry", retry.toString(),
                    "numTries", failures.size());
            return ZTypes.jsonString(map);
        }

        @Override
        public String getMessage() {
            return "Retry exceeded : " + this;
        }
    }

    /**
     * Creates a Decorator with inner function
     * @param function underlying function which is to be decorated for retries
     * @return a decorated function which automatically does retries
     * @param <T> input type to the Function
     * @param <R> output type of the function
     */
    default  <T,R> Function<T,R>  withRetry( Function<T,R> function){
        // optimization trick : if no retry, do not wrap the stuff...
        if ( !can() ) return function;
        // else do wrapping up
        return t -> {
            int numTries = 0;
            // initialize
            List<Failure> failures = new ArrayList<>();
            numTries(numTries);
            while( can() ){
                try {
                    return function.apply(t);
                }catch (Throwable th){
                    logger.warn("Try num {} for {} with {} for error {}", numTries + 1, t, this, th.toString());
                    failures.add(Failure.failure( th) );
                    numTries++;
                    numTries(numTries);
                    try {
                        Thread.sleep(interval());
                    }catch (InterruptedException e){
                        logger.error("Got Interrupted ..." + e);
                        throw new RuntimeException( new TimeoutException("Possible Timeout Not sure..."));
                    }
                }
            }
            logger.error("Maximum retry exceeded!");
            throw new MaximumRetryExceededException( this, failures);
        };
    }

    /**
     * A Random generator for various purposes
     */
    Random random = new SecureRandom();

    /**
     * A Counter Retry Strategy
     */
    class CounterStrategy implements Retry {
        /**
         * Maximum no of retries
         */
        public final int maxRetries;

        /**
         * Interval in ms between retries
         */
        public final long interval ;

        /**
         * current no of retries
         */
        protected int currentState = -1;

        @Override
        public int tries(){
            return currentState;
        }

        /**
         * Constructs a Counter Strategy
         * @param maxRetries maximum no of retries
         * @param interval in ms between retries
         */
        public CounterStrategy( int maxRetries, long interval ){
            this.maxRetries = maxRetries;
            this.interval = interval;
        }

        @Override
        public boolean can() {
            return currentState <= maxRetries ;
        }

        @Override
        public void numTries(int currentNumberOfFailures) {
            currentState = currentNumberOfFailures;
        }

        @Override
        public long interval() {
            return interval;
        }

        @Override
        public String toString() {
            Map<String,Object> map = new HashMap<>();
            map.put(MAX, maxRetries);
            map.put(INTERVAL, interval);
            map.put(STRATEGY, getClass().getName());
            return ZTypes.jsonString(map);
        }
    }

    /**
     * A counter based Retry
     * @param maxRetries maximum tries
     * @param interval wait time between the tries
     * @return a Retry algorithm
     */
    static Retry counter( int maxRetries, long interval) {
        return new CounterStrategy(maxRetries,interval);
    }

    /**
     * A Retry that has random interval spacing
     * @param maxRetries maximum tries
     * @param avgInterval avg wait time between the tries
     * @return a Retry algorithm
     */
    static Retry randomize( int maxRetries, long avgInterval) {

        return new CounterStrategy(maxRetries,avgInterval){
            @Override
            public long interval() {
                long half = avgInterval / 2 ;
                return half + random.nextLong( half + avgInterval );
            }
        };
    }


    /**
     * A Retry that has  interval spacing increasing exponentially as time progresses
     * @param maxRetries maximum tries
     * @param startInterval  initial time gap after the first failure
     * @return a Retry algorithm
     */
    static Retry exponentialBackOff( int maxRetries, long startInterval) {

        return new CounterStrategy(maxRetries, startInterval ){
            long curInterval = startInterval;

            @Override
            public void numTries(int currentNumberOfFailures) {
                super.numTries(currentNumberOfFailures);
                curInterval = (long)(startInterval * Math.exp( currentNumberOfFailures - 1));
            }
            @Override
            public long interval() {
                return curInterval;
            }
        };
    }


    /**
     * Key for the Retry Strategy in Config
     */
    String STRATEGY = "strategy" ;

    /**
     * Key for the Maximum No of  Retry in Config
     */
    String MAX = "max" ;

    /**
     * Key for the Interval in ms of the Retry  in Config
     */
    String INTERVAL  = "interval";

    /**
     * Counter Retry Strategy Alias
     */
    String COUNTER = "counter" ;

    /**
     * Exponential Backoff Retry Strategy Alias
     */
    String EXP = "exp" ;

    /**
     * Random Retry Strategy Alias
     */
    String RANDOM = "random" ;

    /**
     * Creates a Retry Strategy out of a Configuration
     * @param config a configuration map
     * @return a Retry Strategy
     */
    static Retry fromConfig(Map<String,Object> config){
        if ( config.isEmpty() ) return NOP;
        final String strategy = config.getOrDefault(STRATEGY, COUNTER).toString();
        final int maxRetries = ZNumber.integer(config.getOrDefault(MAX, 0),0).intValue();
        final long interval = ZNumber.integer(config.getOrDefault(INTERVAL, Long.MAX_VALUE),0).longValue();
        return switch (strategy.toLowerCase(Locale.ROOT)) {
            case EXP -> exponentialBackOff(maxRetries, interval);
            case RANDOM -> randomize(maxRetries, interval);
            case COUNTER -> counter(maxRetries,interval);
            default -> { // log it out may be?
                logger.error("Could not make a Retry out of : {} --> NOP", ZTypes.jsonString(config));
                yield  NOP;
            }
        };
    }
}
