package cowj.plugins;

import cowj.*;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class StorageAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator {

    final static String CACHE_SIZE = "cache" ;
    final static String STORAGE = "storage" ;
    final static String TOKEN_EXPRESSION = "token" ;
    final static String USER_QUERY = "query" ;
    final static String USER_COLUMN = "user" ;
    final static String EXPIRY_COLUMN = "expiry" ;
    abstract Map<String,Object> userData(String token) throws Exception;

    protected final Map<String,Object> config;
    protected final String tokenExpression;
    protected final String userColumnName ;
    protected final String expColumnName ;
    protected final String storageWrapperKey ;
    protected final String userQuery ;
    protected final Object storage;

    public StorageAuthenticator(Map<String,Object> config){
        this.config = config;
        tokenExpression = config.getOrDefault( TOKEN_EXPRESSION, "body:token").toString();
        userColumnName = config.getOrDefault(USER_COLUMN,"user").toString();
        expColumnName = config.getOrDefault(EXPIRY_COLUMN,"expiry").toString();
        storageWrapperKey = config.getOrDefault( STORAGE, "auth-jdbc").toString();
        userQuery = config.getOrDefault( USER_QUERY, "query").toString();
        storage = Scriptable.DATA_SOURCES.get(storageWrapperKey);
        maxCapacity = ZNumber.integer(config.getOrDefault(CACHE_SIZE, maxCapacity),maxCapacity).intValue();
    }

    @Override
    public String tokenExpression() {
        return tokenExpression;
    }

    @Override
    protected UserInfo tryGetUserInfo(String token) throws Exception {
        Map<String,Object> userData = userData(token);
        final Object userId = userData.get(userColumnName);
        Objects.requireNonNull(userId);
        final Object exp = userData.getOrDefault(expColumnName,0L);
        final long expiry = ZNumber.integer(exp, 0L).longValue();
        return UserInfo.userInfo(userId.toString(), token, expiry);
    }

    public static DataSource.Creator JDBC = (name, config, parent) -> {
        final StorageAuthenticator authenticator = new StorageAuthenticator(config) {
            @Override
            Map<String, Object> userData(String token) throws Exception {
                JDBCWrapper jdbcWrapper = (JDBCWrapper) storage;
                EitherMonad<List<Map<String,Object>>> em = jdbcWrapper.select(userQuery, List.of(token));
                if ( em.inError() ){ throw new RuntimeException(em.error()) ; }
                List<Map<String,Object>> res = em.value();
                if ( res.size() != 1 ){ throw new RuntimeException("Ambiguous Response!") ; }
                return res.get(0);
            }
        };
        return DataSource.dataSource(name, authenticator);
    };
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
