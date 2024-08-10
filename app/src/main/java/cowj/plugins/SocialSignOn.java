package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.TypeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * A provider for social sign on
 * A generic component that works across various Providers
 *  Google
 *  Apple
 *  Meta
 *  LinkedIn
 */
public interface SocialSignOn {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(SocialSignOn.class);

    CurlWrapper curl();

    interface SendPayload{

        default String verb(){ return "GET" ; }

        default String path() { return "/"; }

        default Map<String, String> headers() { return Collections.emptyMap(); }

        default Map<String, String> params() { return Collections.emptyMap() ; }

        default String body() { return "" ;}
    }

    SendPayload payload(String tokenId);

    Set<String> allowedIssuers();

    Set<String> allowedAudiences();

    static boolean verifyJWT(Map<String,Object> jwt , String key, Predicate<Object> matcher){
        Object v = jwt.get(key);
        if ( v == null ){
            logger.error("jwt does not have '{}'", key);
            return false;
        }
        // now test it
        if( matcher.test(v) ){
            return true;
        }
        logger.error("jwt does not have valid '{}'!", key );
        return false;
    }

    default Map<String,Object> jwt( String responseBody){
        return EitherMonad.call( () -> (Map<String,Object>)TypeSystem.OBJECT_MAPPER.readValue( responseBody, Map.class))
                .verify( m -> !m.containsKey("error"))
                .ensure( m -> new IllegalStateException(m.get("error").toString()) )
                .value();
    }

    default void verifyToken(String tokenId){

        SendPayload payload = payload(tokenId);
        /*
        * Send this to Get response
        * Should be no error
        * On OK Response status should be 200 else error
        * Try extract jwt Map from response
        * Failing produce error
        * Extract expiry time stamp, if expired, error
        * Extract issuers and check, else error
        * Extract audience and check else error
        * */
        curl().send( payload.verb(), payload.path(), payload.headers(), payload.params(), payload.body() )
                .ensure( e -> {
                    logger.error("Could not send verification request to destination", e);
                    return Spark.halt(403, "Auth request failed!");
                })
                .verify( c -> c.status == 200 )
                .ensure( c -> {
                    logger.error("Server responded with non 200 status : {}", c.status );
                    return Spark.halt(403, "Auth request response invalid!");
                })
                .then( c -> jwt(c.body() ))
                .ensure( e ->{
                    logger.error("Could not extract jwt token", e);
                    return Spark.halt(403, "JWT extraction failed!");
                })
                .verify( jwt -> verifyJWT(jwt, "expiry", t -> ((Number)t).longValue() <= System.currentTimeMillis()/1000L ) )
                .ensure( e -> Spark.halt(403, "JWT has expired") )
                .verify( jwt -> verifyJWT(jwt, "iss", iss -> allowedIssuers().contains(iss.toString()) ))
                .ensure( e -> Spark.halt(403, "JWT has invalid issuer") )
                .verify( jwt -> verifyJWT(jwt, "aud", aud ->  allowedAudiences().contains(aud.toString()) ))
                .ensure( e -> Spark.halt(403, "JWT has invalid audience") );
        logger.info("JWT token verified - {}", tokenId );
    }

    interface Google extends SocialSignOn {

        @Override
        default SendPayload payload(String tokenId) {
            return new SendPayload() {
                @Override
                public String path() {
                    return "/tokenInfo";
                }
                @Override
                public Map<String, String> params() {
                    return Map.of("id_token", tokenId );
                }
            };
        }
    }

    DataSource.Creator GOOGLE = (name, config, parent) -> {
      Map<String,Object> curlConfig = (Map)config.getOrDefault( "curl", Collections.emptyMap() );
      final CurlWrapper curlWrapper = CurlWrapper.CURL.create( "GoogleSignOn" , curlConfig, parent ).any();
      final Set<String> allowedIssuers = (Set<String>) ((List)config.getOrDefault("issuers", Collections.emptyList())).stream().collect(Collectors.toSet());
      final Set<String> allowedAudiences = (Set<String>) ((List)config.getOrDefault("audiences", Collections.emptyList())).stream().collect(Collectors.toSet());
      final Google google = new Google() {
          @Override
          public CurlWrapper curl() {
              return curlWrapper;
          }

          @Override
          public Set<String> allowedIssuers() {
              return allowedIssuers;
          }

          @Override
          public Set<String> allowedAudiences() {
              return allowedAudiences;
          }
      };
      return DataSource.dataSource(name, google);
    };
}
