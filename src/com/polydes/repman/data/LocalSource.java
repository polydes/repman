package com.polydes.repman.data;

import com.polydes.repman.util.GitRemoteParser;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.net.ChangeEntry;
import stencyl.core.ext.net.ExtensionVersion;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LocalSource
{
    private static final Logger log = Logger.getLogger(LocalSource.class);

    public static final String BASIC_PROPERTIES = "Basic";
    public static final String LOCAL_GIT_PROPERTIES = "GitLocal";
    public static final String REMOTE_GIT_PROPERTIES = "GitRemote";

    private final PropertyChangeSupport pcs;

    private final Path path;
    private boolean loaded;
    private ExtensionInfo info;
    private List<ChangeEntry> changes;
    private List<ExtensionVersion> versions;

    private boolean gitStateLoaded;
    private boolean gitRemoteStateLoaded;
    private boolean hasUnstagedChanges;
    private boolean hasUnpushedCommits;
    private String lastVersionTag;
    private List<String> commitsSinceLastVersionTag;
    private List<String> localTags;
    private List<String> remoteTags;
    private String remoteTaggedArtifactsUrl;

    public LocalSource(Path path)
    {
        this.path = path;
        pcs = new PropertyChangeSupport(this);
        try
        {
            refresh();
        }
        catch (Exception e)
        {
            log.error("Failed to load local extension source", e);
        }
    }

    public void refresh() throws Exception
    {
        unload();
        try
        {
            info = ExtensionInfo.loadFolder(path);
            changes = ExtensionInfo.readChanges(path.resolve("changes.md"));
            versions = ExtensionInfo.readVersions(path.resolve("versions.json"));

            loaded = true;

            pcs.firePropertyChange(BASIC_PROPERTIES, null, Boolean.TRUE);
        }
        catch(Exception ex)
        {
            unload();
            pcs.firePropertyChange(BASIC_PROPERTIES, null, Boolean.FALSE);
            pcs.firePropertyChange(LOCAL_GIT_PROPERTIES, null, Boolean.FALSE);
            pcs.firePropertyChange(REMOTE_GIT_PROPERTIES, null, Boolean.FALSE);
            throw ex;
        }
    }

    private void unload()
    {
        loaded = false;
        info = null;
        changes = List.of();
        versions = List.of();

        gitStateLoaded = false;
        gitRemoteStateLoaded = false;
        hasUnstagedChanges = false;
        hasUnpushedCommits = false;
        lastVersionTag = null;
        commitsSinceLastVersionTag = null;
        localTags = null;
        remoteTags = null;
        remoteTaggedArtifactsUrl = null;
    }

    public void loadGitState()
    {
        gitStateLoaded = false;
        try(Git git = new Git(new FileRepository(path.resolve(".git").toFile())))
        {
            Repository repo = git.getRepository();

            Status status = git.status().call();
            this.hasUnstagedChanges = !status.getModified().isEmpty() ||
                    !status.getUntracked().isEmpty() ||
                    !status.getMissing().isEmpty();

            BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repo, repo.getBranch());
            this.hasUnpushedCommits = trackingStatus != null && trackingStatus.getAheadCount() > 0;

            List<Ref> localTagRefs = git.tagList().call();
            this.localTags = localTagRefs.stream()
                    .map(ref -> ref.getName().replace("refs/tags/", ""))
                    .collect(Collectors.toList());

            this.commitsSinceLastVersionTag = new ArrayList<>();

            // determine commits since last tag
            // before that, map local tag object IDs so we can check when we hit one
            Map<ObjectId,Ref> tagIds = new HashMap<>();
            for (Ref ref : localTagRefs) {
                Ref peeled = repo.getRefDatabase().peel(ref);
                ObjectId targetId = peeled.getPeeledObjectId() != null ? peeled.getPeeledObjectId() : ref.getObjectId();
                tagIds.put(targetId, ref);
            }

            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                if (tagIds.get(commit.getId()) instanceof Ref tagRef) {
                    this.lastVersionTag = tagRef.getName().replace("refs/tags/", "");
                    break;
                }
                this.commitsSinceLastVersionTag.add(commit.getName().substring(0, 7) + " - " + commit.getShortMessage());
            }

            try
            {
                remoteTaggedArtifactsUrl = "https://github.com/"+GitRemoteParser.getRepoOwnerAndName(path.toFile())+"/archive/refs/tags/%s.zip";
            }
            catch (Exception ex)
            {
                remoteTaggedArtifactsUrl = null;
            }

            gitStateLoaded = true;
            pcs.firePropertyChange(LOCAL_GIT_PROPERTIES, null, Boolean.TRUE);
        }
        catch(IOException | GitAPIException ex)
        {
            log.error("Failed to load local extension git state", ex);
            hasUnstagedChanges = false;
            hasUnpushedCommits = false;
            lastVersionTag = null;
            commitsSinceLastVersionTag = null;
            localTags = null;
            pcs.firePropertyChange(LOCAL_GIT_PROPERTIES, null, Boolean.FALSE);
        }
    }

    public void loadGitRemoteState(List<String> tags)
    {
        this.remoteTags = List.copyOf(tags);
        gitRemoteStateLoaded = true;
        pcs.firePropertyChange(REMOTE_GIT_PROPERTIES, null, Boolean.TRUE);
        System.out.println("Loaded remote state: " + path);
    }

    // Getters

    public Path getPath() {
        return path;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public ExtensionInfo getInfo() {
        return info;
    }

    public List<ChangeEntry> getChanges() {
        return changes;
    }

    public List<ExtensionVersion> getVersions() {
        return versions;
    }

    public boolean isGitStateLoaded() {
        return gitStateLoaded;
    }

    public boolean isGitRemoteStateLoaded() {
        return gitRemoteStateLoaded;
    }

    public boolean isHasUnstagedChanges() {
        return hasUnstagedChanges;
    }

    public boolean isHasUnpushedCommits() {
        return hasUnpushedCommits;
    }

    public String getLastVersionTag() {
        return lastVersionTag;
    }

    public List<String> getCommitsSinceLastVersionTag() {
        return commitsSinceLastVersionTag;
    }

    public List<String> getLocalTags() {
        return localTags;
    }

    public List<String> getRemoteTags() {
        return remoteTags;
    }

    public String getRemoteTaggedArtifactsUrl() {
        return remoteTaggedArtifactsUrl;
    }

    // PropertyChangeSupport

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return pcs.getPropertyChangeListeners();
    }

    public void fireIndexedPropertyChange(String propertyName, int index, boolean oldValue, boolean newValue) {
        pcs.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
    }

    public void firePropertyChange(String propertyName, int oldValue, int newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    public void fireIndexedPropertyChange(String propertyName, int index, int oldValue, int newValue) {
        pcs.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    public void firePropertyChange(PropertyChangeEvent event) {
        pcs.firePropertyChange(event);
    }

    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return pcs.getPropertyChangeListeners(propertyName);
    }

    public boolean hasListeners(String propertyName) {
        return pcs.hasListeners(propertyName);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    public void fireIndexedPropertyChange(String propertyName, int index, Object oldValue, Object newValue) {
        pcs.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
    }
}
