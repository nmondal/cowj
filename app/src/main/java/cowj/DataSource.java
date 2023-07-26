package cowj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Cowj abstraction for anything which deals with data
 */
public interface DataSource {

    /**
     * Logger for the DataSource
     */
    Logger logger = LoggerFactory.getLogger(DataSource.class);

    /**
     * Underlying Actual Mechanism
     * @return Actual Object to be used as a DataSource
     */
    Object proxy();

    /**
     * The name via which _ds can use the proxy()
     * @return name of the data source
     */
    String name();

    /**
     * Creator for a DataSource
     */
    interface Creator {
        /**
         * Creates a data source
         * @param name of the data source
         * @param config the configuration parameters
         * @param parent the model object hosting the DataSource
         * @return a DataSource
         */
        DataSource create(String name, Map<String, Object> config, Model parent);
    }

    /**
     * Type Registry for the Creators to Cache Creators
     * Key - name/type of the creator
     * Value  - Creator itself
     */
    Map<String,Creator> REGISTRY = new HashMap<>();

    /**
     * Registering a type into the Type Registry
     * @param type of the Creator
     * @param path from which one should create the Creator
     *             it is a static field of some class with className::fieldName
     *             Separated via "::"
     * @return an EitherMonad abstracting the Creator
     */
    static EitherMonad<Creator> registerType(String type, String path){
        String[] paths = path.split("::");
        try {
            Class<?> clazz = Class.forName(paths[0]);
            Field f = clazz.getDeclaredField(paths[1]);
            Object r = f.get(null);
            if ( !(r instanceof Creator) ){
                throw new IllegalArgumentException("Error registering type... not a creator object : " + r.getClass());
            }
            REGISTRY.put(type,(Creator) r);
            logger.info( "Registered '{}::{}' as provider type for '{}'", clazz.getName(), f.getName(), type );
            return EitherMonad.value((Creator) r);
        }catch (Throwable t){
            logger.error( "Error registering type... : " + t);
            return EitherMonad.error(t);
        }
    }

    /**
     * Universal Creator,
     * Creating from the Type Registry
     */
    Creator UNIVERSAL = (name, config, parent) -> {
        String type = config.getOrDefault("type", "").toString();
        Creator creator = REGISTRY.get(type);
        if (creator == null) throw new IllegalArgumentException("Unknown type of datasource -> value: " + type);
        return creator.create(name, config, parent);
    };
}
