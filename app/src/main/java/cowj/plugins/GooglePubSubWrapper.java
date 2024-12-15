package cowj.plugins;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.MessageQueue;
import cowj.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * A Wrapper around Google Pub Sub Infra
 * This is slightly different from other Message Queue because it is pure async
 * Although we have made it synchronous
 */
public interface GooglePubSubWrapper extends MessageQueue<PubsubMessage, String, List<String>, EitherMonad.Nothing> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(GooglePubSubWrapper.class);

    /**
     * A field which is a Not Implemented error monad
     */
    EitherMonad<EitherMonad.Nothing> NOT_IMPLEMENTED = EitherMonad.error(new UnsupportedOperationException("We do not support Delete!"));

    /**
     * Key to represent that use this as subscriber
     */
    String SUB = "sub";


    /**
     * Key to tell to store the data into a buffer, by making it synchronous queue
     * Points to an integer - defining the size of the buffer
     */
    String SYNC = "sync";

    /**
     * The key to be used to identify the project id for the instance
     * In the configuration map
     */
    String PROJECT_ID = "project-id";

    /**
     * The key to be used to identify the
     * topic-id for the instance for publisher
     * subscription id for the instance for subscriber
     * At a time, can only act as either publisher or subscriber
     * In the configuration map
     */
    String SUBSCRIPTION_ID = "pub-sub-id";

    /**
     * A Google Project Id
     * @return a Google project id
     */
    String projectId();

    /**
     * A Google Pub Sub identifier
     * @return in case publisher then a topic id , if subscription then subscription id
     */
    String idPubSub();

    /**
     * How much time to wait to gather async message into sync
     * Defaulted to the base waitTime() function which has no use
     * @return time in seconds
     */
    default int asyncWaitTime(){
        return waitTime();
    }

    /**
     * The underlying Publisher
     * @return a Publisher
     */
    Publisher publisher();

    /**
     * The underlying Subscriber
     * @return a Subscriber
     */
    Subscriber subscriber();

    /**
     * Creates a PubsubMessage from a string
     * @param m string data for the message
     * @return a PubsubMessage
     */
    default PubsubMessage message(String m) {
        ByteString data = ByteString.copyFromUtf8(m);
        return PubsubMessage.newBuilder().setData(data).build();
    }

    @Override
    default String url() {
        return projectId() + "://" + idPubSub();
    }

    @Override
    default String body(PubsubMessage message) {
        return message.getData().toStringUtf8();
    }

    @Override
    default String id(PubsubMessage message) {
        return message.getMessageId();
    }

    @Override
    default EitherMonad<String> put(String message) {
        final PubsubMessage m = message(message);
        ApiFuture<String> res = publisher().publish(m);
        return EitherMonad.call(() -> res.get(asyncWaitTime(), TimeUnit.SECONDS));
    }

    @Override
    default EitherMonad<List<String>> putAll(String... messages) {
        final Publisher p = publisher();
        List<ApiFuture<String>> res = Arrays.stream(messages).map(m -> p.publish(message(m))).toList();
        return EitherMonad.call(() -> res.stream().map(
                apiFuture -> EitherMonad.runUnsafe(
                        () -> apiFuture.get(asyncWaitTime(), TimeUnit.SECONDS)
                )
        ).toList());
    }

    /**
     * This is not implemented
     * <a href="https://stackoverflow.com/questions/55223199/is-there-a-way-to-delete-a-message-from-pubsub-message-store">...</a>
     * @param pubsubMessage message to be deleted
     * @return EitherMonad.Nothing specifically see NOT_IMPLEMENTED field
     */
    @Override
    default EitherMonad<EitherMonad.Nothing> delete(PubsubMessage pubsubMessage) {
        return NOT_IMPLEMENTED;
    }

    /**
     * In case the 'sync' mode is set, tries to get a bunch of message within a timeout
     * @param maxNumberOfMessages the no of messages to get from the bufferQueue()
     * @param timeOutInMS time out in milli sec
     * @return EitherMonad of List of PubsubMessage
     */
    default EitherMonad<List<PubsubMessage>> getAll(int maxNumberOfMessages, long timeOutInMS) {
        if (maxNumberOfMessages <= 0) throw new IllegalArgumentException("Num message should be > 0");
        final Queue<PubsubMessage> mq = bufferQueue();
        return EitherMonad.call(() -> {
            final long timeWeHave = timeOutInMS ;
            final List<PubsubMessage> res = new ArrayList<>();
            long timeSpent = 0L;
            final long startTime = System.currentTimeMillis();
            while ( res.size() < maxNumberOfMessages && timeSpent < timeWeHave ){
                final boolean inError = EitherMonad.call(mq::remove).then( (res::add) ).inError();
                if ( inError ) {
                    Thread.sleep(42L);
                    timeSpent = System.currentTimeMillis() - startTime  ;
                }
            }
            return res;
        });
    }

    /**
     * In case the 'sync' mode is set, tries to get one message within timeout
     * @param timeOutInMS time out in milli sec
     * @return EitherMonad of one PubsubMessage
     */
    default EitherMonad<PubsubMessage> get( long timeOutInMS) {
        return getAll(1, timeOutInMS).then(l -> l.get(0));
    }


    @Override
    default EitherMonad<List<PubsubMessage>> getAll(int maxNumberOfMessages) {
        return getAll( maxNumberOfMessages, asyncWaitTime() * 1000L );
    }

    @Override
    default EitherMonad<PubsubMessage> get() {
        return get( asyncWaitTime() * 1000L );
    }

    /**
     * Stops the client within the time frame of some seconds
     * @param numSec no of seconds timeout
     */
    default void stop(int numSec) {
        Publisher p = publisher();
        if (p != null) {
            p.shutdown();
            EitherMonad.call(() -> p.awaitTermination(numSec, TimeUnit.SECONDS));
        }
        Subscriber s = subscriber();
        if (s != null) {
            EitherMonad.call(s::stopAsync);
        }
    }

    /**
     * A Message Queue
     * This is only important when we are forcing synchrony as subscriber
     * Otherwise it points to a SynchronousQueue which is useless
     * @return a Queue of type PubsubMessage
     */
    Queue<PubsubMessage> bufferQueue();

    /**
     * A Creator for Google Pub Sub
     */
    DataSource.Creator PUB_SUB = (name, config, parent) -> {

        final String pre_projectId = config.getOrDefault(PROJECT_ID, "").toString();
        if (pre_projectId.isEmpty())
            throw new IllegalArgumentException("'project-id' : project id should be present in config!");
        logger.info("[{}] project id of the queue before transform is '{}'", name, pre_projectId);
        final String projectId = parent.envTemplate(pre_projectId);
        logger.info("[{}] project id of the queue after transform is '{}'", name, projectId);

        final String pre_pub_subId = config.getOrDefault(SUBSCRIPTION_ID, "").toString();
        if (pre_pub_subId.isEmpty())
            throw new IllegalArgumentException("'pub-sub-id' : pub/sub id should be present in config!");
        logger.info("[{}] pub-sub-id of the queue before transform is '{}'", name, pre_pub_subId);
        final String pub_subId = parent.envTemplate(pre_pub_subId);
        logger.info("[{}] pub-sub-id of the queue after transform is '{}'", name, pub_subId);

        final int waitTime = ZNumber.integer(config.getOrDefault(WAIT_TIME, 4), 4).intValue();
        logger.info("[{}] default async wait time of the queue  is '{}'", name, waitTime);

        final boolean isSub = config.containsKey(SUB);
        logger.info("[{}] is subscription ? '{}'", name, isSub);

        final Publisher publisher;
        final Subscriber subscriber;

        final Queue<PubsubMessage> bufferQueue;

        if (isSub) {
            publisher = null;
            logger.info("[{}] subscription id is '{}'", name, pub_subId);
            final boolean doSync = config.containsKey(SYNC);
            logger.info("[{}] synchronous mode '{}'", name, doSync );

            final MessageReceiver receiver;
            if (doSync) {
                final int nBufSize = ZNumber.integer( config.getOrDefault(SYNC, 42) , 42).intValue();
                logger.info("[{}] synchronous buf size '{}'", name, nBufSize );
                bufferQueue = new ArrayBlockingQueue<>(nBufSize);
                receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                    try {
                        bufferQueue.add( message );
                        consumer.ack();
                    }catch (Throwable th){
                        consumer.nack();
                    }
                };
            } else {
                final String messageHandler = config.getOrDefault(SUB, "").toString();
                final Scriptable scriptable = Scriptable.UNIVERSAL.create("gps://" + name, messageHandler);
                bufferQueue = new SynchronousQueue<>();
                receiver =
                        (PubsubMessage message, AckReplyConsumer consumer) -> {
                            Bindings bindings = new SimpleBindings(Map.of("msg", message));
                            final boolean processingError = EitherMonad.call(() -> scriptable.exec(bindings)).inError();
                            if (processingError) {
                                // send again
                                consumer.nack();
                            } else {
                                // did process it, so carry on
                                consumer.ack();
                            }
                        };
            }

            ProjectSubscriptionName subscriptionName =
                    ProjectSubscriptionName.of(projectId, pub_subId);
            subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        } else {
            subscriber = null;
            bufferQueue = new SynchronousQueue<>();
            logger.info("[{}] topic id is '{}'", name, pub_subId);
            TopicName topicName = TopicName.of(projectId, pub_subId);
            publisher = EitherMonad.runUnsafe(() -> Publisher.newBuilder(topicName).build());
        }

        GooglePubSubWrapper wrapper = new GooglePubSubWrapper() {

            @Override
            public Queue<PubsubMessage> bufferQueue() {
                return bufferQueue;
            }

            @Override
            public String projectId() {
                return projectId;
            }

            @Override
            public String idPubSub() {
                return pub_subId;
            }

            @Override
            public Publisher publisher() {
                return publisher;
            }

            @Override
            public Subscriber subscriber() {
                return subscriber;
            }

            @Override
            public int waitTime() {
                return waitTime;
            }
        };
        return DataSource.dataSource(name, wrapper);
    };
}
