package cowj.plugins;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.*;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import cowj.EitherMonad.Nothing ;
/**
 * Azure Event Bus Wrapper
 * Lets one do Queuing stuff on top of Azure Eventing
 */

public interface AzureEventBusWrapper extends MessageQueue<ServiceBusReceivedMessage, Nothing, Boolean, Nothing> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(AzureEventBusWrapper.class);

    ServiceBusSenderClient sender();

    Queue<ServiceBusReceivedMessage> queue();

    @Override
    default EitherMonad<Nothing> put(String messageBody) {
        return EitherMonad.run( () -> sender().sendMessage(new ServiceBusMessage(messageBody)));
    }

    @Override
    default EitherMonad<Boolean> putAll(String... messages) {
        return EitherMonad.call( () ->{
            final ServiceBusSenderClient sender  = sender();
            ServiceBusMessageBatch messageBatch = sender.createMessageBatch();
            boolean all = Arrays.stream(messages).filter( m ->!messageBatch.tryAddMessage(new ServiceBusMessage(m)) )
                    .toList().isEmpty();
            sender.sendMessages(messageBatch);
            return all;
        });
    }

    @Override
    default EitherMonad<ServiceBusReceivedMessage> get() {
        return EitherMonad.call( () -> {
            Queue<ServiceBusReceivedMessage> q = queue();
            if (q.isEmpty()) {
                // TODO log here
                TimeUnit.SECONDS.sleep(waitTime());
            }
            return q.remove();
        });
    }

    @Override
    default EitherMonad<List<ServiceBusReceivedMessage>> getAll(int maxNumberOfMessages) {
        return EitherMonad.call( () ->{
            Queue<ServiceBusReceivedMessage> q = queue();
            int curSize = q.size();
            if ( curSize < maxNumberOfMessages ){
                // TODO log here
                TimeUnit.SECONDS.sleep(waitTime());
            }
            int min = Math.min( q.size(), maxNumberOfMessages );
            // TODO log here too
            return IntStream.range(0,min).boxed().map( i -> q.remove() ).toList();
        });
    }

    @Override
    default EitherMonad<Nothing> delete(ServiceBusReceivedMessage serviceBusReceivedMessage) {
        return EitherMonad.error( new UnsupportedOperationException("Delete is not supported for Azure Event Bus!"));
    }

    interface AsyncMessageProcessor{

        void message( ServiceBusReceivedMessageContext context);

        void error( ServiceBusErrorContext context);
    }

    /**
     * <a href="https://stackoverflow.com/questions/69661714/azure-service-bus-send-messages-synchronously">...</a>
     * Existential Pain for use cases like this
     */
    final class AsyncToSync implements  AsyncMessageProcessor {

        final Queue<ServiceBusReceivedMessage> queueBuffer = new ConcurrentLinkedQueue<>();

        @Override
        public void message(ServiceBusReceivedMessageContext context) {
            ServiceBusReceivedMessage message = context.getMessage();
            logger.info("Processing message. Session: {}, Sequence #: {}. Contents: {}",
                    message.getMessageId(),
                    message.getSequenceNumber(), message.getBody());
            queueBuffer.add(message);
        }

        @Override
        public  void error(ServiceBusErrorContext context) {
            logger.error("Error when receiving messages from namespace: '{}'. Entity: '{}'",
                    context.getFullyQualifiedNamespace(), context.getEntityPath());

            if (!(context.getException() instanceof ServiceBusException exception)) {
                logger.error("Non-ServiceBusException occurred", context.getException());
                return;
            }

            ServiceBusFailureReason reason = exception.getReason();

            if (reason == ServiceBusFailureReason.MESSAGING_ENTITY_DISABLED
                    || reason == ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND
                    || reason == ServiceBusFailureReason.UNAUTHORIZED) {
                logger.error("An unrecoverable error occurred. Stopping processing with reason {}: {}",
                        reason, exception.getMessage());
            } else if (reason == ServiceBusFailureReason.MESSAGE_LOCK_LOST) {
                logger.error("Message lock lost for message", context.getException());
            } else if (reason == ServiceBusFailureReason.SERVICE_BUSY) {
                try {
                    // Choosing an arbitrary amount of time to wait until trying again.
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    logger.error("Unable to sleep for period of time");
                }
            } else {
                logger.error("Error source {}, reason {}, error", context.getErrorSource(),
                        reason, context.getException());
            }
        }
    }


    String NAMESPACE  = "ns" ;

    /**
     * A DataSource.Creator for AzureEventBusWrapper
     */
    DataSource.Creator AEB = (name, config, parent) -> {

        // is name there?
        String qName = config.getOrDefault(QUEUE_NAME, "").toString();
        if (qName.isEmpty())
            throw new IllegalArgumentException("'name: queue name' should be present in config!");
        logger.info("[{}] name of the queue before transform is '{}'", name, qName);
        qName = parent.envTemplate(qName);
        logger.info("[{}] name of the queue after transform is '{}'", name, qName);

        String nameSpace = config.getOrDefault(NAMESPACE, "").toString();
        if (nameSpace.isEmpty())
            throw new IllegalArgumentException("'ns:namespace' should be present in config!");
        logger.info("[{}] namespace of the queue before transform is '{}'", name, nameSpace);
        nameSpace = parent.envTemplate(nameSpace);
        logger.info("[{}] namespace of the queue after transform is '{}'", name, nameSpace);
        nameSpace = nameSpace + ".servicebus.windows.net" ;
        logger.info("[{}] fully qualified namespace of the queue after transform is '{}'", name, nameSpace);

        final int waitTime = ZNumber.integer(config.getOrDefault(WAIT_TIME, 42), 42).intValue();
        logger.info("[{}] wait time of the queue  is '{}'", name, waitTime);


        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();

        final ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(nameSpace)
                .credential(credential)
                .sender()
                .queueName(qName)
                .buildClient();

        final Queue<ServiceBusReceivedMessage> queue;

        if ( waitTime < 0 ){
            logger.info("[{}] will not be used to read message!", name);
            queue = new LinkedList<>();
        } else {
            final AsyncToSync asyncToSync = new AsyncToSync();
            ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
                    .fullyQualifiedNamespace(nameSpace)
                    .credential(credential)
                    .processor()
                    .queueName(qName)
                    .processMessage(asyncToSync::message)
                    .processError(asyncToSync::error)
                    .buildProcessorClient();
            queue = asyncToSync.queueBuffer ;
            processorClient.start();
        }
        final String url = nameSpace + "/" + qName ;
        final AzureEventBusWrapper wrapper = new AzureEventBusWrapper() {

            @Override
            public ServiceBusSenderClient sender() {
                return senderClient;
            }

            @Override
            public Queue<ServiceBusReceivedMessage> queue() {
                return queue;
            }

            @Override
            public int waitTime() {
                return waitTime;
            }

            @Override
            public String url() {
                return url;
            }
        };
        return DataSource.dataSource(name, wrapper);
    };
}
