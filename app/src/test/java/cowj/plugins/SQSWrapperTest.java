package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class SQSWrapperTest {

    final String sqsURL = "sqs.us-east-1.amazonaws.com/1234567890/default_development";
    final String sqsQName = "sqs-queue";

    static MockedStatic<SqsClient> sqsStatic = null ;

    @Before
    public void before(){
        // set AWS Region
        System.setProperty("aws.region", "ap-south-1" );
        sqsStatic = null ;
    }

    @After
    public void after(){
        System.clearProperty("aws.region");
        if ( sqsStatic != null  ){
            sqsStatic.close();
        }
    }

    @Test
    public void initViaURLTest() {
        DataSource ds = SQSWrapper.SQS.create("foo", Map.of("url", sqsURL), () -> ".");
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name());
    }

    @Test
    public void initViaNameTest() {
        sqsStatic =  mockStatic(SqsClient.class);
        SqsClient mockClient = mock( SqsClient.class );
        GetQueueUrlResponse mockResp = mock(GetQueueUrlResponse.class);
        when(mockResp.queueUrl()).thenReturn(sqsURL);
        when(mockClient.getQueueUrl((GetQueueUrlRequest)any())).thenReturn( mockResp );
        sqsStatic.when( SqsClient::create ).thenReturn( mockClient );

        DataSource ds = SQSWrapper.SQS.create("foo", Map.of("name", sqsQName), () -> ".");
        Assert.assertNotNull(ds);
        Assert.assertEquals("foo", ds.name());
    }

    @Test
    public void listUrlsTest(){
        SqsClient mockClient = mock( SqsClient.class );
        ListQueuesResponse mockResp = mock(ListQueuesResponse.class);
        when(mockResp.queueUrls()).thenReturn(List.of(sqsURL));
        when(mockClient.listQueues((ListQueuesRequest)any())).thenReturn( mockResp );
        List<String> urls = SQSWrapper.listQueues(mockClient, "*" ).value();
        Assert.assertEquals(1, urls.size());
    }

    @Test
    public void crashBurnSQSClientTest(){
        final Throwable toBeThrown = new IllegalArgumentException("Invalid!");
        sqsStatic =  mockStatic(SqsClient.class);
        sqsStatic.when( SqsClient::create ).thenThrow( toBeThrown );
        Throwable th = assertThrows(Throwable.class, () -> {
            SQSWrapper.SQS.create("foo", Map.of("url", sqsURL), () -> ".");
        });
        Assert.assertEquals(toBeThrown, th);
    }

    @Test
    public void crashBurnOnInvalidQNameTest(){
        // name is wrongly specified
        Throwable th = assertThrows(Throwable.class, () -> {
            SQSWrapper.SQS.create("foo", Map.of("name", sqsQName), () -> ".");
        });
        Assert.assertTrue( th instanceof  RuntimeException );
        Assert.assertNotNull(th.getCause());

        // Nothing is specified
        th = assertThrows(Throwable.class, () -> {
            SQSWrapper.SQS.create("foo", Map.of(), () -> ".");
        });
        Assert.assertTrue( th instanceof  IllegalArgumentException );

    }

    @Test
    public void sendMessagesTest(){
        sqsStatic =  mockStatic(SqsClient.class);
        SqsClient mockClient = mock( SqsClient.class );
        SendMessageResponse mockResp = mock( SendMessageResponse.class );
        SendMessageBatchResponse mockMultiResp = mock(SendMessageBatchResponse.class);

        when(mockClient.sendMessage( (SendMessageRequest) any() )).thenReturn(mockResp);
        when(mockClient.sendMessageBatch( (SendMessageBatchRequest) any() )).thenReturn(mockMultiResp);

        sqsStatic.when( SqsClient::create ).thenReturn( mockClient );
        DataSource ds = SQSWrapper.SQS.create("foo", Map.of("url", sqsURL), () -> ".");
        SQSWrapper sqsWrapper = ds.any();

        EitherMonad<SendMessageResponse> em = sqsWrapper.put("hello");
        Assert.assertTrue( em.isSuccessful() );
        Assert.assertEquals( mockResp, em.value() );

        EitherMonad<SendMessageBatchResponse> emm = sqsWrapper.putAll("hello", "hi" );
        Assert.assertTrue( emm.isSuccessful() );
        Assert.assertEquals( mockMultiResp, emm.value() );

    }

    @Test
    public void readMessageTest(){
        sqsStatic =  mockStatic(SqsClient.class);
        SqsClient mockClient = mock( SqsClient.class );
        Message sqsMessage = mock(Message.class);
        List<Message> lm = List.of(sqsMessage);
        ReceiveMessageResponse mockResponse = mock(ReceiveMessageResponse.class);
        when(mockResponse.messages()).thenReturn(lm);
        when(mockClient.receiveMessage( (ReceiveMessageRequest)any() ) ).thenReturn(mockResponse);

        sqsStatic.when( SqsClient::create ).thenReturn( mockClient );
        DataSource ds = SQSWrapper.SQS.create("foo", Map.of("url", sqsURL), () -> ".");
        SQSWrapper sqsWrapper = ds.any();

        EitherMonad<Message> em = sqsWrapper.get();
        Assert.assertTrue( em.isSuccessful() );
        Assert.assertEquals( sqsMessage, em.value() );

    }

    @Test
    public void deleteMessageTest(){
        sqsStatic =  mockStatic(SqsClient.class);
        SqsClient mockClient = mock( SqsClient.class );
        Message sqsMessage = mock(Message.class);
        DeleteMessageResponse mockResponse = mock(DeleteMessageResponse.class);
        when(mockClient.deleteMessage( (DeleteMessageRequest) any() ) ).thenReturn(mockResponse);

        sqsStatic.when( SqsClient::create ).thenReturn( mockClient );
        DataSource ds = SQSWrapper.SQS.create("foo", Map.of("url", sqsURL), () -> ".");
        SQSWrapper sqsWrapper = ds.any();

        EitherMonad<DeleteMessageResponse> em = sqsWrapper.delete(sqsMessage);
        Assert.assertTrue( em.isSuccessful() );
        Assert.assertEquals( mockResponse, em.value() );

    }

}
