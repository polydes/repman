package com.polydes.repman.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.polydes.repman.ui.RepmanMain;
import org.apache.log4j.Logger;

public class Prefs
{
	private static final Logger log = Logger.getLogger(Prefs.class);
	
	private static HashMap<String,String> prefs;
	
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
				putLinesInMap(Files.readAllLines(Path.of(RepmanMain.REPMAN_DIR, "prefs.txt")), prefs);
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
			Files.write(Path.of(RepmanMain.REPMAN_DIR, "prefs.txt"), lines);
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
			
			map.put(parts[0].trim(), parts[1].trim());
		}
	}
}
