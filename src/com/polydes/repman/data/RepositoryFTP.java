package com.polydes.repman.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.polydes.repman.Extension;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.util.io.FTPHelper;
import com.polydes.repman.util.io.FTPHelper.FTPConnectionType;

public class RepositoryFTP
{
	public static Map<String, RepoInfo> repositories;
	
	static class RepoInfo
	{
		public FTPConnectionType connectionType;
		public String url;
		public String host;
		public String username;
		public String password;
		public String root;
	}
	
	public static RepoInfo getRepoInfo(String url)
	{
		if(repositories == null)
			loadSources();
		
		return repositories.get(url);
	}
	
	@SuppressWarnings("rawtypes")
	public static void loadSources()
	{
		repositories = new HashMap<>();
		
		try
		{
			YamlReader reader = new YamlReader(new FileReader("repositories.yml"));
			List repositoriesList = (List) ((Map) reader.read()).get("repositories");
		    for(Object o : repositoriesList)
		    {
		    	Map map = (Map) o;
		    	RepoInfo repo = new RepoInfo();
		    	repo.url = (String) map.get("url");
		    	if(map.containsKey("sftp"))
		    		repo.connectionType = FTPConnectionType.SFTP;
		    	else if(map.containsKey("ftps"))
		    		repo.connectionType = FTPConnectionType.FTPS;
		    	else if(map.containsKey("ftp"))
		    		repo.connectionType = FTPConnectionType.FTP;
		    	switch(repo.connectionType)
		    	{
		    		case SFTP: repo.host = (String) map.get("sftp"); break;
		    		case FTPS: repo.host = (String) map.get("ftps"); break;
		    		case FTP: repo.host = (String) map.get("ftp"); break;
		    	}
		    	repo.username = (String) map.get("username");
		    	repo.password = (String) map.get("password");
		    	repo.root = (String) map.get("root");
		    	if(!repo.root.endsWith("/"))
		    		repo.root = repo.root + "/";
		    	
		    	repositories.put(repo.url, repo);
		    }
		}
		catch(FileNotFoundException | YamlException e)
		{
			e.printStackTrace();
			repositories = null;
		}
	}
	
	public static void upload(ExtensionRepository repo, Extension ext, List<String> files)
	{
		RepoInfo info = getRepoInfo(repo.url);
		String extRemote = info.root + ext.type.toString() + "/" + ext.id + "/";
		String extLocal = repo.getExtensionLocalLocation(ext).getAbsolutePath() + File.separator;
		
		FTPHelper ftp = new FTPHelper(info.host, info.connectionType, info.username, info.password);
		for(String toUpload : files)
		{
			boolean binary = toUpload.endsWith(".zip") || toUpload.endsWith(".png");
			String remote = extRemote + toUpload;
			String local = extLocal + toUpload;
			ftp.transfer(true, binary, remote, local);
		}
		ftp.disconnect();
	}
}
