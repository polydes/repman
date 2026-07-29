package com.polydes.repman.tasks;

import com.polydes.repman.Zola;
import com.polydes.repman.data.LocalRepository;
import com.polydes.repman.ui.RepoTree.RepoData;
import com.polydes.repman.util.io.FilesHelper;
import stencyl.core.api.tasks.Task;
import stencyl.core.util.HashHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteTasks
{
    private static Path repoBuildPathTemp;

    public static void serveSite(Task task, List<RepoData> repositories)
    {
        if(repoBuildPathTemp == null)
        {
            try
            {
                repoBuildPathTemp = Files.createTempDirectory("repository_build");
                FilesHelper.deleteFolderOnExit(repoBuildPathTemp);
            }
            catch(IOException ex)
            {
                throw task.failWithError(ex);
            }
        }

        Map<String, LocalRepository> builtRepositories = new HashMap<>();
        for (RepoData repoData : repositories)
        {
            try
            {
                Path repoContentPath = repoBuildPathTemp.resolve(HashHelper.getSHA1Hash(repoData.url));
                FilesHelper.deleteRecursively(repoContentPath);
                RepoTasks.updateRepositoryContent(task, repoData, repoContentPath);
                LocalRepository builtRepository = new LocalRepository(repoData.url, repoContentPath);
                builtRepository.loadFromDisk();
                builtRepositories.put(repoData.url, builtRepository);
                repoData.netRepo.updateRepositoryInfo();
            }
            catch(IOException ex)
            {
                throw task.failWithError(ex);
            }
        }

        try
        {
            Zola.serveSite(builtRepositories);
        }
        catch(Exception ex)
        {
            throw task.failWithError("Failed to build site", ex.getMessage(), ex);
        }
    }

    public static void buildAndPublishSite(Task task, List<RepoData> repositories)
    {
        if(repoBuildPathTemp == null)
        {
            try
            {
                repoBuildPathTemp = Files.createTempDirectory("repository_build");
                FilesHelper.deleteFolderOnExit(repoBuildPathTemp);
            }
            catch(IOException ex)
            {
                throw task.failWithError(ex);
            }
        }

        Map<String, LocalRepository> builtRepositories = new HashMap<>();
        for (RepoData repoData : repositories)
        {
            try
            {
                Path repoContentPath = repoBuildPathTemp.resolve(HashHelper.getSHA1Hash(repoData.url));
                FilesHelper.deleteRecursively(repoContentPath);
                RepoTasks.updateRepositoryContent(task, repoData, repoContentPath);
                LocalRepository builtRepository = new LocalRepository(repoData.url, repoContentPath);
                builtRepository.loadFromDisk();
                builtRepositories.put(repoData.url, builtRepository);
                repoData.netRepo.updateRepositoryInfo();
            }
            catch(IOException ex)
            {
                throw task.failWithError(ex);
            }
        }

        try
        {
            Zola.buildSite(builtRepositories);
        }
        catch(Exception ex)
        {
            throw task.failWithError("Failed to build site", ex.getMessage(), ex);
        }
    }
}
