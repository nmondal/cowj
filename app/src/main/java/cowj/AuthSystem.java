package cowj;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.file_adapter.FileAdapter;
import spark.Spark;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public interface AuthSystem {

    default boolean disabled(){ return true; }

    default Map<String, Object> policy(){ return Collections.emptyMap(); }

    String definitionsDir();
    default String userHeader(){ return "username" ; }
    default String haltMessage(){ return "thou shall not pass!" ; }
    static Adapter adapter( Map<String,Object> conf, String baseDir){
        // fow now do only FileAdapter
        final String policyFileName = conf.getOrDefault(POLICY_FILE_KEY, POLICY_FILE_NAME).toString();
        final String filePath = baseDir + "/" + policyFileName ;
        return new FileAdapter( filePath);
    }

    String MODEL_FILE = "model.conf" ;
    String POLICY_FILE_NAME = "policy.csv" ;
    String POLICY_FILE_KEY = "name" ;

    String DISABLED = "disabled" ;
    String MESSAGE = "message" ;
    String POLICY = "policy" ;

    String USER_HEADER = "user-header" ;

    String UN_AUTHENTICATED = "UnAuthenticated" ;

    String UN_AUTHORIZED = "UnAuthorized" ;

    default void attach( String unprotected){
        if ( disabled() ){ return; }
        final String authDefDir = definitionsDir();
        final Adapter adapter = adapter(policy(), authDefDir);
        final String casBinModel = authDefDir + "/" + MODEL_FILE ;
        final Enforcer enforcer = new Enforcer(casBinModel, adapter);
        final String userHeader = userHeader();
        Spark.before("*", ((request, response) -> {
            final String userName = request.headers(userHeader);
            if ( userName == null ){
                Spark.halt(401, UN_AUTHENTICATED + " : " +  haltMessage());
            }
            final String pathInfo = request.pathInfo();
            final String verb = request.requestMethod();
            if( pathInfo.startsWith(unprotected)) return;
            final boolean thouShallPass = enforcer.enforce( userName, pathInfo, verb);
            if ( !thouShallPass){
                Spark.halt(403, UN_AUTHORIZED + " : " +  haltMessage());
            }
        }));
    }

    AuthSystem NULL = () -> ".";

    static AuthSystem fromFile(String file){
        try {
            File f = new File(file).getCanonicalFile().getAbsoluteFile();
            final String filePath = f.getAbsolutePath();
            final String baseDir = f.getParent();
            Map<String,Object> conf = (Map)ZTypes.yaml( filePath, true);
            System.out.println("Found AuthSystem, attaching : " + filePath );
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
            System.err.println("AuthSystem was not found, returning NULL Auth!");
            return NULL;
        }
    }
}
