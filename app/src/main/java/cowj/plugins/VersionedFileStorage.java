package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.StorageWrapper;
import zoomba.lang.core.types.ZDate;
import zoomba.lang.core.types.ZNumber;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * A Versioned (journaled) file system backed key value Storage for persistence
 * This will ONLY work in a FileSystems capable of non admin soft linking ln -s
 * <a href="https://stackoverflow.com/questions/19230535/create-windows-symbolic-link-with-java-equivalent-to-mklink">...</a>
 * For Windows - please enable Developer Mode - for Non Admin Soft Link
 * <a href="https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development">...</a>
 *
 */
public class VersionedFileStorage extends FileBackedStorage
        implements StorageWrapper.VersionedStorage<String> {

    static final String VERSION_DIR = "__ver__" ;

    static final String LATEST = "__latest__" ;

    /**
     * From here, all buckets will be immediate subdirectory
     * @param mountPoint actual mount point
     */
    public VersionedFileStorage(String mountPoint) {
        super(mountPoint);
    }

    @Override
    public Stream<String> versions(String bucketName, String fileName) {
        final File versionDir = new File( absMountPoint + "/" + bucketName  + "/" + fileName + "/" + VERSION_DIR ) ;
        return EitherMonad.orElse( () ->
                Arrays.stream(versionDir.listFiles()).map(File::getName).sorted(Comparator.reverseOrder()) ,
                Stream.empty() );
    }

    @Override
    public String dataAtVersion(String bucketName, String fileName, String versionId) {
        Path p = Paths.get(absMountPoint + "/" + bucketName  + "/" + fileName + "/" + VERSION_DIR + "/" + versionId );
        return readString(p);
    }

    @Override
    public Map.Entry<String,String> data(String bucketName, String fileName) {
        Path p = Paths.get(absMountPoint + "/" + bucketName  + "/" + fileName + "/" + LATEST );
        return StorageWrapper.entry(fileName, readString( p ) );
    }

    @Override
    public Boolean dumpb(String bucketName, String fileName, byte[] data) {
        File f = new File(absMountPoint + "/" + bucketName );
        if( !f.exists() ) return false;
        final String key = absMountPoint + "/" + bucketName + "/" + fileName + "/" ;
        final String path = key  + LATEST ;
        final String verDir = key + VERSION_DIR ;
        final File keyFile = new File(key);
        final Path latestPath = Paths.get( path );

        EitherMonad<?> em = EitherMonad.run( () -> {
            if ( !keyFile.exists() ) {
                if (fileName.contains("/")) {
                    int li = path.lastIndexOf('/');
                    String folderPath = path.substring(0, li);
                    Files.createDirectories(Paths.get(folderPath));
                }
                Files.createDirectories(Paths.get(verDir));
            }
            synchronized (this) {
                // ensure one can only get handle to this, and thus latest means latest
                final String versionId = ZNumber.radix(new BigInteger(new ZDate().toNano().toString()), 62);
                final String actualFile = verDir + "/" + versionId;
                Path target = Paths.get(actualFile);
                Files.write(target, data);
                Files.deleteIfExists( latestPath);
                Files.createSymbolicLink(latestPath, target);
            }
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
                .filter( path -> path.endsWith( LATEST ) && path.toFile().isFile() )
                .map(path -> {
                    final String nonRooted = path.toFile().getAbsolutePath().substring( rootPrefixLen );
                    final String val = readString(path);
                    final String fileKey = nonRooted.replace(LATEST, "");
                    return StorageWrapper.entry( fileKey, val );
                });

        EitherMonad<Stream<Map.Entry<String,String>>> em = EitherMonad.call( callable );
        return em.isSuccessful() ? em.value() : Stream.empty();
    }

    /**
     * A Field to create the storage
     */
    public static final  DataSource.Creator STORAGE = (name, config, parent) -> {
        String mntPt = config.getOrDefault(MOUNT_POINT, ".").toString() ;
        mntPt = parent.interpretPath(mntPt);
        logger.info("VersionedFileStorage [{}] mount point is defined to be [{}]", name, mntPt );
        final VersionedFileStorage storage = new VersionedFileStorage(mntPt) ;
        logger.info("VersionedFileStorage [{}] mount point resolved to [{}]", name, storage.absMountPoint );
        return DataSource.dataSource(name, storage );
    };
}
