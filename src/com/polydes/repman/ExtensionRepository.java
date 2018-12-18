package com.polydes.repman;

import java.io.File;

import javax.swing.SwingWorker;

import com.polydes.repman.util.NotifierHashMap;

public class ExtensionRepository
{
	public final String url;
	private NetRepoBackend netBackend;
	private LocalRepoBackend localBackend;
	
	public ExtensionRepository(String url)
	{
		this.url = url;
		
		netBackend = new NetRepoBackend(url);
		
		String local = url.replace("http://", "");
		
		localBackend = new LocalRepoBackend(url, local);
		
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
				localBackend.update(netBackend);
				return null;
			}
		}.execute();
	}
	
	public NotifierHashMap<String, Extension> getExtensions(ExtensionType type)
	{
		return localBackend.allExtensions.get(type);
	}

	public File getExtensionLocalLocation(Extension ext)
	{
		return localBackend.getExtensionLocalLocation(ext);
	}
	
	public File getVersionLocalLocation(Extension ext, Version v)
	{
		return localBackend.getVersionLocation(ext, v);
	}
	
	public boolean hasVersionLocally(Extension ext, Version v)
	{
		return localBackend.hasVersionLocally(ext, v);
	}
	
	public void setHasVersionLocally(Extension ext, Version v, boolean value, Runnable callback)
	{
		if(value == hasVersionLocally(ext, v))
			return;
		
		File location = localBackend.getVersionLocation(ext, v);
		
		if(value)
		{
			downloadExtension(ext.type, ext.id, v, location, callback);
		}
		else
		{
			location.delete();
		}
	}

	public void refreshInstalledVersions(Extension ext)
	{
		localBackend.refreshInstalledVersions(ext);
	}
	
	public void setInstalledVersion(Extension ext, Version v, boolean value, Runnable callback)
	{
		localBackend.setInstalledVersion(ext, v, value, callback);
	}

	public void downloadExtension(ExtensionType extensionType, String id, Version version, File downloadLoc, Runnable runnable)
	{
		netBackend.downloadExtension(extensionType, id, version, downloadLoc, runnable);
	}
	
	@Override
	public String toString()
	{
		return url;
	}
}