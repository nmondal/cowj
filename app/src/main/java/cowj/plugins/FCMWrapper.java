package cowj.plugins;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import cowj.DataSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class FCMWrapper {
    public final FirebaseMessaging messaging;

    public BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging.sendMulticast(
                MulticastMessage.builder()
                        .addAllTokens((List<String>) data.get("_tokens"))
                        .setNotification(
                                Notification.builder()
                                        .setTitle((String) data.get("_title"))
                                        .setBody((String) data.get("_body"))
                                        .build()
                        )
                        .build()
        );
    }

    private FCMWrapper(String name, String authFile) throws Exception {
        InputStream is = new FileInputStream(authFile);
        GoogleCredentials credentials = GoogleCredentials.fromStream(is);
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        try {
            FirebaseApp.initializeApp(options);
        } catch (IllegalStateException e) {
            System.err.println("Firebase is already initialized!");

        } finally {
            messaging = FirebaseMessaging.getInstance();
        }
    }

    public static FCMWrapper from(String name, String authFile){
        try {
            return new FCMWrapper(name, authFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static DataSource.Creator FCM = (name, config, parent) -> {
        String authFile = parent.interpretPath((String) config.get("credentials_file"));
        FCMWrapper fcmWrapper = FCMWrapper.from(name, authFile);
        return new DataSource() {
            @Override
            public Object proxy() {
                return fcmWrapper;
            }
            @Override
            public String name() {
                return name;
            }
        };
    };
}
