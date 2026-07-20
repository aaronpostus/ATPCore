package aaronpost.atpcore.registries;

import aaronpost.atpcore.ATPCore;

import java.util.*;

public class DataRegistry<T extends IDataContainer> {
    protected LinkedHashMap<String, T> dataMap = new LinkedHashMap<>();
    private final String dataName;
    private final String fileName;
    private final String subPath;
    private final Class<T> clazz;
    private boolean initialized = false;

    public DataRegistry(Class<T> clazz, String name, String fileName, String subPath) {
        this.dataName = name;
        this.fileName = fileName;
        this.subPath = subPath;
        this.clazz = clazz;
    }
    public String getFileName() {
        return fileName;
    }
    public String getSubPath() {
        return subPath;
    }
    public Class<T> getClazz() {
        return clazz;
    }
    public static <T> T getStatAtLevel(T[] statArray, int level, T defaultVal) {
        // clamp level to prevent illegal input
        if (level < 1) {
            level = 1;
        }
        // if no list is defined, assume 1
        if (statArray == null || statArray.length == 0) {
            return defaultVal;
        }
        // if index is outside of range of list, return last defined element
        int length = statArray.length;
        if (level > length) {
            return statArray[length - 1];
        }
        // routine case, return user defined stat for th level
        return statArray[level - 1];
    }

    public void put(T element) {
        dataMap.put(element.getName(),element);
    }

    public void add(ArrayList<T> data) {
        for (T element : data) {
            try {
                element.validate();
                String name = element.getName();
                if (dataMap.containsKey(name)) {
                    throw new IllegalArgumentException(name + " uses a duplicate " + dataName + " name");
                }
                dataMap.put(element.getName(), element);
            } catch (Exception e) {
                ATPCore.logWarning(e.getMessage() + " and was not loaded.");
            }
        }
        ATPCore.log(dataMap.size() + " " + dataName + "s passed validation.");
    }

    public T get(String key) {
        T value = dataMap.get(key);
        if (value == null && !initialized) {
            throw new IllegalStateException("Missing " + dataName + ": " + key);
        }
        return value;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean exists(String key) {
        return dataMap.containsKey(key);
    }

    public Collection<T> getAllValues() {
        return dataMap.values();
    }
}
