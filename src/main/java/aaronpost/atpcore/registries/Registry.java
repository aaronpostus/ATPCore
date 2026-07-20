package aaronpost.atpcore.registries;

import aaronpost.atpcore.schematics.LocationWrapper2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base registry-of-registries. Game plugins extend this class, declare their
 * static {@link DataRegistry} fields, and register them via {@link #register}
 * in a static initializer — insertion order defines load order.
 *
 * The boolean flag marks whether the registry is auto-loadable from a single
 * JSON list file (true) or loaded through custom logic by the game plugin (false).
 */
public class Registry {
    // LinkedHashMap preserves insertion order — this is the order registries load in
    public static final Map<DataRegistry<?>, Boolean> registries = new LinkedHashMap<>();

    /**
     * Named coordinates registry ("Locations.json"), owned by ATPCore because
     * the coordinate wand and {@code AdminCoordinateMenu} operate on it.
     * The host plugin decides where the file lives — call {@link #initLocationData}
     * from onEnable before registering registries. Create-once: safe on reload.
     */
    public static DataRegistry<LocationWrapper2> LocationData;

    public static synchronized void initLocationData(String subPath) {
        if (LocationData == null) {
            LocationData = new DataRegistry<>(LocationWrapper2.class, "location", "Locations.json", subPath);
        }
    }

    protected static void register(DataRegistry<?> registry, boolean autoLoad) {
        registries.put(registry, autoLoad);
    }

    public static void setAllInitialized(boolean initialized) {
        for (DataRegistry<?> registry : registries.keySet()) {
            registry.setInitialized(initialized);
        }
    }

    public static void clear() {
        for (DataRegistry<?> registry : registries.keySet()) {
            registry.dataMap.clear();
            registry.setInitialized(false);
        }
    }
}
