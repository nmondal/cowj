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
        default String inputSchema(){
            return schemas().getOrDefault( INPUT, "");
        }
        default String schema(String label){
            return schemas().getOrDefault( label, "");
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

    static boolean testExpression(Request request, Response response, String expression) {
        try {
            Scriptable scriptable = Scriptable.ZMB.create(Scriptable.INLINE, expression);
            Object r = scriptable.exec(request,response);
            return ZTypes.bool(r,false);
        }catch (Throwable t){
            return false;
        }
    }

    Map<String,String> statusLabels();

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

    TypeSystem NULL = fromConfig( Map.of( VERIFICATION,
            Map.of( Verification.INPUT, false), Verification.OUTPUT, false) ,"");

    static TypeSystem fromFile( String filePath){
        try {
            File f = new File(filePath).getAbsoluteFile().getCanonicalFile();
            Map<String,Object> config = (Map)ZTypes.yaml(f.getPath(),true);
            TypeSystem ts =  fromConfig( config, f.getParent());
            System.out.println("Schema is found in the system. Attaching...: " + filePath );
            return ts;
        }catch (Throwable ignore){
            System.err.println("No Valid Schema is attached to the system - returning NULL Type Checker!");
            return NULL;
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
            } catch (Throwable e) {
                Spark.halt(409,"Input Schema Validation failed : " + e);
            } finally {
                final long endTime = System.currentTimeMillis();
                System.err.printf("?? Input Verification took %d ms %n", endTime - startTime);
            }
        };
    }

    default Filter outputSchemaVerificationFilter(String path){
        // support only input as of now...
        return  (request, response) -> {
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
                System.err.printf("Output Schema Validation failed. Route '%s' : %n %s %n", path, e);
            } finally {
                final long endTime = System.currentTimeMillis();
                System.err.printf("?? Output Verification took %d ms %n", endTime - startTime);
            }
        };
    }

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
