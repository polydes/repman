package com.polydes.repman.util;

import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.RepositoryFTP;
import com.polydes.repman.data.RepositoryFTP.FileToUpload;
import com.polydes.repman.ui.RepoTree.RepoData;
import com.polydes.repman.util.io.FilesHelper;
import org.apache.commons.lang3.StringUtils;
import stencyl.core.api.Version;
import stencyl.core.api.tasks.TaskManager;
import stencyl.core.ext.net.ExtensionVersion;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;
import stencyl.core.ext.net.RepositoryManifest.ArtifactEntry;
import stencyl.core.ext.net.RepositoryManifest.PackageEntry;
import stencyl.core.util.HashHelper;
import stencyl.core.util.Util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;

public class Helpers
{
    private static Path mirrorUpdatesTemp;

    public static void updateRepositoryManifest(RepoData repoData)
    {
        TaskManager.runTask("Update Manifest", task -> {
            try
            {
                repoData.netRepo.getNetBackend().update(true);
                RepositoryManifest manifest = repoData.netRepo.getManifest();
                if(manifest == null)
                {
                    manifest = new RepositoryManifest(List.of());
                }

                Map<String, PackageEntry> packages = new HashMap<>();
                for(var entry : manifest.entries())
                {
                    packages.put(entry.id(), entry);
                }

                if(mirrorUpdatesTemp == null)
                    mirrorUpdatesTemp = Files.createTempDirectory("mirror");
                FilesHelper.deleteFolderOnExit(mirrorUpdatesTemp);
                Path mirrorUpdates = mirrorUpdatesTemp.resolve(HashHelper.getSHA1Hash(repoData.url));

                for(LocalSource local : repoData.localRepo.sources().values())
                {
                    String extensionID = local.getInfo().getID();

                    Path sourcePath = local.getPath();

                    if(local.isLoaded() && local.isGitStateLoaded() && local.isGitRemoteStateLoaded())
                    {
                        PackageEntry entry = packages.computeIfAbsent(extensionID, id -> {
                            return new PackageEntry(id, 0, List.of());
                        });

                        List<ArtifactEntry> artifacts = new ArrayList<>();

                        Set<String> localTags = new HashSet<>(local.getLocalTags());
                        Set<String> remoteTags = new HashSet<>(local.getRemoteTags());

                        for(ExtensionVersion version : local.getVersions())
                        {
                            Version v = version.version();
                            String versionString = v.toString();
                            boolean inLocal = localTags.contains(versionString) || localTags.contains("v" + versionString);
                            boolean inRemote = remoteTags.contains(versionString) || remoteTags.contains("v" + versionString);

                            if(inLocal && inRemote)
                            {
                                String url = String.format(local.getRemoteTaggedArtifactsUrl(), versionString);
                                artifacts.add(new ArtifactEntry(versionString, url));
                            }
                        }

                        if(!entry.artifacts().equals(artifacts))
                        {
                            packages.put(entry.id(), new PackageEntry(entry.id(), entry.revision() + 1, artifacts));
                        }
                    }

                    if(!(packages.get(extensionID) instanceof PackageEntry pe) || pe.artifacts().isEmpty())
                    {
                        continue;
                    }

                    Path mirrorPath = repoData.netRepo.getExtensionLocalLocation(extensionID);
                    Path updatedMirrorPath = mirrorUpdates.resolve("extensions/"+extensionID);
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

                    List<FileToUpload> filesToUpload = new ArrayList<>();
                    for(String path : List.of("info.json", "versions.json", "changes.md", "icon.png")) {
                        Path localMirror = mirrorPath.resolve(path);
                        Path localSource = updatedMirrorPath.resolve(path);
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
                        RepositoryFTP.upload(repoData.netRepo, extensionID, filesToUpload);
                    }
                }

                List<PackageEntry> newEntries = packages.values().stream()
                        .filter(entry -> !entry.artifacts().isEmpty())
                        .toList();
                manifest = new RepositoryManifest(newEntries);
                String manifestContent = manifest.serialize();
                Path manifestPath = mirrorUpdates.resolve("manifest.json");
                Files.createDirectories(manifestPath.getParent());
                Files.writeString(manifestPath, manifestContent);
                RepositoryFTP.upload(repoData.netRepo, List.of(new FileToUpload(manifestPath, "manifest.json")));

                FilesHelper.deleteRecursively(mirrorUpdates);

                repoData.netRepo.updateRepositoryInfo();
            }
            catch(Exception ex)
            {
                throw task.failWithError("Upload Failed", ex.getMessage(), ex);
            }
        });
    }
}
