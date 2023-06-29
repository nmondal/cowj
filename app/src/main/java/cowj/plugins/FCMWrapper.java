package cowj.plugins;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import cowj.DataSource;
import cowj.Scriptable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface FCMWrapper {
    FirebaseMessaging messaging();

    String TITLE = "title";
    String BODY = "body";
    String IMAGE = "image";
    String TOKEN = "token";
    String TOKENS = "tokens";
    String DATUM = "data";
    String SECRET_MANAGER = "secrets";

    static void computeIfPresent(Map<String, Object> map, String key, BiConsumer<String, String> function) {
        // trick to reduce branching at a performance cost
        Object v = map.get(key);
        if (v == null) return;
        function.accept(key, v.toString());
    }

    static Message message(Map<String, Object> message) {

        Message.Builder builder = Message.builder();
        computeIfPresent(message, TOKEN, (k, v) -> builder.setToken(v));
        Notification.Builder b = Notification.builder();
        computeIfPresent(message, TITLE, (k, v) -> b.setTitle(v));
        computeIfPresent(message, BODY, (k, v) -> b.setBody(v));
        computeIfPresent(message, IMAGE, (k, v) -> b.setImage(v));
        // other properties gets added like as is...
        Map<String, Object> data = (Map<String, Object>) message.getOrDefault(DATUM, Collections.emptyMap());
        data.forEach((key, value) -> builder.putData(key, value.toString()));
        return builder.setNotification(b.build()).build();
    }

    static MulticastMessage multicastMessage(Map<String, Object> message) {
        MulticastMessage.Builder builder = MulticastMessage.builder();
        List<String> tokens = (List) message.getOrDefault(TOKENS, Collections.emptyList());
        builder.addAllTokens(tokens);
        Notification.Builder b = Notification.builder();
        computeIfPresent(message, TITLE, (k, v) -> b.setTitle(v));
        computeIfPresent(message, BODY, (k, v) -> b.setBody(v));
        computeIfPresent(message, IMAGE, (k, v) -> b.setImage(v));
        // other properties gets added like as is...
        Map<String, Object> data = (Map<String, Object>) message.getOrDefault(DATUM, Collections.emptyMap());
        data.forEach((key, value) -> builder.putData(key, value.toString()));
        return builder.setNotification(b.build()).build();
    }

    default BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().sendMulticast(multicastMessage(data));
    }

    default String sendMessage(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().send(message(data));
    }

    DataSource.Creator FCM = (name, config, parent) -> {
        try {
            String key = config.getOrDefault("key", "").toString();
            if (key.isEmpty()) {
                FirebaseApp.initializeApp();
            } else {
                String secretManagerName = config.getOrDefault(SECRET_MANAGER, "").toString();
                SecretManager sm = (SecretManager) Scriptable.DATA_SOURCES.getOrDefault(secretManagerName, SecretManager.DEFAULT);

                String credentials = sm.getOrDefault(key, "");

                FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(
                                        new ByteArrayInputStream(credentials.getBytes(StandardCharsets.UTF_8))
                                ))
                                .build()
                );
            }

        } catch (IllegalStateException e) {
            System.err.println("Firebase is already initialized!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        FCMWrapper fcmWrapper = FirebaseMessaging::getInstance;
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
