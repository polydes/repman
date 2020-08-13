package com.polydes.repman;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.polydes.repman.ExtensionDependency.Type;
import com.polydes.repman.ExtensionManifest.ExtensionCategory;
import com.polydes.repman.data.Prefs;
import com.polydes.repman.data.Sources;
import com.polydes.repman.data.Sources.SourceMap;
import com.polydes.repman.data.Sources.TypesMap;
import com.polydes.repman.util.NotifierHashMap;
import com.polydes.repman.util.Util;
import com.polydes.repman.util.Zip;

public class LocalRepoBackend extends RepoBackend
{
	private static final Logger log = Logger.getLogger(LocalRepoBackend.class);
	
	private String url;
	private String root;
	
	public Map<ExtensionType, NotifierHashMap<String, Extension>> allExtensions;
	
	public LocalRepoBackend(String url, String local)
	{
		this.url = url;
		
		String fs = File.separator;
		root = new File(Prefs.get(Prefs.SW_WORKSPACE) + "repositories" + fs + local + fs).getAbsolutePath() + fs;
		
		allExtensions = new HashMap<>();
		allExtensions.put(ExtensionType.ENGINE, new NotifierHashMap<>());
		allExtensions.put(ExtensionType.TOOLSET, new NotifierHashMap<>());
		
		loadFromDisk();
	}
	
	public void loadFromDisk()
	{
		for(ExtensionType type : allExtensions.keySet())
		{
			File extensionListDir = new File(root, type.toString());
			extensionListDir.mkdirs();
			for(String extensionID : extensionListDir.list())
			{
				if(new File(extensionListDir, extensionID + File.separator + "info.txt").exists())
				{
					loadExtension(type, extensionID);
				}
			}
		}
		
		TypesMap types = Sources.getRepoSources(url);
		for(Entry<ExtensionType, SourceMap> entry : types.entrySet())
		{
			ExtensionType type = entry.getKey();
			SourceMap sources = entry.getValue();
			Map<String, Extension> loadedExtensions = allExtensions.get(type);
			
			for(String extensionID : sources.keySet())
			{
				if(!loadedExtensions.containsKey(extensionID))
				{
					loadDummyExtension(type, extensionID);
				}
			}
		}
	}

	public void update(NetRepoBackend netRepo)
	{
		for(ExtensionType type : allExtensions.keySet())
		{
			for(String extensionID : netRepo.getList(type))
			{
				String extRoot = root + type.toString() + File.separator + extensionID + File.separator;
				int currentLocalVersion = -1;
				int currentNetVersion = -1;
				
				File localRevisionFile = new File(extRoot + "revision");
				
				if(localRevisionFile.exists())
				{
					try
					{
						currentLocalVersion = Util.parseInt(FileUtils.readFileToString(localRevisionFile), -1);
					}
					catch(IOException e)
					{
						log.error(e.getMessage(), e);
					}
				}
				
				currentNetVersion = netRepo.getRevision(type, extensionID);
				
				if(currentNetVersion > currentLocalVersion)
				{
					final String revString = "" + currentNetVersion;
					
					List<Pair<String, String>> downloads = new ArrayList<>();
					downloads.add(new ImmutablePair<>(netRepo.getIconUrl(type, extensionID), extRoot + "icon.png"));
					downloads.add(new ImmutablePair<>(netRepo.getInfoUrl(type, extensionID), extRoot + "info.txt"));
					downloads.add(new ImmutablePair<>(netRepo.getVersionsUrl(type, extensionID), extRoot + "versions.json"));
					
					downloadAll(downloads, ()->{
						
						try
						{
							FileUtils.writeStringToFile(localRevisionFile, revString);
						}
						catch(Exception e)
						{
							log.error(e.getMessage(), e);
						}
						
						loadExtension(type, extensionID);
						
					});
				}
			}
		}
	}
	
	private void loadExtension(ExtensionType type, String extensionID)
	{
		System.out.println("loading " + type + ", " + extensionID);
		
		String extRoot = root + type.toString() + File.separator + extensionID + File.separator;
		Extension ext = new Extension(type, extensionID);
		
		try
		{
			ext.icon = new ImageIcon(ImageIO.read(new File(extRoot + "icon.png")));
		}
		catch(IOException ex)
		{
			log.error(ex.getMessage(), ex);
		}
		
		String info;
		Map<String, String> properties = new HashMap<>();
		
		try
		{
			info = FileUtils.readFileToString(new File(extRoot + "info.txt"));
			Prefs.putLinesInMap(Arrays.asList(info.split("\n")), properties);
		}
		catch(IOException ex)
		{
			log.error(ex.getMessage(), ex);
		}
		
		ext.name = properties.get("Name");
		ext.author = properties.get("Author");
		ext.description = properties.get("Description");
		ext.website = properties.get("Website");
		ext.repository = url;
		if(type == ExtensionType.TOOLSET)
		{
			ext.cat = ExtensionCategory.fromString(properties.get("Type"));
		}
		
		ext.versions = new ArrayList<>();
		JSONArray versions_json = jsonFromFile(extRoot + "versions.json").getJSONArray("versions");
		versions_json.forEach(j -> ext.versions.add(ExtensionVersion.fromJSON((JSONObject) j, ext.type.toExtensionDependencyType())));
		ext.versions.sort((v1, v2) -> v1.version.compareTo(v2.version));
		
		refreshInstalledVersions(ext);
		
		allExtensions.get(type).put(ext.id, ext);
	}
	
	private void loadDummyExtension(ExtensionType type, String extensionID)
	{
		System.out.println("loading " + type + ", " + extensionID + " (dummy extension).");
		
		Extension ext = new Extension(type, extensionID);
		ext.icon = new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
		ext.name = "[" + extensionID + "]";
		ext.author = "___";
		ext.description = "___";
		ext.website = "___";
		ext.repository = url;
		
		if(type == ExtensionType.TOOLSET)
		{
			ext.cat = ExtensionCategory.NORMAL;
		}
		
		ext.versions = new ArrayList<>();
		allExtensions.get(type).put(ext.id, ext);
	}

	public File getExtensionLocalLocation(Extension ext)
	{
		return new File(root, ext.type.toString() + File.separator + ext.id + File.separator);
	}
	
	public File getVersionLocation(Extension ext, Version v)
	{
//		String fileExt = ext.type == ExtensionType.ENGINE ? ".zip" : ".jar";
		return new File(root, ext.type.toString() + File.separator + ext.id + File.separator + v + ".zip");
	}
	
	public boolean hasVersionLocally(Extension ext, Version v)
	{
		return getVersionLocation(ext, v).exists();
	}

	public void setInstalledVersion(Extension ext, Version v, boolean value, Runnable callback)
	{
		boolean isEngine = ext.type == ExtensionType.ENGINE;
		String folder = isEngine ? "engine-extensions" : "extensions";
		String fileExt = isEngine ? "" : ".jar";
		File installed = new File(Prefs.get(Prefs.SW_WORKSPACE) + folder + File.separator + ext.id + fileExt);
		
		if(isEngine)
		{
			try
			{
				FileUtils.deleteDirectory(installed);
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		else
			installed.delete();
		
		if(value)
		{
			File local = getVersionLocation(ext, v);
			Zip.unzip(local, isEngine ? installed : installed.getParentFile());
		}
		
		refreshInstalledVersions(ext);
		
		callback.run();
	}
	
	public void refreshInstalledVersions(Extension ext)
	{
		boolean isEngine = ext.type == ExtensionType.ENGINE;
		String folder = isEngine ? "engine-extensions" : "extensions";
		String fileExt = isEngine ? "" : ".jar";
		File installed = new File(Prefs.get(Prefs.SW_WORKSPACE) + folder + File.separator + ext.id + fileExt);
		Version installedVersion = null;
		
		if(installed.exists())
		{
			try
			{
				if(isEngine)
				{
					Map<String,String> map = new HashMap<>();
					Prefs.putLinesInMap(FileUtils.readLines(new File(installed, "info.txt")), map);
					installedVersion = new Version(map.get("version"));
				}
				else
				{
					ExtensionManifest man = ExtensionManifest.fromJar(installed);
					installedVersion = man.version;
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		
		for(ExtensionVersion ev : ext.versions)
			ev.installed = ev.version.equals(installedVersion);
	}
	
	/*-------------------------------------*\
	 * Data
	\*-------------------------------------*/ 
	
	public static class ExtensionVersion implements Comparable<ExtensionVersion>
	{
		public String changes;
		public Version version;
		public String date;
		public ExtensionDependency[] dependencies;
		
		public boolean installed;
		public boolean local;
		public boolean dirty;
		
		static ExtensionVersion fromJSON(JSONObject j, Type defaultType)
		{
			String changes = j.getString("changes");
			Version version = new Version(j.getString("version"));
			String date = j.has("date") ? j.getString("date") : "";
			ExtensionDependency[] dependencies = ExtensionDependency.fromStringList(j.getString("dependencies"), defaultType);
			
			return new ExtensionVersion(changes, version, date, dependencies);
		}
		
		public JSONObject toJSON()
		{
			JSONObject o = new JSONObject();
			o.put("changes", changes);
			o.put("version", "" + version);
			o.put("date", date);
			o.put("dependencies", StringUtils.join(dependencies, ","));
			
			return o;
		}
		
		public ExtensionVersion(String changes, Version version, String date, ExtensionDependency[] dependencies)
		{
			this.changes = changes;
			this.version = version;
			this.date = date;
			this.dependencies = dependencies;
		}
		
		@Override
		public int compareTo(ExtensionVersion o)
		{
			return version.compareTo(o.version);
		}
	}
}
