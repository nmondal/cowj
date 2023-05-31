package cowj;

import spark.Route;

public interface RouteCreator {
    Route create(String path, String handler);

    RouteCreator NOP = (path, handler) -> (Route) (request, response) -> null;
}
