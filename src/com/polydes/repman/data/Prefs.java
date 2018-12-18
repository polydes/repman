package com.polydes.repman.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

public class Prefs
{
	private static final Logger log = Logger.getLogger(Prefs.class);
	
	private static HashMap<String,String> prefs;
	
	public static final String SW_WORKSPACE = "sw.workspace";
	public static final String WINDOW_WIDTH = "window.width";
	public static final String WINDOW_HEIGHT = "window.height";
	public static final String SPLIT_PANE_WIDTH = "splitpane.width";
	
	public static String get(String key)
	{
		return get().getOrDefault(key, "");
	}
	
	public static HashMap<String,String> get()
	{
		if(prefs == null)
		{
			prefs = new HashMap<>();
			
			try
			{
				putLinesInMap(FileUtils.readLines(new File("prefs.txt")), prefs);
			}
			catch(IOException e)
			{
				log.error(e.getMessage(), e);
			}
		}
		
		return prefs;
	}
	
	public static void save()
	{
		prefs = get();
		
		List<String> lines = new ArrayList<>();
		
		for(Entry<String, String> entry : prefs.entrySet())
		{
			lines.add(entry.getKey() + "=" + entry.getValue());
		}
		
		try
		{
			FileUtils.writeLines(new File("prefs.txt"), lines, "\n");
		}
		catch(IOException e)
		{
			log.error(e.getMessage(), e);
		}
	}
	
	public static void putLinesInMap(List<String> lines, Map<String,String> map)
	{
		for(String line : lines)
		{
			line = line.trim();
			if(line.isEmpty())
				continue;
			
			String[] parts = line.split("=");
			if(parts.length < 2)
			{
				log.error("Bad line in list: " + line);
				continue;
			}
			
			map.put(parts[0], parts[1]);
		}
	}
}
