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
     * Name for  UnAuthenticated
     */
    String UN_AUTHENTICATED = "UnAuthenticated" ;

    /**
     * Name for  UnAuthorized
     */
    String UN_AUTHORIZED = "UnAuthorized" ;

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
        final Enforcer enforcer = new Enforcer(casBinModel, adapter);
        final String userHeader = userHeader();
        Spark.before("*", ((request, response) -> {
            final String pathInfo = request.pathInfo();
            final String verb = request.requestMethod();
            final String userName = request.headers(userHeader);
            if ( userName == null ){
                Spark.halt(401, UN_AUTHENTICATED + " : " +  haltMessage());
            }
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
     * Creates an AuthSystem
     * @param file from this file
     * @return AuthSystem
     */
    static AuthSystem fromFile(String file){
        try {
            File f = new File(file).getCanonicalFile().getAbsoluteFile();
            final String filePath = f.getAbsolutePath();
            final String baseDir = f.getParent();
            Map<String,Object> conf = (Map)ZTypes.yaml( filePath, true);
            logger.info("Found AuthSystem, attaching : " + filePath );
            return new AuthSystem() {
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
