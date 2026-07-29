package com.polydes.repman.data;

import org.apache.log4j.Logger;
import org.json.JSONException;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalRepository
{
    private String url;
    private Path path;
    private RepositoryManifest manifest;
    private Map<String, NetExtension> packages;

    public LocalRepository(String url, Path path) {
        this.url = url;
        this.path = path;
    }

    public String getUrl() {
        return url;
    }

    public Path getPath() {
        return path;
    }

    public RepositoryManifest getManifest() {
        return manifest;
    }

    public Map<String, NetExtension> getPackages() {
        return packages;
    }

    public void loadFromDisk() throws IOException
    {
        String manifestContent = Files.readString(path.resolve("manifest.json"));
        manifest = RepositoryManifest.parse(manifestContent);
        packages = new HashMap<>();
        Path extensionsPath = path.resolve("extensions");
        for(String id : extensionsPath.toFile().list())
        {
            loadExtension(id, extensionsPath.resolve(id));
        }
    }

    public void loadExtension(String id, Path extRoot) throws IOException
    {
        NetExtension ext = new NetExtension(id);
        ext.loadInfo(extRoot, extRoot.resolve("info.json"));

        ext.repository = url;
        Path versionsPath = extRoot.resolve("versions.json");
        ext.versions = ExtensionInfo.readVersions(versionsPath);

        packages.put(id, ext);
    }
}
