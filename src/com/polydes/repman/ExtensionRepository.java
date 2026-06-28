package com.polydes.repman;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import stencyl.core.api.struct.NotifierHashMap;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.backend.LocalRepoBackend;
import stencyl.core.ext.backend.NetRepoBackend;
import stencyl.core.api.Version;
import stencyl.core.ext.net.NetExtension;

public class ExtensionRepository
{
	private static final Logger log = Logger.getLogger(ExtensionRepository.class);

	public final String url;
	private final Path cachePath;
	private final NetRepoBackend netBackend;
	private final LocalRepoBackend localBackend;

	public ExtensionRepository(String url, Path cacheRoot)
	{
		this.url = url;

		netBackend = new NetRepoBackend(url);
		
		String local = url.replace("http://", "").replace("https://", "");
		cachePath = cacheRoot.resolve(local);

		localBackend = new LocalRepoBackend(url, cachePath);
		
		updateRepositoryInfo();
	}
	
	public static boolean verifyUrl(String url)
	{
		return NetRepoBackend.verifyUrl(url);
	}
	
	public void updateRepositoryInfo()
	{
		new SwingWorker<String, Void>()
		{
			@Override
			protected String doInBackground() throws Exception
			{
				localBackend.update(netBackend, true);
				return null;
			}
		}.execute();
	}
	
	public NotifierHashMap<String, NetExtension> getExtensions()
	{
		return localBackend.allExtensions;
	}

	public Path getExtensionLocalLocation(String extensionID)
	{
		return cachePath.resolve(extensionID);
	}
	
	public Path getVersionLocalLocation(String extensionID, Version v)
	{
		return cachePath.resolve(extensionID, v + ".zip");
	}
	
	public boolean hasVersionLocally(NetExtension ext, Version v)
	{
		return Files.exists(cachePath.resolve(ext.id, v + ".zip"));
	}
	
	public void setHasVersionLocally(NetExtension ext, Version v, boolean value, Runnable callback)
	{
		if(value == hasVersionLocally(ext, v))
			return;

		Path location = cachePath.resolve(ext.id, v + ".zip");
		
		if(value)
		{
			String url = netBackend.getDownloadUrl(ext.id, v);
			try
			{
				FileUtils.copyURLToFile(new URI(url).toURL(), location.toFile(), 20000, 20000);
				callback.run();
			}
			catch(IOException | URISyntaxException e)
			{
				log.error(e.getMessage(), e);
			}
		}
		else
		{
			try
			{
				Files.delete(location);
			}
			catch(IOException e)
			{
				log.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public String toString()
	{
		return url;
	}

	public void loadExtension(Path extensionPath)
	{
		try
		{
			ExtensionInfo info = ExtensionInfo.loadExtensionInfo(extensionPath);
			if(info != null)
			{
				NetExtension ext = new NetExtension(info.getID());
				ext.id = info.getID();
				ext.name = info.getName();
				ext.description = info.getDescription();
				ext.author = info.getAuthorName();
				ext.cat = info.getType();
				ext.website = info.getWebsite();
				ext.repository = info.getRepository();
				ext.icon = info.getIcon();
				if(Files.exists(extensionPath.resolve("versions.json")))
				{
					ext.versions = ExtensionInfo.readVersions(extensionPath.resolve("versions.json"));
				}
				else
				{
					ext.versions = List.of();
				}
				localBackend.allExtensions.put(info.getID(), ext);
			}
		}
		catch(IOException e)
		{
			log.error(e.getMessage(), e);
		}
	}
}