package cowj;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.JsonSchemaVersion;
import com.worldturner.medeia.api.PathSchemaSource;
import com.worldturner.medeia.api.SchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;
import zoomba.lang.core.types.ZNumber;
import zoomba.lang.core.types.ZTypes;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
        String PARAMETERS = "params" ;

        /**
         * Gets the input json schema file
         * @return input json schema file
         */
        default String parameterSchema(){
            return schemas().getOrDefault( PARAMETERS, "");
        }

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
     * Storage access patterns mapping against schema
     * @return storage mapping
     */
    Map<String,Map<String,Object>> storages();

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
     * Key name for the storage access schema configuration
     */
    String STORAGES = "storages" ;

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
        final Map<String,Map<String,Object>> storageConfig = (Map)config.getOrDefault( STORAGES, Collections.emptyMap());

        // load all schema files...
        File[] files = new File( baseDir ).listFiles((dir, name) -> name.toLowerCase().endsWith(".json") );
        logger.info("loading type system from dir: '{}'", baseDir );
        final List<SchemaSource> schemas;
        if ( files == null ) {
            schemas = Collections.emptyList();
        } else {
            final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.DRAFT07;
            schemas = Arrays.stream(files).map(file -> {
                Path p = file.getAbsoluteFile().toPath();
                logger.info("loading schema file : {}", p );
                return (SchemaSource) new PathSchemaSource(p, jsonSchemaVersion);
            }).toList();
        }

        return new TypeSystem() {
            @Override
            public Map<String, Map<String,Signature>> routes() {
                return routes;
            }

            @Override
            public Map<String, Map<String, Object>> storages() {
                return storageConfig;
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

            @Override
            public List<SchemaSource> schemas() {
                return schemas;
            }
        };
    }

    /**
     * A NULL TypeSystem which is turned off
     */
    TypeSystem NULL = fromConfig( Map.of( VERIFICATION,
            Map.of( Verification.INPUT, false), Verification.OUTPUT, false) ,"");


    /**
     * Key to find the loaded type system
     */
    String DS_TYPE = "ds:types" ;

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
            logger.info("Schema is found in the system. Attaching...: {}", filePath);
            DataSource.registerDataSource(DS_TYPE, ts); // load the data source type system
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
            // you have to excuse us for the contrived way to write it - it was written to save up on test coverage
            // writing this way ensures that the entire line is covered, it was separately tested to check that it works
            FileWatcher.ofCacheAndRegister( VALIDATORS, TypeSystem::reloadForFileWatcher );
        }
    };

    /**
     * A static method to reload schema if required for FileWatcher
     * @param jsonSchemaPath absolute path of the JSON schema file
     * @return a SchemaValidator
     */
    static SchemaValidator reloadForFileWatcher( String jsonSchemaPath){
        return DataSource.dataSourceOrElse( DS_TYPE, NULL).loadSchema( jsonSchemaPath );
    }

    /**
     * Creates a SchemaValidator from a Json schema file
     * @param jsonSchemaPath absolute path of the JSON schema file
     * @return a SchemaValidator
     */
    default SchemaValidator loadSchema(String jsonSchemaPath) {
        SchemaValidator validator = VALIDATORS.get(jsonSchemaPath);
        if ( validator != null ) return validator;
        Path p = Paths.get(jsonSchemaPath);
        List<SchemaSource> repo = schemas().stream()
                .filter( ss -> !Objects.equals(ss.getInput().getName(), jsonSchemaPath))
                .collect(Collectors.toList());
        JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.DRAFT07;
        SchemaSource source = new PathSchemaSource(p,jsonSchemaVersion);
        repo.add(0, source);
        validator = API.loadSchemas(repo);
        VALIDATORS.put(jsonSchemaPath,validator);
        return validator;
    }

    /**
     * Key of the parsed input data body accessible from Scriptable
     */
    String PARSED_BODY = "_body" ;

    /**
     * Key of the parsed input param body accessible from Scriptable
     */
    String PARSED_PARAMS = "_params" ;

    /**
     * Key for checking if input schema validation failed or not
     * If this is not set, then the input is not verified by the input schema validator
     */
    String INPUT_SCHEMA_VALIDATION_FAILED = "_is_failed" ;

    /**
     * A bunch of schemas defined in the type system
     * This is important for interconnected schemas who depend on each other
     * @return schemas defined in the type system
     */
    List<SchemaSource> schemas();

    /**
     * A validating json string to json object converter
     * @param schemaPath path of the JSON Schema file to validate against, relative to the definition directory
     * @param potentialJsonBody input string to be converted to json
     * @return an EitherMonad consist of potential parsed json object
     */
    default EitherMonad<Object> json(String schemaPath, String potentialJsonBody) {
        try {
            final String jsonSchemaPath = definitionsDir() + File.separator +  schemaPath;
            SchemaValidator validator = loadSchema(jsonSchemaPath);
            JsonParser unvalidatedParser =
                    OBJECT_MAPPER.getFactory().createParser(potentialJsonBody);
            JsonParser validatedParser = API.decorateJsonParser(validator, unvalidatedParser);
            Object parsedBody = OBJECT_MAPPER.readValue(validatedParser, Object.class);
            return EitherMonad.value(parsedBody);
        } catch (Throwable ex){
            logger.debug("Schema verification error : {}", ex.toString());
            return EitherMonad.error(ex);
        }
    }

    /**
     * Checks whether an Object matches against a schema or not
     * @param schemaPath path of the schema file, relative to the definition directory
     * @param o object, which needs to be matched
     * @return true if matches, false if does not match
     */
    default boolean match(String schemaPath, Object o) {
        try {
            final String jsonString;
            if ( o instanceof CharSequence ){
                jsonString = o.toString();
            } else {
                jsonString =  OBJECT_MAPPER.writeValueAsString(o);
            }
            return json(schemaPath, jsonString).isSuccessful();
        }catch (Throwable ex){
            logger.debug("Object {} to JSON String Conversion error : {}", o , ex.toString());
        }
        return false;
    }

    /**
     * Tries to progressively cast a string into a primitive type ( bool, numeric, string )
     * @param s the input string
     * @return a primitive type object - like bool, or numeric or same string if it can not convert
     */
    static Object autoCast(String s) {
        Boolean b = ZTypes.bool(s);
        if ( b != null ) return b;
        Number n = ZNumber.number(s);
        if ( n != null ) return n;
        return s;
    }

    /**
     * Converts a query map into a real map with underlying objects
     * Use autoCast to cast them
     * If a value has more than one element treat as list
     * @param map input map
     * @return a flattened out map having objects as values
     */
    static Map<String,Object> toRealMap( Map<String,String[]> map){
        final Map<String,Object> json = new HashMap<>();
        map.forEach( (k,v) -> {
            if ( v.length > 1 ){
                List<Object> l = new ArrayList<>(v.length);
                Arrays.stream(v).forEach( s -> l.add( autoCast(s)) );
                json.put(k,l);
            }else{
                json.put(k,autoCast(v[0]));
            }
        });
        return json;
    }

    /**
     * Process query parameters , halts if schema did not match
     * @param request for this request
     * @param paramSchema based on this schema path
     * @param startTime when the parent intercept method was called, time in ms
     */
    default void handleParameters(Request request, String paramSchema, long startTime){
        if ( paramSchema.isEmpty() ) return;
        Map<String,String[]> map = request.queryMap().toMap();
        Map<String,Object> json = toRealMap(map);
        final String jsonString =  EitherMonad.orElse( () -> OBJECT_MAPPER.writeValueAsString(json), "{}");
        EitherMonad<Object> em = json( paramSchema, jsonString);
        try {
            if ( em.inError() ){
                final String message = "Query Parameter Schema Validation failed : " + em.error();
                Spark.halt(409, message);
            }
            request.attribute(PARSED_PARAMS, em.value());
        }finally {
            final long endTime = System.currentTimeMillis();
            logger.info("?? Parameter Verification [success: {}] took {} ms", em.isSuccessful(), endTime - startTime);
        }
    }

    /**
     * Creates a spark.Filter before filter from JSON Schema path to verify input schema
     * @param path to the JSON Schema file
     * @return a spark.Filter before filter
     */
    default Filter inputSchemaVerificationFilter(String path){
        // support only request body as of now...
        return  (request, response) -> {
            final long startTime = System.currentTimeMillis();
            final String verb = request.requestMethod().toLowerCase(Locale.ROOT);
            Signature signature = routes().get(path).get(verb);
            if ( signature == null ){ return; }
            // handle params
            handleParameters( request, signature.parameterSchema(),startTime);
            // now the rest...
            if ( verb.equals("get") ){ return; }

            final String schemaPath = signature.inputSchema();
            if ( schemaPath.isEmpty() ) { return; }

            final String potentialJsonBody = request.body() ;
            EitherMonad<Object> typedParsing = json( schemaPath, potentialJsonBody);
            final boolean success = typedParsing.isSuccessful();
            request.attribute(INPUT_SCHEMA_VALIDATION_FAILED, !success );
            try {
                if ( success ){
                    // we should also add this to the request to ensure no further parsing for the same?
                    request.attribute(PARSED_BODY, typedParsing.value() );
                } else {
                    final String message = "Input Schema Validation failed : " + typedParsing.error();
                    logger.debug(message);
                    Spark.halt(409, message);
                }
            } finally { // this is necessary because of schema failures...
                final long endTime = System.currentTimeMillis();
                logger.info("?? Input Verification [success: {}] took {} ms", success, endTime - startTime);
            }
        };
    }

    /**
     * Once the output schema is verified, the content type would be set to this
     */
    String RESP_CONTENT_TYPE = "application/json" ;

    /**
     * Creates a spark.Filter afterAfter filter from JSON Schema path to verify output schema
     * @param path to the JSON Schema file
     * @return a spark.Filter afterAfter filter
     */
    default Filter outputSchemaVerificationFilter(String path){
        // support only response body as of now...
        return  (request, response) -> {
            final long startTime = System.currentTimeMillis();
            if ( Boolean.TRUE.equals( request.attribute(INPUT_SCHEMA_VALIDATION_FAILED))){
                logger.info("Bypassing Output Schema Validation, reason: Input Schema validation failed");
                return;
            }
            final String potentialJsonBody = response.body() ;
            if ( potentialJsonBody == null ){
                logger.error("Bypassing Output Schema Validation, reason: Nothing is in the response body");
                logger.error("Possible NON-String response from Route : Ensure returning string for output Schema Validation!");
                return;
            }
            final String verb = request.requestMethod().toLowerCase(Locale.ROOT);
            Signature signature = routes().get(path).get(verb);
            if ( signature == null ){ return; }
            // which pattern matched?
            Optional<String> optLabel = signature.schemas().keySet().stream().filter( label -> {
               if ( Signature.INPUT.equals(label)) return false;
               // ensuring that if nothing is found, it comes as a false expression
               String statusExpression = statusLabels().getOrDefault( label, "false" );
               return testExpression(request,response, statusExpression);
            }).findFirst();
            if ( optLabel.isEmpty() ) return;
            final String schemaPath = signature.schema(optLabel.get());
            if ( schemaPath.isEmpty() ) return;
            EitherMonad<Object> typedParsing = json( schemaPath, potentialJsonBody);
            final boolean success = typedParsing.isSuccessful();
            if ( success){
                // automatically set JSON type in response
                response.type(RESP_CONTENT_TYPE);
            } else {
                logger.error("Original Response: {} ==> {}", response.status(), response.body());
                logger.error("Output Schema Validation failed. Route '{}' : \n {}", path, typedParsing.error().toString());
            }
            final long endTime = System.currentTimeMillis();
            logger.info("?? Output Verification [success: {}] took {} ms", success, endTime - startTime);
        };
    }

    /**
     * Attaches the TypeSystem Input Schema Validation to a Spark instance
     */
    default void attachInput(){
        if (!verification().in()) return;
        routes().keySet().forEach(path -> {
            Filter schemaVerifier = inputSchemaVerificationFilter(path);
            Spark.before(path, schemaVerifier);
        });
    }

    /**
     * Attaches the TypeSystem Output Schema Validation to a Spark instance
     */
    default void attachOutput(){
        if ( !verification().out() ) return;
        routes().keySet().forEach(path -> {
            Filter schemaVerifier = outputSchemaVerificationFilter(path);
            // this is costly, we should avoid it...
            Spark.afterAfter(path, schemaVerifier);
        });
    }
}
