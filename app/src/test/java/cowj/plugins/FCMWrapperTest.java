package cowj.plugins;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import cowj.DataSource;
import cowj.Model;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static cowj.plugins.FCMWrapper.*;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class FCMWrapperTest {

    private static FCMWrapper fcm = null ;

    @BeforeClass
    public static void boot(){
        Model model = () -> ".";
        DataSource ds = FCMWrapper.FCM.create("foo", Collections.emptyMap(), model );
        Assert.assertNotNull(ds);
        Assert.assertTrue( ds.proxy() instanceof FCMWrapper );
        Assert.assertEquals( "foo", ds.name());
        fcm = (FCMWrapper) ds.proxy();
    }

    @AfterClass
    public static void tearDown(){
        fcm = null;
    }

    @Test
    public void singleMessageTest()  {
        Map<String,Object> payload = Map.of(
                TITLE, "boom",
                BODY, "foo bar",
                IMAGE, "https://something/img.jpg",
                TOKEN, "foobar",
                DATUM, Map.of("x", 42 )
        );
        // fascinating design, impossible to test this payload now
        Message m = FCMWrapper.message(payload);
        Assert.assertNotNull(m);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            fcm.sendMessage(payload);
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void multiCastMessageTest(){
        // do not send image to branch cover...
        Map<String,Object> payload = Map.of(
                TITLE, "boom",
                BODY, "foo bar",
                TOKENS, Arrays.asList("foobar", "barfoo"),
                DATUM, Map.of("x", 42 )
        );
        // fascinating design, impossible to test this payload now
        MulticastMessage m = FCMWrapper.multicastMessage(payload);
        Assert.assertNotNull(m);
        Exception exception = assertThrows(RuntimeException.class, () -> {
            fcm.sendMulticast(payload);
        });
        Assert.assertNotNull(exception);
    }

    @Test
    public void mockMessageSendTest() throws Exception {
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        when(firebaseMessaging.send(any())).thenReturn("foo");
        BatchResponse br = mock(BatchResponse.class);
        when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(br);
        FCMWrapper fcmWrapper = () -> firebaseMessaging;
        String r = fcmWrapper.sendMessage( Map.of("token", "abcd" ));
        Assert.assertEquals( "foo", r);
        BatchResponse ret = fcmWrapper.sendMulticast( Map.of("tokens", Arrays.asList("a","b") ) );
        Assert.assertEquals( br, ret);
    }
}
