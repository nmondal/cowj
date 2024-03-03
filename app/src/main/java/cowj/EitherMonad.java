package cowj;

import java.util.concurrent.Callable;

/**
 * Abstraction over Result of any operation
 * Either The result of Type V, or that of Throwable
 * @see <a href="https://hackage.haskell.org/package/category-extras-0.53.5/docs/Control-Monad-Either.html">Haskell : Either</a>
 * @param <V> type of the Result
 */
public final class EitherMonad<V> {
    private final V value;
    private final Throwable err;

    /**
     * Is the instance error type ?
     * @return true if error type, else false
     */
    public boolean inError(){
        return  !isSuccessful();
    }

    /**
     * Is the instance success type ? Does it contain Result of type V?
     * @return true if V type, else false
     */
    public boolean isSuccessful(){ return  err == null; }

    /**
     * Gets the value
     * @return underlying value of type V
     */
    public V value() { return value; }

    /**
     * Gets the Error
     * @return underlying Error, a Throwable
     */
    public Throwable error() { return err; }

    private EitherMonad(V value, Throwable err){
        this.value = value;
        this.err = err;
    }

    /**
     * Creates an EitherMonad of error
     * @param th underlying Throwable
     * @return an EitherMonad
     * @param <V> of type
     */
    public static <V> EitherMonad<V>  error(Throwable th){
        return new EitherMonad<>(null, th);
    }

    /**
     * Creates an EitherMonad of error
     * @param value underlying value of type V
     * @return an EitherMonad
     * @param <V> of type
     */
    public static <V> EitherMonad<V> value(V value){
        return new EitherMonad<>(value, null);
    }

    /**
     * Creates an EitherMonad by running the callable code
     * If successful, returns the result , if not returns error
     * @param callable Callable code to be called
     * @return EitherMonad of type of the Callable
     * @param <V> type of the Callable
     */
    public static <V> EitherMonad<V> call( Callable<V> callable){
        try {
            return value(callable.call());
        }catch (Throwable t){
            return error(t);
        }
    }

    /**
     * Runs the callable code
     * If successful, returns the result , if not throws Runtime error
     * @param callable Callable code to be called
     * @return Result of callable
     * @param <V> type of the Callable
     */
    public static <V> V runUnsafe( Callable<V> callable){
        try {
            return callable.call();
        }catch (Throwable t){
            if ( t instanceof RuntimeException )throw  (RuntimeException)t;
            throw new RuntimeException(t);
        }
    }

    /**
     * Runs the callable code
     * If successful, returns the result , if not returns null
     * @param callable Callable code to be called
     * @param whenError when error, return this value
     * @return Result of callable
     * @param <V> type of the Callable
     */
    public static <V> V orElse( Callable<V> callable, V whenError){
        try {
            return callable.call();
        }catch (Throwable t){
            return whenError;
        }
    }

    /**
     * Runs the callable code
     * If successful, returns the result , if not returns null
     * @param callable Callable code to be called
     * @return Result of callable
     * @param <V> type of the Callable
     */
    public static <V> V orNull( Callable<V> callable){
        return orElse(callable, null);
    }
}
