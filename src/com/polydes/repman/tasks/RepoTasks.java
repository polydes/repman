package com.polydes.repman.tasks;

import com.polydes.repman.data.GithubTagArtifactSupplier;
import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.RepositoryStore;
import com.polydes.repman.data.RepositoryStore.FileToUpload;
import com.polydes.repman.ui.RepoTree.RepoData;
import stencyl.core.api.Version;
import stencyl.core.api.tasks.Task;
import stencyl.core.ext.net.ExtensionVersion;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;
import stencyl.core.ext.net.RepositoryManifest.ArtifactEntry;
import stencyl.core.ext.net.RepositoryManifest.PackageEntry;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

public class RepoTasks
{
    //artifact supplier
    // GitHub tags supplier
    // local artifact supplier

    public static void updateRepositoryContent(Task task, RepoData repoData, Path repoPath)
    {
        try
        {
            RepositoryManifest manifest = null;
            if(repoData.netRepo.isConnected())
            {
                repoData.netRepo.getNetBackend().update(true);
                manifest = repoData.netRepo.getManifest();
            }
            if(manifest == null)
            {
                manifest = new RepositoryManifest(List.of());
            }

            Map<String, PackageEntry> packages = new HashMap<>();
            for(var entry : manifest.entries())
            {
                packages.put(entry.id(), entry);
            }

            for(LocalSource local : repoData.localRepo.sources().values())
            {
                String extensionID = local.getInfo().getID();

                Path sourcePath = local.getPath();

                List<ArtifactEntry> artifacts = new ArrayList<>();

                GithubTagArtifactSupplier githubTagArtifactSupplier = new GithubTagArtifactSupplier();

                if(!githubTagArtifactSupplier.prepareToSupplyArtifacts(local))
                {
                    throw task.failWithError("GithubTagArtifactSupplier", "Can't provide git-based tags for repository: " + local.getInfo().getID());
                }

                for(ExtensionVersion version : local.getVersions())
                {
                    Version v = version.version();
                    if(githubTagArtifactSupplier.getArtifact(v) instanceof ArtifactEntry entry)
                    {
                        artifacts.add(entry);
                    }
                }

                if(artifacts.isEmpty())
                {
                    continue;
                }

                Path updatedMirrorPath = repoPath.resolve("extensions/"+extensionID);
                Files.createDirectories(updatedMirrorPath);

                Image icon = local.getInfo().getIcon();
                Path iconFile = updatedMirrorPath.resolve("icon.png");
                BufferedImage bi = new BufferedImage(icon.getWidth(null), icon.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics g = bi.createGraphics();
                g.drawImage(icon, 0, 0, null);
                g.dispose();
                ImageIO.write(bi, "png", iconFile.toFile());

                NetExtension.writeExtensionData(local.getInfo(), updatedMirrorPath, "info.json");

                Files.copy(sourcePath.resolve("versions.json"), updatedMirrorPath.resolve("versions.json"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(sourcePath.resolve("changes.md"), updatedMirrorPath.resolve("changes.md"), StandardCopyOption.REPLACE_EXISTING);

                byte[] revision = hashFiles(updatedMirrorPath, List.of("info.json", "versions.json", "icon.png", "changes.md"));

                packages.put(extensionID, new PackageEntry(extensionID, revision, artifacts));
            }

            List<PackageEntry> newEntries = packages.values().stream()
                    .filter(entry -> !entry.artifacts().isEmpty())
                    .toList();
            manifest = new RepositoryManifest(newEntries);
            String manifestContent = manifest.serialize();
            Path manifestPath = repoPath.resolve("manifest.json");
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, manifestContent);
        }
        catch(Exception ex)
        {
            throw task.failWithError("Repository Update Failed", ex.getMessage(), ex);
        }
    }

    private static byte[] hashFiles(Path contentPath, List<String> filenames) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            for(String name : filenames)
            {
                digest.update(Files.readAllBytes(contentPath.resolve(name)));
            }

            return digest.digest();
        }
        catch(NoSuchAlgorithmException e)
        {
            //this won't happen
            throw new RuntimeException(e);
        }
    }

    public static void mirrorRepositoryContent(Task task, RepoData repoData, Path repoPath)
    {
        try
        {
            for(LocalSource local : repoData.localRepo.sources().values())
            {
                String extensionID = local.getInfo().getID();
                Path localMirrorPath = repoData.netRepo.getExtensionLocalLocation(extensionID);
                Path contentPath = repoPath.resolve("extensions/"+extensionID);

                List<FileToUpload> filesToUpload = new ArrayList<>();
                for(String path : List.of("info.json", "versions.json", "changes.md", "icon.png")) {
                    Path localMirror = localMirrorPath.resolve(path);
                    Path localSource = contentPath.resolve(path);
                    if(!Files.exists(localMirror) || !Arrays.equals(Files.readAllBytes(localMirror), Files.readAllBytes(localSource)))
                    {
                        filesToUpload.add(new FileToUpload(localSource, path));
                    }
                }

                //TODO: include uploads of new extension versions that we want to store on server

                //old code
//                    for(VersionInfo vi : versionList)
//                        if(vi.localVersion != null && vi.remoteVersion == null && hasLocal(vi.version))
//                            filesToUpload.add(vi.version() + ".zip");

                if(!filesToUpload.isEmpty())
                {
                    RepositoryStore.upload(repoData.netRepo, extensionID, filesToUpload);
                }
            }

            RepositoryStore.upload(repoData.netRepo, List.of(new FileToUpload(repoPath.resolve("manifest.json"), "manifest.json")));
        }
        catch(IOException ex)
        {
            throw task.failWithError("Failed to mirror repository content", ex.getMessage(), ex);
        }
    }
}
