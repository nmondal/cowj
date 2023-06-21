package cowj;

import java.util.HashMap;
import java.util.Map;

/// Setting variables through System class is cumbersome.
/// We want to set up some environment variables using secret manager
public class CowjRuntime {
    public static Map<String, String> env = new HashMap<>(System.getenv());
}
