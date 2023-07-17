package cowj;

/**
 * Abstraction over Result of any operation
 * Either The result of Type V, or that of Throwable
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
     * Is the instance success type ? Does it contain Result of type <V>?
     * @return true if <V> type, else false
     */
    public boolean isSuccessful(){ return  err == null; }

    /**
     * Gets the value
     * @return underlying value of type <V>
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
     * @param value underlying <V>
     * @return an EitherMonad
     * @param <V> of type
     */
    public static <V> EitherMonad<V> value(V value){
        return new EitherMonad<>(value, null);
    }
}
