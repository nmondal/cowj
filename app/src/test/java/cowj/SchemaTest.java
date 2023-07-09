package cowj;

import org.junit.Assert;
import org.junit.Test;

public class SchemaTest {

    @Test
    public void loadSchemaTest(){
        TypeSystem typeSystem = TypeSystem.fromFile( "samples/prod/static/types/schema.yaml");
        Assert.assertFalse( typeSystem.routes().isEmpty() );
        Assert.assertEquals( 2, typeSystem.routes().size());
    }
}
