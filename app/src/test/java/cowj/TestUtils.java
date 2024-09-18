package cowj;

public class TestUtils {

    public static  EitherMonad<Long> timeIt( Runnable r){
        final long start = System.nanoTime();
        try{
            r.run();
            final long end = System.nanoTime();
            return EitherMonad.value(end - start);
        }catch (Throwable err){
            return EitherMonad.error(err);
        }
    }
}
