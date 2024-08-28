package cowj.plugins;

import cowj.*;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;

import java.util.*;

/**
 * A Class to connect to actual identity providers from the token string
 * For performance reason things are cached
 */
public abstract class StorageAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator
        implements Authenticator.TokenAuthenticator.TokenIssuer {

    /**
     * Key for the Cache Size in configuration
     */
    final static String CACHE_SIZE = "cache" ;

    /**
     * Key for the storage
     * This must point to a valid data source itself
     */
    final static String DATASOURCE_STORAGE = "storage" ;

    /**
     * Key for the expression for token extractor in configuration
     */
    final static String TOKEN_EXPRESSION = "token" ;

    /**
     * Key for the query string that would be used to extract UserInfo from Data Store
     * Underlying storage must return a single item list of Map Object
     * For
     *  JDBC : a query -
     *  REDIS :  key to redis
     *  Google Storage : of format bucket_name/file_name
     */
    final static String USER_QUERY = "query" ;

    /**
     * Key for the user id - would be used to fetch user id from the map returned
     */
    final static String USER_COLUMN = "user" ;

    /**
     * Key for the expiry  - would be used to fetch expiry timestamp from the map returned
     */
    final static String EXPIRY_COLUMN = "expiry" ;

    /**
     * Key for the allowed risky paths
     */
    final static String RISKS = "risks" ;

    /**
     * Gathers using underlying storage the Map object containing the user id from the token
     * @param token string token for the user
     * @return a Map of string,object representing user information
     * @throws Exception in case of any error
     */
    abstract Map<String,Object> userData(String token) throws Exception;

    /**
     * Configuration map which was passed down
     */
    protected final Map<String,Object> config;

    /**
     * Token Expression loaded from configuration
     */
    protected final String tokenExpression;

    /**
     * Key of the User Id or Column  loaded from configuration
     */
    protected final String userColumnName ;

    /**
     * Key for the Token Expiry  loaded from configuration
     */
    protected final String expColumnName ;

    /**
     * Name of the underlying Data Source
     */
    protected final String storageWrapperKey ;

    /**
     * Query String for the underlying Data Source to gather the user information
     */
    protected final String userQuery ;

    /**
     * Actual underlying storage object
     */
    protected final Object storage;

    /**
     * Actual risky paths where auth would not applicable
     */
    protected final Set<String> risks;

    /**
     * Creates one abstract StorageAuthenticator
     * @param config configuration parameters to create one
     */
    public StorageAuthenticator(Map<String,Object> config){
        this.config = config;
        tokenExpression = config.getOrDefault( TOKEN_EXPRESSION, "body:token").toString();
        userColumnName = config.getOrDefault(USER_COLUMN,"user").toString();
        expColumnName = config.getOrDefault(EXPIRY_COLUMN,"expiry").toString();
        storageWrapperKey = config.getOrDefault(DATASOURCE_STORAGE, "auth-jdbc").toString();
        userQuery = config.getOrDefault( USER_QUERY, "query").toString();
        storage = DataSource.dataSource(storageWrapperKey);
        risks = Set.<String>copyOf((List) config.getOrDefault(RISKS, Collections.emptyList()));
        maxCapacity = ZNumber.integer(config.getOrDefault(CACHE_SIZE, maxCapacity),maxCapacity).intValue();
    }

    @Override
    public String tokenExpression() {
        return tokenExpression;
    }

    @Override
    public Set<String> risks() {
        return risks;
    }

    @Override
    protected UserInfo tryGetUserInfo(String token) throws Exception {
        Map<String,Object> userData = userData(token);
        final Object userId = userData.get(userColumnName);
        Objects.requireNonNull(userId);
        final Object exp = userData.getOrDefault(expColumnName,0L);
        final long expiry = ZNumber.integer(exp, 0L).longValue();
        return UserInfo.userInfo(userId.toString(), token, expiry, userData);
    }

    /**
     * A JDBC creator to create a JDBC driven Authenticator
     */
    public static DataSource.Creator JDBC = (name, config, parent) -> {
        final StorageAuthenticator authenticator = new StorageAuthenticator(config) {

            @Override
            public String issueToken(String user, long expiry) {
                throw new UnsupportedOperationException("JDBC does not support creating token as of now!");
            }

            @Override
            Map<String, Object> userData(String token) throws Exception {
                JDBCWrapper jdbcWrapper = (JDBCWrapper) storage;
                EitherMonad<List<Map<String,Object>>> em = jdbcWrapper.select(userQuery, List.of(token));
                if ( em.inError() ){ throw new RuntimeException(em.error()) ; }
                List<Map<String,Object>> res = em.value();
                if ( res.size() != 1 ){ throw new RuntimeException(
                        String.format("Ambiguous Response : token : '%s', resp size '%d", token, res.size())) ; }
                return res.get(0);
            }
        };
        return DataSource.dataSource(name, authenticator);
    };

    /**
     * A REDIS creator to create a REDIS driven Authenticator
     */
    public static DataSource.Creator REDIS = (name, config, parent) -> {
        final StorageAuthenticator authenticator = new StorageAuthenticator(config) {

            @Override
            public String issueToken(String user, long expiry) throws Exception {
                UnifiedJedis jedis = (UnifiedJedis) storage;
                Map<String,Object> userData = Map.of(userColumnName, user, expColumnName, expiry );
                final String data = TypeSystem.OBJECT_MAPPER.writeValueAsString(userData);
                final String token = token(user, expiry);
                jedis.hset(userQuery, token, data );
                return token;
            }

            /**
             * userQuery becomes the name of the Map, which is storing the token --> user Data map
             *
             * @param token string token for the user - this will be used as key to the map pointed by userQuery
             * @return A map ( json type ) storing the user details of that token
             * @throws Exception in case of any error
             */
            @Override
            Map<String, Object> userData(String token) throws Exception {
                UnifiedJedis jedis = (UnifiedJedis) storage;
                String jsonData = jedis.hget( userQuery, token );
                final Map<String,Object> data = TypeSystem.OBJECT_MAPPER.readValue( jsonData, Map.class);
                return data;
            }
        };
        return DataSource.dataSource(name, authenticator);
    };

    /**
     * A STORAGE based creator to create a STORAGE driven Authenticator
     * Google Storage
     * S3 - AWS Storage
     * Memory, File, everything works, see @{StorageWrapper}
     */
    public static DataSource.Creator STORAGE = (name, config, parent) -> {
        final StorageAuthenticator authenticator = new StorageAuthenticator(config) {

            @Override
            public String issueToken(String user, long expiry) throws Exception {
                StorageWrapper<?,?,?> storageWrapper = (StorageWrapper<?,?,?>) storage;
                Map<String,Object> userData = Map.of(userColumnName, user, expColumnName, expiry );
                final String token = token(user, expiry);
                // bucket/prefix_for_tokens/token
                String[] arr = userQuery.split("/");
                storageWrapper.dump(arr[0], arr[1] +"/" + token, userData);
                return token;
            }

            /**
             * userQuery becomes bucket_name/token_storage_prefix
             * token would be passed post this bucket_name/token_storage_prefix/token
             * @param token string token for the user - this will be used as key to the map pointed by userQuery
             * @return A map ( json type ) storing the user details of that token
             * @throws Exception in case of any error
             */
            @Override
            Map<String, Object> userData(String token) throws Exception {
                StorageWrapper<?,?,?> storageWrapper = (StorageWrapper<?,?,?>) storage;
                // bucket/prefix_for_tokens/token
                String[] arr = userQuery.split("/");
                Object o = storageWrapper.load(arr[0], arr[1] + "/" + token );
                if ( o instanceof Map){
                    return (Map)o;
                }
                throw new RuntimeException("Resulting Object is not Map!");
            }
        };
        return DataSource.dataSource(name, authenticator);
    };
}
