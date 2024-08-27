package cowj.plugins;

import cowj.Authenticator;
import cowj.DataSource;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import zoomba.lang.core.operations.Function;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT Token based Authentication provider
 * Idea is copied from :
 * <a href="https://github.com/metamug/java-jwt/blob/master/src/main/java/com/metamug/jwt/JWebToken.java">...</a>
 */
public abstract class JWTAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator
        implements Authenticator.TokenAuthenticator.TokenIssuer {
    final static Logger logger = LoggerFactory.getLogger(JWTAuthenticator.class);
    final long STD_EXPIRY_OFFSET = 24 * 60 * 60  ; // 1 day, in seconds

    /**
     * Secret key which is used to sign the payload
     * @return Secret Key to be used to sign the payload
     */
    public abstract String secretKey();

    /**
     * Who issued the token?
     * @return whoever issued the token
     */
    public abstract String issuer();

    /**
     * Given a token the time to live
     * @return offset from current time to the expiry time, time to live
     */
    public long expiryOffset(){
        return STD_EXPIRY_OFFSET;
    }

    /**
     * A handcrafted simple implementation of JWT Auth mechanism
     */
    public class JWebToken implements UserInfo{

        private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        private final Map<String,Object> payload = new TreeMap<>();

        /**
         * A view to the underlying payload
         * @return unmodifiable view of the payload
         */
        public Map<String,Object> json(){
            return Collections.unmodifiableMap(payload);
        }

        private String signature;
        private String encodedHeader;
        private String token;

        private JWebToken() {
            encodedHeader = encode(JWT_HEADER);
        }

        /**
         * Gets the current second
         * @return current second
         */
        public static long currentSecond(){
            return System.currentTimeMillis() / 1000 ;
        }

        /**
         * Creates a JWT token
         * @param sub subject - the user id
         * @param aud audience - list of items where user would have access
         * @param expires time when the token should expire
         */
        public JWebToken(String sub, List<?> aud, long expires) {
            this( Map.of("sub", sub, "aud", aud, "exp", expires ));
        }

        /**
         * Is the object of type bounded integer
         * @param v the input object
         * @return true if an integer or long false otherwise
         */
        public static boolean isInt(Object v){
           return v instanceof Long || v instanceof Integer  ;
        }

        /**
         * Verification rules for the JWT token fields
         * A map with key as the field name, value a Predicate
         */
        public static Map<String, Predicate<Object>> JWT_FIELD_VERIFIERS = Map.of(
            "sub", ( (v) -> v instanceof String ),
                "aud", ( (v) -> v instanceof List<?> ),
                "exp", ( JWebToken::isInt  ),
                "iat", ( JWebToken::isInt  ),
                "iss", ( (v) -> v instanceof String  ),
                "jti", ( (v) -> v instanceof String  )
        );

        /**
         * Verification rules for the JWT token fields
         * A map with key as the field name,
         * value a Supplier which supplies the value if missing
         */
        public final Map<String, Supplier<Object>> JWT_FIELD_PRODUCERS = Map.of(
                "aud", (Collections::emptyList),
                "iat", (JWebToken::currentSecond),
                "iss", (JWTAuthenticator.this::issuer),
                "jti", () -> UUID.randomUUID().toString(),
                "exp", () -> JWTAuthenticator.this.expiryOffset() + currentSecond()
        );

        /**
         * Creates a JWT token
         * @param map which is the template for the payload for JWT
         */
        public JWebToken(Map<String,Object> map) {
            this();
            if ( !map.containsKey("sub") ){
                logger.error( "JWT keys does not have mandatory field 'sub'" );
                throw new IllegalArgumentException("JWT payload has no sub!");
            }
            List<?> failed = JWT_FIELD_VERIFIERS.entrySet().stream()
                    .filter( entry -> map.containsKey( entry.getKey() ) ) // only when key does exist
                    .filter( entry ->  !entry.getValue().test( map.get(entry.getKey())) ) // type mismatch
                    .map(Map.Entry::getKey).toList();

            if ( !failed.isEmpty() ){
                logger.error( "JWT keys failed type validation : {}", failed );
                throw new IllegalArgumentException("JWT Parameters Type mismatch! " + failed);
            }
            payload.putAll(map); // put into payload whatever came as of now
            JWT_FIELD_PRODUCERS.entrySet().stream()
                    .filter( (entry) -> ! map.containsKey(entry.getKey()))
                    .forEach( entry ->  payload.put( entry.getKey(), entry.getValue().get() ));

            signature = hmacSha256(encodedHeader + "." + encode(payload), JWTAuthenticator.this.secretKey());
            this.token = toString();
        }

        /**
         * Constructs a token back from String rep
         * @param token string rep for the token
         * @throws Exception in case of any errors encountered
         */
        public JWebToken(String token) throws Exception {
            this();
            this.token = token;
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid Token format");
            }
            if (encodedHeader.equals(parts[0])) {
                encodedHeader = parts[0];
            } else {
                throw new NoSuchAlgorithmException("JWT Header is Incorrect: " + parts[0]);
            }
            final String decodedPayload = decode(parts[1]);
            Map<String,Object> tokenPayload = (Map)ZTypes.json(decodedPayload);
            payload.putAll(tokenPayload);
            if (!payload.containsKey("exp")) {
                throw new Exception("Payload doesn't contain expiry " + payload);
            }
            signature = parts[2];
        }

        @Override
        public String toString() {
            return encodedHeader + "." + encode(payload) + "." + signature;
        }

        /**
         * Is the Token actually having a valid signature
         * @return true if it is, false otherwise
         */
        public boolean isSignatureValid() {
            final String data = token.substring(0, token.lastIndexOf('.'));
            final String computed = hmacSha256(data, JWTAuthenticator.this.secretKey());
            return signature.equals(computed); //signature matched
        }

        /**
         * Gets the subject of the token
         * @return subject of the token
         */
        public String getSubject() {
            return payload.get("sub").toString();
        }

        /**
         * Audience of the token
         * @return audience of the token
         */
        public List<String> getAudience() {
            return (List)payload.getOrDefault("aud", Collections.emptyList());
        }

        private static String encode(Object obj) {
            if ( obj instanceof Map ){
                obj = ZTypes.jsonString(obj);
            }
            return encode(obj.toString().getBytes(StandardCharsets.UTF_8));
        }

        private static String encode(byte[] bytes) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        private static String decode(String encodedString) {
            return new String(Base64.getUrlDecoder().decode(encodedString));
        }

        private String hmacSha256(String data, String secret) {
            try {
                //MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = secret.getBytes(StandardCharsets.UTF_8);//digest.digest(secret.getBytes(StandardCharsets.UTF_8));
                Mac sha256Hmac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec(hash, "HmacSHA256");
                sha256Hmac.init(secretKey);
                byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return encode(signedBytes);
            } catch (Throwable ex) {
                logger.error("Error while creating SHA : {}", ex.toString());
                throw Function.runTimeException(ex);
            }
        }

        @Override
        public String id() {
            return getSubject();
        }

        @Override
        public String token() {
            return token;
        }

        /**
         * Because JWT works in Sec, we need to convert it here
         * @return expiry in approx milli sec
         */
        @Override
        public long expiry() {
            long l = ((Number)payload.getOrDefault( "exp", currentSecond() - 1 )).longValue();
            return l * 1000; // because underlying system runs with ms level accuracy
        }

        @Override
        public Map<String, Object> data() {
            return payload;
        }
    }

    /**
     * Authentication header
     * <a href="https://stackoverflow.com/questions/33265812/best-http-authorization-header-type-for-jwt">...</a>
     */
    public static final String AUTHENTICATION_HEADER = "Authorization" ;

    /**
     * Authentication header Bearer Token value prefix
     */
    public static final String BEARER = "Bearer" ;

    @Override
    public String tokenExpression() {
        return ""; // this is no op
    }

    @Override
    public String token(Request request) throws Exception {
        if ( !request.headers().contains(AUTHENTICATION_HEADER)) throw new Exception("Auth header not found!");
        String hVal = request.headers(AUTHENTICATION_HEADER);
        if ( !hVal.startsWith(BEARER) ) throw new Exception("Bearer token not found!");
        String[] splits = hVal.split( " +");
        if ( splits.length != 2 ) throw new Exception("Bearer token misconfigured!");
        return splits[1];
    }

    /**
     * Creates a token with parameters
     * @param sub subject of the token
     * @param expires expiry time in  sec
     * @param aud audience for the token
     * @return a token object
     */
    public JWebToken jwt(String sub,long expires, List<?> aud){
        return new JWebToken(sub, aud, expires);
    }

    /**
     * Creates a token with parameters - audience is empty
     * @param sub subject of the token
     * @param expires expiry time in milli sec
     * @return a token object
     */
    public JWebToken jwt(String sub, long expires){
        return jwt(sub,  expires, Collections.emptyList());
    }

    @Override
    public String issueToken(String user, long expiry) throws Exception {
        return jwt(user, expiry).toString() ;
    }

    /**
     * Creates a token with parameters
     *  - audience is empty
     *  - expires after suitable configured timeout @see{ expiryOffset() }
     * @param sub subject of the token
     * @return a token object
     */
    public JWebToken jwt(String sub){
        return jwt(sub, JWebToken.currentSecond() + expiryOffset() );
    }

    /**
     * Creates a token with parameters
     * @param map underlying payload of the token
     * @return a token object
     */
    public JWebToken jwt(Map<String,Object> map){
        return new JWebToken(map);
    }

    @Override
    protected UserInfo tryGetUserInfo(String token) throws Exception {
        final long startVerification = System.nanoTime();
        try {
            JWebToken jWebToken = new JWebToken(token);
            if ( jWebToken.isSignatureValid() ) return jWebToken;
            throw new Exception("Invalid Signature " + token );
        } finally {
            final long delta = System.nanoTime() - startVerification;
            logger.debug("JWT Token Verification took {} ns", delta);
        }
    }

    /**
     * Key for the Cache Size in configuration
     */
    public final static String CACHE_SIZE = "cache" ;

    /**
     * Key for the secret key to be read from the secret manager in configuration
     */
    public final static String SECRET_KEY = "secret-key" ;

    /**
     * Key for the issuer key to be read from the secret manager in configuration
     */
    public final static String ISSUER = "issuer" ;

    /**
     * Key for the secret manager name in configuration
     */
    public final static String SECRET_MANAGER = "secrets" ;

    /**
     * Key for the expiry time offset in configuration in Seconds
     */
    public final static String EXPIRY = "expiry" ;


    /**
     * Key for the allowed risky paths
     */
    final static String RISKS = "risks" ;

    /**
     * Constructs an Authenticator
     * Sets the maxCapacity to 0 by default, because it should JWT should not store anything
     * It is self-signed
     */
    public JWTAuthenticator(){
        super();
        super.maxCapacity = 0; // JWT does not need caching
    }

    /**
     * A creator for JWT Authenticator Data Source
     */
    public static DataSource.Creator JWT = (name, config, parent) -> {

        final String keyForSecretKey = config.getOrDefault(SECRET_KEY, "").toString();
        logger.info("{} : key for secret key is : [{}]", name, keyForSecretKey);

        final String keyForIssuer = config.getOrDefault(ISSUER, "").toString();
        logger.info("{} : key for issuer is : [{}]", name,  keyForIssuer);

        final String secretManagerName = config.getOrDefault( SECRET_MANAGER, "").toString();
        logger.info("{} : secret manager is : [{}]", name, secretManagerName);

        final SecretManager sm = DataSource.dataSourceOrElse(secretManagerName, SecretManager.DEFAULT);
        logger.info("{} : loaded secret manager is : [{}]", name, sm.getClass());

        final String secretKey = parent.template( keyForSecretKey, sm.env());
        logger.info("{} : loaded secret key is : [{}]", name, secretKey);

        final String issuer = parent.template(keyForIssuer, sm.env());
        logger.info("{} : loaded jwt issuer is : [{}]", name, issuer);

        final Set<String> risks = Set.<String>copyOf((List) config.getOrDefault(RISKS, Collections.emptyList()));
        logger.info("{} : loaded risks are : [{}]", name, risks);

        final JWTAuthenticator authenticator = new JWTAuthenticator() {
            final long expiryOffset = ZNumber.integer( config.getOrDefault( EXPIRY, super.expiryOffset()), super.expiryOffset()).longValue();
            @Override
            public String secretKey() {
                return secretKey;
            }
            @Override
            public String issuer() {
                return issuer;
            }
            @Override
            public long expiryOffset() {
                return expiryOffset;
            }

            @Override
            public Set<String> risks() {
                return risks;
            }

        };
        // setup cache, the token verification is a CPU intensive process
        authenticator.maxCapacity = ZNumber.integer( config.getOrDefault( CACHE_SIZE, 0), 0).intValue();
        logger.info("{} : max cache for jwt token auth is : [{}]", name, authenticator.maxCapacity);
        logger.info("{} : default expiry for jwt token auth is : [{}] seconds", name, authenticator.expiryOffset());
        return DataSource.dataSource(name, authenticator);
    };
}
