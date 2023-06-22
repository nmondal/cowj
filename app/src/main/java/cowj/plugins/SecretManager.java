package cowj.plugins;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import cowj.DataSource;
import zoomba.lang.core.types.ZTypes;

import java.io.IOException;
import java.util.Map;

public interface SecretManager {
    Map<String,String> env();

    default String getOrDefault(String key, String defaultVal) {
        return env().getOrDefault(key, defaultVal);
    }

    private static SecretManager from(Map<String, String> map) {
        return () -> map;
    }

    DataSource.Creator LOCAL = (name, config, parent) -> new DataSource() {
        @Override
        public Object proxy() {
            return from(System.getenv());
        }

        @Override
        public String name() {
            return name;
        }
    };

    DataSource.Creator GSM = (name, config, parent) -> {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            String secret = config.getOrDefault("config", "").toString();
            String projectID = config.getOrDefault("project-id", "").toString();
            AccessSecretVersionResponse resp = client.accessSecretVersion(SecretVersionName.of(projectID, secret, "latest"));
            String jsonString = resp.getPayload().getData().toStringUtf8();
            Map object = (Map) ZTypes.json(jsonString);

            return new DataSource() {
                @Override
                public Object proxy() {
                    return from(object);
                }

                @Override
                public String name() {
                    return name;
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
}
