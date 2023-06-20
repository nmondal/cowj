package cowj;

public final class EitherMonad<V> {
    private final V value;
    private final Throwable err;

    public boolean inError(){
        return  !isSuccessful();
    }
    public boolean isSuccessful(){ return  err == null; }

    public boolean isValid(){
        return value == null && err == null  || value != null && err != null ;
    }
    public V value() { return value; }
    public Throwable error() { return err; }

    private EitherMonad(V value, Throwable err){
        this.value = value;
        this.err = err;
    }

    public static <V> EitherMonad<V>  error(Throwable th){
        return new EitherMonad<>(null, th);
    }
    public static <V> EitherMonad<V> value(V value){
        return new EitherMonad<>(value, null);
    }
}
