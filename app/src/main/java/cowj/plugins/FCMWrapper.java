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

public final class FCMWrapper {
    public final FirebaseMessaging messaging;

    private Message message(Map<String,Object> message){
        Message.Builder builder = Message.builder();
        Object v = message.get("_token");
        if ( v != null ){
            builder.setToken( v.toString() );
        }
        Notification.Builder b = Notification.builder();
        v = message.get("_title");
        if ( v != null ){
            b.setTitle( v.toString() );
        }
        v = message.get("_body");
        if (  v != null ){
            b.setBody( v.toString() );
        }
        v = message.get("_image");
        if (  v != null ){
            b.setImage( v.toString() );
        }
        // other properties gets added like as is...
        message.keySet().stream().filter(  k -> !k.startsWith("_")).forEach( k -> {
            Object x = message.get(k);
            builder.putData(k, x.toString());
        });
        return builder.setNotification( b.build() ).build();
    }

    private MulticastMessage multicastMessage(Map<String,Object> message){
        MulticastMessage.Builder builder = MulticastMessage.builder();
        List<String> tokens = (List) message.getOrDefault("_tokens", Collections.emptyList());
        builder.addAllTokens( tokens );
        Notification.Builder b = Notification.builder();
        Object v = message.get("_title");
        if ( v != null ){
            b.setTitle( v.toString() );
        }
        v = message.get("_body");
        if (  v != null ){
            b.setBody( v.toString() );
        }
        v = message.get("_image");
        if (  v != null ){
            b.setImage( v.toString() );
        }
        // other properties gets added like as is...
        message.keySet().stream().filter(  k -> !k.startsWith("_")).forEach( k -> {
            Object x = message.get(k);
            builder.putData(k, x.toString());
        });
        return builder.setNotification( b.build() ).build();
    }

    public BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging.sendMulticast(multicastMessage(data));
    }

    public String sendMessage(Map<String, Object> data) throws FirebaseMessagingException {
        return messaging.send(message(data));
    }

    private FCMWrapper(String authFile) throws Exception {
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

    public static FCMWrapper from(String authFile){
        try {
            return new FCMWrapper(authFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static DataSource.Creator FCM = (name, config, parent) -> {
        String authFile = parent.interpretPath((String) config.get("credentials_file"));
        FCMWrapper fcmWrapper = FCMWrapper.from(authFile);
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
