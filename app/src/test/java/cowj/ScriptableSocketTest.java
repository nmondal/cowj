package cowj;

import org.eclipse.jetty.websocket.api.Session;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScriptableSocketTest {

    static ModelRunner mr;

    @BeforeClass
    public static void before(){
        mr = ModelRunnerTest.runModel("samples/websocket/websocket.yaml" );
    }

    @AfterClass
    public static void after(){
        if ( mr == null ) return;
        mr.stop();
        mr = null;
    }

    @Test
    public void incomingMessagesTest() throws Exception {
        final List<String> messages = new ArrayList<>();
        // TODO handling errors, how?
        final Object[] status = new Object[]{ false, null };
        final WebSocketClient clientEndPoint = new WebSocketClient(new URI("ws://localhost:5555/ws")) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {}

            @Override
            public void onMessage(String message) {
                messages.add(message);
                System.out.println(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                status[0] = true;
            }

            @Override
            public void onError(Exception ex) {
                status[1] = ex;
            }
        };
        clientEndPoint.connect();
        Thread.sleep(1000);
        clientEndPoint.send("foo!");
        Thread.sleep(5000);
        mr.stop();
        Thread.sleep(1500);
        clientEndPoint.close();
        Assert.assertFalse(messages.isEmpty());
        Assert.assertEquals("Welcome!", messages.get(0));
        Assert.assertTrue( messages.contains("ya!") );
        Assert.assertTrue( messages.size() >= 3 );
        Assert.assertEquals(true, status[0]);
    }

    @Test
    public void errorTest(){
        ScriptableSocket ss = ScriptableSocket.socket( "/err", "samples/test_scripts/error_1_arg.zm" );
        Session session = mock(Session.class);
        ss.connected(session); // load it up
        final RuntimeException th = new RuntimeException("Boom!");
        ss.error(session, th );
        doThrow( th ).when( session).sendText( any(), any());
        EitherMonad<?> em = ScriptableSocket.broadcast("/err", "hey!", 3 );
        Assert.assertTrue(em.inError());
        Assert.assertSame(th, em.error());
        ss.closed(session, 42, "boom!" );
    }
}
