package cowj;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import zoomba.lang.core.types.ZDate;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A FileWatcher to support automatic loading of
 *  - scripts
 *  - json schemas
 *  Given a string path is tested to possible to be handled by the instance ( Predicate )
 *  System should consume the string file path ( Consumer )
 */
interface FileWatcher extends Consumer<String>, Predicate<String> {

    /**
     * Logs with timestamp, formatted print, but always with newline to standard out
     * @param format format specifier string
     * @param args arguments to format the string
     */
    static void log(String format, Object...args){
        String resp = new ZDate() + "=>" + String.format(format,args);
        System.out.println(resp);
    }

    /**
     * Logs with timestamp, formatted print, but always with newline to standard err
     * @param format format specifier string
     * @param args arguments to format the string
     */
    static void err(String format, Object...args){
        String resp = new ZDate() + "=>" + String.format(format,args);
        System.err.println(resp);
    }

    /**
     * Default implementation for if string path is acceptable
     * @param s the input argument
     * @return true if acceptable, false if not
     */
    @Override
    default boolean test(String s) {
        return true;
    }

    /**
     * A FileWatcher to be used for logging changes in file - all files
     */
    FileWatcher FILE_MODIFICATION_LOGGER = s -> log("File Modified : %s", s);

    /**
     * Registry for  FileWatcher
     */
    List<FileWatcher> FILE_WATCHERS = new ArrayList<>( List.of(FILE_MODIFICATION_LOGGER) );

    /**
     * Starts watching and emitting File Modification events for a directory, recursively
     * @param directory to be watched recursively for file modifications
     */
    static void startWatchDog(String directory) {
        if (App.isProdMode()) return;
        Runnable runnable = () -> {
            try {
                File absFile = new File(directory).getCanonicalFile().getAbsoluteFile();
                DirectoryWatcher watcher = DirectoryWatcher.builder()
                        .path(absFile.toPath()) // or use paths(directoriesToWatch)
                        .listener(event -> {
                            if ( DirectoryChangeEvent.EventType.MODIFY.equals( event.eventType()) ) {/* file modified */
                                final String file = event.path().toAbsolutePath().toString();
                                FILE_WATCHERS.stream().filter(fileWatcher -> fileWatcher.test(file)).forEach(fileWatcher -> fileWatcher.accept(file));
                            }
                        }).build();
                watcher.watch();

            } catch (Throwable t) {
                err("Error Setting up File Watcher : " + t);
            }
        };
        Thread watcherThread = new Thread(runnable);
        watcherThread.start();
    }
}