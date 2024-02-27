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
import java.util.stream.Stream;

/**
 * A file system backed Storage for persistence
 */
public class FileBackedStorage implements StorageWrapper<Boolean,Boolean,String>  {

    public final String absMountPoint;
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
        EitherMonad<Boolean> em = EitherMonad.call( () -> {
            Files.createDirectories( f.toPath() );
            return true;
        }  );
        return em.isSuccessful() && em.value() ;
    }

    public static boolean deleteRecurse(Path path){
        EitherMonad<Boolean> em = EitherMonad.call( () ->{
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            return true;
        });
        return em.isSuccessful() && !path.toFile().exists();
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( !f.exists() ) return false;
        return deleteRecurse( f.toPath() );
    }

    @Override
    public Boolean dumps(String bucketName, String fileName, String data) {
        return dumpb(bucketName, fileName, bytes(data));
    }

    @Override
    public boolean fileExist(String bucketName, String fileName) {
        File f = new File(absMountPoint + "/" + bucketName +"/" + fileName );
        return f.exists();
    }


    public static String readString(Path path){
        EitherMonad<String> em = EitherMonad.call( () -> Files.readString( path ));
        return em.isSuccessful() ? em.value() : "" ;
    }

    @Override
    public String data(String bucketName, String fileName) {
        File f = new File(absMountPoint + "/" + bucketName  + "/" + fileName );
        return readString( f.toPath() );
    }

    @Override
    public byte[] bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String utf8(String input) {
        return input;
    }

    @Override
    public Boolean dumpb(String bucketName, String fileName, byte[] data) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( !f.exists() ) return false;
        final String path = absMountPoint + "/" + bucketName + "/" + fileName ;
        EitherMonad<Boolean> em = EitherMonad.call( () -> {
            if (fileName.contains("/")) {
                int li = path.lastIndexOf('/');
                String folderPath = path.substring(0, li);
                Files.createDirectories( Paths.get(folderPath));
            }
            Files.write(Paths.get(path), data);
            return true;
        });
        return em.isSuccessful() && em.value() ;
    }

    @Override
    public Stream<String> stream(String bucketName, String directoryPrefix) {
        Path prefixPath = Paths.get( absMountPoint + "/" + bucketName + "/" + directoryPrefix );
        EitherMonad<Stream<String>> em = EitherMonad.call( () -> Files.walk(prefixPath)
                .sorted(Comparator.reverseOrder())
                .map(path -> {
                    if (path.toFile().isDirectory()) return "";
                    return readString(path);
                }));
        return em.isSuccessful() ? em.value() : Stream.empty();
    }

    @Override
    public boolean delete(String bucketName, String path) {
        File f = new File(absMountPoint + "/" + bucketName + "/" + path );
        if( !f.exists() ) return false;
        return deleteRecurse(f.toPath());
    }

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
