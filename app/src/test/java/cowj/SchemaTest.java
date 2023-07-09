package cowj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import zoomba.lang.core.types.ZTypes;

import java.util.Map;

public class SchemaTest {

    @Before
    public void before(){
        mr = null;
    }

    @After
    public void after(){
        if ( mr == null ) return;
        mr.stop();
        mr = null;
    }

    @Test
    public void loadSchemaTest(){
        TypeSystem typeSystem = TypeSystem.fromFile( "samples/prod/static/types/schema.yaml");
        Assert.assertFalse( typeSystem.routes().isEmpty() );
        Assert.assertEquals( 2, typeSystem.routes().size());
    }

    ModelRunner mr;

    @Test
    public void validSchemaTest(){
        mr = ModelRunnerTest.runModel("samples/prod/prod.yaml" );
        String r = ModelRunnerTest.get( "http://localhost:5042", "/person/foobar");
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("foobar") );
        Assert.assertTrue( r.contains("not found") );
        String body = ZTypes.jsonString(Map.of( "firstName", "foo", "lastName", "bar"));
        r = ModelRunnerTest.post( "http://localhost:5042", "/person",  body);
        Assert.assertNotNull(r);
        Map<String,String> m = (Map)ZTypes.json(r);
        String id = m.get("personId");
        Assert.assertNotNull(id);
        r = ModelRunnerTest.get( "http://localhost:5042", "/person/" + id );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains(id) );
    }

    @Test
    public void invalidSchemaTest(){
        mr = ModelRunnerTest.runModel("samples/prod/prod.yaml" );
        String r = ModelRunnerTest.post( "http://localhost:5042", "/person",  "foo bar!" );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("Validation") );
    }
}
