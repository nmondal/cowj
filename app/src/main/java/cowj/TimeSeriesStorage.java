package cowj;

import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An abstraction over key value storage to do finite precision time series
 * That means, we can specify the bucket size as in  day, hour, min, sec, ms
 * All entry within the same timespan would get into the same bucket with a unique name,
 * But present as in ms time
 * Internally stores time in UTC all the time
 */
public interface TimeSeriesStorage {

    /**
     * Underlying precision mode for the storage
     * How much precisely it stores the data
     */
    enum Precision{
        /**
         * Yearly extremely low frequency
         */
        YEAR,
        /**
         * Monthly very low frequency
         */
        MONTH,
        /**
         * Day wise  moderately low frequency
         */
        DAY,

        /**
         * Per Hour ( 24 hours format )  - reasonable frequency storage
         */
        HOUR,

        /**
         * Every Minute gets a bucket, moderate frequency storage
         */
        MIN,

        /**
         * Every Second gets a bucket, high frequency storage
         */
        SEC,

        /**
         * Every Milli Second gets a bucket, super high frequency storage
         */
        MS,
    }

    /**
     * Logical Bucket Definition (prefixes) for the various precisions
     */
    Map<Precision,DateTimeFormatter> FORMATTER_MAP = Map.of(
            Precision.YEAR, DateTimeFormatter.ofPattern("yyyy"),
            Precision.MONTH, DateTimeFormatter.ofPattern("yyyy/MM"),
            Precision.DAY, DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            Precision.HOUR, DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"),
            Precision.MIN, DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm"),
            Precision.SEC, DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss"),
            Precision.MS, DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss/SSS")
            );

    /**
     * Underlying precision for this instance
     * @return Precision for this instance
     */
    Precision precision();

    /**
     * Underlying StorageWrapper for this instance
     * @return StorageWrapper for this instance
     */
    StorageWrapper<?, ?, ?> storage();

    /**
     * Underlying StorageWrapper Bucket to be used for this instance
     * @return StorageWrapper Bucket for this instance
     */
    String bucket();

    /**
     * Underlying base prefix to be used for this instance
     * @return base prefix for this instance
     */
    String base();

    /**
     * Puts an entry into the abstract TimeSeries
     * @param extension for the file to be used
     * @param value data for the file
     */
    default void put(String extension, Object value) {
        final Instant instant = Instant.now();
        DateTimeFormatter formatter = FORMATTER_MAP.get(precision());
        final BigInteger toNano = BigInteger.valueOf(instant.toEpochMilli()).add(BigInteger.valueOf(instant.getNano()));
        final String temporalUID = toNano + "_" + ThreadLocalRandom.current().nextInt(10000);
        final String utcTS = instant.atZone(ZoneId.of("UTC")).format(formatter);
        final String bucket = bucket();
        final String fileName = base() + "/" + utcTS + "/" + temporalUID + extension;
        StorageWrapper<?, ?, ?> storage = storage();
        if (value instanceof byte[]) {
            storage.dumpb(bucket, fileName, (byte[]) value);
        } else if ( value instanceof Collections || value instanceof Map  ){ // string and like
            storage.dump(bucket, fileName, value );
        }
        else { // rest just follow this, ugly null ptr is a problem
            storage.dumps(bucket, fileName, value.toString());
        }
    }

    /**
     * Gets the step size required to travel the prefixed storage
     * @param p Precision
     * @return a TemporalAmount which is used as step size
     */
    static TemporalAmount step( Precision p){
        return switch (p){
            case MS -> Duration.ofMillis(1);
            case SEC -> Duration.ofSeconds(1);
            case MIN -> Duration.ofMinutes(1);
            case HOUR -> Duration.ofHours(1);
            case DAY -> Duration.ofDays(1);
            case MONTH -> Period.ofMonths(1);
            case YEAR -> Period.ofYears(1);
        };
    }

    /**
     * Finds all Entries between UTC start and end in MS
     * @param startUTCMS start system time in ms  ( inclusive )
     * @param endUTCMS end system time in ms in UTC ( exclusive )
     * @return a List of Entries between these two
     */
    default List<Map.Entry<String, Object>> list(long startUTCMS, long endUTCMS) {
        if ( endUTCMS <= startUTCMS ) return Collections.emptyList() ;
        DateTimeFormatter formatter = FORMATTER_MAP.get(precision());
        ZonedDateTime t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startUTCMS), ZoneId.of( "UTC"));
        TemporalAmount s = step( precision() );
        ZonedDateTime e = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endUTCMS), ZoneId.of( "UTC"));

        List<Map.Entry<String, Object>> ret = new ArrayList<>();
        StorageWrapper<?, ?, ?> storage = storage();
        final String base = base();
        final String bucket = bucket() ;
        while ( t.compareTo(e) < 0 ){
            final String prefix = base + "/" + formatter.format(t) + "/" ;
            List<Map.Entry<String, Object>> tmp = storage.entriesData( bucket, prefix ).toList();
            ret.addAll(tmp);
            t = t.plus(s);
        }
        return ret;
    }

    /**
     * Creates a time series data abstraction using underlying storage
     * @param storageWrapper the underlying storage
     * @param bucket to be used for storing this data
     * @param base prefix to be used before any other prefix
     * @param precision one of the constants see string rep for @{Precision}
     * @return a TimeSeriesStorage abstraction
     */
    static TimeSeriesStorage fromStorage( StorageWrapper<?, ?, ?> storageWrapper, String bucket, String base, String precision ){
        final Precision p = Enum.valueOf( Precision.class, precision.toUpperCase(Locale.ROOT) );
        return new TimeSeriesStorage() {
            @Override
            public Precision precision() {
                return p;
            }

            @Override
            public StorageWrapper<?, ?, ?> storage() {
                return storageWrapper;
            }

            @Override
            public String bucket() {
                return bucket;
            }

            @Override
            public String base() {
                return base;
            }
        };
    }
}
