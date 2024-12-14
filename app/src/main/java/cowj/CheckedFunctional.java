package cowj;

/**
 * A basal implementation for bad design in Java's explicit throwable
 * <a href="https://stackoverflow.com/questions/11584159/is-there-a-way-to-make-runnables-run-throw-an-exception">...</a>
 */
public final class CheckedFunctional {

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

}
