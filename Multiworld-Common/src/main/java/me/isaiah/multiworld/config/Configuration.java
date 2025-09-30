/**
 * Isaiah's Configuration File Format
 * Tiny two file YAML-like configuration parser
 * 
 * Unlicense
 */
package me.isaiah.multiworld.config;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
public class Configuration {

    protected LinkedHashMap<String, Object> contentMap;

    public Configuration() {
    }

    public Configuration(LinkedHashMap<String, Object> contentMap) {
        this.contentMap = contentMap;
    }

    /**
     */
    public <T> T getOrDefault(String key, T defaul) {
        Object val = contentMap.get(key);
        return val != null ? (T) val : defaul;
    }

    /**
     */
    public <T> T get(Class<T> type, String key) {
        return (T) contentMap.get(key);
    }

    /**
     */
    public Object getObject(String key) {
        return contentMap.get(key);
    }

    /**
     */
    public String getString(String key) {
        Object o = contentMap.get(key);
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        return String.valueOf(o);
    }

    /**
     */
    public boolean getBoolean(String key) {
        Object o = contentMap.get(key);
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return false;
    }

    /**
     */
    public int getInt(String key) {
        Object o = contentMap.get(key);
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt((String) o);
            } catch (NumberFormatException e) {
                try {
                    return (int) Long.parseLong((String) o);
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     */
    public double getDouble(String key) {
        Object o = contentMap.get(key);
        if (o == null) return 0.0d;
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException e) {
                return 0.0d;
            }
        }
        return 0.0d;
    }

    /**
     */
    public long getLong(String key) {
        Object o = contentMap.get(key);
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
    
    /**
     */
    public boolean is_set(String key) {
    	return contentMap.containsKey(key);
    } 

    /**
     * @param key - key with which the specified value is to be associated
     * @param value - value to be associated with the specified key
     * 
     * @return the previous value, or null.
     */
    public void set(String key, Object value) {
        contentMap.put(key, value);
    }

    public void save(File to) throws IOException {
    }

    public void save() throws IOException {
    }
    
    /**
     */
	public boolean hasSection(String sect) {
		for (String s : contentMap.keySet()) {
			if (s.startsWith(sect)) {
				return true;
			}
		}
		return false;
	}

	public LinkedHashMap<String, Object> getSection(String sect) {
	    LinkedHashMap<String, Object> contentMapSection = new LinkedHashMap<>();
	    
	    for (Map.Entry<String, Object> entry : contentMap.entrySet()) {
	        if (entry.getKey().startsWith(sect)) {
	        	
	        	if (entry.getKey().startsWith(sect)) {

	        		if (entry.getKey().indexOf('.') == -1) {
	        			continue;
	        		}
	        		
	        		String key2 =  entry.getKey().split(Pattern.quote("."))[1].split(Pattern.quote("."))[0];
	        		contentMapSection.put(key2, entry.getValue());
	        	}
	        }
	    }
	    
	    return contentMapSection;
	}

}