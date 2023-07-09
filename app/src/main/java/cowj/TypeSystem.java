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
import spark.Spark;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

    static TypeSystem fromConfig(Map<String,Object> config, String baseDir){

        final Map<String,Map<String,Signature>> routes = new LinkedHashMap<>();
        config.forEach( (path,v) -> {
            Map<String,Map<String,String>> verbMap = (Map)v;
            Map<String,Signature> verbToSigMap = new LinkedHashMap<>();
            verbMap.forEach( (verb,c) -> {
                Signature signature = () -> c;
                verbToSigMap.put(verb,signature);
            } );
            routes.put(path, verbToSigMap );
        });

        return new TypeSystem() {
            @Override
            public Map<String, Map<String,Signature>> routes() {
                return routes;
            }

            @Override
            public String definitionsDir() {
                return baseDir;
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

    default Filter schemaVerificationFilter(String path){
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
            Filter schemaVerifier = schemaVerificationFilter(path);
            Spark.before(path,schemaVerifier);
        });
    }
}
