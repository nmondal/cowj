package cowj;

public class TestUtils {

    public static  EitherMonad<Long> timeIt( Runnable r){
        long start = System.nanoTime();
        try{
            r.run();
            long end = System.nanoTime();
            return EitherMonad.value(end - start);
        }catch (Throwable err){
            return EitherMonad.error(err);
        }
    }
}
