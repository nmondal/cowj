package cowj.plugins;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import cowj.DataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import zoomba.lang.core.types.ZTypes;

import java.io.IOException;
import java.util.Map;


/**
 * Abstraction for Runtime Environment for a Cowj App
 */
public interface SecretManager {

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
     * Creates a SecretManager from configuration
     * @param map the configuration
     * @return a SecretManager
     */
    private static SecretManager from(Map<String, String> map) {
        return () -> map;
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
            final SecretManager secretManager = from(object);
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
            final SecretManager secretManager = from(object);
            return DataSource.dataSource(name, secretManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * A DataSource.Creator for Azure Keyvault Secret
     */
    DataSource.Creator AKS = (name, config, parent) -> {
    try {
        String secretKey = config.getOrDefault("config", "").toString();
        String secret = parent.envTemplate(secretKey);
        SecretClient secretClient = new SecretClientBuilder() //<https://learn.microsoft.com/en-us/java/api/overview/azure/security-keyvault-secrets-readme?view=azure-java-stable>
                .vaultUrl(config.getOrDefault("vaultUrl", "").toString()) // this way no need for change everytime
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        KeyVaultSecret retrievedSecret = secretClient.getSecret(secret);
        String jsonString = retrievedSecret.getValue();
        Map object = (Map) ZTypes.json(jsonString);
        final SecretManager secretManager = from(object);
        return DataSource.dataSource(name, secretManager);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}