package cowj;

import spark.Request;
import spark.Spark;
import zoomba.lang.core.interpreter.ZMethodInterceptor;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public interface Authenticator {

    /**
     * Name for  UnAuthenticated
     */
    String UN_AUTHENTICATED = "UnAuthenticated" ;

    String USER_ID = "user-id" ;

    static  <R> R  safeAuthExecute(Callable<R> callable){
        try {
            return callable.call();
        }catch (Throwable t){
            Spark.halt(401, UN_AUTHENTICATED);
        }
        return null;
    }

    interface UserInfo{
        String id();
        String token();
        long expiry();
        static  UserInfo userInfo(String id, String token, long expiry){
            return new UserInfo() {
                @Override
                public String id() {
                    return id;
                }

                @Override
                public String token() {
                    return token;
                }

                @Override
                public long expiry() {
                    return expiry;
                }
            };
        }
        UserInfo GUEST = UserInfo.userInfo( "", "", Long.MAX_VALUE);
    }
    UserInfo userInfo(Request request) throws Exception ;

    default String authenticate(Request request){
        UserInfo userInfo = safeAuthExecute( () -> userInfo(request));
        assert userInfo != null;
        // already expired...
        if ( userInfo.expiry() < System.currentTimeMillis() ){
            Spark.halt(401, UN_AUTHENTICATED);
        }
        request.attribute(USER_ID, userInfo.id());
        return userInfo.id();
    }

    interface TokenAuthenticator extends Authenticator {

        String HEADER_KEY = "header:" ;
        String BODY_KEY = "body:" ;

        String tokenExpression();
        default String token(Request request) throws Exception {
            final String expr = tokenExpression();
            if ( expr.startsWith(HEADER_KEY) ){
                String key = expr.replace(HEADER_KEY,"");
                return request.headers(key);
            }
            if ( expr.startsWith(BODY_KEY) ){
                String key = expr.replace(BODY_KEY,"");
                Object body = ZTypes.json( request.body() );
                return ZMethodInterceptor.Default.jxPath(body, key, "").toString();
            }
            throw new IllegalArgumentException(expr);
        }

        UserInfo userFromToken(String token) throws Exception ;
        @Override
        default UserInfo userInfo(Request request) throws Exception {
            String token = Authenticator.safeAuthExecute( () -> token(request));
            return Authenticator.safeAuthExecute(() -> userFromToken(token));
        }

        abstract class CachedAuthenticator implements TokenAuthenticator {
            protected int maxCapacity = 1024;

            public int cacheSize(){
                return maxCapacity;
            }

            protected final Map<String,UserInfo> lru = Collections.synchronizedMap(new LinkedHashMap<>(){
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > maxCapacity;
                }
            });

            protected abstract UserInfo tryGetUserInfo(String token) throws Exception ;

            @Override
            public UserInfo userFromToken(String token) throws Exception {
                if ( lru.containsKey( token ) ){
                    return lru.get(token);
                }
                UserInfo userInfo = Authenticator.safeAuthExecute( () -> tryGetUserInfo(token));
                lru.put(userInfo.token(), userInfo);
                return userInfo;
            }
        }
    }
}