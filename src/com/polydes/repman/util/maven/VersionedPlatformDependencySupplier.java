package com.polydes.repman.util.maven;

import stencyl.core.api.Version;
import stencyl.core.api.tasks.Task;
import stencyl.core.util.JavaCompiler;
import stencyl.core.util.JavaCompiler.*;
import stencyl.core.util.javac.MavenDependencySupplier;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class VersionedPlatformDependencySupplier implements DependencySupplier
{
    private static final Logger log = Logger.getLogger(VersionedPlatformDependencySupplier.class);

    private static final String STENCYL_MAVEN = "https://www.stencyl.com/dl/maven2/releases";

    private final Version stencylVersion;
    private final MavenDependencySupplier mavenDependencySupplier;
    private final Map<String,String> platformDepVersions;

    public VersionedPlatformDependencySupplier(Version stencylVersion) {
        this.stencylVersion = stencylVersion;
        mavenDependencySupplier = new MavenDependencySupplier(List.of(MavenDependencySupplier.MAVEN_CENTRAL, STENCYL_MAVEN));
        platformDepVersions = new HashMap<>();
    }

    @Override
    public void init(Task task, DependencyResolverContext ctx) {
        try {
            platformDepVersions.putAll(EnforcedPlatformBomResolver.resolvePlatformVersions(
                    Path.of(".m2"), STENCYL_MAVEN,
                    "com.stencyl", "stencyl-platform", stencylVersion.toString()
            ));
        } catch (Exception e) {
            throw task.failWithError(e);
        }
        mavenDependencySupplier.init(task, ctx);
        mavenDependencySupplier.addPath(task, new MavenDependency("com.stencyl", "stencyl", stencylVersion.toString(), null), ctx);
    }

    @Override
    public void addPath(Task task, Dependency dep, DependencyResolverContext ctx)
    {
        if(!(dep instanceof PlatformDependency(String group, String artifact)))
            throw new IllegalArgumentException();

        if(dep == JavaCompiler.FULL_PLATFORM)
        {
            for(var entry : platformDepVersions.entrySet())
            {
                String[] parts = entry.getKey().split(":");
                String entryGroup = parts[0], entryArtifact = parts[1], entryVersion = entry.getValue();
                mavenDependencySupplier.addPath(task, new MavenDependency(entryGroup, entryArtifact, entryVersion, null), ctx);
            }
            return;
        }
        if(platformDepVersions.get(group + ":" + artifact) instanceof String version)
        {
            mavenDependencySupplier.addPath(task, new MavenDependency(group, artifact, version, null), ctx);
            return;
        }

        log.error("Couldn't resolve path for platform dependency: " + group + ":"+ artifact);
    }
}
