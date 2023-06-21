package cowj.plugins;

import cowj.DataSource;

import java.util.Map;

public interface SecretManager {

    default Map<String,String> env(){
        return System.getenv();
    }

    SecretManager LOCAL_ENV = new SecretManager() {};

    DataSource.Creator LOCAL = (name, config, parent) -> new DataSource() {
        @Override
        public Object proxy() {
            return LOCAL_ENV;
        }

        @Override
        public String name() {
            return name;
        }
    };
}
