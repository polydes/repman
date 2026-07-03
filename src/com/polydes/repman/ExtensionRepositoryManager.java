package com.polydes.repman;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.polydes.repman.data.Sources;
import com.polydes.repman.data.Sources.Repository;
import org.apache.log4j.Logger;

import stencyl.core.api.struct.NotifierHashMap;
import stencyl.core.ext.backend.NetRepoBackend.RepoApiException;

public class ExtensionRepositoryManager
{
	private static final Logger log = Logger.getLogger(ExtensionRepositoryManager.class);
	
	private final NotifierHashMap<String, ExtensionRepository> repositories = new NotifierHashMap<String, ExtensionRepository>();
	
	public NotifierHashMap<String, ExtensionRepository> getRepositories()
	{
		return repositories;
	}

	public void loadRepositoriesFromDisk() throws IOException
	{
		Map<String, Repository> reposMap = Sources.getSources();

		for(var repoEntry : reposMap.entrySet())
		{
			String url = repoEntry.getKey();
			Repository repoSources = repoEntry.getValue();

			ExtensionRepository repo = new ExtensionRepository(url, repoSources.cachePath());
			try {
				repo.connect();
			} catch (RepoApiException | IOException e) {
                log.error(e.getMessage(), e);
            }
            repositories.put(repoEntry.getKey(), repo);

//			Files.createDirectories(repoSources.cachePath());
//			for(var entry : repoSources.sources().entrySet())
//			{
//				String extID = entry.getKey();
//				Path mirrorPath = repoSources.cachePath().resolve(extID);
//				if(Files.exists(mirrorPath))
//				{
//					repo.loadExtension(mirrorPath);
//				}
//			}
		}
	}
}
