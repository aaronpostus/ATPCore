package aaronpost.atpcore.schematics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.io.Serializable;

public class LocationWrapper implements Serializable {
    private final double x;
    private final double y;
    private final double z;
    private final String world;

    public LocationWrapper(Location loc) {
        x = loc.getX();
        y = loc.getY();
        z = loc.getZ();
        world = loc.getWorld().getName();
    }

    public Location getLoc() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }

    public Block getBlock() {
        return getLoc().getBlock();
    }

    @Override
    public String toString() {
        return "x: " + Math.round(x) +
                ", y: " + Math.round(y) +
                ", z: " + Math.round(z) +
                ", world: " + world;
    }
}
