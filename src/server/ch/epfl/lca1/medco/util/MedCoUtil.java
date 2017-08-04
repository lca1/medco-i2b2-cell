/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the i2b2 Software License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *                 Raj Kuttan
 *                 Lori Phillips
 */
package ch.epfl.lca1.medco.util;

import java.io.IOException;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import javax.sql.DataSource;

import ch.epfl.lca1.medco.i2b2.MessagesUtil;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import ch.epfl.lca1.medco.util.exceptions.MedCoError;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.ServiceLocator;
import org.springframework.util.StopWatch;


// TODO: reorganize this file, split in 2 (have a config file)

/**
 * This is the PM service's main utility class
 * This utility class provides support for
 * fetching resources like datasouce, to read application
 * properties, to get ejb home,etc.
 * $Id: PMUtil.java,v 1.3 2009/07/10 18:40:07 mem61 Exp $
 * @author rkuttan
 */
//TODO: add exception config not valid TODO TODO
// TODO: reorganize!!!

//TODO: get rid of the application dir and use that:
/*
String fileName = System.getProperty("jboss.server.config.dir") + "/my.properties";
try(FileInputStream fis = new FileInputStream(fileName)) {
  properties.load(fis);
}
*/

/**
 * todo:
 * required = true -> no default value
 * required = false -> default value provided (have default vals as constant)
 */
public class MedCoUtil {

    /**
     * Names of general MedCo properties.
     */
    public static final String MEDCO_APP_NAME_PROPERTIES = "medco.app.name",
                                MEDCO_APP_VERSION_PROPERTIES = "medco.app.version",
								LOG_LEVEL_PROPERTIES = "medco.log.level";

	/**
     * Names of properties related to the I2B2 communications.
	 */
	public static final String PMCELL_WS_URL_PROPERTIES = "medco.i2b2.pm.url",
								ONTCELL_WS_URL_PROPERTIES = "medco.i2b2.ont.url",
								CRCCELL_WS_URL_PROPERTIES = "medco.i2b2.crc.url",
								FRCELL_WS_URL_PROPERTIES = "medco.i2b2.fr.url",
                                MEDCOCELL_WS_URL_PROPERTIES = "medco.i2b2.medco.url",
								I2B2CELLS_WS_WAITTIME_PROPERTIES = "medco.i2b2.waittimems";

    /**
     * Names of properties related to the Unlynx communications.
     */
    public static final String UNLYNX_BINARY_PATH_PROPERTIES = "medco.unlynx.binarypath",
                                UNLYNX_DEBUG_LEVEL_PROPERTIES = "medco.unlynx.debuglevel",
                                UNLYNX_GROUP_FILE_PATH_PROPERTIES = "medco.unlynx.groupfilepath",
                                UNLYNX_ENTRY_POINT_IDX_PROPERTIES = "medco.unlynx.entrypointidx",
                                UNLYNX_PROOFS_PROPERTIES = "medco.unlynx.proofs";

    public static final String JBOSS_CONFIG_DIR_PROPERTIES = "jboss.server.config.dir";

    /** application property filename**/
    public static final String APPLICATION_PROPERTIES_FILENAME = "medco.properties";

    /** todo:property name for datasource present in app property file**/
    private static final String DATASOURCE_JNDI_PROPERTIES = "medco.jndi.datasource_name";

    /** property name for metadata schema name**/
    private static final String METADATA_SCHEMA_NAME_PROPERTIES = "medco.bootstrapdb.metadataschema";

    /** class instance field**/
    private static MedCoUtil thisInstance = null;

    /** service locator field**/
    private static ServiceLocator serviceLocator = null;

    /** field to store application properties **/
    private static Properties appProperties = null;

    /* --- performance evaluation settings --
    * todo: disable all logging
    * todo: output of times
    *
    * */
    public static final String PERF_EVAL_MODE_PROPERTIES = "medco.perfevalmode.enable";


    /** field to store app datasource**/
    private DataSource dataSource = null;

    /**
     * Names of JAXB packages that will be loaded.
     */
    private static final String[] JAXB_PACKAGES = new String[]{
            "edu.harvard.i2b2.crc.datavo.i2b2message",
            "edu.harvard.i2b2.crc.datavo.pdo",
            "edu.harvard.i2b2.crc.datavo.pdo.query",
            "edu.harvard.i2b2.crc.datavo.setfinder.query",
            "edu.harvard.i2b2.crc.datavo.pm",
            "edu.harvard.i2b2.crc.datavo.ontology",
            "edu.harvard.i2b2.crc.datavo.i2b2result",
			"edu.harvard.i2b2.crc.loader.datavo.fr",
			"edu.harvard.i2b2.crc.loader.datavo.loader.query"
    };

    /**
     * Private constructor to make the class singleton.
     * @param requireSuccess if method should throw exception when configuration fails to be loaded (set false for testing environment)
     */
    private MedCoUtil(boolean requireSuccess) {
        this.initAppProperties(requireSuccess);
    }
    
    private static MessagesUtil jaxbUtil;
    
	public static MessagesUtil getMsgUtil() {
		if (jaxbUtil == null) {
			jaxbUtil = new MessagesUtil(JAXB_PACKAGES);
		}

		return jaxbUtil;
	}

    /**
     * default: false
     * @return
     */
	public boolean getPerfEvalMode() {
        String perf = getPropertyValue(PERF_EVAL_MODE_PROPERTIES, false);

        // if null or invalid -> false
        return Boolean.parseBoolean(perf);
    }
	
	/*
	protected ModifierType getModifierMetadataFromOntology(ItemType item, SecurityType securityType, String projectId) 
			throws ConceptNotFoundException, OntologyException {
		
		// extract ontology query info from the modifier constraint
		ItemType.ConstrainByModifier modifierConstrain = item.getConstrainByModifier();
		String modifierKey = modifierConstrain.getModifierKey();
		String modifierAppliedPath = modifierConstrain.getAppliedPath();
		
		// query the ontology to get the metadata
		ModifierType modifierType = null;
		try {
			// get ontology cell query URL
			QueryProcessorUtil qpUtil = QueryProcessorUtil.getInstance();
			String ontologyUrl = qpUtil.getCRCPropertyValue(QueryProcessorUtil.ONTOLOGYCELL_ROOT_WS_URL_PROPERTIES);
			String getModifierOperationName = qpUtil.getCRCPropertyValue(QueryProcessorUtil.ONTOLOGYCELL_GETMODIFIERINFO_URL_PROPERTIES);
			String ontologyGetModifierInfoUrl = ontologyUrl	+ getModifierOperationName;
			log.debug("Ontology getModifierinfo url from property file [" + ontologyGetModifierInfoUrl + "]");
			
			// query ontology cell
			modifierType = CallOntologyUtil.callGetModifierInfo(modifierKey,
					modifierAppliedPath, securityType,
					projectId, ontologyGetModifierInfoUrl);

		} catch (JAXBUtilException | I2B2Exception | AxisFault | XMLStreamException e) {
			log.error("Error while fetching metadata [" + modifierKey + "] from ontology ", e);
			throw new OntologyException(
				"Error while fetching metadata ["+ modifierKey + "] from ontology " + StackTraceUtil.getStackTrace(e));
		}
		
		// return value null means was not found
		if (modifierType == null) {
			throw new ConceptNotFoundException("Error getting modifierinfo for modifier key [" + modifierKey +
					"] and appliedPath [" + modifierAppliedPath + "]");
		}

		return modifierType;
	}*/
	
    /**
     * Return this class instance
     * @return OntologyUtil
     */
    public static MedCoUtil getInstance() {
        if (thisInstance == null) {
            thisInstance = new MedCoUtil(true);
        }

        serviceLocator = ServiceLocator.getInstance();

        return thisInstance;
    }

    public static MedCoUtil getTestInstance() {
        if (thisInstance == null) {
            thisInstance = new MedCoUtil(false);
        }

        serviceLocator = ServiceLocator.getInstance();
        return thisInstance;
    }




    /**
     * Return metadata schema name
     * @return
     * @throws I2B2Exception
     */
    public String getMetaDataSchemaName() throws I2B2Exception {
        return getPropertyValue(METADATA_SCHEMA_NAME_PROPERTIES, true).trim()+ ".";
    }

	/**
	 * Get Project managment cell's service url
	 * 
	 * @return
	 * @throws I2B2Exception
	 */
	public String getProjectManagementCellUrl() {
		return getPropertyValue(PMCELL_WS_URL_PROPERTIES, false);
	}

	public int getI2b2Waittimems() {
		String timems = getPropertyValue(I2B2CELLS_WS_WAITTIME_PROPERTIES, true);

		try {
			int timemsLong = Integer.parseInt(timems);

			if (timemsLong < 0) {
				Logger.warn("Invalid wait time value, returning default");
				return 180000;
			} else {
				return timemsLong;
			}

		} catch (NumberFormatException e) {
			Logger.warn("Cannot parse int", e);
			return 180000;
		}
	}

	public String getLogLevel() {
		String fromPropFile = getPropertyValue(LOG_LEVEL_PROPERTIES, false);
		return fromPropFile == null ? "INFO" : fromPropFile;
	}

	public String getOntologyCellUrl() {
		return getPropertyValue(ONTCELL_WS_URL_PROPERTIES, true);
	}
    public String getMedCoCellUrl() {
        return getPropertyValue(MEDCOCELL_WS_URL_PROPERTIES, true);
    }

	public String getDataRepositoryCellUrl() {
		return getPropertyValue(CRCCELL_WS_URL_PROPERTIES, true);
	}
	public String getFileRepositoryCellUrl() {
		return getPropertyValue(FRCELL_WS_URL_PROPERTIES, true);
	}

    public String getApplicationName() {
	    String prop = getPropertyValue(MEDCO_APP_NAME_PROPERTIES, false);
        return prop == null ? "MedCo I2B2 cell" : prop;
    }
    public String getApplicationVersion() {
	    String prop = getPropertyValue(MEDCO_APP_VERSION_PROPERTIES, false);
        return prop == null ? "0.01" : prop;
    }

	/**
	 * Get Project managment cell's service url
	 * 
	 * @return
	 * @throws I2B2Exception
	 */
	public String getUnlynxBinPath() {
		return getPropertyValue(UNLYNX_BINARY_PATH_PROPERTIES, true);
	}
	
	/**
	 * Ensures 0 <= debug level <= 5.
	 * 
	 * @return the unlynx debug level
	 */
	public int getUnlynxDebugLevel() {
		String lvl = getPropertyValue(UNLYNX_DEBUG_LEVEL_PROPERTIES, false);
		
		try {
			int lvlInt = Integer.parseInt(lvl);
			
			if (lvlInt >= 0 && lvlInt <= 5) {
				return lvlInt;
			} else {
				return 0;
			}
			
		} catch (NumberFormatException e) {
			Logger.warn("Cannot parse int", e);
			return 0;
		}
	}
	
	public String getUnlynxGroupFilePath() {
		return getPropertyValue(UNLYNX_GROUP_FILE_PATH_PROPERTIES, true);
	}
	
	public int getUnlynxEntryPointIdx() {
		String idx = getPropertyValue(UNLYNX_ENTRY_POINT_IDX_PROPERTIES, true);
		
		try {
			int idxInt = Integer.parseInt(idx);
			
			if (idxInt >= 0) {
				return idxInt;
			} else {
				throw Logger.error(new MedCoError("Invalid entry point index in configuration"));
			}
			
		} catch (NumberFormatException e) {
			throw Logger.error(new MedCoError("Invalid entry point index in configuration"));
		}
	}
	
	/**
	 * 
	 * @return true if proofs should be computed
	 */
	public int getUnlynxProofsFlag() {
		String proofs = getPropertyValue(UNLYNX_PROOFS_PROPERTIES, false);
		
		try {
			int proofsBool = Integer.parseInt(proofs);
			
			if (proofsBool >= 0 && proofsBool <= 1) {
				return proofsBool;
			} else {
				return 0;
			}
			
		} catch (NumberFormatException e) {
			Logger.warn("Cannot parse int: " + e.getMessage());
			return 0;
		}
	}
	
    /**
     * Return app server datasource
     * @return datasource
     * @throws I2B2Exception
     */
    public DataSource getDataSource(String dataSourceName) throws I2B2Exception {
        if (dataSource == null) {
            dataSource = serviceLocator.getAppServerDataSource(dataSourceName);
        }
    	return dataSource;
    }

    /**
     * Set the datasource, for tests purposes.
     */
    public void setDataSource(DataSource ds) {
        dataSource = ds;
    }
    

	public void convertToUppercaseStrings( List< String > list )
	{
		ListIterator< String > iterator = list.listIterator();

		while ( iterator.hasNext() ) 
		{
			String color = iterator.next();  // get item                 
			iterator.set( color.toUpperCase() ); // convert to upper case
		} // end while
	}
	public  String toHex(byte[] digest) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < digest.length; i++) {
			buf.append(Integer.toHexString((int) digest[i] & 0x00FF));
		}
		return buf.toString();
	}

	public String generateMessageId() {
		StringWriter strWriter = new StringWriter();
		for(int i=0; i<20; i++) {
			int num = getValidAcsiiValue();
			//System.out.println("Generated number: " + num + " char: "+(char)num);
			strWriter.append((char)num);
		}
		return strWriter.toString();
	}
	
	private int getValidAcsiiValue() {
		int number = 48;
		while(true) {
			number = 48+(int) Math.round(Math.random() * 74);
			if((number > 47 && number < 58) || (number > 64 && number < 91) 
				|| (number > 96 && number < 123)) {
					break;
				}
		}
		return number;
		
	}

	
	public String getHashedPassword(String pass) {
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(pass.getBytes());
			return toHex(md5.digest());
		} catch (NoSuchAlgorithmException e) {
			Logger.error(e);
		}
		return null;
	}
    
    //---------------------
    // private methods here
    //---------------------

	/**
	 * Initialize the application properties.
	 * 
	 * @throws MedCoError if they cannot be loaded (unrecoverable)
	 */
	private void initAppProperties(boolean requireSuccess) {
		
		try {
			// read application directory property
			String appDir = System.getProperty(JBOSS_CONFIG_DIR_PROPERTIES) == null ?
                    "./etc/spring/medcoapp" :
                    System.getProperty(JBOSS_CONFIG_DIR_PROPERTIES)  + "/medcoapp";

			// prepare to read the properties
	        String appPropertyFile = appDir + "/" + APPLICATION_PROPERTIES_FILENAME;
	        FileSystemResource fileSystemResource = new FileSystemResource(appPropertyFile);
	        PropertiesFactoryBean pfb = new PropertiesFactoryBean();
	        pfb.setLocation(fileSystemResource);
	        
	        // read and parse the properties
	        pfb.afterPropertiesSet();
            appProperties = (Properties) pfb.getObject();
            
            if (appProperties == null) {
            	throw new I2B2Exception("appProperties null at end of initialization");
            }
            
		} catch (I2B2Exception | IOException | NullPointerException e) {
		    if (requireSuccess) {
                throw new MedCoError("Could not load application properties " + JBOSS_CONFIG_DIR_PROPERTIES +
                        " - " + APPLICATION_PROPERTIES_FILENAME, e);
            } else {
		        Logger.warn(e);
		        appProperties = new Properties();
            }
		}
	}
	
	
    /**
     * Returns a property from the properties file.
     * If the property is required and is not found, throws error.
     * 
     * @param propertyName the property name
     * @param required flag to indicate if the property is required
     * @return the value of the property or null if not found and not required
     * 
     * @throws MedCoError if a required property if not found
     * 
     */
    private String getPropertyValue(String propertyName, boolean required) {

        String propertyValue = appProperties.getProperty(propertyName);

        if (required && (propertyValue == null || propertyValue.trim().length() <= 0)) {
        	throw new MedCoError("Application property file (" + APPLICATION_PROPERTIES_FILENAME + ") missing " + propertyName + " required entry");
        } 
        
        return propertyValue;
    }

    /**
     * Sets via a method a property. Mainly used for testing purposes.
     *
     * @param propertyName name of the property (use the static fields name in this class)
     * @param propertyValue value of the property
     */
    public void setProperty(String propertyName, String propertyValue) {
        appProperties.setProperty(propertyName, propertyValue);
    }
}
