package cowj.plugins;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import cowj.DataSource;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Abstraction for Googles Notification Service Firebase Messaging
 */
public interface FCMWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(FCMWrapper.class);

    /**
     * Underlying FirebaseMessaging
     * @return a FirebaseMessaging instance for the app instance
     */
    FirebaseMessaging messaging();

    /**
     * Key for the title to be used for notification message
     */
    String TITLE = "title";

    /**
     * Key for the body to be used for notification message
     */
    String BODY = "body";

    /**
     * Key for the image url to be used for notification message
     */
    String IMAGE = "image";

    /**
     * Key for the device token to be used for notification message
     */
    String TOKEN = "token";

    /**
     * Key for the list of device tokens to be used for notification message
     */
    String TOKENS = "tokens";

    /**
     * Key for the extra data to be used for notification message
     */
    String DATUM = "data";

    /**
     * Key for the SecretManager to be used to initialize  FCMWrapper instance
     */
    String SECRET_MANAGER = "secrets";

    /**
     * Method to call for a method to be executed if a map has a key
     * @param map - the map
     * @param key - the key
     * @param function the function we should trigger of the map has the key
     */
    static void computeIfPresent(Map<String, Object> map, String key, BiConsumer<String, String> function) {
        // trick to reduce branching at a performance cost
        Object v = map.get(key);
        if (v == null) return;
        function.accept(key, v.toString());
    }

    /**
     * From configuration creates a Message
     * @param message the configuration
     * @return a Message
     */
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

    /**
     * From configuration creates a MulticastMessage
     * @param message the configuration
     * @return a MulticastMessage
     */
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

    /**
     * From configuration creates a MulticastMessage and send the message
     * @param data the configuration
     * @return a BatchResponse
     * @throws FirebaseMessagingException in case of error
     */
    default BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().sendEachForMulticast(multicastMessage(data));
    }

    /**
     * From configuration creates a Message and send the message
     * @param data the configuration
     * @return response string
     * @throws FirebaseMessagingException in case of error
     */
    default String sendMessage(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().send(message(data));
    }

    /**
     * A DataSource.Creator for FCMWrapper
     */
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
            logger.warn("Firebase is already initialized!");
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
