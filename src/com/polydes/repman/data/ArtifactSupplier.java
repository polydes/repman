package com.polydes.repman.data;

import stencyl.core.api.Version;
import stencyl.core.ext.net.RepositoryManifest.ArtifactEntry;

public interface ArtifactSupplier
{
    ArtifactEntry getArtifact(Version version);
}
