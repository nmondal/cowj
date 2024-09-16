package cowj.plugins;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import cowj.DataSource;
import cowj.Model;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * Abstraction for Runtime Environment for a Cowj App
 */
public interface SecretManager {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(SecretManager.class);


    /**
     * Underlying Environment
     * @return a map
     */
    Map<String,String> env();

    /**
     * Gets the value of a key, else return default
     * @param key to be fetched value against
     * @param defaultVal in case key does not exist return this
     * @return value against the key, if key not found returns defaultVal
     */
    default String getOrDefault(String key, String defaultVal) {
        return env().getOrDefault(key, defaultVal);
    }

    /**
     * The key to the configuration which has an exhaustive list of secret keys
     * Which we would be using in this application
     * If the value pointed to is a String, then this is a pointer to the actual JSON object having the values
     */
    String SECRET_KEYS = "config" ;


    /**
     * The key to the configuration which specifies the time in seconds to reload the keys
     * If this is less than or equal to zero, does not reload
     */
    String RELOAD_INTERVAL = "reload" ;

    /**
     * Use this function to create any Secret Manager
     * @since  Sept 2024
     * @param config a Map Object
     * @param secretFetcher a Function that takes a secret key and maps it to values
     * @return a SecretManager
     */
    static SecretManager secretManager(String name, Map config, Model parent, Function<String,String> secretFetcher){
        logger.info("SecretManager [{}] creations started",name);
        final Object cfg = config.get( SECRET_KEYS );
        if ( cfg == null ){ throw new IllegalArgumentException("SecretManager must have a 'config' element!"); }
        if ( cfg instanceof String ){
            logger.info("SecretManager [{}] config points to nested json string, will transform and load", name);
            String secret = parent.envTemplate(cfg.toString());
            logger.info("SecretManager [{}] transformed key is [{}]", name, secret);
            // this is the case where we have a nested json to run things through
            String jsonString = secretFetcher.apply(secret);
            Map obj = (Map)ZTypes.json( jsonString);
            return () -> obj;
        }
        final Collection<String> entries;

        if ( cfg instanceof List<?>){
            logger.info("SecretManager [{}] using list as secret entries", name);
            entries = (List)cfg ;
        } else {
            logger.info("SecretManager [{}] using Map as secret entries", name);
            entries = ((Map)cfg).entrySet() ;
        }

        final long reloadInSeconds = ZNumber.integer( config.getOrDefault( RELOAD_INTERVAL, -42 ), -4).longValue();

        final Map<String,String> envMap ;
        if ( reloadInSeconds <= 0 ){
            envMap = new HashMap<>();
        } else {
            final ExpiringMap<String,String> eMap = ExpiringMap.builder()
                    .maxSize( entries.size() )
                    .expiration( reloadInSeconds, TimeUnit.SECONDS).build();
            ExpirationListener<String,String> listener = (k,v) -> {
                String secretValue = secretFetcher.apply(k);
                eMap.put(k, secretValue) ;
            };
            eMap.addExpirationListener(listener);
            envMap = eMap;
        }
        // now just iterate and load
        entries.stream().forEach( secret -> {
            secret = parent.envTemplate(secret);
            logger.info("SecretManager [{}] transformed key is [{}]", name, secret);
            String secretValue = secretFetcher.apply(secret);
            envMap.put(secret, secretValue) ;
        } );
        return () -> envMap ;
    }

    /**
     * A Local System Environment based DataSource.Creator
     */
    DataSource.Creator LOCAL = (name, config, parent) -> new DataSource() {
        @Override
        public Object proxy() {
            return DEFAULT;
        }

        @Override
        public String name() {
            return name;
        }
    };

    /**
     * A Local System Environment based SecretManager
     */
    SecretManager DEFAULT = System::getenv;

    /**
     * A DataSource.Creator for Google SecretManager
     */
    DataSource.Creator GSM = (name, config, parent) -> {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            String secretKey = config.getOrDefault("config", "").toString();
            String secret = parent.envTemplate(secretKey);
            String projectID = config.getOrDefault("project-id", "").toString();
            AccessSecretVersionResponse resp = client.accessSecretVersion(SecretVersionName.of(projectID, secret, "latest"));
            String jsonString = resp.getPayload().getData().toStringUtf8();
            Map object = (Map) ZTypes.json(jsonString);
            final SecretManager secretManager = () -> object;
            return DataSource.dataSource(name, secretManager);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * A DataSource.Creator for AWS SecretManager
     */
    DataSource.Creator ASM = (name, config, parent) -> {
        try(SecretsManagerClient secretsClient = SecretsManagerClient.create()) {
            String secretKey = config.getOrDefault("config", "").toString();
            String secret = parent.envTemplate(secretKey);
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secret)
                    .build();

            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            String jsonString = valueResponse.secretString();
            Map object = (Map) ZTypes.json(jsonString);
            final SecretManager secretManager = () -> object;
            return DataSource.dataSource(name, secretManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * A DataSource.Creator for Azure KeyVault Secret
     * Key Vault Url is specified using "url" property
     * <a href="https://learn.microsoft.com/en-us/java/api/overview/azure/security-keyvault-secrets-readme?view=azure-java-stable">...</a>
     */
    DataSource.Creator AKV = (name, config, parent) -> {

        String vaultUrl = config.getOrDefault("url", "").toString();
        logger.info("AKV SecretManager [{}] vault url is [{}]", name, vaultUrl);
        vaultUrl = parent.envTemplate(vaultUrl);
        logger.info("AKV SecretManager [{}] transformed vault url is [{}]", name, vaultUrl);

        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        Function<String,String> secretFetcher = (secret) -> {
            KeyVaultSecret retrievedSecret = secretClient.getSecret(secret);
            return retrievedSecret.getValue();
        };
        final SecretManager secretManager = secretManager(name, config, parent, secretFetcher );
        return DataSource.dataSource(name, secretManager);
    };
}