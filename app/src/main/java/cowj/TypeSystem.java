package cowj;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.JsonSchemaVersion;
import com.worldturner.medeia.api.PathSchemaSource;
import com.worldturner.medeia.api.SchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Underlying JSON Schema based Type system
 * <a href="https://stackoverflow.com/questions/7939137/what-http-status-code-should-be-used-for-wrong-input">...</a>
 */
public interface TypeSystem {

    /**
     * Logger for the Cowj TypeSystem
     */
    Logger logger = LoggerFactory.getLogger(TypeSystem.class);

    /**
     * Route Signature Abstraction
     */
    interface Signature{

        /**
         * mapping of label against json schema file names
         * @return a map of labels against schema for it
         */
        Map<String,String> schemas();

        /**
         * Special constant to define input schema
         */
        String INPUT = "in" ;

        /**
         * Gets the input json schema file
         * @return input json schema file
         */
        default String inputSchema(){
            return schemas().getOrDefault( INPUT, "");
        }

        /**
         * Given a label gets the schema type
         * @param label name of the label
         * @return Json Schema file associated with the label
         */
        default String schema(String label){
            return schemas().getOrDefault( label, "");
        }
    }

    /**
     * Routes mapping against schema
     * @return routes mapping
     */
    Map<String,Map<String,Signature>> routes();

    /**
     * Absolute Location of the types directory where all schema files live
     * @return absolute location of the types directory
     */
    String definitionsDir();

    /**
     * Key name for the routes configuration
     */
    String ROUTES = "routes" ;

    /**
     * Key name for the labels configuration
     */
    String LABELS = "labels" ;

    /**
     * Key name for the verification configuration
     */
    String VERIFICATION = "verify" ;

    /**
     * Abstraction for verification scheme
     */
    interface Verification {

        /**
         * Key name for the verification for input
         */
        String INPUT = "in";

        /**
         * Key name for the verification for output
         */
        String OUTPUT = "out";

        /**
         * Underlying configuration
         * @return verification configuration as map
         */
        Map<String,Object> conf();

        /**
         * Is the input verification turned on ?
         * @return true if turned on, false if turned off
         */
        default boolean in(){
           return ZTypes.bool( conf().getOrDefault(INPUT, true), true);
        }

        /**
         * Is the output verification turned on ?
         * @return true if turned on, false if turned off
         */
        default boolean out(){
            return ZTypes.bool( conf().getOrDefault(OUTPUT, false), false);
        }
    }

    /**
     * Associated Verification
     * @return the verification scheme for the TypeSystem
     */
    Verification verification();

    /**
     * Tests a label expression with Request, Response
     * @param request  spark.Request
     * @param response  spark.Response
     * @param expression label expression
     * @return true if expression evaluates to true, false otherwise including error
     */
    static boolean testExpression(Request request, Response response, String expression) {
        try {
            Scriptable scriptable = Scriptable.ZMB.create(Scriptable.INLINE, expression);
            Object r = scriptable.exec(request,response);
            return ZTypes.bool(r,false);
        }catch (Throwable t){
            return false;
        }
    }

    /**
     * Mapping for status labels
     * map of label_name : label_expression
     * @return status label mapping
     */
    Map<String,String> statusLabels();

    /**
     * Creates a TypeSystem from config
     * @param config configuration  map
     * @param baseDir directory where all files are stored for TypeSystem
     * @return a TypeSystem
     */
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
        Map<String,String> statusLabels = (Map)config.getOrDefault( LABELS, Collections.emptyMap());

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

            @Override
            public Map<String, String> statusLabels() {
                return statusLabels;
            }
        };
    }

    /**
     * A NULL TypeSystem which is turned off
     */
    TypeSystem NULL = fromConfig( Map.of( VERIFICATION,
            Map.of( Verification.INPUT, false), Verification.OUTPUT, false) ,"");

    /**
     * Creates a TypeSystem from a yaml file
     * @param filePath path of the yaml file
     * @return a TypeSystem
     */
    static TypeSystem fromFile( String filePath){
        try {
            File f = new File(filePath).getAbsoluteFile().getCanonicalFile();
            Map<String,Object> config = (Map)ZTypes.yaml(f.getPath(),true);
            TypeSystem ts =  fromConfig( config, f.getParent());
            logger.info("Schema is found in the system. Attaching...: " + filePath );
            return ts;
        }catch (Throwable ignore){
            logger.error("No Valid Schema is attached to the system - returning NULL Type Checker!");
            return NULL;
        }
    }

    /**
     * Default MedeiaJacksonApi
     */
    MedeiaJacksonApi API = new MedeiaJacksonApi();

    /**
     * Default ObjectMapper
     */
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Cached SchemaValidator
     * Key - file name
     * Value - SchemaValidator
     */
    Map<String,SchemaValidator> VALIDATORS = new HashMap<>();

    /**
     * Stale Static call to register auto reload for type system schemas
     */
    Serializable staticInitHack = new Serializable() {
        static {
            FileWatcher.ofCacheAndRegister( VALIDATORS, TypeSystem::loadSchema);
        }
    };

    /**
     * Creates a SchemaValidator from a Json schema file
     * @param jsonSchemaPath path of the JSON schema file
     * @return SchemaValidator
     */
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

    /**
     * Key of the parsed input data body accessible from Scriptable
     */
    String PARSED_BODY = "_body" ;

    /**
     * Key for checking if input schema validation failed or not
     * If this is not set, then the input is not verified by the input schema validator
     */
    String INPUT_SCHEMA_VALIDATION_FAILED = "_is_failed" ;

    /**
     * Creates a spark.Filter before filter from JSON Schema path to verify input schema
     * @param path to the JSON Schema file
     * @return a spark.Filter before filter
     */
    default Filter inputSchemaVerificationFilter(String path){
        // support only input as of now...
        return  (request, response) -> {
            final long startTime = System.currentTimeMillis();
            final String verb = request.requestMethod().toLowerCase(Locale.ROOT);
            if ( verb.equals("get") ){ return; }
            Signature signature = routes().get(path).get(verb);
            if ( signature == null ){ return; }
            final String schemaPath = signature.inputSchema();
            if ( schemaPath.isEmpty() ) { return; }
            final String jsonSchemaPath = definitionsDir() + "/"+  schemaPath;
            SchemaValidator validator = loadSchema(jsonSchemaPath);
            final String potentialJsonBody = request.body() ;
            JsonParser unvalidatedParser =
                    OBJECT_MAPPER.getFactory().createParser(potentialJsonBody);
            JsonParser validatedParser = API.decorateJsonParser(validator, unvalidatedParser);
            try {
                Object  parsedBody = OBJECT_MAPPER.readValue(validatedParser, Object.class);
                // we should also add this to the request to ensure no further parsing for the same?
                request.attribute(PARSED_BODY, parsedBody );
                request.attribute(INPUT_SCHEMA_VALIDATION_FAILED, false );
            } catch (Throwable e) {
                request.attribute(INPUT_SCHEMA_VALIDATION_FAILED, true );
                Spark.halt(409,"Input Schema Validation failed : " + e);
            } finally {
                final long endTime = System.currentTimeMillis();
                logger.error("?? Input Verification took {} ms", endTime - startTime);
            }
        };
    }


    /**
     * Creates a spark.Filter afterAfter filter from JSON Schema path to verify output schema
     * @param path to the JSON Schema file
     * @return a spark.Filter afterAfter filter
     */
    default Filter outputSchemaVerificationFilter(String path){
        // support only input as of now...
        return  (request, response) -> {
            if ( Boolean.TRUE.equals( request.attribute(INPUT_SCHEMA_VALIDATION_FAILED))){
                logger.info("Bypassing Output Schema Validation, reason: Input Schema validation failed");
                return;
            }
            final long startTime = System.currentTimeMillis();
            final String verb = request.requestMethod().toLowerCase(Locale.ROOT);
            Signature signature = routes().get(path).get(verb);
            if ( signature == null ){ return; }
            // which pattern matched?
            Optional<String> optLabel = signature.schemas().keySet().stream().filter( label -> {
               if ( Signature.INPUT.equals(label)) return false;
               String statusExpression = statusLabels().get( label );
               if ( statusExpression == null ) return false;
               return testExpression(request,response, statusExpression);
            }).findFirst();
            if ( optLabel.isEmpty() ) return;
            final String schemaPath = signature.schema(optLabel.get());
            if ( schemaPath.isEmpty() ) return;
            final String jsonSchemaPath = definitionsDir() + "/"+  schemaPath;
            SchemaValidator validator = loadSchema(jsonSchemaPath);
            final String potentialJsonBody = response.body() ;
            JsonParser unvalidatedParser =
                    OBJECT_MAPPER.getFactory().createParser(potentialJsonBody);
            JsonParser validatedParser = API.decorateJsonParser(validator, unvalidatedParser);
            try {
                OBJECT_MAPPER.readValue(validatedParser, Object.class);
            } catch (Throwable e) {
                logger.error("Original Response: {} ==> {}", response.status(), response.body());
                logger.error("Output Schema Validation failed. Route '{}' : \n {}", path, e.toString());
            } finally {
                final long endTime = System.currentTimeMillis();
                logger.error("?? Output Verification took {} ms", endTime - startTime);
            }
        };
    }

    /**
     * Attaches the TypeSystem to a Spark instance
     */
    default void attach() {
        if (verification().in()) { // only if verification is in...
            routes().keySet().forEach(path -> {
                Filter schemaVerifier = inputSchemaVerificationFilter(path);
                Spark.before(path, schemaVerifier);
            });
        }
        if ( verification().out() ){ // only if verification out is set in
            routes().keySet().forEach(path -> {
                Filter schemaVerifier = outputSchemaVerificationFilter(path);
                // this is costly, we should avoid it...
                Spark.afterAfter(path, schemaVerifier);
            });
        }
    }
}
