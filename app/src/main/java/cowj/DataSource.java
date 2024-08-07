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
     * Converts the proxy() object to a Type
     *
     * @return proxy() as type T
     * @param <T> type parameter
     */
    default <T> T any(){
        return (T)proxy();
    }

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
     * Creates a Data Source Wrapper
     * @param name of the data source - to be used as key
     * @param proxy underlying actual object
     * @return a DataSource object instance
     */
    static DataSource dataSource(String name, Object proxy){
        return new DataSource() {
            @Override
            public Object proxy() {
                return proxy;
            }
            @Override
            public String name() {
                return name;
            }
        };
    }

    /**
     * The key 'type' which defines type of data source
     */
    String DS_TYPE = "type" ;

    /**
     * Universal Creator,
     * Creating from the Type Registry
     */
    Creator UNIVERSAL = (name, config, parent) -> {
        String type = config.getOrDefault(DS_TYPE, "").toString();
        Creator creator = REGISTRY.get(type);
        if (creator == null) throw new IllegalArgumentException("Unknown type of datasource -> value: " + type);
        return creator.create(name, config, parent);
    };

    /**
     * Various data sources store as map
     * key - name of the DataSource
     * Value - the proxy() of the DataSource
     * Inside a Scriptable script this is accessible via _ds
     */
    Map<String, Object> DATA_SOURCES = new HashMap<>();


    /**
     * Gets the data source by name
     * @param dsName name of the data source
     * @return the data source associated with the name
     * @param <T> type of the object instance stored as a data source
     */
    static <T> T dataSource(String dsName){
        return (T)DATA_SOURCES.get(dsName);
    }

    /**
     * Gets the data source by name
     * @param dsName name of the data source
     * @param otherwise  in case the name is not found, returns this instance
     * @return the data source associated with the name
     * @param <T> type of the object instance stored as a data source
     */
    static <T> T dataSourceOrElse(String dsName, T otherwise){
        return (T)DATA_SOURCES.getOrDefault(dsName, otherwise );
    }

    /**
     * Registers the data source by name
     * @param dsName name of the data source
     * @param dataSource the data source to be associated with the name
     * @param <T> type of the object instance to be stored as a data source
     */
    static <T> void registerDataSource(String dsName, T dataSource){
        DATA_SOURCES.put(dsName, dataSource);
    }

    /**
     * Removes the data source from instance registry
     * @param dsName name of the data source
     * @return the data source which was associated with the name
     * @param <T> type of the object instance
     */
    static <T> T unregisterDataSource(String dsName){
        return (T) DATA_SOURCES.remove(dsName);
    }
}
