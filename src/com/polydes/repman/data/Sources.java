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

import javax.swing.Icon;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.polydes.repman.Extension;
import com.polydes.repman.ExtensionDependency;
import com.polydes.repman.ExtensionManifest;
import com.polydes.repman.ExtensionType;
import com.polydes.repman.LocalRepoBackend.ExtensionVersion;
import com.polydes.repman.Version;
import com.polydes.repman.ui.RepmanMain;
import com.polydes.repman.util.Zip;

public class Sources
{
	public static final class SourceMap extends HashMap<String, String>{};
	public static final class TypesMap extends HashMap<ExtensionType, SourceMap>{};
	public static final class ReposMap extends HashMap<String, TypesMap>{};
	
	private static ReposMap repos;
	
	public static TypesMap getRepoSources(String url)
	{
		if(repos == null)
			loadSources();
		
		if(repos == null)
			return null;
		
		return repos.get(url);
	}
	
	public static String getSource(Extension ext)
	{
		if(repos == null)
			loadSources();
		
		if(repos == null)
			return null;
		
		TypesMap types = repos.get(ext.repository);
		if(types == null)
			return null;
		
		SourceMap sources = types.get(ext.type);
		if(sources == null)
			return null;
		
		return sources.get(ext.id);
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void loadSources()
	{
		repos = new ReposMap();
		
		try
		{
			YamlReader reader = new YamlReader(new FileReader("sources.yml"));
			List repositories = (List) ((Map) reader.read()).get("repositories");
		    for(Object repo : repositories)
		    {
		    	Map map = (Map) repo;
		    	String url = (String) map.get("url");
		    	
		    	TypesMap types = new TypesMap();
		    	repos.put(url, types);
		    	
		    	for(ExtensionType type : ExtensionType.values())
		    	{
		    		if(map.containsKey(type.toString()))
			    	{
			    		SourceMap sources = new SourceMap();
			    		types.put(type, sources);
			    		
				    	for(Object entry : ((Map) map.get(type.toString())).entrySet())
				    	{
				    		Entry<String,String> e = (Entry<String,String>) entry;
				    		sources.put(e.getKey(), e.getValue());
				    	}
			    	}
		    	}
		    }
		}
		catch(FileNotFoundException | YamlException e)
		{
			e.printStackTrace();
			repos = null;
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
		
		//TODO: READ EXTENSION INFO
		
		for(ExtensionVersion v : ext.versions)
		{
			if(v.version.equals(version))
			{
				throw new Exception("Can't build a version that already exists (" + v.version + ").");
			}
		}

		//TODO: whether to package as source or to build is no longer about whether it's an engine extension or not.
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
			boolean success = false;
			//TODO: BUILD THE EXTENSION
			if(!success)
			{
				throw new Exception("Failed to build .jar");
			}

			//TODO: UPDATE EXPECTED OUTPUT PATH AND MANIFEST INFO
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
			dest.getParentFile().mkdirs();
			//TODO: CORRECT OUTPUT FOR ENGINE+TOOLSET OR TOOLSET-ONLY
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
