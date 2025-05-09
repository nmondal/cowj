package cowj.plugins;

import cowj.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import zoomba.lang.core.types.ZNumber;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cowj.DataSource.*;

/**
 * Abstraction for REDIS via JEDIS
 */
public interface RedisWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(RedisWrapper.class);


    /**
     * Key for the hosted REDIS urls to be used
     * They should be of host:port notation
     */
    String URLS = "urls";


    /**
     * Key for the REDIS client configuration to be used
     * This key should point to a json object type
     */
    String CLIENT_CONFIG = "client-config";

    /**
     * A Configuration Class to read from JSON/Map and create a  JedisClientConfig
     */
    final class Config{

        /**
         * Connection Timeout  in ms
         */
        public static final String CONNECTION_TIME_OUT = "connection-timeout" ;

        /**
         * Socket Timeout  in ms
         */
        public static final String SOCKET_TIME_OUT = "socket-timeout" ;

        /**
         * Booking Socket Timeout  in ms
         */
        public static final String BLOCKING_SOCKET_TIME_OUT = "blocking-socket-timeout" ;

        /**
         * Username for the connection
         */
        public static final String USER = "usr" ;

        /**
         * Password for the connection
         */
        public static final String PASSWORD = "pwd" ;

        /**
         * Database for the connection
         */
        public static final String DATABASE = "db" ;


        static JedisClientConfig fromConfig( Map<String, Object> clientConfig ){
            DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
            // use this as default basis
            DefaultJedisClientConfig def = DefaultJedisClientConfig.builder().build();

            computeIfPresent( clientConfig, CONNECTION_TIME_OUT,
                    INTEGER_CONVERTER, def.getConnectionTimeoutMillis(), builder::connectionTimeoutMillis );

            computeIfPresent( clientConfig, SOCKET_TIME_OUT,
                    INTEGER_CONVERTER, def.getSocketTimeoutMillis(), builder::socketTimeoutMillis);

            computeIfPresent( clientConfig, BLOCKING_SOCKET_TIME_OUT,
                    INTEGER_CONVERTER, def.getBlockingSocketTimeoutMillis(), builder::blockingSocketTimeoutMillis );

            computeIfPresent(clientConfig, DATABASE,
                    INTEGER_CONVERTER, def.getDatabase(), builder::database );

            computeIfPresent(clientConfig, USER, STRING_CONVERTER, "",  builder::user );

            computeIfPresent(clientConfig, PASSWORD, STRING_CONVERTER, "", builder::password );

            return builder.build();

        }

        static UnifiedJedis jedis( Set<HostAndPort> jedisClusterNodes , Map<String, Object> clientConfig){
            if ( clientConfig.isEmpty() ){
                if ( jedisClusterNodes.size() > 1 ){
                    return  new JedisCluster(jedisClusterNodes);
                }
                final HostAndPort hp = jedisClusterNodes.iterator().next();
                return new JedisPooled(hp);
            }
            // here is some useful work
            JedisClientConfig jedisClientConfig = fromConfig( clientConfig );
            if ( jedisClusterNodes.size() > 1 ){
                return  new JedisCluster(jedisClusterNodes, jedisClientConfig );
            }
            final HostAndPort hp = jedisClusterNodes.iterator().next();
            return new JedisPooled(hp, jedisClientConfig );
        }
    }

    /**
     * A DataSource.Creator which returns proxy() as redis.clients.jedis.UnifiedJedis
     */
    DataSource.Creator REDIS = (name, config, parent) -> {
        List<String> urls = DataSource.list( config, parent, URLS);
        if (urls.isEmpty()) throw new IllegalArgumentException("url list is empty!");

        Map<String, Object> clientConfigMap = DataSource.map( config, parent, CLIENT_CONFIG );

        Set<HostAndPort> jedisClusterNodes =
                urls.stream().map(s -> {
                    String[] arr = s.split(":");
                    return new HostAndPort(arr[0], ZNumber.integer(arr[1], 6379).intValue());
                }).collect(Collectors.toSet());
        final UnifiedJedis jedis = Config.jedis( jedisClusterNodes, clientConfigMap );
        return DataSource.dataSource(name, jedis);
    };
}
