package cowj.plugins;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import zoomba.lang.core.types.ZTypes;

import java.util.*;
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
     * Key for the SecretManager to be used
     */
    String SECRET_MANAGER = "secrets";

    static  <T> T  getFromConfig(Map<String,Object> config , Model parent, String myValue, T defaultValue){
        final String mySecretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
        SecretManager sm =  DataSource.dataSourceOrElse(mySecretManagerName, SecretManager.DEFAULT);
        String urlJson = parent.template(myValue, sm.env());
        try {
            return (T) ZTypes.json(urlJson);
        }catch (Throwable t){
            logger.error("There was an error loading '{}' from secret manager : {}" , myValue, t.getMessage() );
            return defaultValue;
        }
    }


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
     * Use this function to create any Secret Manager
     * Config object must have a key 'config'
     * If the value of the key is string, this is used to redirect and load a string which would be a JSON object
     * This JSON object should be a map of string,string and thus would be used as the basal dictionary
     *   - List of keys to load
     *   - Map - where keys are keys to load and values are descriptions
     * @since  Sept 2024
     * @param name of the Secret Manager in the config
     * @param config a Map Object which depicts how to create a Secret Manager
     * @param parent parent ModelRunner for the SecretManager
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
            entries = ((Map)cfg).keySet() ;
        }

        final Map<String,String> envMap = new HashMap<>();
        // now just iterate and load
        entries.forEach(secret -> {
            logger.info("SecretManager [{}] secret key name is [{}]", name, secret);
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
     * Project Identifier is mandatory and is the field 'project-id'
     */
    DataSource.Creator GSM = (name, config, parent) -> EitherMonad.call( () -> {
        logger.info("Started Creating Google Secret Manager [{}]",name);
        SecretManagerServiceClient client = SecretManagerServiceClient.create();
        final String projectID = config.getOrDefault("project-id", "").toString();
        final Function<String, String> secretFetcher = (secret) -> {
            AccessSecretVersionResponse resp = client.accessSecretVersion(SecretVersionName.of(projectID, secret, "latest"));
            return resp.getPayload().getData().toStringUtf8();
        };
        final SecretManager secretManager = secretManager(name, config, parent, secretFetcher);
        return DataSource.dataSource(name, secretManager);
    }).ensure().value();

    /**
     * A DataSource.Creator for AWS SecretManager
     */
    DataSource.Creator ASM = (name, config, parent) -> {
        logger.info("Started Creating AWS Secret Manager [{}]",name);
        SecretsManagerClient secretsClient = SecretsManagerClient.create();
        final Function<String, String> secretFetcher = (secret) -> {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                        .secretId(secret)
                        .build();
            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            return valueResponse.secretString();
        };
        final SecretManager secretManager = secretManager(name, config, parent, secretFetcher);
        return DataSource.dataSource(name, secretManager);
    };

    /**
     * This primarily exists for testability
     * W/o this, there would be no way to test AzureKeyVault w/o actual Impl with Azure
     */
    final class AzureKeyVaultClientCreator{
        /**
         * Creates a SecretClient from vaultURL and default cred
         * @param vaultUrl url of the vault
         * @return SecretClient
         */
        static SecretClient create( String vaultUrl){
            return new SecretClientBuilder()
                    .vaultUrl(vaultUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        }
    }

    /**
     * A DataSource.Creator for Azure KeyVault Secret
     * Key Vault Url is specified using "url" property
     * <a href="https://learn.microsoft.com/en-us/java/api/overview/azure/security-keyvault-secrets-readme?view=azure-java-stable">...</a>
     */
    DataSource.Creator AKV = (name, config, parent) -> {
        logger.info("Started Creating Azure Key Vault Secret Manager [{}]",name);
        String vaultUrl = config.getOrDefault("url", "").toString();
        logger.info("AKV SecretManager [{}] vault url is [{}]", name, vaultUrl);
        vaultUrl = parent.envTemplate(vaultUrl);
        logger.info("AKV SecretManager [{}] transformed vault url is [{}]", name, vaultUrl);

        final SecretClient secretClient = AzureKeyVaultClientCreator.create(vaultUrl);

        final Function<String,String> secretFetcher = (secret) -> {
            KeyVaultSecret retrievedSecret = secretClient.getSecret(secret);
            return retrievedSecret.getValue();
        };
        final SecretManager secretManager = secretManager(name, config, parent, secretFetcher );
        return DataSource.dataSource(name, secretManager);
    };
}