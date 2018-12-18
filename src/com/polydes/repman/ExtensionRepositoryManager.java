package com.polydes.repman;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.polydes.repman.data.Prefs;
import com.polydes.repman.util.NotifierHashMap;
import com.polydes.repman.util.io.IterableNodeList;
import com.polydes.repman.util.io.XMLHelper;

public class ExtensionRepositoryManager
{
	private static final Logger log = Logger.getLogger(ExtensionRepositoryManager.class);
	
	private final NotifierHashMap<String, ExtensionRepository> repositories = new NotifierHashMap<String, ExtensionRepository>();
	
	public NotifierHashMap<String, ExtensionRepository> getRepositories()
	{
		return repositories;
	}
	
	public void loadRepositories()
	{
		File settingsFile = new File(Prefs.get(Prefs.SW_WORKSPACE), "repositories" + File.separator + "repositories.xml");
		
		boolean settingsFileExists = settingsFile.exists();
		
		Set<String> urls = new HashSet<String>();
		
		if(settingsFileExists)
		{
			try
			{
				Element settings = XMLHelper.readXMLFromFile(settingsFile).getDocumentElement();
				
				for(Element e : IterableNodeList.elements(settings.getElementsByTagName("repository")))
					urls.add(e.getAttribute("url"));
			}
			
			catch (IOException e)
			{
				log.error(e.getMessage(), e);
			}
		}
		
		for(String url : urls)
		{
			if(!url.isEmpty() && !repositories.containsKey(url))
			{
				repositories.put(url, new ExtensionRepository(url));
			}
		}
		
		if(!settingsFileExists)
		{
			saveRepositoryStatus();
		}
	}
	
	public void saveRepositoryStatus()
	{
		Document document = XMLHelper.newDocument();
		
		Element root = document.createElement("repositories");
		
		for(ExtensionRepository repo : repositories.values())
		{
			Element repository = document.createElement("repository");
			repository.setAttribute("url", repo.url);
			root.appendChild(repository);
		}
		
		document.appendChild(root);
		
		File settingsFolder = new File(Prefs.get(Prefs.SW_WORKSPACE), "repositories");
		settingsFolder.mkdirs();
		File settingsFile = new File(settingsFolder, "repositories.xml");

		try
		{
			XMLHelper.writeXMLToFile(document, settingsFile);
		}
		
		catch (IOException e)
		{
			log.error(e.getMessage(), e);
		}
	}
}
