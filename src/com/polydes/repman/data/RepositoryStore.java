package com.polydes.repman.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.ui.RepmanMain;
import com.polydes.repman.util.io.FTPHelper;
import com.polydes.repman.util.io.FTPHelper.FTPConnectionType;

public class RepositoryStore
{
	public static Map<String, RepoInfo> repositories;

	public sealed interface RepoInfo
	{

	}

	static final class FtpRepoInfo implements RepoInfo
	{
		public FTPConnectionType connectionType;
		public String url;
		public String host;
		public String username;
		public String password;
		public String root;
	}

	static final class LocalRepoInfo implements RepoInfo
	{
		public String url;
		public Path localPath;
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
			YamlReader reader = new YamlReader(new FileReader(RepmanMain.REPMAN_DIR + File.separator + "repositories.yml"));
			List repositoriesList = (List) ((Map) reader.read()).get("repositories");
		    for(Object o : repositoriesList)
		    {
		    	Map map = (Map) o;
				String type = (String) map.get("type");
				String url = (String) map.get("url");
				switch(type)
				{
					case "ftp" -> {
						FtpRepoInfo repo = new FtpRepoInfo();
						repo.url = url;
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
					case "local" -> {
						LocalRepoInfo repo = new LocalRepoInfo();
						repo.url = url;
						repo.localPath = Path.of((String) map.get("path"));
						repositories.put(repo.url, repo);
					}
				}
		    }
		}
		catch(FileNotFoundException | YamlException e)
		{
			e.printStackTrace();
			repositories = null;
		}
	}

	public record FileToUpload(Path fromPath, String relativePath) {}
	public static void upload(ExtensionRepository repo, String extensionID, List<FileToUpload> files)
	{
		RepoInfo info = getRepoInfo(repo.url);
		switch(info)
		{
			case FtpRepoInfo ftpInfo -> {
				String extRemote = ftpInfo.root + "extensions" + "/" + extensionID + "/";

				FTPHelper ftp = new FTPHelper(ftpInfo.host, ftpInfo.connectionType, ftpInfo.username, ftpInfo.password);
				for(FileToUpload toUpload : files)
				{
					boolean binary = toUpload.relativePath.endsWith(".zip") || toUpload.relativePath.endsWith(".png");
					String remote = extRemote + toUpload.relativePath;
					String local = toUpload.fromPath.toString();
					ftp.transfer(true, binary, remote, local);
				}
				ftp.disconnect();
			}
			case LocalRepoInfo localInfo -> {
				try
				{
					for(FileToUpload toUpload : files)
					{
						Path targetPath = localInfo.localPath.resolve("extensions", extensionID, toUpload.relativePath);
						Files.createDirectories(targetPath.getParent());
						Files.copy(toUpload.fromPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
				}
			}
		}

	}
	public static void upload(ExtensionRepository repo, List<FileToUpload> files)
	{
		RepoInfo info = getRepoInfo(repo.url);

		switch (info)
		{
			case FtpRepoInfo ftpInfo -> {
				FTPHelper ftp = new FTPHelper(ftpInfo.host, ftpInfo.connectionType, ftpInfo.username, ftpInfo.password);
				for(FileToUpload toUpload : files)
				{
					boolean binary = toUpload.relativePath.endsWith(".zip") || toUpload.relativePath.endsWith(".png");
					String remote = ftpInfo.root + toUpload.relativePath;
					String local = toUpload.fromPath.toString();
					ftp.transfer(true, binary, remote, local);
				}
				ftp.disconnect();
			}
			case LocalRepoInfo localInfo -> {
				try
				{
					for(FileToUpload toUpload : files)
					{
						Path targetPath = localInfo.localPath.resolve(toUpload.relativePath);
						Files.createDirectories(targetPath.getParent());
						Files.copy(toUpload.fromPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
				}
			}
		}
	}
}
