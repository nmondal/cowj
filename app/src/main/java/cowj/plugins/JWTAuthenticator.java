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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT Token based Authentication provider
 * Idea is copied from :
 * <a href="https://github.com/metamug/java-jwt/blob/master/src/main/java/com/metamug/jwt/JWebToken.java">...</a>
 */
public abstract class JWTAuthenticator extends Authenticator.TokenAuthenticator.CachedAuthenticator {
    final static Logger logger = LoggerFactory.getLogger(JWTAuthenticator.class);
    final long STD_EXPIRY_OFFSET = 24 * 60 * 60 * 1000 ; // 1 day

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
        private String signature;
        private String encodedHeader;
        private String token;

        private JWebToken() {
            encodedHeader = encode(JWT_HEADER);
        }

        /**
         * Creates a JWT token
         * @param sub subject - the user id
         * @param aud audience - list of items where user would have access
         * @param expires time where the token should expire
         */
        public JWebToken(String sub, List<?> aud, long expires) {
            this();
            payload.put("sub", sub);
            payload.put("aud", aud); // Authorization
            payload.put("exp", expires);
            payload.put("iat", System.currentTimeMillis());
            payload.put("iss", JWTAuthenticator.this.issuer());
            payload.put("jti", UUID.randomUUID().toString()); //how do we use this?
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
            final String data = encodedHeader + "." + encode(payload);
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
            return (List)payload.get("aud");
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

        @Override
        public long expiry() {
            return (long)payload.get("exp");
        }

        @Override
        public Map<String, Object> data() {
            return payload;
        }
    }

    /**
     * Authentication header
     */
    public static final String AUTHENTICATION_HEADER = "Authentication" ;

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
     * @param expires expiry time in milli sec
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

    /**
     * Creates a token with parameters
     *  - audience is empty
     *  - expires after suitable configured timeout @see{ expiryOffset() }
     * @param sub subject of the token
     * @return a token object
     */
    public JWebToken jwt(String sub){
        return jwt(sub, System.currentTimeMillis() + expiryOffset() );
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
     * Key for the expiry time offset in configuration
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

        final SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(secretManagerName, SecretManager.DEFAULT);
        logger.info("{} : loaded secret manager is : [{}]", name, sm.getClass());

        final String secretKey = sm.getOrDefault(keyForSecretKey, keyForSecretKey);
        logger.info("{} : loaded secret key is : [{}]", name, secretKey);

        final Set<String> risks = Set.<String>copyOf((List) config.getOrDefault(RISKS, Collections.emptyList()));
        logger.info("{} : loaded risks are : [{}]", name, risks);

        final String issuer = sm.getOrDefault(keyForIssuer, keyForIssuer);
        logger.info("{} : loaded jwt issuer is : [{}]", name, issuer);

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
        return DataSource.dataSource(name, authenticator);
    };
}
