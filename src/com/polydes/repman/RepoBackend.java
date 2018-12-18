package com.polydes.repman;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class RepoBackend
{
	private static final Logger log = Logger.getLogger(RepoBackend.class);
	
	/*-------------------------------------*\
	 * Helpers
	\*-------------------------------------*/ 
	
	protected void downloadAll(List<Pair<String, String>> downloads, Runnable callback)
	{
		final Set<String> urls = new HashSet<>();
		for(Pair<String, String> currentDownload : downloads)
		{
			urls.add(currentDownload.getLeft());
		}
		
		for(Pair<String, String> currentDownload : downloads)
		{
			SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>()
			{
				@Override
				protected Integer doInBackground() throws Exception
				{
					String url = currentDownload.getLeft();
					String local = currentDownload.getRight();
					download(url, local);
					urls.remove(url);
					if(urls.isEmpty())
					{
						callback.run();
					}
					
					return 0;
				}
			};
			
			worker.execute();
		}
	}
	
	protected void download(String url, String local)
	{
		try
		{
			FileUtils.copyURLToFile(new URL(url), new File(local), 20000, 20000);
		}
		catch(IOException e)
		{
			log.error(e.getMessage(), e);
		}
	}
	
	protected String dataFromUrl(String url)
	{
		try (InputStream in = new URL(url).openStream())
		{
			return IOUtils.toString(in);
		}
		catch (IOException e)
		{
			log.error(e.getMessage(), e);
			return "";
		}
	}
	
	protected JSONObject jsonFromFile(String filepath, Object... formatting)
	{
		try
		{
//			System.out.println(String.format(url, formatting));
			return new JSONObject(FileUtils.readFileToString(new File(filepath)));
		}
		catch(JSONException | IOException ex)
		{
			log.error(ex.getMessage(), ex);
			return null;
		}
	}
	
	protected JSONObject jsonFromUrl(String url, Object... formatting)
	{
		try
		{
//			System.out.println(String.format(url, formatting));
			return new JSONObject(dataFromUrl(String.format(url, formatting)));
		}
		catch(JSONException ex)
		{
			log.error(ex.getMessage(), ex);
			return null;
		}
	}
	
	protected String dataFromUrl(String url, Object... formatting)
	{
//		System.out.println(String.format(url, formatting));
		return dataFromUrl(String.format(url, formatting));
	}
}
