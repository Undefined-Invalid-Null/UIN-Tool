package com.UIN.Tool.shared.settings.properties;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.UIN.Tool.shared.file.FileUtils;
import com.UIN.Tool.shared.file.filesystem.FileType;
import com.UIN.Tool.shared.logger.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class SharedProperties {

    private Properties mProperties;
    private Map<String, Object> mMap;

    private final Context mContext;
    private final File mPropertiesFile;
    private final Set<String> mPropertiesList;
    private final SharedPropertiesParser mSharedPropertiesParser;

    private final Object mLock = new Object();

    // ========== 使用普通 Map 代替 Guava BiMap ==========
    private static final Map<String, Boolean> MAP_GENERIC_BOOLEAN = new HashMap<>();
    private static final Map<Boolean, String> MAP_GENERIC_BOOLEAN_INVERSE = new HashMap<>();

    private static final Map<String, Boolean> MAP_GENERIC_INVERTED_BOOLEAN = new HashMap<>();
    private static final Map<Boolean, String> MAP_GENERIC_INVERTED_BOOLEAN_INVERSE = new HashMap<>();

    static {
        MAP_GENERIC_BOOLEAN.put("true", true);
        MAP_GENERIC_BOOLEAN.put("false", false);
        MAP_GENERIC_BOOLEAN_INVERSE.put(true, "true");
        MAP_GENERIC_BOOLEAN_INVERSE.put(false, "false");

        MAP_GENERIC_INVERTED_BOOLEAN.put("true", false);
        MAP_GENERIC_INVERTED_BOOLEAN.put("false", true);
        MAP_GENERIC_INVERTED_BOOLEAN_INVERSE.put(false, "true");
        MAP_GENERIC_INVERTED_BOOLEAN_INVERSE.put(true, "false");
    }

    private static final String LOG_TAG = "SharedProperties";

    // ========== 构造函数 ==========
    public SharedProperties(@NonNull Context context, @Nullable File propertiesFile, Set<String> propertiesList, @NonNull SharedPropertiesParser sharedPropertiesParser) {
        mContext = context.getApplicationContext();
        mPropertiesFile = propertiesFile;
        mPropertiesList = propertiesList;
        mSharedPropertiesParser = sharedPropertiesParser;
        mProperties = new Properties();
        mMap = new HashMap<>();
    }

    // ========== 实例方法 ==========
    public void loadPropertiesFromDisk() {
        synchronized (mLock) {
            Properties properties = getProperties(false);
            if (properties == null) properties = new Properties();

            HashMap<String, Object> map = new HashMap<>();
            Properties newProperties = new Properties();

            Set<String> propertiesList = mPropertiesList;
            if (propertiesList == null) propertiesList = properties.stringPropertyNames();

            String value;
            Object internalValue;
            for (String key : propertiesList) {
                value = properties.getProperty(key);
                internalValue = mSharedPropertiesParser.getInternalPropertyValueFromValue(mContext, key, value);
                if (putToMap(map, key, internalValue)) {
                    putToProperties(newProperties, key, value);
                }
            }

            mMap = map;
            mProperties = newProperties;
        }
    }

    public Properties getProperties(boolean cached) {
        synchronized (mLock) {
            if (cached) {
                if (mProperties == null) mProperties = new Properties();
                return getPropertiesCopy(mProperties);
            } else {
                return getPropertiesFromFile(mContext, mPropertiesFile, mSharedPropertiesParser);
            }
        }
    }

    public String getProperty(String key, boolean cached) {
        synchronized (mLock) {
            return (String) getProperties(cached).get(key);
        }
    }

    public Map<String, Object> getInternalProperties() {
        synchronized (mLock) {
            if (mMap == null) mMap = new HashMap<>();
            return getMapCopy(mMap);
        }
    }

    public Object getInternalProperty(String key) {
        synchronized (mLock) {
            if (key != null) return getInternalProperties().get(key);
            else return null;
        }
    }

    // ========== 静态方法 ==========
    public static Properties getPropertiesFromFile(Context context, File propertiesFile, @Nullable SharedPropertiesParser sharedPropertiesParser) {
        Properties properties = new Properties();

        if (propertiesFile == null) {
            Logger.logWarn(LOG_TAG, "Not loading properties since file is null");
            return properties;
        }

        try {
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                Logger.logVerbose(LOG_TAG, "Loading properties from \"" + propertiesFile.getAbsolutePath() + "\" file");
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            if (context != null)
                Toast.makeText(context, "Could not open properties file \"" + propertiesFile.getAbsolutePath() + "\": " + e.getMessage(), Toast.LENGTH_LONG).show();
            Logger.logStackTraceWithMessage(LOG_TAG, "Error loading properties file \"" + propertiesFile.getAbsolutePath() + "\"", e);
            return null;
        }

        if (sharedPropertiesParser != null && context != null)
            return sharedPropertiesParser.preProcessPropertiesOnReadFromDisk(context, properties);
        else
            return properties;
    }

    public static File getPropertiesFileFromList(List<String> propertiesFilePaths, @NonNull String logTag) {
        if (propertiesFilePaths == null || propertiesFilePaths.size() == 0)
            return null;

        for(String propertiesFilePath : propertiesFilePaths) {
            File propertiesFile = new File(propertiesFilePath);
            FileType fileType = FileUtils.getFileType(propertiesFilePath, false);
            if (fileType == FileType.REGULAR) {
                if (propertiesFile.canRead())
                    return propertiesFile;
                else
                    Logger.logWarn(logTag, "Ignoring properties file at \"" + propertiesFilePath + "\" since it is not readable");
            } else if (fileType != FileType.NO_EXIST) {
                Logger.logWarn(logTag, "Ignoring properties file at \"" + propertiesFilePath + "\" of type: \"" + fileType.getName() + "\"");
            }
        }

        Logger.logDebug(logTag, "No readable properties file found at: " + propertiesFilePaths);
        return null;
    }

    public static String getProperty(Context context, File propertiesFile, String key, String def) {
        return getProperty(context, propertiesFile, key, def, null);
    }

    public static String getProperty(Context context, File propertiesFile, String key, String def, @Nullable SharedPropertiesParser sharedPropertiesParser) {
        return (String) getDefaultIfNull(getDefaultIfNull(getPropertiesFromFile(context, propertiesFile, sharedPropertiesParser), new Properties()).get(key), def);
    }

    public static Object getInternalProperty(Context context, File propertiesFile, String key, @NonNull SharedPropertiesParser sharedPropertiesParser) {
        String value = (String) getDefaultIfNull(getPropertiesFromFile(context, propertiesFile, sharedPropertiesParser), new Properties()).get(key);
        return sharedPropertiesParser.getInternalPropertyValueFromValue(context, key, value);
    }

    public static boolean isPropertyValueTrue(Context context, File propertiesFile, String key, boolean logErrorOnInvalidValue) {
        return isPropertyValueTrue(context, propertiesFile, key, logErrorOnInvalidValue, null);
    }

    public static boolean isPropertyValueTrue(Context context, File propertiesFile, String key, boolean logErrorOnInvalidValue, @Nullable SharedPropertiesParser sharedPropertiesParser) {
        return (boolean) getBooleanValueForStringValue(key, (String) getProperty(context, propertiesFile, key, null, sharedPropertiesParser), false, logErrorOnInvalidValue, LOG_TAG);
    }

    public static boolean isPropertyValueFalse(Context context, File propertiesFile, String key, boolean logErrorOnInvalidValue) {
        return isPropertyValueFalse(context, propertiesFile, key, logErrorOnInvalidValue, null);
    }

    public static boolean isPropertyValueFalse(Context context, File propertiesFile, String key, boolean logErrorOnInvalidValue, @Nullable SharedPropertiesParser sharedPropertiesParser) {
        return (boolean) getInvertedBooleanValueForStringValue(key, (String) getProperty(context, propertiesFile, key, null, sharedPropertiesParser), true, logErrorOnInvalidValue, LOG_TAG);
    }

    public static boolean putToMap(HashMap<String, Object> map, String key, Object value) {
        if (map == null) {
            Logger.logError(LOG_TAG, "Map passed to SharedProperties.putToProperties() is null");
            return false;
        }

        if (key == null) {
            Logger.logError(LOG_TAG, "Cannot put a null key into properties map");
            return false;
        }

        boolean put = false;
        if (value != null) {
            Class<?> clazz = value.getClass();
            if (clazz.isPrimitive() || isWrapperType(clazz) || value instanceof String) {
                put = true;
            }
        } else {
            put = true;
        }

        if (put) {
            map.put(key, value);
            return true;
        } else {
            Logger.logError(LOG_TAG, "Cannot put a non-primitive value for the key \"" + key + "\" into properties map");
            return false;
        }
    }

    private static boolean isWrapperType(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Byte.class || clazz == Character.class ||
               clazz == Short.class || clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class || clazz == Void.class;
    }

    public static boolean putToProperties(Properties properties, String key, String value) {
        if (properties == null) {
            Logger.logError(LOG_TAG, "Properties passed to SharedProperties.putToProperties() is null");
            return false;
        }

        if (key == null) {
            Logger.logError(LOG_TAG, "Cannot put a null key into properties");
            return false;
        }

        if (value != null) {
            properties.put(key, value);
            return true;
        } else {
            properties.remove(key);
        }
        return true;
    }

    public static Properties getPropertiesCopy(Properties inputProperties) {
        if (inputProperties == null) return null;

        Properties outputProperties = new Properties();
        for (String key : inputProperties.stringPropertyNames()) {
            outputProperties.put(key, inputProperties.get(key));
        }
        return outputProperties;
    }

    public static Map<String, Object> getMapCopy(Map<String, Object> map) {
        if (map == null) return null;
        return new HashMap<>(map);
    }

    public static Boolean getBooleanValueForStringValue(String value) {
        return MAP_GENERIC_BOOLEAN.get(toLowerCase(value));
    }

    public static boolean getBooleanValueForStringValue(String key, String value, boolean def, boolean logErrorOnInvalidValue, String logTag) {
        return (boolean) getDefaultIfNotInMap(key, MAP_GENERIC_BOOLEAN, MAP_GENERIC_BOOLEAN_INVERSE, toLowerCase(value), def, logErrorOnInvalidValue, logTag);
    }

    public static boolean getInvertedBooleanValueForStringValue(String key, String value, boolean def, boolean logErrorOnInvalidValue, String logTag) {
        return (boolean) getDefaultIfNotInMap(key, MAP_GENERIC_INVERTED_BOOLEAN, MAP_GENERIC_INVERTED_BOOLEAN_INVERSE, toLowerCase(value), def, logErrorOnInvalidValue, logTag);
    }

    // ========== getDefaultIfNotInMap - 完整版本（带反向映射） ==========
    @SuppressWarnings("unchecked")
    public static <K, V> Object getDefaultIfNotInMap(
            String key,
            Map<K, V> forwardMap,
            Map<V, K> inverseMap,
            Object inputValue,
            Object defaultOutputValue,
            boolean logErrorOnInvalidValue,
            String logTag) {

        V outputValue = forwardMap.get(inputValue);
        if (outputValue == null) {
            Object defaultInputKey = null;
            if (inverseMap != null) {
                defaultInputKey = inverseMap.get(defaultOutputValue);
            }
            if (defaultInputKey == null) {
                defaultInputKey = defaultOutputValue;
            }

            if (logErrorOnInvalidValue && inputValue != null) {
                if (key != null) {
                    Logger.logError(logTag, "The value \"" + inputValue + "\" for the key \"" + key +
                            "\" is invalid. Using default value \"" + defaultInputKey + "\" instead.");
                } else {
                    Logger.logError(logTag, "The value \"" + inputValue + "\" is invalid. Using default value \"" + defaultInputKey + "\" instead.");
                }
            }
            return defaultOutputValue;
        } else {
            return outputValue;
        }
    }

    // ========== getDefaultIfNotInMap - 简化版本（不带反向映射） ==========
    @SuppressWarnings("unchecked")
    public static <K, V> Object getDefaultIfNotInMap(
            String key,
            Map<K, V> forwardMap,
            Object inputValue,
            Object defaultOutputValue,
            boolean logErrorOnInvalidValue,
            String logTag) {

        V outputValue = forwardMap.get(inputValue);
        if (outputValue == null) {
            if (logErrorOnInvalidValue && inputValue != null) {
                if (key != null) {
                    Logger.logError(logTag, "The value \"" + inputValue + "\" for the key \"" + key +
                            "\" is invalid. Using default value \"" + defaultOutputValue + "\" instead.");
                } else {
                    Logger.logError(logTag, "The value \"" + inputValue + "\" is invalid. Using default value \"" + defaultOutputValue + "\" instead.");
                }
            }
            return defaultOutputValue;
        } else {
            return outputValue;
        }
    }

    // ========== 工具方法 ==========
    public static <T> T getDefaultIfNull(@Nullable T object, @Nullable T def) {
        return (object == null) ? def : object;
    }

    public static String getDefaultIfNullOrEmpty(@Nullable String object, @Nullable String def) {
        return (object == null || object.isEmpty()) ? def : object;
    }

    public static String toLowerCase(String value) {
        if (value == null) return null;
        else return value.toLowerCase();
    }

    public static int getDefaultIfNotInRange(String key, int value, int def, int min, int max, boolean logErrorOnInvalidValue, boolean ignoreErrorIfValueZero, String logTag) {
        if (value < min || value > max) {
            if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0)) {
                if (key != null)
                    Logger.logError(logTag, "The value \"" + value + "\" for the key \"" + key + "\" is not within the range " + min + "-" + max + " (inclusive). Using default value \"" + def + "\" instead.");
                else
                    Logger.logError(logTag, "The value \"" + value + "\" is not within the range " + min + "-" + max + " (inclusive). Using default value \"" + def + "\" instead.");
            }
            return def;
        } else {
            return value;
        }
    }

    public static float getDefaultIfNotInRange(String key, float value, float def, float min, float max, boolean logErrorOnInvalidValue, boolean ignoreErrorIfValueZero, String logTag) {
        if (value < min || value > max) {
            if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0)) {
                if (key != null)
                    Logger.logError(logTag, "The value \"" + value + "\" for the key \"" + key + "\" is not within the range " + min + "-" + max + " (inclusive). Using default value \"" + def + "\" instead.");
                else
                    Logger.logError(logTag, "The value \"" + value + "\" is not within the range " + min + "-" + max + " (inclusive). Using default value \"" + def + "\" instead.");
            }
            return def;
        } else {
            return value;
        }
    }
}