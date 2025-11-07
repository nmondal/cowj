package cowj;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class EitherMonadTest {

    @Test
    public void basicTest(){
        EitherMonad<String> ems = EitherMonad.value("");
        Assert.assertTrue( ems.isSuccessful() );
        Assert.assertFalse( ems.inError() );
        ems = EitherMonad.error(new Throwable());
        Assert.assertFalse( ems.isSuccessful() );
        Assert.assertTrue( ems.inError() );
    }

    @Test
    public void unsafeTest(){
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

    @Test
    public void orConditionTest(){
        // Regular flow
        Assert.assertEquals( 1L, EitherMonad.orElse( () -> 1L, 0 ));
        final Callable<Integer> c = () -> {
            throw new RuntimeException("boom!") ;
        };
        // Else flow
        Assert.assertEquals( 0, (int)EitherMonad.orElse( c, 0));
        Assert.assertNull( EitherMonad.orNull(c));
    }

    @Test
    public void thenTest(){
        // Regular flow
        Assert.assertEquals( 0, (int)EitherMonad.value(1).then(x -> x-1).value());
        final Throwable t = new RuntimeException();
        Assert.assertEquals( t, EitherMonad.error(t) .then(x -> x).error());
    }

    @Test
    public void ensureTest(){
        // Regular flow
        Assert.assertEquals( 1, (int)EitherMonad.value(1).ensure().value());
        final Throwable t = new RuntimeException();
        Throwable th = assertThrows(RuntimeException.class, () -> {
            EitherMonad.error(t).ensure();
        });
        Assert.assertEquals(t,th);
        final Throwable tt = new Throwable();
        th = assertThrows(RuntimeException.class, () -> {
            EitherMonad.error(tt).ensure();
        });
        Assert.assertEquals(tt, th.getCause());
    }

    @Test
    public void runnableTest(){
        Assert.assertTrue( EitherMonad.run( () -> {}).isSuccessful() );
        Assert.assertTrue( EitherMonad.run( () -> {
            int i = 1 / 0;
        }).inError() );
    }

    @Test
    public void whenSuccessTest(){
        final String[] arr = new String[]{ null } ;
        CheckedFunctional.Consumer<String,?> c = (x) -> {  arr[0] = x ; };
        EitherMonad<String> em = EitherMonad.error(new Throwable());
        assertSame( em, em.whenSuccess(c) );
        assertNull(arr[0]);

        em = EitherMonad.value("Hello!");
        assertSame( em, em.whenSuccess(c) );
        assertEquals("Hello!", arr[0] );


    }
}
