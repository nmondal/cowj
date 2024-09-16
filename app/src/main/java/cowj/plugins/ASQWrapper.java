package cowj.plugins;

import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.QueueProperties;
import com.azure.storage.queue.models.SendMessageResult;
import cowj.DataSource;
import cowj.EitherMonad;
import cowj.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zoomba.lang.core.types.ZNumber;

import java.time.Duration;
import java.util.*;

import cowj.EitherMonad.Nothing ;


/**
 *  Azure Storage Queues Wrapper
 * Lets one do Queuing stuff on top of Azure Storage Queue
 * <a href="https://learn.microsoft.com/en-us/answers/questions/6376/difference-between-azure-service-bus-and-amazon-sq">...</a>
 * <a href="https://learn.microsoft.com/en-us/azure/storage/queues/storage-quickstart-queues-java">...</a>
 */
public interface ASQWrapper extends MessageQueue<QueueMessageItem, SendMessageResult, List<SendMessageResult>, Nothing> {

    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(ASQWrapper.class);

    @Override
    default String body(QueueMessageItem message) {
        return message.getBody().toString();
    }

    @Override
    default String id(QueueMessageItem message) {
        return message.getMessageId();
    }

    /**
     * A QueueClient which allows us to use Azure Storage Queue
     * @return underlying QueueClient
     */
    QueueClient client();

    /**
     * How long till we make each item visible for different consumers in Seconds
     * <a href="https://github.com/Azure/azure-sdk-for-net/issues/30716">...</a>
     * @return seconds for making queue item eventual visibility
     */
    int visibility();

    @Override
    default EitherMonad<SendMessageResult> put(String messageBody) {
        return EitherMonad.call( () -> client().sendMessage(messageBody));
    }

    @Override
    default EitherMonad<List<SendMessageResult>> putAll(String... messages) {
        return EitherMonad.call( () ->{
            final QueueClient client  = client();
            return Arrays.stream(messages).map(client::sendMessage).toList();
        });
    }

    @Override
    default EitherMonad<QueueMessageItem> get() {
        return getAll(1).then(List::getFirst);
    }

    /**
     * Approximate size of the queue
     *
     * @return approximate size of the queue
     */
    default long size(){
        QueueProperties properties = client().getProperties();
        return properties.getApproximateMessagesCount();
    }

    @Override
    default EitherMonad<List<QueueMessageItem>> getAll(int maxNumberOfMessages) {
        return EitherMonad.call( () -> client().receiveMessages(maxNumberOfMessages,
                Duration.ofSeconds( visibility() ), // visibility timeout
                Duration.ofSeconds( waitTime()), // duration time out
                Context.NONE // no context
                ).stream().toList());
    }

    @Override
    default EitherMonad<Nothing> delete(QueueMessageItem messageItem) {
        return EitherMonad.run( () -> client().deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt()) );
    }

    /**
     * Key for Visibility Timeout
     * <a href="https://github.com/Azure/azure-sdk-for-net/issues/30716">...</a>
     */
    String VISIBILITY_TIMEOUT = "visibility" ;

    /**
     * A DataSource.Creator for Azure Storage Queues Wrapper
     */
    DataSource.Creator ASQ = (name, config, parent) -> {

        // is name there?
        String qName = config.getOrDefault(QUEUE_NAME, "").toString();
        if (qName.isEmpty())
            throw new IllegalArgumentException("'name' : queue name should be present in config!");
        logger.info("[{}] name of the queue before transform is '{}'", name, qName);
        qName = parent.envTemplate(qName);
        logger.info("[{}] name of the queue after transform is '{}'", name, qName);

        String url = config.getOrDefault(QUEUE_URL, "").toString();
        if (url.isEmpty())
            throw new IllegalArgumentException("'url' : queue url should be present in config!");
        logger.info("[{}] url of the queue before transform is '{}'", name, url);


        final String queueUrl = parent.envTemplate(url);
        logger.info("[{}] url of the queue after transform is '{}'", name, queueUrl);


        final int waitTime = ZNumber.integer(config.getOrDefault(WAIT_TIME, 42), 42).intValue();
        logger.info("[{}] wait time of the queue  is '{}'", name, waitTime);

        final int visibility = ZNumber.integer(config.getOrDefault(VISIBILITY_TIMEOUT, 42), 42).intValue();
        logger.info("[{}] visibility time of the queue  is '{}'", name, visibility);

        final QueueClient queueClient = new QueueClientBuilder()
                .endpoint("https://<storage-account-name>.queue.core.windows.net/")
                .queueName(qName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        final ASQWrapper wrapper = new ASQWrapper() {
            @Override
            public QueueClient client() {
                return queueClient;
            }

            @Override
            public int waitTime() {
                return waitTime;
            }

            @Override
            public int visibility() {
                return visibility;
            }

            @Override
            public String url() {
                return queueUrl;
            }
        };
        return DataSource.dataSource(name, wrapper);
    };
}
