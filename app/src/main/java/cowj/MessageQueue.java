package cowj;



import java.util.List;

/**
 * A Generic Message Queue
 * @param <M> Message Type
 * @param <MR> Send Message Response Type
 * @param <BR> Batch Message Sending Response Type
 * @param <DR> Delete Message Response Type
 */

public interface  MessageQueue<M,MR,BR,DR> {



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
     * Gets a message
     * @return an EitherMonad of type M
     */
    EitherMonad<M> get();

    /**
     * Gets a bunch of  Messages from the Queue
     * @param maxNumberOfMessages max messages to read
     * @return EitherMonad of a List of Message
     */
    EitherMonad<List<M>> getAll(int maxNumberOfMessages);

    /**
     * Puts a single Message in the Queue
     * @param messageBody  body of the message
     * @return EitherMonad of a SendMessageResponse
     */
    EitherMonad<MR> put(String messageBody);

    /**
     * Puts a bunch of Messages in the Queue
     *
     * @param messages  body of the messages
     * @return EitherMonad of a SendMessageBatchResponse
     */
    EitherMonad<BR> putAll(String... messages);


    /**
     * Deletes the message from Queue
     * @param m message to be deleted
     * @return EitherMonad of a DeleteMessageResponse
     */
    EitherMonad<DR> delete(M m);

    /**
     * Key for Name of the queue  in the configuration
     * One of the name or url must be present
     */
    String QUEUE_NAME = "name";

    /**
     * Key for Url of the queue  in the configuration
     * One of the name or url must be present
     */
    String QUEUE_URL = "url";
    /**
     * Key for read timeout of the queue  in the configuration
     * Waits for this many seconds before stating no messages found
     */
    String WAIT_TIME = "timeout";

}
