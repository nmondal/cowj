package cowj.plugins;

import cowj.*;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;

import java.util.*;

/**
 * A Class to connect to actual identity providers from the token string
 * For performance reason things are cached
 */
public abstract class StorageAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator {

    /**
     * Key for the Cache Size in configuration
     */
    final static String CACHE_SIZE = "cache" ;

    /**
     * Key for the storage
     * This must point to a valid data source itself
     */
    final static String STORAGE = "storage" ;

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
        storageWrapperKey = config.getOrDefault( STORAGE, "auth-jdbc").toString();
        userQuery = config.getOrDefault( USER_QUERY, "query").toString();
        storage = Scriptable.DATA_SOURCES.get(storageWrapperKey);
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
            Map<String, Object> userData(String token) throws Exception {
                UnifiedJedis jedis = (UnifiedJedis) storage;
                Map<String,String> data = jedis.hgetAll(userQuery);
                return Map.of( userColumnName, data.get(userColumnName), expColumnName, data.get(expColumnName));
            }
        };
        return DataSource.dataSource(name, authenticator);
    };

    /**
     * A GOOGLE_STORAGE creator to create a GOOGLE STORAGE driven Authenticator
     */
    public static DataSource.Creator GOOGLE_STORAGE = (name, config, parent) -> {
        final StorageAuthenticator authenticator = new StorageAuthenticator(config) {
            @Override
            Map<String, Object> userData(String token) throws Exception {
                GoogleStorageWrapper googleStorageWrapper = (GoogleStorageWrapper) storage;
                String[] arr = userQuery.split("/");
                Object o = googleStorageWrapper.load(arr[0], arr[1] );
                if ( o instanceof Map){
                    return (Map)o;
                }
                throw new RuntimeException("Resulting Object is not Map!");
            }
        };
        return DataSource.dataSource(name, authenticator);
    };
}
