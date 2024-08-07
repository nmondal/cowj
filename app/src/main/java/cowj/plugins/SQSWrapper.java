package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import zoomba.lang.core.types.ZNumber;

import java.util.List;
import java.util.stream.IntStream;

/**
 * AWS SQS Wrapper
 * Lets one do Queuing stuff on SQS
 */
public interface SQSWrapper {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(SQSWrapper.class);

    /**
     * Gets the underlying SqsClient
     *
     * @return Underlying SqsClient
     */
    SqsClient sqsClient();


    /**
     * time to wait before response with no message
     *
     * @return time to wait before response with no message
     */
    int waitTime();

    /**
     * Queue URL
     *
     * @return URL for the queue in question
     */
    String url();


    /**
     * List down all the queues with the prefix - to return the list of urls
     *
     * @param sqsClient a SqsClient
     * @param prefix a prefix for the queue names
     * @return list of queue urls
     */
    static EitherMonad<List<String>> listQueues(SqsClient sqsClient, String prefix) {
        return EitherMonad.call(() -> {
            ListQueuesRequest listQueuesRequest = ListQueuesRequest.builder().queueNamePrefix(prefix).build();
            ListQueuesResponse listQueuesResponse = sqsClient.listQueues(listQueuesRequest);
            return listQueuesResponse.queueUrls();
        });
    }

    /**
     * Given a named queue, returns the queue url
     *
     * @param sqsClient a SqsClient
     * @param queueName name of the queue
     * @return EitherMonad of corresponding url for that queue
     */
    static EitherMonad<String> url(SqsClient sqsClient, String queueName) {
        return EitherMonad.call(() -> {
            GetQueueUrlResponse getQueueUrlResponse =
                    sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            return getQueueUrlResponse.queueUrl();
        });
    }

    /**
     * Sends message to the queue url
     *
     * @param sqsClient a SqsClient
     * @param queueUrl     queue url where we should send the message
     * @param delaySeconds amount of seconds to delay the message
     * @param messageBody  body of the message
     * @return EitherMonad of SendMessageResponse
     */
    static EitherMonad<SendMessageResponse> sendMessage(SqsClient sqsClient, String queueUrl, int delaySeconds, String messageBody) {
        final SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .delaySeconds(delaySeconds)
                .build();
        return EitherMonad.call(() -> sqsClient.sendMessage(sendMsgRequest));
    }

    /**
     * Sends a bunch of messages to the queue
     *
     * @param sqsClient a SqsClient
     * @param queueUrl     queue url where we should send the message
     * @param delaySeconds amount of seconds to delay each of the messages
     * @param messages     an arbitrary number of message bodies to send
     * @return EitherMonad of SendMessageBatchResponse
     */
    static EitherMonad<SendMessageBatchResponse> sendMessages(SqsClient sqsClient, String queueUrl, int delaySeconds, String... messages) {
        if (messages.length == 0)
            return EitherMonad.error(new IllegalArgumentException("At least one message needed!"));
        SendMessageBatchRequestEntry[] entries = new SendMessageBatchRequestEntry[messages.length];
        IntStream.range(0, messages.length).forEach((inx) -> {
            final String messageId = String.valueOf(inx);
            final String messageBody = messages[inx];
            entries[inx] = SendMessageBatchRequestEntry.builder().id(messageId).messageBody(messageBody).delaySeconds(delaySeconds).build();
        });
        SendMessageBatchRequest sendMessageBatchRequest = SendMessageBatchRequest.builder()
                .queueUrl(queueUrl).entries(entries).build();
        return EitherMonad.call(() -> sqsClient.sendMessageBatch(sendMessageBatchRequest));
    }

    /**
     * Gets a List of Message from the queue
     *
     * @param sqsClient a SqsClient
     * @param queueUrl queue url from where we should read the message
     * @param waitTimeSeconds time to wait before response with no message
     * @param maxNumberOfMessages maximum no of messages to read
     * @return EitherMonad of List of Message
     */
    static EitherMonad<List<Message>> getMessages(SqsClient sqsClient, String queueUrl, int waitTimeSeconds, int maxNumberOfMessages) {
        return EitherMonad.call(() -> {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl).waitTimeSeconds(waitTimeSeconds)
                    .maxNumberOfMessages(maxNumberOfMessages)
                    .build();
            return sqsClient.receiveMessage(receiveMessageRequest).messages();
        });
    }

    /**
     * Gets a single message, if exists
     *
     * @param sqsClient a SqsClient
     * @param queueUrl queue url from where we should read the message
     * @param waitTimeSeconds time to wait before response with no message
     * @return EitherMonad of Message
     */
    static EitherMonad<Message> getMessage(SqsClient sqsClient, String queueUrl, int waitTimeSeconds) {
        EitherMonad<List<Message>> em = getMessages(sqsClient, queueUrl, waitTimeSeconds, 1);
        if (em.inError()) return EitherMonad.error(em.error());
        return EitherMonad.call(() -> em.value().get(0));
    }

    /**
     * Deletes a message from the queue
     *
     * @param sqsClient a SqsClient
     * @param queueUrl queue url from where we should delete the message
     * @param message the message to delete
     * @return EitherMonad of DeleteMessageResponse
     */
    static EitherMonad<DeleteMessageResponse> deleteMessage(SqsClient sqsClient, String queueUrl, Message message) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();
        return EitherMonad.call(() -> sqsClient.deleteMessage(deleteMessageRequest));
    }


    /**
     * Gets a single Message from the SQS
     * @return EitherMonad of a Message
     */
    default EitherMonad<Message> get(){
        return getMessage(sqsClient(), url(), waitTime());
    }

    /**
     * Gets a bunch of  Messages from the SQS
     * @param maxNumberOfMessages max messages to read
     * @return EitherMonad of a List of Message
     */
    default EitherMonad<List<Message>> getAll(int maxNumberOfMessages){
        return getMessages( sqsClient(), url(), waitTime(), maxNumberOfMessages );
    }

    /**
     * Puts a single Message in the SQS
     * @param messageBody  body of the message
     * @return EitherMonad of a SendMessageResponse
     */
    default EitherMonad<SendMessageResponse> put(String messageBody){
        return putDelayed(messageBody,0);
    }

    /**
     * Puts a single Message in the SQS
     * @param messageBody  body of the message
     * @param delaySec time to delay the message by integer amount
     * @return EitherMonad of a SendMessageResponse
     */
    default EitherMonad<SendMessageResponse> putDelayed(String messageBody, int delaySec){
        return sendMessage(sqsClient(), url(), delaySec, messageBody);
    }

    /**
     * Puts a bunch of Messages in the SQS
     *
     * @param messages  body of the messages
     * @return EitherMonad of a SendMessageBatchResponse
     */
    default EitherMonad<SendMessageBatchResponse> putAll(String... messages){
        return putAllDelayed(0, messages);
    }

    /**
     * Puts a bunch of Messages in the SQS
     * @param delaySec time to delay the messages by integer amount
     * @param messages  body of the messages
     * @return EitherMonad of a SendMessageBatchResponse
     */
    default EitherMonad<SendMessageBatchResponse> putAllDelayed(int delaySec, String... messages){
        return sendMessages(sqsClient(), url(), delaySec, messages);
    }

    /**
     * Deletes the message from SQS
     * @param m message to be deleted
     * @return EitherMonad of a DeleteMessageResponse
     */
    default EitherMonad<DeleteMessageResponse> delete(Message m){
        return deleteMessage(sqsClient(), url(), m);
    }

    /**
     * Key for Name of the queue  in the configuration
     * One of the name or url must be present
     */
    String QUEUE_NAME = "name" ;

    /**
     * Key for Url of the queue  in the configuration
     * One of the name or url must be present
     */
    String QUEUE_URL = "url" ;

    /**
     * Key for read timeout of the queue  in the configuration
     * Waits for this many seconds before stating no messages found
     */
    String WAIT_TIME = "timeout" ;

    /**
     * A DataSource.Creator for SQSWrapper
     * <a href="https://stackoverflow.com/questions/68951722/how-to-authenticate-between-ec2-and-sqs-and-local-machine-to-sqs">...</a>
     */
    DataSource.Creator SQS = (name, config, parent) -> {
        final SqsClient sqsClient = EitherMonad.runUnsafe(SqsClient::create);
        String url = config.getOrDefault( QUEUE_URL, "").toString();
        if ( url.isEmpty() ){
            // is name there?
            String qName = config.getOrDefault( QUEUE_NAME, "").toString();
            if ( qName.isEmpty() ) throw new IllegalArgumentException("Either 'url' or 'name' should be present in config!");
            logger.info("[{}] name of the queue before transform is '{}'", name, qName );
            qName = parent.envTemplate( qName );
            logger.info("[{}] name of the queue after transform is '{}'", name, qName );
            EitherMonad<String> qU = url( sqsClient, qName ) ;
            if ( qU.inError() ) throw new IllegalArgumentException(qU.error() );
            url = qU.value();
        }else{
            logger.info("[{}] url of the queue before transform is '{}'", name, url );
            url = parent.envTemplate( url );
        }
        final String queueUrl = url;
        logger.info("[{}] final url of the queue after transform is '{}'", name, url );
        final int waitTime = ZNumber.integer( config.getOrDefault( WAIT_TIME, 42), 42).intValue();
        logger.info("[{}] wait time of the queue  is '{}'", name, waitTime );

        final SQSWrapper wrapper = new SQSWrapper() {
            @Override
            public SqsClient sqsClient() {
                return sqsClient;
            }

            @Override
            public int waitTime() {
                return waitTime;
            }

            @Override
            public String url() {
                return queueUrl ;
            }
        };
        return DataSource.dataSource(name, wrapper);
    };

}
