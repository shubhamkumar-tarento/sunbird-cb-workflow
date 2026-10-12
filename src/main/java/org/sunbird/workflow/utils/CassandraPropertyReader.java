package org.sunbird.workflow.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class will be used to read cassandratablecolumn properties file.
 * @author fathima
 *
 */

public class CassandraPropertyReader {

	private static final Logger logger = LoggerFactory.getLogger(CassandraPropertyReader.class);

	private final Properties properties = new Properties();
	  private static final String PROPERTIES_FILE_NAME = "cassandratablecolumn.properties";
	  private static CassandraPropertyReader cassandraPropertyReader = null;

	  /** private default constructor 
	 * @throws IOException */
	  private CassandraPropertyReader() throws IOException {
	    InputStream in = this.getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME);
	    try {
	      properties.load(in);
	    } catch (IOException e) {
	    	throw e;
	    }
	  }

	  public static synchronized CassandraPropertyReader getInstance() {
	    if (null == cassandraPropertyReader) {
	      try {
	    	  cassandraPropertyReader = new CassandraPropertyReader();
	      } catch (IOException e) {
	    	  logger.error("Failed to initialize CassandraPropertyReader", e);
	      }
	    }
	    return cassandraPropertyReader;
	  }

	  /**
	   * Method to read value from resource file .
	   *
	   * @param key property value to read
	   * @return value corresponding to given key if found else will return key itself.
	   */
	  public String readProperty(String key) {
	    return properties.getProperty(key) != null ? properties.getProperty(key) : key;
	  }
}
