package com.polydes.repman.data;

import com.polydes.repman.util.GitRemoteParser;
import stencyl.core.api.Version;
import stencyl.core.ext.net.RepositoryManifest.ArtifactEntry;

import java.util.HashSet;
import java.util.Set;

public class GithubTagArtifactSupplier implements ArtifactSupplier
{
    private String remoteTaggedArtifactsUrl;
    private Set<String> localTags;

    public boolean prepareToSupplyArtifacts(LocalSource localSource)
    {
        if(!localSource.isLoaded() || !localSource.isGitStateLoaded())
        {
            return false;
        }
        try
        {
            String ownerAndName = GitRemoteParser.getRepoOwnerAndName(localSource.getPath().toFile());
            remoteTaggedArtifactsUrl = "https://github.com/"+ownerAndName+"/archive/refs/tags/%s.zip";
        }
        catch (Exception ex)
        {
            remoteTaggedArtifactsUrl = null;
            return false;
        }
        localTags = new HashSet<>(localSource.getLocalTags());
        return true;
    }

    @Override
    public ArtifactEntry getArtifact(Version version) {
        String versionString = version.toString();
        if(localTags.contains(versionString))
        {
            String url = String.format(remoteTaggedArtifactsUrl, versionString);
            return new ArtifactEntry(versionString, url);
        }
        else if(localTags.contains("v" + versionString))
        {
            String url = String.format(remoteTaggedArtifactsUrl, "v" + versionString);
            return new ArtifactEntry(versionString, url);
        }

        return null;
    }
}
