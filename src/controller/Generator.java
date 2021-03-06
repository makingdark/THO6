package controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Logger;

import model.Businessrule;
import model.Value;

public class Generator {
	private Properties templateProperties,placeHolderProperties;
	private String ruleTemplate;
	private HashMap<String, String> placeHolderHashmap;
	private Businessrule br = new Businessrule();
	/**
	 * Generate the businessrule with the following steps
	 * 1. create a new businessrule
	 * 2. set the name of the businessrule
	 * 3. get the rest of the data for the businessrule from the tooldatabase
	 * 4. read the templates for the specific database and check if the files exist
	 * 5. read the xmlfile with the placeholder definition to determine what to replace
	 * 6. fill a map(placeHolderHashmap) with the entry key and value
	 * 7. call method to replace the placeholders with the correct values
	 * @param brName
	 */
	public String generate(String brName) {
		String sqlStatement = "";
		Logger logger = Logger.getLogger("defaultLogger");
		br = new Businessrule();
		br.setName(brName);
		br.loadFromDbIntoObject();
		String[] nameParts = br.getName().split("_");
		/*
		 * BRG_IDD_TRG_RNG_ORA_001
		 * 0 = BRG = Businessrule generator
		 * 1 = IDD = Naam van het veld. eerste 2 letters en laatste letter
		 * 2 = TRG = Trigger
		 * 3 = RNG = Rule type
		 * 4 = ORA = Databasetype
		 * 5 = 001 = Volgnummer
		 */
		
		String databaseType = nameParts[4];
		String ruleType = nameParts[3];
		this.templateProperties = new Properties();				
		this.placeHolderProperties = new Properties();
		String templateLocation = "/Xml/RuleTemplates/" + databaseType + "/" + ruleType + ".xml";
		String placeholderLocation = "/Xml/RuleTemplates/" + databaseType + "/" + ruleType + "Placeholder.xml";
		InputStream fisPlaceholder = Thread.currentThread().getContextClassLoader().getResourceAsStream(placeholderLocation);
		InputStream fisTemplate = Thread.currentThread().getContextClassLoader().getResourceAsStream(templateLocation);

		if(fisPlaceholder != null && fisTemplate != null){
			try {
				templateProperties.loadFromXML(fisTemplate);
				placeHolderProperties.loadFromXML(fisPlaceholder);
			} catch (InvalidPropertiesFormatException e) {
				e.printStackTrace();
			} catch (IOException e) {
				logger.severe("Error reading xml template file");
			}
						
			Enumeration<Object> keys= placeHolderProperties.keys();
			placeHolderHashmap = new HashMap <String, String>();
			while(keys.hasMoreElements()){
				Object keyO = keys.nextElement();
				String key = keyO.toString();
				placeHolderHashmap.put(key, placeHolderProperties.getProperty(key));
				logger.config(key + " --- " + placeHolderProperties.getProperty(key));
			}
			
			this.ruleTemplate = this.templateProperties.getProperty("template");
			sqlStatement = replacePlaceholderWithValues(ruleTemplate);
			logger.config(sqlStatement);
		} else {
			logger.severe("Error couldn't find Xml template file");
		}
		return sqlStatement;
	}

	/**
	 * replacePlaceholderWithValues is a method that replaces
	 * placeholder names with actual names. 
	 * 
	 * @param unfinishedTemplate A String which contains the full template. It contains placeholders which will be replaced.
	 * @return The rule template with relevant information.
	 */
	public String replacePlaceholderWithValues(String unfinishedTemplate){
		String finishedTemplate = "";
		
		for (HashMap.Entry<String, String> replacement : placeHolderHashmap.entrySet()) {
			String[] valueParts = replacement.getValue().split("\\.");
			String object = valueParts[0];
			String attribute = valueParts[1];
			String replacingValue = "NoValueFound";
			
			if(object.equals("businessrule")){
				switch (attribute) {
	            case "name": replacingValue = br.getName();break;
	            case "operator": replacingValue = br.getOperator();break;
	            case "values": 
	            	ArrayList<String> values = replaceValue(br); 
	            	switch(valueParts[2] + ""){
	            	case "all": Iterator<String> i = values.iterator(); 
	                replacingValue="";
	                 while(i.hasNext()){
	                  
	                  String tmpV = i.next();
	                  replacingValue += tmpV;
	                  if(i.hasNext()){
	                   replacingValue += ",";
	                   }
	                  }break;
	            	default: replacingValue = (valueParts[3].equals("order") ==true
	            			? valueParts[2] + "" : 
	            				(valueParts[3].equals("value") ==true? values.get(Integer.parseInt(valueParts[2])-1): replacingValue));
	            	};break;
	            	
	            case "attributes": 
	            	switch(valueParts[2]){
	            	case "all": br.getValues();
	            	default: replacingValue = (valueParts[3].equals("order") 
	            			? br.getValueByOrder(valueParts[2]).getOrder() + "" 
	            			: (valueParts[3].equals("schema") 
	            					? br.getAttributeByOrder(valueParts[2]).getSchema()
	            					: (valueParts[3].equals("tabel") 
	    	            					? br.getAttributeByOrder(valueParts[2]).getTabel()
	    	    	            			: (valueParts[3].equals("kolom") 
	    	    	    	            			? br.getAttributeByOrder(valueParts[2]).getKolom()
	    	    	    	    	            	: replacingValue))));
	            	};break;
				}
				if(unfinishedTemplate == null){
					System.out.println("unftemp");
				} else if (replacement.getKey() == null) {
					System.out.println("repla");
				} else if (replacingValue == null) {
					System.out.println("value");
				}
				unfinishedTemplate = unfinishedTemplate.replace(replacement.getKey(), replacingValue);
			}
		}
		
		finishedTemplate= unfinishedTemplate;
		return finishedTemplate;
	}
	
	/**
	 * Method to return the value with the correct format. for example a string will have be surrounded by single quotes.
	 * @param rule
	 * @return
	 */
	public ArrayList<String> replaceValue(Businessrule rule) {
		ArrayList<String> result = new ArrayList<String>();
		ArrayList<Value> valueList = rule.getValues();
		for(Value v : valueList) {
			if(v.getDatatype().equals("STRING")) {
				result.add("'"+v.getValue()+"'");
			} else if(v.getDatatype().equals("NUMBER")) {
				result.add(v.getValue());
			} else if(v.getDatatype().equals("DATE")) {
				result.add("to_date('"+v.getValue()+"','DD-MM-YYYY HH24:MI')");
			}
		}
		return result;
	}
}
