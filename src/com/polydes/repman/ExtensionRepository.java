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
import stencyl.core.api.struct.NotifierMap;
import stencyl.core.api.struct.NotifierMap.MapListener;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.backend.NetRepoBackend;
import stencyl.core.api.Version;
import stencyl.core.ext.backend.NetRepoBackend.RepoApiException;
import stencyl.core.ext.backend.NetRepoBackendV4;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;

public class ExtensionRepository
{
	private static final Logger log = Logger.getLogger(ExtensionRepository.class);

	public final String url;
	private final Path cacheRoot;
	private Path cachePath;
	private NetRepoBackend netBackend;

	public ExtensionRepository(String url, Path cacheRoot)
	{
		this.url = url;
		this.cacheRoot = cacheRoot;
	}

	public void connect() throws IOException, RepoApiException
	{
		if(netBackend != null)
			return;

		String local = url.replace("http://", "").replace("https://", "");
		cachePath = cacheRoot.resolve(local);
		netBackend = NetRepoBackend.getBackend(url, cachePath);

		updateRepositoryInfo();
	}

	public void updateRepositoryInfo()
	{
		if(netBackend == null)
			return;

		new SwingWorker<String, Void>()
		{
			@Override
			protected String doInBackground() throws Exception
			{
				netBackend.update(true);
				return null;
			}
		}.execute();
	}

	public NetRepoBackend getNetBackend() {
		return netBackend;
	}

	public NotifierHashMap<String, NetExtension> getExtensions()
	{
		return netBackend.getExtensions();
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

	public RepositoryManifest getManifest() {
		return ((NetRepoBackendV4) netBackend).getManifest(false);
	}

	public boolean isConnected()
	{
		return netBackend != null;
	}
}