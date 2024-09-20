package cowj.plugins;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.QueueProperties;
import com.azure.storage.queue.models.SendMessageResult;
import cowj.DataSource;
import cowj.EitherMonad;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ASQWrapperTest {

    @Test
    public void creationTest(){
        DataSource ds = ASQWrapper.ASQ.create( "foo",
                Map.of("name", "foo-bar", "url", "https://foo.bar.com/foo-bar"), () -> ".");
        ASQWrapper asq = ds.any();
        Assert.assertEquals( 42, asq.waitTime() );
        Assert.assertEquals( 42, asq.visibility() );
        Assert.assertEquals( "https://foo.bar.com/foo-bar", asq.url() );
        Assert.assertNotNull( asq.client() );

        // Also now test the
        final String body = "hello" ;
        final String id = "id" ;
        QueueMessageItem mi = mock(QueueMessageItem.class);
        BinaryData bd = BinaryData.fromString(body);
        when(mi.getBody()).thenReturn(bd);
        when(mi.getMessageId()).thenReturn(id);

        // here are the bunch of tests for message parsing
        Assert.assertEquals(id, asq.id(mi));
        Assert.assertEquals(body, asq.body(mi));
    }

    @Test
    public void creationFailureTest(){
        IllegalArgumentException ex = Assert.assertThrows( IllegalArgumentException.class, () ->{
            ASQWrapper.ASQ.create( "foo", Map.of(), () -> ".");
        });
        Assert.assertTrue( ex.getMessage().contains( "'name'" ));

        ex = Assert.assertThrows( IllegalArgumentException.class, () ->{
            ASQWrapper.ASQ.create( "foo",
                    Map.of("name", "bar"), () -> ".");
        });
        Assert.assertTrue( ex.getMessage().contains( "'url'" ));
    }

    static ASQWrapper createMockWrapper(QueueClient qc){
        // Create the stuff
        return new ASQWrapper() {
            @Override
            public QueueClient client() {
                return qc;
            }

            @Override
            public int visibility() {
                return 0;
            }

            @Override
            public int waitTime() {
                return 0;
            }

            @Override
            public String url() {
                return "";
            }
        } ;
    }

    @Test
    public void sendMessageTest(){
        final String id = "id" ;
        SendMessageResult sr = mock(SendMessageResult.class);
        when(sr.getMessageId()).thenReturn(id) ;
        final QueueClient qc = mock(QueueClient.class);
        when(qc.sendMessage((String)any() )).thenReturn(sr);
        // create
        ASQWrapper asq = createMockWrapper(qc);

        // Now put
        EitherMonad<SendMessageResult> single = asq.put("hello");
        Assert.assertTrue( single.isSuccessful() );
        Assert.assertEquals( id, single.value().getMessageId() );

        // Put multiple
        EitherMonad<List<SendMessageResult>> plural = asq.putAll("hello", "hi", "foo" );
        Assert.assertTrue( plural.isSuccessful() );
        Assert.assertFalse( plural.value().isEmpty() );
        plural.value().forEach( x -> Assert.assertEquals(id, x.getMessageId() ));
    }

    @Test
    public void queueSizeTest(){
        QueueProperties qp = mock(QueueProperties.class);
        when(qp.getApproximateMessagesCount()).thenReturn(42);
        final QueueClient qc = mock(QueueClient.class);
        when(qc.getProperties()).thenReturn(qp);
        // create
        ASQWrapper asq = createMockWrapper(qc);
        Assert.assertEquals(42, asq.size() );
    }

    @Test
    public void deleteTest(){
        QueueMessageItem mi = mock(QueueMessageItem.class);
        when(mi.getMessageId()).thenReturn("id") ;
        when(mi.getPopReceipt()).thenReturn("pop") ;
        final QueueClient qc = mock(QueueClient.class);
        // create
        ASQWrapper asq = createMockWrapper(qc);
        Assert.assertTrue( asq.delete(mi).isSuccessful() );
        Assert.assertFalse( asq.delete(null).isSuccessful() );
    }


    @Test
    public void insufficientMessagesTest(){
        final QueueClient qc = mock(QueueClient.class);
        PagedIterable<QueueMessageItem> pi = mock(PagedIterable.class);
        when(pi.stream()).thenReturn( Stream.of());
        when(qc.receiveMessages( any(), any(), any() , any() )).thenReturn(pi);
        // create
        ASQWrapper asq = createMockWrapper(qc);
        EitherMonad<?> em = asq.get();
        Assert.assertTrue( em.inError() );
        em = asq.getAll(10);
        Assert.assertTrue( em.inError() );

    }

    @Test
    public void singleMessageGetTest(){
        QueueMessageItem mi = mock(QueueMessageItem.class);
        List<QueueMessageItem> l = List.of(mi,mi,mi) ;

        final QueueClient qc = mock(QueueClient.class);
        PagedIterable<QueueMessageItem> pi = mock(PagedIterable.class);
        when(pi.stream()).thenReturn( l.stream() );
        when(qc.receiveMessages( any(), any(), any() , any() )).thenReturn(pi);
        // create
        ASQWrapper asq = createMockWrapper(qc);
        EitherMonad<?> single = asq.get();
        Assert.assertTrue( single.isSuccessful() );

    }

    @Test
    public void multipleMessageGetTest(){
        QueueMessageItem mi = mock(QueueMessageItem.class);
        List<QueueMessageItem> l = List.of(mi,mi,mi) ;

        final QueueClient qc = mock(QueueClient.class);
        PagedIterable<QueueMessageItem> pi = mock(PagedIterable.class);
        when(pi.stream()).thenReturn( l.stream() );
        when(qc.receiveMessages( any(), any(), any() , any() )).thenReturn(pi);
        // create
        ASQWrapper asq = createMockWrapper(qc);

        EitherMonad<List<QueueMessageItem>> plural = asq.getAll(10);
        Assert.assertTrue( plural.isSuccessful() );
        Assert.assertEquals( 3, plural.value().size() );
    }
}
