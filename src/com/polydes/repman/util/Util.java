package com.polydes.repman.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.log4j.Logger;

public class Util
{
	private static final Logger log = Logger.getLogger(Util.class);
	
	public static int parseInt(String toParse, int defaultValue)
	{
		try
		{
			return Integer.parseInt(toParse);
		}
		
		catch (NumberFormatException e)
		{
			log.warn("Could not parse \"" + toParse + "\". Defaulting to: " + defaultValue + ". " + getCaller());
			
			return defaultValue;
		}
	}
	
	private static String getCaller()
	{
		StackTraceElement[] elements = Thread.currentThread().getStackTrace();
		
		int index = 0;
		
		if (elements.length == 0)
		{
			return "";
		}
		
		else if (elements.length > 3)
		{
			index = 3;
		}
		
		else 
		{
			index = elements.length - 1;
		}
		
		StackTraceElement element = elements[index];

		return element.getClassName() + "." + element.getMethodName() + " (line: " + element.getLineNumber() + ")";
	}
	
	public static <T> T derive(T object, Map<String, Object> props)
	{
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Map<String, T> getPropsTyped(Object o, String... props)
	{
		Map<String, T> toReturn = new HashMap<>();
		for(String prop : props)
		{
			try
			{
				T t = (T) FieldUtils.readField(o, prop, true);
				toReturn.put(prop, t);
			}
			catch(IllegalAccessException e)
			{
				log.error(e.getMessage(), e);
			}
		}
		return toReturn;
	}
	
	public static Map<String, Object> getProps(Object o, String... props)
	{
		Map<String, Object> toReturn = new HashMap<>();
		for(String prop : props)
		{
			try
			{
				toReturn.put(prop, FieldUtils.readField(o, prop, true));
			}
			catch(IllegalAccessException e)
			{
				log.error(e.getMessage(), e);
			}
		}
		return toReturn;
	}
}
