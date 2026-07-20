package aaronpost.atpcore.schematics;

import aaronpost.atpcore.registries.IDataContainer;
import org.bukkit.Location;

public class LocationWrapper2 extends LocationWrapper implements IDataContainer {
    private final String name;
    private final float yaw;
    private final float pitch;
    public LocationWrapper2(String name, Location loc) {
        super(loc);
        this.name = name;
        yaw = loc.getYaw();
        pitch = loc.getPitch();
    }

    @Override
    public void validate() {

    }

    public String getName() {
        return name;
    }
    @Override
    public Location getLoc() {
        Location loc = super.getLoc();
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }
    @Override
    public String toString() {
        return super.toString();
    }
}
