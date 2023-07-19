package cowj;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import zoomba.lang.core.types.ZDate;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

interface FileWatcher extends Consumer<String>, Predicate<String> {
    static void log(String format, Object...args){
        String resp = new ZDate() + "=>" + String.format(format,args);
        System.out.println(resp);
    }
    static void err(String format, Object...args){
        String resp = new ZDate() + "=>" + String.format(format,args);
        System.err.println(resp);
    }

    @Override
    default boolean test(String s) {
        return true;
    }
    FileWatcher FILE_MODIFICATION_LOGGER = s -> log("File Modified : %s", s);

    List<FileWatcher> FILE_WATCHERS = new ArrayList<>( List.of(FILE_MODIFICATION_LOGGER) );

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