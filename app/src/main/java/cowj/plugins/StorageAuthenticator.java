package cowj.plugins;

import cowj.*;
import redis.clients.jedis.UnifiedJedis;
import zoomba.lang.core.types.ZNumber;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class StorageAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator {

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
        tokenExpression = config.getOrDefault( TOKEN_EXPRESSION, "").toString();
        userColumnName = config.getOrDefault(USER_COLUMN,"").toString();
        expColumnName = config.getOrDefault(EXPIRY_COLUMN,"").toString();
        storageWrapperKey = config.getOrDefault( STORAGE, "").toString();
        userQuery = config.getOrDefault( USER_QUERY, "").toString();
        storage = Scriptable.DATA_SOURCES.get(storageWrapperKey);
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
        assert expiry != 0L;
        return UserInfo.userInfo(userId.toString(), token, expiry);
    }

    DataSource.Creator JDBC = (name, config, parent) -> {
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

    DataSource.Creator REDIS = (name, config, parent) -> {
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
}
