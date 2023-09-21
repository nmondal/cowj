package cowj.plugins;

import cowj.Authenticator;
import cowj.DataSource;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
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
    Logger logger = LoggerFactory.getLogger(JWTAuthenticator.class);
    final long STD_EXPIRY_OFFSET = 24 * 60 * 60 * 1000 ; // 1 day

    public abstract String secretKey();
    public abstract String issuer();

    public long expiryOffset(){
        return STD_EXPIRY_OFFSET;
    }

    public class JWebToken implements UserInfo{

        private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        private final Map<String,Object> payload = new TreeMap<>();
        private String signature;
        private String encodedHeader;
        private String token;

        private JWebToken() {
            encodedHeader = encode(JWT_HEADER);
        }

        public JWebToken(Map<String,Object> payload) {
            this(payload.get("sub").toString(),
                    (List)payload.getOrDefault("aud", Collections.emptyList()),
                    (long)payload.getOrDefault("exp",  System.currentTimeMillis() ) );
        }

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
            if (payload.isEmpty()) {
                throw new Exception("Payload is Empty: ");
            }
            if (!payload.containsKey("exp")) {
                throw new Exception("Payload doesn't contain expiry " + payload);
            }
            signature = parts[2];
        }

        @Override
        public String toString() {
            return encodedHeader + "." + encode(payload) + "." + signature;
        }

        public boolean isSignatureValid() {
            final String data = encodedHeader + "." + encode(payload);
            final String computed = hmacSha256(data, JWTAuthenticator.this.secretKey());
            return signature.equals(computed); //signature matched
        }

        public String getSubject() {
            return payload.get("sub").toString();
        }

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
            } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
                logger.error("Error while creating SHA : {}", ex.toString());
                return null;
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

    public static final String AUTHENTICATION_HEADER = "Authentication" ;

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

    public JWebToken jwt(String sub,long expires, List<?> aud){
        return new JWebToken(sub, aud, expires);
    }
    public JWebToken jwt(String sub, long expires){
        return jwt(sub,  expires, Collections.emptyList());
    }
    public JWebToken jwt(String sub){
        return jwt(sub, System.currentTimeMillis() + expiryOffset() );
    }

    @Override
    protected UserInfo tryGetUserInfo(String token) throws Exception {
        JWebToken jWebToken = new JWebToken(token);
        if ( jWebToken.isSignatureValid() ) return jWebToken;
        throw new Exception("Invalid Signature " + token );
    }

    public final static String SECRET_KEY = "secret-key" ;
    public final static String ISSUER = "issuer" ;
    public final static String SECRET_MANAGER = "secrets" ;

    public final static String EXPIRY = "expiry" ;

    public JWTAuthenticator(){
        super();
        super.maxCapacity = 0; // JWT does not need caching
    }
    public static DataSource.Creator JWT = (name, config, parent) -> {

        final String keyForSecretKey = config.getOrDefault(SECRET_KEY, "").toString();

        final String keyForIssuer = config.getOrDefault(ISSUER, "").toString();

        final String secretManagerName = config.getOrDefault( SECRET_MANAGER, "").toString();

        final SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(secretManagerName, SecretManager.DEFAULT);

        final String secretKey = sm.getOrDefault(keyForSecretKey, keyForSecretKey);

        final String issuer = sm.getOrDefault(keyForIssuer, keyForIssuer);

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
        };
        return DataSource.dataSource(name, authenticator);
    };
}
