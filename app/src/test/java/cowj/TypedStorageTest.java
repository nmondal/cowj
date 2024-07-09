package cowj;


import org.junit.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertThrows;

public class TypedStorageTest {

    static TypedStorage.SchemaRegistry registry = new TypedStorage.SchemaRegistry() {
        @Override
        public Map<Pattern, String> patternsToSchemas() {
            return Map.of(Pattern.compile("a/b"), "Person.json");
        }
        @Override
        public String pathSeperator() {
            return "/";
        }
    };

    static TypeSystem typeSystem;

    static Map<String,String> mem = new HashMap<>();

    static StorageWrapper<Boolean, Boolean, Map.Entry<String, String>> wrapper = new StorageWrapper.SimpleKeyValueStorage() {

        @Override
        public Boolean dumps(String bucketName, String fileName, String data) {
            mem.put(fileName,data);
            return true;
        }

        @Override
        public boolean fileExist(String bucketName, String fileName) {
            return mem.containsKey(fileName);
        }

        @Override
        public Map.Entry<String, String> data(String bucketName, String fileName) {
            String val = mem.get(fileName);
            return Map.entry(fileName,val);
        }

        @Override
        public Boolean dumpb(String bucketName, String fileName, byte[] data) {
            return false;
        }

        @Override
        public Stream<Map.Entry<String, String>> stream(String bucketName, String directoryPrefix) {
            return Stream.empty();
        }

        @Override
        public Boolean createBucket(String bucketName, String location, boolean preventPublicAccess) {
            return true;
        }

        @Override
        public boolean deleteBucket(String bucketName) {
            return false;
        }

        @Override
        public boolean delete(String bucketName, String path) {
            return false;
        }
    } ;

    @BeforeClass
    public static void beforeClass(){
        DataSource.unregisterDataSource(TypeSystem.DS_TYPE);
        typeSystem = TypeSystem.fromFile( "samples/prod/static/types/schema.yaml");
        mem.put("b", "42");
    }

    @AfterClass
    public static void afterClass(){
        DataSource.unregisterDataSource(TypeSystem.DS_TYPE);
    }

    @Test
    public void missingSchemaTest(){
        TypedStorage<Boolean, Boolean, Map.Entry<String, String>> typedStorage = TypedStorage.typedStorage( typeSystem, registry, wrapper, true);
        Assert.assertTrue( typedStorage.dumps("foo", "bar", "42") );
        Assert.assertEquals( "42", mem.get("bar"));
        Assert.assertEquals( "42", typedStorage.loads("foo","bar"));
    }

    @Test
    public void wrongSchemaTest(){
        TypedStorage<Boolean, Boolean, Map.Entry<String, String>> typedStorage = TypedStorage.typedStorage( typeSystem, registry, wrapper, true);
        Throwable th = assertThrows(TypedStorage.InvalidSchemaError.class, () -> {
            typedStorage.dumps("a", "b", "42");
        });
        Assert.assertNotNull(th);

        th = assertThrows(TypedStorage.InvalidSchemaError.class, () -> {
            typedStorage.loads("a", "b");
        });
        Assert.assertNotNull(th);
    }

    @Test
    public void facadeTest(){
        TypedStorage<Boolean, Boolean, Map.Entry<String, String>> typedStorage = TypedStorage.typedStorage( typeSystem, registry, wrapper, false);
        // read unverified first
        Assert.assertEquals( "42", typedStorage.loads("foo","b"));
        // rest are facade
        Map.Entry<String,String> entry = Map.entry("bar", "42");
        Assert.assertTrue(typedStorage.createBucket("foo", "", false));
        Assert.assertFalse( typedStorage.delete("foo","bar"));
        Assert.assertFalse( typedStorage.deleteBucket("foo"));
        Assert.assertEquals( Stream.empty().count(), typedStorage.stream("foo","bar").count());
        Assert.assertEquals( "bar", typedStorage.key(entry));
        Assert.assertEquals( entry.getValue(), new String(typedStorage.bytes(entry)));
        Assert.assertFalse( typedStorage.dumpb("foo","bar", new byte[]{} ));
        Assert.assertTrue( typedStorage.fileExist("foo","b" ));
    }
}
