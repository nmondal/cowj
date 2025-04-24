package cowj.plugins;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class GooglePubSubWrapperTest {

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

    }
}
