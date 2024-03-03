package cowj.plugins;

import cowj.EitherMonad;
import cowj.StorageWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A versioned map to handle in memory versioning of data
 * Versions are stored as string rep of integer versions.
 * Latest is stored as 0, higher numerical value means older version
 * @param <K> type of key for the map
 * @param <V> type of value for the map
 */
public interface VersionedMap<K,V>  extends Map<K,V> {

    /**
     * The underlying map which will be used as the storage
     * @return an underlying map
     */
    Map<K, List<V>> underlying();

    /**
     * Streams versions for the key
     * @param key the key value
     * @return a Stream of Strings for the data stored at the key
     */
    default Stream<String> versions( K key) {
        List<V> history = underlying().getOrDefault(key, Collections.emptyList());
        return IntStream.range(0, history.size()).mapToObj(String::valueOf);
    }

    /**
     * Gets the data at the version for a key , key must be integer passed as string
     * @param key the key
     * @param versionId version whose data we need to get
     * @return data for that key at that version, if not found or any error, null
     */
    default V dataAtVersion(K key, String versionId) {
        final List<V> history = underlying().getOrDefault(key, Collections.emptyList());
        return EitherMonad.orNull( () -> history.get( Integer.parseInt(versionId) ) );
    }

    @Override
    default int size() {
        return underlying().size();
    }

    @Override
    default boolean isEmpty() {
        return underlying().isEmpty();
    }

    @Override
    default boolean containsKey(Object key) {
        return underlying().containsKey(key);
    }

    @Override
    default boolean containsValue(Object value) {
        return underlying().values().stream().anyMatch(list -> list.getFirst().equals(value) );
    }

    @Override
    default V get(Object key) {
        return EitherMonad.orNull( () -> underlying().getOrDefault( key , Collections.emptyList()).getFirst());
    }

    @Nullable
    @Override
     default V put(K key, V value) {
        Map<K,List<V>> u = underlying();
        synchronized (u){
            List<V> history = u.getOrDefault(key, Collections.emptyList());
            try {
                V latest = history.get(0);
                history.add(0, value);
                return latest;
            }catch (Throwable ignore){
                    List<V> ll = new LinkedList<>();
                    ll.add(value);
                    u.put(key, Collections.synchronizedList(ll));
                    return null;
            }
        }
    }

    @Override
    default V remove(Object key) {
        Map<K,List<V>> u = underlying();
        synchronized (u){
           List<V> history = u.remove(key);
           try {
                return history.get(0);
           }catch (Throwable ignore){
               return null;
           }
        }
    }

    @Override
    default void putAll(@NotNull Map<? extends K, ? extends V> m) {
        m.forEach(this::put);
    }

    @Override
    default void clear() {
        underlying().clear();
    }

    @NotNull
    @Override
    default Set<K> keySet() {
        return underlying().keySet();
    }

    @NotNull
    @Override
    default Collection<V> values() {
        return underlying().values().stream().map(List::getFirst).toList();
    }

    @NotNull
    @Override
    default Set<Entry<K, V>> entrySet() {
        return underlying().entrySet().stream()
                .map( e -> StorageWrapper.entry( e.getKey(), e.getValue().getFirst()))
                .collect(Collectors.toSet());
    }

    static <K,V> VersionedMap<K,V> versionedMap(Map<K,List<V>> map){
        return () -> map;
    }
    static <K,V> VersionedMap<K,V> versionedMap(){
        return versionedMap(new HashMap<>());
    }
}
