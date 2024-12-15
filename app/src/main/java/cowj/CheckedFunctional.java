package cowj;


/**
 * A basal implementation for bad design in Java's explicit throwable
 * <a href="https://stackoverflow.com/questions/11584159/is-there-a-way-to-make-runnables-run-throw-an-exception">...</a>
 */
public enum CheckedFunctional {
    // https://stackoverflow.com/questions/8848107/how-to-construct-a-non-instantiable-and-non-inheritable-class-in-java
    ;
    // following the footstep for the Giants
    /**
     * A wrapper for Wrapping up errors
     *
     */
    final static class Error extends RuntimeException{

        private Error(Throwable cause){
            super(cause);
        }

        /**
         * Returns the throwable itself if it is a RuntimeException
         * Otherwise, wraps it into an Error type and returns
         * @param t any Throwable
         * @return an Error or a RuntimeException
         */
        public static RuntimeException error(Throwable t){
            if ( t instanceof RuntimeException ) return (RuntimeException) t;
            return new Error(t);
        }

        public static Throwable cause(Throwable t){
            if ( t instanceof Error ) return t.getCause();
            return t;
        }
    }

    /**
     * Runnable can not have explicit throws associated with it
     * And this is the solution
     * @param <E> Type of Error thrown, really
     */
    @FunctionalInterface
    public interface Runnable<E extends Throwable> extends java.lang.Runnable {

        @Override
        default void run(){
            try {
                runThrows();
            }
            catch (Throwable ex) {
                throw Error.error(ex);
            }
        }

        /**
         * A basal implementation for bad design in Java's explicit throwable
         * Runnable can not have explicit throws associated with it
         * And this is the solution
         * @throws E any type of Error/Exception/Throwable
         */
        void runThrows() throws E;
    }

    /**
     * Function can not have explicit throws associated with it
     * And this is the solution
     * @param <T> Type of input
     * @param <R> Type of result
     * @param <E> Type of Error thrown, really
     */
    @FunctionalInterface
    public interface Function<T,R, E extends Throwable> extends java.util.function.Function<T,R> {

        @Override
        default R apply(T t ){
            try {
                return applyThrows(t);
            }
            catch (Throwable ex) {
                throw Error.error(ex);
            }
        }

        /**
         * A basal implementation for bad design in Java's explicit throwable
         * Function can not have explicit throws associated with it
         * @param t input
         * @return a value
         * @throws E some error
         */
        R applyThrows(T t) throws E;
    }

    /**
     * Consumer can not have explicit throws associated with it
     * And this is the solution
     * @param <T> Type of input
     * @param <E> Type of Error thrown, really
     */
    @FunctionalInterface
    public interface Consumer<T, E extends Throwable> extends java.util.function.Consumer<T> {

        @Override
        default void accept(T t ){
            try {
                acceptThrows(t);
            }
            catch (Throwable ex) {
                throw Error.error(ex);
            }
        }

        /**
         * A basal implementation for bad design in Java's explicit throwable
         * Function can not have explicit throws associated with it
         * @param t input
         * @throws E some error
         */
        void acceptThrows(T t) throws E;
    }
}
