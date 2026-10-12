package org.sunbird.workflow.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/*
 * @author Amit Kumar
 *
 * this class is used for reading properties file
 */
public class PropertiesCache {

    public final Map<String, Float> attributePercentageMap = new ConcurrentHashMap<>();
    private final String[] fileName = {
            "cassandra.config.properties",
            "cassandratablecolumn.properties",
            "application.properties"
    };
    private final Properties configProp = new Properties();

    /**
     * private default constructor
     */
    private PropertiesCache() {
        for (String file : fileName) {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(file);
            try {
                configProp.load(in);
            } catch (IOException e) {
            }
        }
    }

    private static class InstanceHolder {
        private static final PropertiesCache INSTANCE = new PropertiesCache();
    }

    public static PropertiesCache getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public void saveConfigProperty(String key, String value) {
        configProp.setProperty(key, value);
    }

    public String getProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key) != null ? configProp.getProperty(key) : key;
    }

    /**
     * Method to read value from resource file .
     *
     * @param key
     * @return
     */
    public String readProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key);
    }
    
	public String readCustomError(String key) {
		if (StringUtils.isNoneBlank(key)) {
			key = key.replace(" ", "_");
		}
		return readProperty(key);
	}
}