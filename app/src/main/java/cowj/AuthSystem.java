package cowj;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.file_adapter.FileAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static cowj.Authenticator.UN_AUTHENTICATED;

/**
 * Auth System - essentially integrates JCasbin into the system
 * @see <a href="https://github.com/casbin/jcasbin">JCasbin</a>
 */
public interface AuthSystem {

    /**
     * Logger for the Auth
     */
    Logger logger = LoggerFactory.getLogger(AuthSystem.class);

    /**
     * Disables or Enables auth
     * @return true if auth is disabled, false if auth is enabled
     */
    default boolean disabled(){ return true; }

    /**
     * Policy setting of the Auth
     * Currently only support is for "file" type
     * @return bunch of properties
     */
    default Map<String, Object> policy(){ return Collections.emptyMap(); }

    /**
     * Gets the directory where all auth related files are kept
     * @return directory of the auth files
     */
    String definitionsDir();

    /**
     * User Header, from which user-id should be extracted
     * @return name of the header which has users id
     */
    default String userHeader(){ return "username" ; }

    /**
     * In case of Un-Auth, what message users would see
     * @return un-auth message
     */
    default String haltMessage(){ return "thou shall not pass!" ; }

    /**
     * Creates a JCasbin policy adapter
     * @see <a href="https://casbin.org/docs/adapters">Casbin Policy Adapters</a>
     * @param conf properties which will be used to create the adapter
     * @param baseDir base directory of the auth files
     * @return a JCasbin policy adapter
     */
    static Adapter adapter( Map<String,Object> conf, String baseDir){
        // fow now do only FileAdapter
        final String policyFileName = conf.getOrDefault(POLICY_FILE_KEY, POLICY_FILE_NAME).toString();
        final String filePath = baseDir + "/" + policyFileName ;
        return new FileAdapter( filePath);
    }

    /**
     * Name of the fixed model file
     */
    String MODEL_FILE = "model.conf" ;

    /**
     * Name of the default policy file for JCasbin file CSV adapter
     */
    String POLICY_FILE_NAME = "policy.csv" ;

    /**
     * Key for the file name for CSV adapter
     */
    String POLICY_FILE_KEY = "file" ;

    /**
     * Name for the Auth Disabled key
     */
    String DISABLED = "disabled" ;


    /**
     * Name for the Authenticator key
     */
    String AUTHENTICATOR_PROVIDER = "provider" ;

    /**
     * Name for the Un-Auth Message key
     */
    String MESSAGE = "message" ;

    /**
     * Name for the policy settings key
     */
    String POLICY = "policy" ;

    /**
     * Name for the User ID Header key
     */
    String USER_HEADER = "user-header" ;

    /**
     * Name for  UnAuthorized
     */
    String UN_AUTHORIZED = "UnAuthorized" ;

    /**
     * Gets the  Authenticator for the system
     * Default implementation just checks if the header has the user id or not
     * @return An Authenticator Object
     */
    default Authenticator authenticator(){
        final String userHeader = userHeader();
        return request -> {
            final String userName = request.headers(userHeader);
            if ( userName == null ){
                Spark.halt(401, UN_AUTHENTICATED + " : " +  haltMessage());
            }
            return Authenticator.UserInfo.userInfo(userName, "", Long.MAX_VALUE, Collections.emptyMap());
        };
    }

    /**
     * Attaches an Auth System to Spark-Java
     * Note :
     * Any route not explicitly not allowed in the policy will be accessible by guests automatically
     * So we need to be careful about it while defining policy
     */
    default void attach(){
        if ( disabled() ){ return; }
        final String authDefDir = definitionsDir();
        final Adapter adapter = adapter(policy(), authDefDir);
        final String casBinModel = authDefDir + "/" + MODEL_FILE ;
        final Authenticator authenticator = authenticator();
        final Enforcer enforcer = new Enforcer(casBinModel, adapter);
        Spark.before("*", ((request, response) -> {
            final String pathInfo = request.uri(); // jetty 12 spark pathInfo comes null, hence uri
            final String verb = request.requestMethod();
            final String userName = authenticator.authenticate(request);
            final boolean thouShallPass = enforcer.enforce( userName, pathInfo, verb);
            if ( !thouShallPass){
                Spark.halt(403, UN_AUTHORIZED + " : " +  haltMessage());
            }
        }));
    }

    /**
     *  A NULL, pointless Auth system which does not do anything
     */
    AuthSystem NULL = () -> ".";

    /**
     * Tries to create an Authenticator from a Map of string, object
     * In case conf is empty  - then returns a default authenticator
     * @param conf the map of string, object to create the authenticator
     * @param model underlying model
     * @param def default authenticator to be passed in case of empty conf
     * @return an Authenticator from the conf, failing returns the def
     */
    static Authenticator fromConfig(Map<String,Object> conf, Model model, Authenticator def){
        if ( conf.isEmpty() ) return def;
        DataSource ds = DataSource.UNIVERSAL.create( "ds:auth", conf, model);
        // load the stuff as data source
        DataSource.registerDataSource(ds.name(), ds.proxy());
        return (Authenticator) ds.proxy();
    }

    /**
     * Creates an AuthSystem
     * @param file from this file
     * @param model underlying Model
     * @return AuthSystem
     */
    static AuthSystem fromFile(String file, Model model ){
        try {
            File f = new File(file).getCanonicalFile().getAbsoluteFile();
            final String filePath = f.getAbsolutePath();
            final String baseDir = f.getParent();
            final Map<String,Object> conf = (Map)ZTypes.yaml( filePath, true);
            logger.info("Found AuthSystem, attaching : " + filePath );

            return new AuthSystem() {
                final Authenticator authenticator =
                        fromConfig((Map) conf.getOrDefault( AUTHENTICATOR_PROVIDER, Collections.emptyMap()), model,
                        AuthSystem.super.authenticator() );
                @Override
                public boolean disabled() {
                    return  ZTypes.bool(conf.getOrDefault(DISABLED, AuthSystem.super.disabled()),true);
                }

                @Override
                public String userHeader() {
                    return conf.getOrDefault( USER_HEADER, AuthSystem.super.userHeader()).toString();
                }

                @Override
                public String haltMessage() {
                    return conf.getOrDefault( MESSAGE, AuthSystem.super.haltMessage()).toString();
                }

                @Override
                public Map<String, Object> policy() {
                    return (Map)conf.getOrDefault( POLICY, Collections.emptyMap());
                }

                @Override
                public Authenticator authenticator(){
                    return authenticator;
                }

                @Override
                public String definitionsDir() {
                    return baseDir;
                }
            };
        }catch (Exception ignore){
            logger.error("AuthSystem was not found, returning NULL Auth!");
            return NULL;
        }
    }
}
