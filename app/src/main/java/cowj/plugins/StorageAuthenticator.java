package cowj.plugins;

import cowj.*;
import zoomba.lang.core.types.ZNumber;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class StorageAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator {

    final static String JDBC_CON = "jdbc" ;
    final static String TOKEN_EXPRESSION = "token" ;
    final static String USER_QUERY = "query" ;
    final static String USER_COLUMN = "user" ;
    final static String EXPIRY_COLUMN = "expiry" ;

    DataSource.Creator JDBC = (name, config, parent) -> {
        final String jdbcKey = config.getOrDefault( JDBC_CON, "").toString();
        final String tokenExpression = config.getOrDefault( TOKEN_EXPRESSION, "").toString();
        final String userQuery = config.getOrDefault( USER_QUERY, "").toString();
        final JDBCWrapper jdbcWrapper = (JDBCWrapper) Scriptable.DATA_SOURCES.get(jdbcKey);
        final String userColumnName = config.getOrDefault(USER_COLUMN,"").toString();
        final String expColumnName = config.getOrDefault(EXPIRY_COLUMN,"").toString();

        final StorageAuthenticator authenticator = new StorageAuthenticator() {
            @Override
            protected UserInfo tryGetUserInfo(String token) throws Exception {
                EitherMonad<List<Map<String,Object>>> em = jdbcWrapper.select(userQuery, List.of(token));
                if ( em.inError() ){ throw new RuntimeException(em.error()) ; }
                List<Map<String,Object>> res = em.value();
                if ( res.size() != 1 ){ throw new RuntimeException("Ambiguous Response!") ; }
                Map<String,Object> userData = res.get(0);
                final Object userId = userData.get(userColumnName);
                Objects.requireNonNull(userId);
                final Object exp = userData.get(expColumnName);
                final long expiry = ZNumber.integer(exp, 0L).longValue();
                assert expiry != 0L;
                return UserInfo.userInfo(userId.toString(), token, expiry);
            }

            @Override
            public String tokenExpression() {
                return tokenExpression;
            }
        };

        return new DataSource() {
            @Override
            public Object proxy() {
                return authenticator;
            }
            @Override
            public String name() {
                return name;
            }
        };
    };
}
