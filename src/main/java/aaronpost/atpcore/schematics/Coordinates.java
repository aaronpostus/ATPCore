package aaronpost.atpcore.schematics;

import org.bukkit.Location;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Coordinates implements Serializable {
    public static Coordinates c = new Coordinates();
    private final List<LocationWrapper> coordinates = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    // Transient because Gson cannot serialize Maps.
    private transient Map<String, LocationWrapper> coordinatesWithKeys = new HashMap<>();

    public Coordinates() {

    }

    public void addCoordinate(Location l, String key) {
        LocationWrapper loc = new LocationWrapper(l);
        coordinates.add(loc);
        keys.add(key);
        coordinatesWithKeys.put(key, loc);
    }

    public Location getCoordinate(String key) {
        return coordinatesWithKeys.get(key).getLoc();
    }
}
