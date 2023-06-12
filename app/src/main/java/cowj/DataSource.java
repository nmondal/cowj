package cowj;

import java.lang.reflect.Field;
import java.util.*;

public interface DataSource {

    Object proxy();

    String name();

    interface Creator {
        DataSource create(String name, Map<String, Object> config, Model parent);
    }

    Map<String,Creator> REGISTRY = new HashMap<>();

    static void registerType(String type, String path){
        String[] paths = path.split("::");
        try {
            Class<?> clazz = Class.forName(paths[0]);
            Field f = clazz.getDeclaredField(paths[1]);
            Object r = f.get(null);
            if ( !(r instanceof Creator) ){
                System.err.println( "Error registering type... not a creator object : " + r.getClass());
                return;
            }
            REGISTRY.put(type,(Creator) r);
            System.out.printf("Registered '%s::%s' as provider type for '%s' %n", clazz.getName(), f.getName(), type );
        }catch (Throwable t){
            System.err.println( "Error registering type... : " + t);
        }
    }

    Creator UNIVERSAL = (name, config, parent) -> {
        String type = config.getOrDefault("type", "").toString();
        Creator creator = REGISTRY.get(type);
        if (creator == null) throw new IllegalStateException("Unknown type of datasource -> value: " + type);
        return creator.create(name, config, parent);
    };
}
