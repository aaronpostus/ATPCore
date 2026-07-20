package aaronpost.atpcore.persistence;

import aaronpost.atpcore.ATPCore;
import aaronpost.atpcore.registries.DataRegistry;
import aaronpost.atpcore.registries.IDataContainer;
import aaronpost.atpcore.schematics.Schematic;
import aaronpost.atpcore.schematics.Schematics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Generic JSON persistence shared by all game plugins: schematic files and
 * registry list files. Paths are passed in by the host plugin so each plugin
 * keeps its data in its own folder.
 */
public final class CoreSerializer {

    private CoreSerializer() {}

    /** Writes every created/modified schematic in {@link Schematics#s} to {@code schematicsDir}. */
    public static void serializeSchematics(File schematicsDir) throws IOException {
        List<Schematic> schematics = Schematics.s.getUpdatedSchematics();
        GsonBuilder builder = new GsonBuilder();
        builder.enableComplexMapKeySerialization();
        builder.setPrettyPrinting();
        Gson g = builder.create();

        schematicsDir.mkdirs();
        for (Schematic schematic : schematics) {
            File file = new File(schematicsDir, schematic.getName() + ".json");

            try (Writer w = new FileWriter(file, false)) {
                g.toJson(schematic, w);
                ATPCore.log("Updated/created data for " + schematic.getName() + ".json");
            } catch (IOException e) {
                ATPCore.plugin.getLogger().log(Level.SEVERE, "Failed to serialize schematic: " + schematic.getName(), e);
            }
        }
    }

    /** Loads every schematic json in {@code schematicsDir}. Does not add them to {@link Schematics#s}. */
    public static List<Schematic> deserializeSchematics(File schematicsDir) throws IOException {
        File[] schematicsFilePath = schematicsDir.listFiles();
        List<Schematic> schematics = new ArrayList<>();
        int numberOfLoadedSchematics = 0;
        if (schematicsFilePath != null) {
            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            for (File file : schematicsFilePath) {
                if (file.exists()) {
                    if(!file.getName().contains(".json")) { continue; } // skip anything that's not json
                    try (Reader reader = new FileReader(file)) {
                        Schematic s = gson.fromJson(reader, Schematic.class);
                        schematics.add(s);
                    }
                    catch (Exception e)
                    {
                        ATPCore.plugin.getLogger().log(Level.SEVERE, "Exception deserializing schematic: " + file, e);
                    }
                    numberOfLoadedSchematics++;
                } else {
                    ATPCore.log("Something is horribly wrong with the file system. Fix this.");
                    return null;
                }
            }
            ATPCore.log("Loaded " + numberOfLoadedSchematics + " schematics.");
            return schematics;
        }
        return Collections.emptyList();
    }

    /**
     * Loads a registry's backing JSON list file ({@code <subPath>/<fileName>}),
     * creating an empty file if missing. Returns the raw deserialized list;
     * callers pass it to {@link DataRegistry#add} for validation.
     */
    public static <T extends IDataContainer> ArrayList<T> deserializeList(DataRegistry<T> reg) throws IOException {
        String fileName = reg.getFileName();
        String subPath = reg.getSubPath();
        Class<T> clazz = reg.getClazz();
        File file = new File(subPath + File.separator + fileName);

        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (created) {
                ATPCore.log("Created new config file: " + fileName);
            } else {
                ATPCore.log("Could not create config file for: " + fileName);
            }
            return new ArrayList<>();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(LocalDate.class,new LocalDateTypeAdapter()).create();
        Type listType = TypeToken.getParameterized(List.class, clazz).getType();

        try (Reader reader = new FileReader(file)) {
            try {
                ArrayList<T> data = gson.fromJson(reader, listType);
                if (data == null) return new ArrayList<>();
                return data;
            } catch (Exception e) {
                throw new IOException(fileName + " is formatted incorrectly and cannot be read: " + e.getMessage());
            }
        }
    }
}
