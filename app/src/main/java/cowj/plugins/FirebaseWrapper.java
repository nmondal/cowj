package cowj.plugins;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import cowj.DataSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

public final class FirebaseWrapper  {

    public final FirebaseMessaging messaging;

    public static Message.Builder messageBuilder(){
        return Message.builder();
    }

    public static MulticastMessage.Builder multicastMessageBuilder(){
        return MulticastMessage.builder();
    }

    public Message message(Map<String,Object> message){
        // TODO Hemil does this
        return messageBuilder().build();
    }

    public MulticastMessage multicastMessage(Map<String,Object> message){
        // TODO Hemil does this
        return MulticastMessage.builder().build();
    }

    private FirebaseWrapper(String name, String authFile) throws Exception {
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

    public static FirebaseWrapper from(String name, String authFile){
        try {
            return new FirebaseWrapper(name, authFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static {
        DataSource.Creator FCM = (name, config, parent) -> {
            String authFile = parent.interpretPath((String) config.get("credentials_file"));
            FirebaseWrapper firebaseWrapper = FirebaseWrapper.from(name, authFile);
            return new DataSource() {
                @Override
                public Object proxy() {
                    return firebaseWrapper;
                }
                @Override
                public String name() {
                    return name;
                }
            };
        };
        DataSource.REGISTRY.put("fcm", FCM);
    }

}
