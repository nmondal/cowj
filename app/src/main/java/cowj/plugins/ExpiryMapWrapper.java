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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public interface ExpiryMapWrapper {
    /**
     * Logger for the wrapper
     */
    Logger logger = LoggerFactory.getLogger(ExpiryMapWrapper.class);

    String MAX_SIZE = "size" ;

    String EXPIRY_TIME = "expiry" ;

    String POLICY = "policy" ;

    String LISTENER = "listener";

    String KEY = "_key" ;

    String VALUE = "_Value" ;

    ExpiringMap<String, Object> map();

    ExpirationListener<String,Object> EXPIRATION_LISTENER = (k,v ) -> logger.info( "Item Expired : {} -> {}", k, v );

    DataSource.Creator CACHE = (name, config, parent) -> {

        final int maxSize = (int)config.getOrDefault(MAX_SIZE, 32) ;
        final long expiry = (int)config.getOrDefault(EXPIRY_TIME, 30000) ;
        final String policy = config.getOrDefault(POLICY, "CREATED").toString().toUpperCase(Locale.ROOT) ;
        final String listenerPath = config.getOrDefault(LISTENER, "").toString();
        final ExpiringMap<String,Object> map = ExpiringMap.builder()
                .maxSize(maxSize).expiration(expiry, TimeUnit.MILLISECONDS).expirationPolicy( ExpirationPolicy.valueOf(policy))
                .build();

        Scriptable scriptable = Scriptable.UNIVERSAL.create ( "_expiry_listener_",  parent.interpretPath( listenerPath));
        ExpirationListener<String,Object> el = (k,v) -> {
            Bindings bindings = new SimpleBindings();
            bindings.put(KEY, k );
            bindings.put(VALUE, v );
            try {
                scriptable.exec(bindings);
            } catch (Throwable th) {
                logger.error("Error while processing Expiry Listener : {}", th.toString() );
            }
        };
        map.addAsyncExpirationListener(EXPIRATION_LISTENER);
        map.addAsyncExpirationListener(el);
        final ExpiryMapWrapper expiryMapWrapper = () -> map;
        return DataSource.dataSource(name, expiryMapWrapper);
    };
}
