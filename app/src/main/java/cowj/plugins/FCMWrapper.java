package cowj.plugins;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import cowj.DataSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface FCMWrapper {
    FirebaseMessaging messaging();
    String TITLE = "title" ;
    String BODY = "body" ;
    String IMAGE = "image" ;
    String TOKEN = "token" ;
    String TOKENS = "tokens" ;
    String DATUM = "data" ;

    static void computeIfPresent(Map<String,Object> map, String key, BiConsumer<String,String> function){
        // trick to reduce branching at a performance cost
        Object v = map.get(key);
        if ( v == null ) return;
        function.accept(key, v.toString());
    }

    static Message message(Map<String,Object> message){

        Message.Builder builder = Message.builder();
        computeIfPresent( message, TOKEN, (k,v) -> builder.setToken(v) );
        Notification.Builder b = Notification.builder();
        computeIfPresent(message, TITLE, (k,v) -> b.setTitle(v) );
        computeIfPresent(message, BODY, (k,v) -> b.setBody(v) );
        computeIfPresent(message, IMAGE, (k,v) -> b.setImage(v) );
        // other properties gets added like as is...
        Map<String, Object> data = (Map<String, Object>) message.getOrDefault(DATUM, Collections.emptyMap());
        data.forEach((key, value) -> builder.putData(key, value.toString()));
        return builder.setNotification( b.build() ).build();
    }

    static MulticastMessage multicastMessage(Map<String,Object> message){
        MulticastMessage.Builder builder = MulticastMessage.builder();
        List<String> tokens = (List) message.getOrDefault(TOKENS, Collections.emptyList());
        builder.addAllTokens( tokens );
        Notification.Builder b = Notification.builder();
        computeIfPresent(message, TITLE, (k,v) -> b.setTitle(v) );
        computeIfPresent(message, BODY, (k,v) -> b.setBody(v) );
        computeIfPresent(message, IMAGE, (k,v) -> b.setImage(v) );
        // other properties gets added like as is...
        Map<String, Object> data = (Map<String, Object>) message.getOrDefault(DATUM, Collections.emptyMap());
        data.forEach((key, value) -> builder.putData(key, value.toString()));
        return builder.setNotification( b.build() ).build();
    }

    default BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().sendMulticast(multicastMessage(data));
    }

    default String sendMessage(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging().send(message(data));
    }

    DataSource.Creator FCM = (name, config, parent) -> {
        FCMWrapper fcmWrapper = () -> {
            try {
                try {
                    FirebaseApp.initializeApp();
                } catch (IllegalStateException e) {
                    System.err.println("Firebase is already initialized!");
                }

                return FirebaseMessaging.getInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
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
