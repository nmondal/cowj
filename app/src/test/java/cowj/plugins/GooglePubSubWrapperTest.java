package cowj.plugins;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

public class GooglePubSubWrapperTest {

    ApiFuture<String> as = new ApiFuture<>() {
        @Override
        public void addListener(Runnable listener, Executor executor) {

        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            return "42";
        }

        @Override
        public String get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return "42";
        }
    };

    GooglePubSubWrapper instance = new GooglePubSubWrapper() {

        Publisher pub = mock( Publisher.class );
        Subscriber sub = mock( Subscriber.class ) ;

        @Override
        public String projectId() {
            return "tmp";
        }

        @Override
        public String idPubSub() {
            return "tmp";
        }

        @Override
        public Publisher publisher() {
            return pub;
        }

        @Override
        public Subscriber subscriber() {
            return sub;
        }

        @Override
        public Queue<PubsubMessage> bufferQueue() {
            return null;
        }

        @Override
        public int waitTime() {
            return 42;
        }
    };

    @Test
    public void createPubTest() throws IOException {
        MockedStatic<Publisher> gPub = mockStatic(Publisher.class) ;
        Publisher pub = mock( Publisher.class);
        Publisher.Builder b = mock( Publisher.Builder.class );
        when( b.build()).thenReturn( pub );
        gPub.when( () -> Publisher.newBuilder( (TopicName)any() )).thenReturn( b );
        Map<String,Object> conf = Map.of( "project-id", "foo", "pub-sub-id" , "foo" );
        GooglePubSubWrapper gps =  GooglePubSubWrapper.PUB_SUB.create("foo", conf, () ->"." ).any() ;
        assertNotNull( gps );
        assertNull( gps.subscriber() );
        assertSame( pub, gps.publisher() );
        assertEquals( "foo", gps.projectId() );
        assertEquals( "foo", gps.idPubSub() );
        assertEquals( 4, gps.asyncWaitTime() );
        assertThrows( IllegalArgumentException.class,
                () -> GooglePubSubWrapper.PUB_SUB.create("foo",  Map.of(), () -> ".")) ;
        assertThrows( IllegalArgumentException.class,
                () -> GooglePubSubWrapper.PUB_SUB.create("foo",  Map.of("project-id", "xxx"), () -> ".")) ;

        gps.stop(10);
    }

    @Test
    public void createSubTest(){
        Map<String,Object> asyncConf = Map.of( "project-id", "foo", "pub-sub-id" , "foo" , "sub", "");

        GooglePubSubWrapper gps =  GooglePubSubWrapper.PUB_SUB.create("foo", asyncConf, () ->"." ).any() ;
        assertNotNull( gps );
        assertNull( gps.publisher() );
        assertNotNull( gps.subscriber() );
        assertTrue( gps.bufferQueue() instanceof SynchronousQueue);

        Map<String,Object> syncConf = Map.of( "project-id", "foo", "pub-sub-id" , "foo" , "sync", 4);

        gps =  GooglePubSubWrapper.PUB_SUB.create("foo", syncConf, () ->"." ).any() ;
        assertNotNull( gps );
        assertNull( gps.publisher() );
        assertNotNull( gps.subscriber() );
        assertTrue( gps.bufferQueue() instanceof ArrayBlockingQueue);
        gps.stop(10);
    }

    @Test
    public void messageTest(){
        PubsubMessage m = instance.message( "hello!") ;
        String s = instance.body( m );
        assertEquals( "hello!" , s );
        assertEquals( m.getMessageId() , instance.id(m) );
        assertEquals( "tmp://tmp" , instance.url() );
    }

    @Test
    public void messagePutTest()  {
        Publisher p = instance.publisher();
        when(p.publish(any() )).thenReturn(as);
        assertEquals( "42",  instance.put("hi").value() );
        assertEquals(List.of("42", "42") , instance.putAll("a", "b").value() );
    }

    @Test
    public void messageDeleteTest()  {
        PubsubMessage m = instance.message( "hello!") ;
        assertSame( GooglePubSubWrapper.NOT_IMPLEMENTED,  instance.delete( m ) );
    }

    @Test
    public void messageGetTest()  {

    }
}
