package cowj;

import java.util.Map;

public interface DataSource {

    Object proxy();

    interface Creator {
        DataSource create(Map<String,Object> config);
    }

    Creator UNIVERSAL = config -> null;
}
