/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package cowj;

public class App {
    public static void main(String[] args) {
        ModelRunner mr = ModelRunner.fromModel( "app/samples/hello/hello.yaml") ;
        mr.run();
    }
}
