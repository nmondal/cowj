package cowj.plugins;

import cowj.DataSource;
import cowj.Scriptable;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An ExpiringMap Map Wrapper Implementation
 */
public interface ExpiryMapWrapper {
    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(ExpiryMapWrapper.class);

    /**
     * key for Maximum Size of the Map
     */
    String MAX_SIZE = "size" ;

    /**
     * key for Time to expiry
     */
    String EXPIRY_TIME = "expiry" ;

    /**
     * key for expiry policy, can be "created" or "accessed"
     */
    String POLICY = "policy" ;

    /**
     * key for  expiry event listener
     */
    String LISTENER = "listener";

    /**
     * key for key to be passed in Scriptable listener
     */

    String KEY = "_key" ;

    /**
     * key for value to be passed in Scriptable listener
     */
    String VALUE = "_Value" ;

    /**
     * An ExpiringMap created from the configuration
     * @return an ExpiringMap
     */
    ExpiringMap<String, Object> map();

    /**
     * A Synchronized Map wrapper over ExpiringMap created from the configuration
     * @return a Synchronized Map wrapper over  ExpiringMap
     */
    default Map<String,Object> shared(){
        return Collections.synchronizedMap(map());
    }

    /**
     * A Creator for the ExpiryMapWrapper
     */
    DataSource.Creator CACHE = (name, config, parent) -> {

        final int maxSize = (int)config.getOrDefault(MAX_SIZE, 32) ;
        logger.info("ExpiryMap '{}' maxSize {}", name, maxSize);
        final long expiry = (int)config.getOrDefault(EXPIRY_TIME, 60000) ;
        logger.info("ExpiryMap '{}' expiration in ms {}", name, expiry);
        final String policy = config.getOrDefault(POLICY, "CREATED").toString().toUpperCase(Locale.ROOT) ;
        logger.info("ExpiryMap '{}' expiry policy {}", name, policy);
        final String listenerPath = config.getOrDefault(LISTENER, "").toString();
        logger.info("ExpiryMap '{}' listener {}", name, listenerPath);
        final ExpiringMap<String,Object> map = ExpiringMap.builder()
                .maxSize(maxSize).expiration(expiry, TimeUnit.MILLISECONDS).expirationPolicy( ExpirationPolicy.valueOf(policy))
                .build();
        Scriptable scriptable = Scriptable.UNIVERSAL.create ( "_expiry_listener_",  parent.interpretPath( listenerPath));
        ExpirationListener<String,Object> customHandler = (k,v) -> {
            Bindings bindings = new SimpleBindings();
            bindings.put(KEY, k );
            bindings.put(VALUE, v );
            try {
                logger.info( "In {} before processing custom expiry handler : {} -> {}", name, k, v );
                scriptable.exec(bindings);
                logger.info( "In {} after processing custom expiry handler : {} -> {}", name, k, v );
            } catch (Throwable th) {
                logger.error("In {} error while processing Expiry Listener : {}", name , th.toString() );
            }
        };
        ExpirationListener<String,Object> expirationListener = (k,v ) -> logger.info( "In {} item Expired : {} -> {}", name, k, v );
        map.addAsyncExpirationListener(expirationListener);
        map.addAsyncExpirationListener(customHandler);
        final ExpiryMapWrapper expiryMapWrapper = () -> map;
        return DataSource.dataSource(name, expiryMapWrapper);
    };
}
