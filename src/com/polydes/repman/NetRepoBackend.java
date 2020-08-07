package com.polydes.repman;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.polydes.repman.util.Util;

public class NetRepoBackend extends RepoBackend
{
	private static final Logger log = Logger.getLogger(NetRepoBackend.class);
	
	private static final int HIGHEST_KNOWN_REPOSITORY_API_VERSION = 3;
	
	private String url;
	private int apiVersion;
	
	public NetRepoBackend(String url)
	{
		this.url = url;
		
		RepoBackend.quiet = true;
		
		apiVersion = getVersion();
		if(apiVersion < 0)
			log.warn(url + " doesn't point to a valid repository, or it can't be connected to.");
		
		RepoBackend.quiet = false;
	}
	
	public static boolean verifyUrl(String url)
	{
		NetRepoBackend repo = new NetRepoBackend(url);
		return repo.getVersion() > 0;
	}
	
	/*-------------------------------------*\
	 * API
	\*-------------------------------------*/ 
	
	public List<String> getList(ExtensionType type)
	{
		if(apiVersion == 1)
		{
			List<String> extensionList = new ArrayList<>();
			
			JSONObject o = jsonFromUrl("%s/v1/%s/list/", url, type.toString());
			if(o == null)
				return extensionList;
			
			JSONArray extensions = null;
			
			try
			{
				extensions = o.getJSONArray("extensions");
				for(Object extension : extensions)
				{
					extensionList.add((String) extension);
				}
			}
			catch(Exception ex)
			{
				log.error(ex.getMessage(), ex);
			}
			
			return extensionList;
		}
		else if(apiVersion == 2 || apiVersion == 3)
		{
			String info = dataFromUrl("%s/v%d/%s/list.txt", url, apiVersion, type.toString());
			if(info.isBlank()) return new ArrayList<>();
			return Arrays.asList(info.split("\\r?\\n"));
		}
		
		return null;
	}
	
	private int getVersion()
	{
		JSONObject o = jsonFromUrl("%s/access/", url);
		if(o == null)
			return -1;
		
		JSONArray versions = o.getJSONArray("versions");
		int highest = -1;
		for(Object version : versions)
		{
			int v = Integer.parseInt(((String) version).substring(1));
			if(v > highest && v <= HIGHEST_KNOWN_REPOSITORY_API_VERSION)
				highest = v;
		}
		
		return highest;
	}
	
	public boolean isValid()
	{
		return apiVersion >= 1;
	}
	
	public void downloadExtension(ExtensionType type, String id, Version version, File downloadTo, Runnable callback)
	{
		String downloadUrl = apiVersion == 1 ?
			String.format("%s/v1/%s/%s/get/%s", url, type.toString(), id, version) :
			String.format("%s/v%d/%s/%s/%s.zip", url, apiVersion, type.toString(), id, version);
		
		downloadAll(Arrays.asList(new ImmutablePair<>(downloadUrl, downloadTo.getAbsolutePath())), callback);
	}

	public int getRevision(ExtensionType type, String extensionID)
	{
		if(apiVersion == 1)
		{
			try (InputStream in = new URL(String.format("%s/v1/%s/%s/revision", url, type.toString(), extensionID)).openStream())
			{
				String revisionString = IOUtils.toString(in);
				revisionString = revisionString.trim();
				return Util.parseInt(revisionString, -1);
			}
			catch (IOException e)
			{
				log.debug("No revision data for " + type.toString() + " extension " + extensionID);
			}
		}
		if(apiVersion == 2 || apiVersion == 3)
		{
			String revisionString = dataFromUrl("%s/v%d/%s/%s/revision", url, apiVersion, type.toString(), extensionID).trim();
			return Util.parseInt(revisionString, -1);
		}
		
		return -1;
	}

	public String getIconUrl(ExtensionType type, String extensionID)
	{
		if(apiVersion == 1)
		{
			return String.format("%s/v1/%s/%s/get/icon", url, type.toString(), extensionID);
		}
		if(apiVersion == 2 || apiVersion == 3)
		{
			return String.format("%s/v%d/%s/%s/icon.png", url, apiVersion, type.toString(), extensionID);
		}
		
		return null;
	}

	public String getInfoUrl(ExtensionType type, String extensionID)
	{
		if(apiVersion == 1)
		{
			return String.format("%s/v1/%s/%s/get/info", url, type.toString(), extensionID);
		}
		if(apiVersion == 2 || apiVersion == 3)
		{
			return String.format("%s/v%d/%s/%s/info.txt", url, apiVersion, type.toString(), extensionID);
		}
		
		return null;
	}

	public String getVersionsUrl(ExtensionType type, String extensionID)
	{
		if(apiVersion == 1)
		{
			return String.format("%s/v1/%s/%s/versions?format=json", url, type.toString(), extensionID);
		}
		if(apiVersion == 2 || apiVersion == 3)
		{
			return String.format("%s/v%d/%s/%s/versions.json", url, apiVersion, type.toString(), extensionID);
		}
		
		return null;
	}
}
