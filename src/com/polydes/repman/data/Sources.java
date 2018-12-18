package com.polydes.repman.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.polydes.repman.Extension;
import com.polydes.repman.ExtensionDependency;
import com.polydes.repman.ExtensionManifest;
import com.polydes.repman.ExtensionType;
import com.polydes.repman.LocalRepoBackend.ExtensionVersion;
import com.polydes.repman.Version;
import com.polydes.repman.ui.RepmanMain;
import com.polydes.repman.util.AntExecutor;
import com.polydes.repman.util.Zip;
import com.polydes.repman.util.io.IterableNodeList;
import com.polydes.repman.util.io.XMLHelper;

public class Sources
{
	public static Map<String, String> sources;
	
	public static String getSource(Extension ext)
	{
		if(sources == null)
			loadSources();
		if(sources == null)
			return null;
		
		String sourceKey = ext.repository + "::" + ext.type.toString() + "::" + ext.id;
		
		return sources.get(sourceKey);
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void loadSources()
	{
		sources = new HashMap<>();
		
		try
		{
			YamlReader reader = new YamlReader(new FileReader("sources.yml"));
			List repositories = (List) ((Map) reader.read()).get("repositories");
		    for(Object repo : repositories)
		    {
		    	Map map = (Map) repo;
		    	String url = (String) map.get("url");
		    	
		    	if(map.containsKey("engine"))
		    	{
			    	for(Object entry : ((Map) map.get("engine")).entrySet())
			    	{
			    		Entry<String,String> e = (Entry<String,String>) entry;
			    		sources.put(url + "::engine::" + e.getKey(), e.getValue());
			    	}
		    	}
		    	
		    	if(map.containsKey("toolset"))
		    	{
			    	for(Object entry : ((Map) map.get("toolset")).entrySet())
			    	{
			    		Entry<String,String> e = (Entry<String,String>) entry;
			    		sources.put(url + "::toolset::" + e.getKey(), e.getValue());
			    	}
		    	}
		    }
		}
		catch(FileNotFoundException | YamlException e)
		{
			e.printStackTrace();
			sources = null;
		}
	}
	
	public static void buildSource(Extension ext, Consumer<ExtensionVersion> callback) throws Exception
	{
		String source = getSource(ext);
		File sourceFile = source != null ? new File(source) : null;
		if(sourceFile == null || !sourceFile.exists())
			throw new Exception("Invalid source folder.");
		
		ExtensionDependency[] deps = null;
		Version version = null;
		
		String name = "";
		String description = "";
		String author = "";
		String website = "";
		Icon icon = null;
		
		if(ext.type == ExtensionType.ENGINE)
		{
			try
			{
				List<String> info = FileUtils.readLines(new File(sourceFile, "info.txt"));
				Map<String,String> map = new HashMap<>();
				Prefs.putLinesInMap(info, map);
				version = new Version(map.get("version"));
				deps = ExtensionDependency.fromStringList(map.get("dependencies"), ExtensionDependency.Type.ENGINE);
				
				name = map.get("name");
				description = map.get("description");
				author = map.get("author");
				website = map.get("website");
			}
			catch(IOException e)
			{
				throw new Exception("Failed to read info.txt.", e);
			}
			try
			{
				icon = new ImageIcon(ImageIO.read(new File(sourceFile, "icon.png")));
			}
			catch(IOException e)
			{
				throw new Exception("Failed to read icon.png.", e);
			}
		}
		else
		{
			try
			{
				//XXX: For now this only works with polydes extensions
				
				File buildFile = new File(sourceFile, "build.xml");
				Document doc = XMLHelper.readXMLFromFile(buildFile);
				for(Element e : IterableNodeList.elements(doc.getDocumentElement().getElementsByTagName("property")))
				{
					if(e.getAttribute("name").equals("version"))
						version = new Version(e.getAttribute("value"));
				}
			}
			catch(IOException e)
			{
				throw new Exception("Failed to read build.xml", e);
			}
		}
		
		for(ExtensionVersion v : ext.versions)
		{
			if(v.version.equals(version))
			{
				throw new Exception("Can't build a version that already exists (" + v.version + ").");
			}
		}
		
		if(ext.type == ExtensionType.ENGINE)
		{
			//zip folder
			File dest = RepmanMain.instance.getErm().getRepositories().get(ext.repository).getVersionLocalLocation(ext, version);
			dest.getParentFile().mkdirs();
			Zip.zip(sourceFile, dest);
		}
		else
		{
			//build jar
			File buildFile = new File(sourceFile, "build.xml");
			
			boolean success = AntExecutor.executeAntTask(buildFile.getAbsolutePath());
			if(!success)
			{
				throw new Exception("Failed to build .jar");
			}
			
			String fs = File.separator;
			File outJar = new File(Prefs.get(Prefs.SW_WORKSPACE) + "extensions" + fs + ext.id + ".jar");
			try
			{
				ExtensionManifest man = ExtensionManifest.fromJar(outJar);
				deps = man.dependencies;
				
				name = man.name;
				description = man.description;
				author = man.authorName;
				website = man.website;
				icon = man.icon;
			}
			catch(IOException e)
			{
				throw new Exception("Failed to read .jar manifest.");
			}
			
			File dest = RepmanMain.instance.getErm().getRepositories().get(ext.repository).getVersionLocalLocation(ext, version);
			Zip.zip(outJar, dest);
		}
		
		ext.name = name;
		ext.description = description;
		ext.author = author;
		ext.website = website;
		ext.icon = icon;
		
		DateFormat df = new SimpleDateFormat("MM/dd/yyyy");//DateFormat.getDateInstance(DateFormat.SHORT, Locale.ENGLISH);
		String dateNow = df.format(Calendar.getInstance().getTime());
		
		ExtensionVersion newVersion = new ExtensionVersion("", version, dateNow, deps);
		newVersion.local = true;
		newVersion.dirty = true;
		callback.accept(newVersion);
	}
}
