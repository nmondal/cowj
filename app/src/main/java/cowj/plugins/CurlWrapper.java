package cowj.plugins;

import cowj.DataSource;
import spark.Response;
import zoomba.lang.core.io.ZWeb;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CurlWrapper {

    ZWeb.ZWebCom send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body);

    /// Forwards the request to the host and sets up the headers and
    /// status in response. Returns the body of the response.
    /// I need to take resp as input because Response cannot be created
    /// outside of Spark
    String forward(String verb, String path, Map<String, String> headers, Map<String, String> params, String body, Response response);

    DataSource.Creator CURL = (name, config, parent) -> {
        try {
            String baseUrl = config.getOrDefault("url", "").toString();

            final CurlWrapper curlWrapper = new CurlWrapper() {
                @Override
                public ZWeb.ZWebCom send(String verb, String path, Map<String, String> headers, Map<String, String> params, String body) {
                    ZWeb zWeb = new ZWeb(baseUrl); // every call gets its own con
                    zWeb.headers.putAll(headers);
                    try {
                        return zWeb.send(verb, path, params, body);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        return null;
                    }
                }

                @Override
                public String forward(String verb, String path, Map<String, String> headers, Map<String, String> params, String body, Response response) {
                    ZWeb.ZWebCom zWeb = send(verb, path, headers, params, body);
                    if (zWeb == null) {
                        return null;
                    }

                    response.status(zWeb.status);

                    for (Map.Entry<Object, Object> entry : ((Map<Object, Object>)zWeb.map).entrySet()) {
                        /// I don't know why but HTTP status line is returned as
                        /// a value with null key. Status line should not be
                        /// returned. So skipping null keys
                        if (entry.getKey() == null) {
                            continue;
                        }

                        Object value = entry.getValue();

                        /// Some headers have multiple values.
                        /// They are returned as List. According to
                        /// standard, they should be comma separated
                        if (value instanceof List) {
                            value = ((List<?>) value).stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(","));
                        }
                        response.header(entry.getKey().toString(), value.toString());
                    }
                    return zWeb.body();
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
