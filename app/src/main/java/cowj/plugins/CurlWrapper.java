package cowj.plugins;

import cowj.DataSource;
import zoomba.lang.core.io.ZWeb;

import java.util.Map;

public interface CurlWrapper {

    ZWeb.ZWebCom send(String verb, String path, Map<String,String> headers, Map<String,String> params, String body);

    DataSource.Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();

            final CurlWrapper curlWrapper = (verb, path, headers, params, body) -> {
                ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                zWeb.headers.putAll(headers);
                try {
                    return zWeb.send(verb, path, params, body);
                }catch (Throwable t){
                    return null;
                }
            };

            return new DataSource() {
                @Override
                public Object proxy() {
                    return curlWrapper;
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
