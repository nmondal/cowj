package cowj.plugins;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import cowj.DataSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface FCMWrapper {
    FirebaseMessaging messaging();
    String TITLE = "title" ;
    String BODY = "body" ;
    String IMAGE = "image" ;
    String TOKEN = "token" ;
    String TOKENS = "tokens" ;
    String DATUM = "data" ;

    private Message message(Map<String,Object> message){
        Message.Builder builder = Message.builder();
        Object v = message.get(TOKEN);
        if ( v != null ){
            builder.setToken( v.toString() );
        }
        Notification.Builder b = Notification.builder();
        v = message.get(TITLE);
        if ( v != null ){
            b.setTitle( v.toString() );
        }
        v = message.get(BODY);
        if (  v != null ){
            b.setBody( v.toString() );
        }
        v = message.get(IMAGE);
        if (  v != null ){
            b.setImage( v.toString() );
        }
        // other properties gets added like as is...
        Map<String, Object> data = (Map<String, Object>) message.getOrDefault(DATUM, Collections.emptyMap());
        data.forEach((key, value) -> builder.putData(key, value.toString()));
        return builder.setNotification( b.build() ).build();
    }

    private MulticastMessage multicastMessage(Map<String,Object> message){
        MulticastMessage.Builder builder = MulticastMessage.builder();
        List<String> tokens = (List) message.getOrDefault(TOKENS, Collections.emptyList());
        builder.addAllTokens( tokens );
        Notification.Builder b = Notification.builder();
        Object v = message.get(TITLE);
        if ( v != null ){
            b.setTitle( v.toString() );
        }
        v = message.get(BODY);
        if (  v != null ){
            b.setBody( v.toString() );
        }
        v = message.get(IMAGE);
        if (  v != null ){
            b.setImage( v.toString() );
        }
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
