package cowj.plugins;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import cowj.DataSource;

public class GoogleStorageWrapper {
    public static DataSource.Creator STORAGE = (name, config, parent) -> {
        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();
            return new DataSource() {
                @Override
                public Object proxy() {
                    return storage;
                }

                @Override
                public String name() {
                    return name;
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    };
}
