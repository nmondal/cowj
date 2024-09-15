package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.StorageWrapper;
import zoomba.lang.core.operations.Function;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * A file system backed Storage for persistence
 */
public class FileBackedStorage implements StorageWrapper.SimpleKeyValueStorage {

    /**
     * Actual file storage mount point for the system
     * From here, all buckets will be immediate subdirectory
     *
     */
    public final String absMountPoint;

    /**
     * From here, all buckets will be immediate subdirectory
     * @param mountPoint actual mount point
     */
    public FileBackedStorage(String mountPoint) {
        try {
            absMountPoint = new File(mountPoint).getAbsoluteFile().getCanonicalPath();
        }catch (Throwable t){
            throw Function.runTimeException(t);
        }
    }

    @Override
    public Boolean createBucket(String bucketName, String location, boolean preventPublicAccess) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( f.exists() ) return false;
        EitherMonad<EitherMonad.Nothing> em = EitherMonad.run( () -> Files.createDirectories( f.toPath() ));
        return em.isSuccessful();
    }

    /**
     * Recursively deletes a directory
     * @param path starting path of the directory
     * @return true if path existed, and it could delete the path, false otherwise
     */
    public static boolean deleteRecurse(Path path){
        if ( !path.toFile().exists() ) return false;
        EitherMonad.call( () ->{
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            return true;
        });
        return  !path.toFile().exists();
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( !f.exists() ) return false;
        return deleteRecurse( f.toPath() );
    }

    @Override
    public Boolean dumps(String bucketName, String fileName, String data) {
        return dumpb(bucketName, fileName, data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean fileExist(String bucketName, String fileName) {
        File f = new File(absMountPoint + "/" + bucketName +"/" + fileName );
        return f.exists();
    }

    /**
     * Reads entire file as String
     * @param path from this path
     * @return String if successful, null if any error happened or no file found
     */
    public static String readString(Path path){
        EitherMonad<String> em = EitherMonad.call( () -> Files.readString( path ));
        return em.isSuccessful() ? em.value() : null ;
    }

    @Override
    public Map.Entry<String,String> data(String bucketName, String fileName) {
        File f = new File(absMountPoint + "/" + bucketName  + "/" + fileName );
        return StorageWrapper.entry(fileName, readString( f.toPath() ) );
    }

    @Override
    public Boolean dumpb(String bucketName, String fileName, byte[] data) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( !f.exists() ) return false;
        final String path = absMountPoint + "/" + bucketName + "/" + fileName ;
        EitherMonad<EitherMonad.Nothing> em = EitherMonad.run( () -> {
            if (fileName.contains("/")) {
                int li = path.lastIndexOf('/');
                String folderPath = path.substring(0, li);
                Files.createDirectories( Paths.get(folderPath));
            }
            Files.write(Paths.get(path), data);
        });
        return em.isSuccessful() ;
    }

    @Override
    public Stream<Map.Entry<String,String>> stream(String bucketName, String directoryPrefix) {
        final String rootPrefix = absMountPoint + "/" + bucketName + "/" ;
        final int rootPrefixLen = rootPrefix.length();
        Path prefixPath = Paths.get( rootPrefix + directoryPrefix );
        Callable<Stream<Map.Entry<String,String>>> callable = () -> Files.walk(prefixPath)
                .sorted()
                .filter( path -> path.toFile().isFile())
                .map(path -> {
                    final String nonRooted = path.toFile().getAbsolutePath().substring( rootPrefixLen );
                    final String val = readString(path);
                    return StorageWrapper.entry( nonRooted, val );
                });

        EitherMonad<Stream<Map.Entry<String,String>>> em = EitherMonad.call( callable );
        return em.isSuccessful() ? em.value() : Stream.empty();
    }

    @Override
    public boolean delete(String bucketName, String path) {
        File f = new File(absMountPoint + "/" + bucketName + "/" + path );
        if( !f.exists() ) return false;
        return deleteRecurse(f.toPath());
    }

    /**
     * Key for configuration mount point to be passed to the creator
     */
    public static final String MOUNT_POINT = "mount-point" ;

    /**
     * A Field to create the storage
     */
    public static final  DataSource.Creator STORAGE = (name, config, parent) -> {
        String mntPt = config.getOrDefault(MOUNT_POINT, ".").toString() ;
        mntPt = parent.interpretPath(mntPt);
        logger.info("FileBackedStorage [{}] mount point is defined to be [{}]", name, mntPt );
        final FileBackedStorage storage = new FileBackedStorage(mntPt) ;
        logger.info("FileBackedStorage [{}] mount point resolved to [{}]", name, storage.absMountPoint );
        return DataSource.dataSource(name, storage );
    };
}
