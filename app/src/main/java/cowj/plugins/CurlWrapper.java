package cowj.plugins;

import cowj.DataSource;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;
import java.util.Map;

public class CurlWrapper {
    public static DataSource.Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();
            Map<String, String> headers = (Map) config.getOrDefault("headers", Collections.emptyMap());
            ZWeb zWeb = new ZWeb(baseUrl);
            if (!headers.isEmpty()) {
                zWeb.headers.putAll(headers);
            }
            return new DataSource() {
                @Override
                public Object proxy() {
                    return zWeb;
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
