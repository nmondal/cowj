package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertThrows;

public class PluginsTest {

    @Test
    public void testNonExistingClass(){
        EitherMonad<DataSource.Creator> em = DataSource.registerType("foo", "a.b.c::FOO");
        Assert.assertTrue(em.inError());
        Assert.assertTrue( em.error().getMessage().contains("a.b.c"));
    }

    @Test
    public void testExistingButInvalidClass(){
        EitherMonad<DataSource.Creator> em = DataSource.registerType("foo", "java.lang.String::CASE_INSENSITIVE_ORDER");
        Assert.assertTrue(em.inError());
        Assert.assertTrue( em.error().getMessage().contains("not a creator"));
    }

    @Test
    public void unRegisteredTypeTest(){
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DataSource.UNIVERSAL.create("foo", Map.of("type", "bar"), null);
        });
        Assert.assertNotNull(exception);
        Assert.assertTrue( exception.getMessage().contains("Unknown type of datasource"));
        Assert.assertTrue( exception.getMessage().contains("bar"));
    }

    @Test
    public void testEitherMonadUnsafe(){
        Assert.assertEquals(Integer.valueOf(42), EitherMonad.runUnsafe( () -> 42 ) );
        Exception exception = assertThrows(RuntimeException.class, () -> {
            EitherMonad.runUnsafe( () -> { throw new RuntimeException("boom!") ; });
        });
        Assert.assertTrue( exception.getMessage().contains("boom!"));
        exception = assertThrows(RuntimeException.class, () -> {
            EitherMonad.runUnsafe( () -> { throw new Exception("boom!") ; });
        });
        Assert.assertTrue( exception.getCause().getMessage().contains("boom!"));
    }
}
