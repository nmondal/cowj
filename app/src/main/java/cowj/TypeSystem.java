package cowj;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.JsonSchemaVersion;
import com.worldturner.medeia.api.PathSchemaSource;
import com.worldturner.medeia.api.SchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;
import org.jetbrains.annotations.NotNull;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiPredicate;

/*
* https://stackoverflow.com/questions/7939137/what-http-status-code-should-be-used-for-wrong-input
* */
public interface TypeSystem {

    interface Signature{
        Map<String,String> schemas();
        String INPUT = "in" ;
        String OUT_OK = "ok" ;
        String OUT_ERR = "err" ;
        default String inputSchema(){
            return schemas().getOrDefault( INPUT, "");
        }
        default String okSchema(){
            return schemas().getOrDefault( OUT_OK, "");
        }
        default String errSchema(){
            return schemas().getOrDefault( OUT_ERR, "");
        }

    }

    Map<String,Map<String,Signature>> routes();

    String definitionsDir();

    String ROUTES = "routes" ;
    String LABELS = "labels" ;
    String VERIFICATION = "verify" ;

    interface Verification {
        String INPUT = "in";
        String OUTPUT = "out";
        Map<String,Object> conf();
        default boolean in(){
           return ZTypes.bool( conf().getOrDefault(INPUT, true), true);
        }
        default boolean out(){
            return ZTypes.bool( conf().getOrDefault(OUTPUT, false), false);
        }
    }

    Verification verification();

    interface StatusLabel extends BiPredicate<Request,Response> {
        default String name() { return entry().getKey() ; };
        default String expression() { return entry().getValue(); }
        Map.Entry<String,String> entry();

        @Override
        default boolean test(Request request, Response response) {
            try {
                Scriptable scriptable = Scriptable.ZMB.create(Scriptable.INLINE, expression());
                Object r = scriptable.exec(request,response);
                return ZTypes.bool(r,false);
            }catch (Throwable t){
                return false;
            }
        }

    }

    static TypeSystem fromConfig(Map<String,Object> config, String baseDir){
        Map<String,Object> routeConfig = (Map)config.getOrDefault( ROUTES, Collections.emptyMap());
        final Map<String,Map<String,Signature>> routes = new LinkedHashMap<>();
        routeConfig.forEach( (path,v) -> {
            Map<String,Map<String,String>> verbMap = (Map)v;
            Map<String,Signature> verbToSigMap = new LinkedHashMap<>();
            verbMap.forEach( (verb,c) -> {
                Signature signature = () -> c;
                verbToSigMap.put(verb,signature);
            } );
            routes.put(path, verbToSigMap );
        });

        Map<String,Object> verificationConfig = (Map)config.getOrDefault( VERIFICATION, Collections.emptyMap());
        final Verification verification = () -> verificationConfig;
        return new TypeSystem() {
            @Override
            public Map<String, Map<String,Signature>> routes() {
                return routes;
            }

            @Override
            public String definitionsDir() {
                return baseDir;
            }

            @Override
            public Verification verification() {
                return verification;
            }
        };
    }

    static TypeSystem fromFile( String filePath){
        try {
            File f = new File(filePath).getAbsoluteFile().getCanonicalFile();
            Map<String,Object> config = (Map)ZTypes.yaml(f.getPath(),true);
            return fromConfig( config, f.getParent());
        }catch (Throwable t){
            throw new RuntimeException(t);
        }
    }

    MedeiaJacksonApi API = new MedeiaJacksonApi();

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    Map<String,SchemaValidator> VALIDATORS = new HashMap<>();

    @NotNull
    static SchemaValidator loadSchema(String jsonSchemaPath) {
        SchemaValidator validator = VALIDATORS.get(jsonSchemaPath);
        if ( validator != null ) return validator;
        Path p = Paths.get(jsonSchemaPath);
        JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.DRAFT07;
        SchemaSource source = new PathSchemaSource(p,jsonSchemaVersion);
        validator = API.loadSchema(source);
        VALIDATORS.put(jsonSchemaPath,validator);
        return validator;
    }

    String PARSED_BODY = "_body" ;

    default Filter inputSchemaVerificationFilter(String path){
        // support only input as of now...
        return  (request, response) -> {
            final String verb = request.requestMethod().toLowerCase(Locale.ROOT);
            if ( verb.equals("get") ){ return; }
            Signature signature = routes().get(path).get(verb);
            if ( signature == null ){ return; }
            final String jsonSchemaPath = definitionsDir() + "/"+  signature.inputSchema();
            SchemaValidator validator = loadSchema(jsonSchemaPath);
            final String potentialJsonBody = request.body() ;
            JsonParser unvalidatedParser =
                    OBJECT_MAPPER.getFactory().createParser(potentialJsonBody);
            JsonParser validatedParser = API.decorateJsonParser(validator, unvalidatedParser);
            try {
                Object  parsedBody = OBJECT_MAPPER.readValue(validatedParser, Object.class);
                // we should also add this to the request to ensure no further parsing for the same?
                request.attribute(PARSED_BODY, parsedBody );
            } catch (Throwable e) {
                Spark.halt(409,"Input Schema Validation failed : " + e);
            }
        };
    }

    default void attach(){
        routes().keySet().forEach( path -> {
            Filter schemaVerifier = inputSchemaVerificationFilter(path);
            Spark.before(path,schemaVerifier);
        });
    }
}
