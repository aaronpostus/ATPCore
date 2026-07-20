package aaronpost.atpcore.schematics;

import java.io.Serializable;
import java.util.*;

public class Schematics implements Serializable {
    public static Schematics s = new Schematics();
    private final List<Schematic> schematics = new ArrayList<>();
    private final Set<Schematic> schemChanged = new HashSet<>();

    // Game-plugin hooks. Installed once at startup by the host plugin.
    private transient BannerResolver bannerResolver = null;
    private transient PasteConfig pasteConfig = PasteConfig.DEFAULT;

    public BannerResolver getBannerResolver() {
        return bannerResolver;
    }

    public void setBannerResolver(BannerResolver bannerResolver) {
        this.bannerResolver = bannerResolver;
    }

    public PasteConfig getPasteConfig() {
        return pasteConfig;
    }

    public void setPasteConfig(PasteConfig pasteConfig) {
        this.pasteConfig = pasteConfig != null ? pasteConfig : PasteConfig.DEFAULT;
    }

    public boolean isNew(Schematic s)
    {
        return schemChanged.contains(s);
    }

    public void clear() {
        schematics.clear();
        schemChanged.clear();
    }

    public void addSchematic(Schematic s) {
        schematics.add(s);
        schemChanged.add(s);
    }

    public void addSchematic(List<Schematic> s) {
        schematics.addAll(s);
    }

    public Schematic getSchematic(String name) {
        for (Schematic schematic : schematics) {
            if (schematic.getName().equals(name)) {
                return schematic;
            }
        }
        return null;
    }

    public List<Schematic> getUpdatedSchematics()
    {
        return new ArrayList<>(schemChanged);
    }

    public List<Schematic> getSchematics() {
        return schematics;
    }
    public void removeSchematic(Schematic s)
    {
        schematics.remove(s);
        schemChanged.remove(s);
    }

    public void markUpdated(Schematic s)
    {
        schemChanged.add(s);
    }
}
