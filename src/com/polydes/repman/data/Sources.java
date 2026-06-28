package com.polydes.repman.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.ui.RepoTree.ExtData;
import com.polydes.repman.util.maven.VersionedPlatformDependencySupplier;
import stencyl.core.api.struct.NotifierHashMap;
import stencyl.core.api.struct.NotifierMap;
import stencyl.core.api.tasks.Task;
import stencyl.core.ext.ExtensionBuilder;
import com.polydes.repman.ui.RepmanMain;
import com.polydes.repman.util.Zip;
import stencyl.core.api.Version;
import stencyl.core.ext.ExtensionDependency;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.ExtensionVersion;
import stencyl.core.util.JavaCompiler;
import stencyl.core.util.JavaCompiler.*;
import stencyl.core.util.javac.FileDependencySupplier;
import stencyl.core.util.javac.MavenDependencySupplier;

public class Sources
{
	public record Repository(String url, Path cachePath, NotifierMap<String, LocalSource> sources) {}

	private static final NotifierMap<String, Repository> repos = new NotifierHashMap<>();
	private static boolean loaded = false;

	public static boolean isLoaded()
	{
		return loaded;
	}

	public static NotifierMap<String, Repository> getSources()
	{
		return repos;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void loadSources()
	{
		loaded = true;
		
		try
		{
			YamlReader reader = new YamlReader(new FileReader(RepmanMain.REPMAN_DIR + File.separator + "sources.yml"));
			List repositories = (List) ((Map) reader.read()).get("repositories");
		    for(Object repo : repositories)
		    {
		    	Map map = (Map) repo;
		    	String url = (String) map.get("url");
				Path cachePath = Path.of((String) map.get("cache"));

				Map<String,String> extensionMap = (Map) map.get("extensions");
				NotifierMap<String,LocalSource> sources = new NotifierHashMap<>();

				for(var entry : extensionMap.entrySet()) {
					sources.put(entry.getKey(), new LocalSource(Path.of(entry.getValue())));
				}

		    	repos.put(url, new Repository(url, cachePath, sources));
		    }
		}
		catch(FileNotFoundException | YamlException e)
		{
			e.printStackTrace();
			for(var key : List.copyOf(repos.keySet()))
				repos.remove(key);
			loaded = false;
		}
	}

	public static void buildSource(Task task, ExtData ext, Consumer<ExtensionVersion> callback) throws Exception
	{
		if(ext.localExt == null || !Files.exists(ext.localExt.getPath()))
			throw new Exception("Invalid source folder.");
		if(!ext.localExt.isLoaded())
			throw new Exception("Source folder not loaded.");

		ExtensionInfo info = ext.localExt.getInfo();

		ExtensionDependency[] deps = info.getDependencies();
		Version version = info.getVersion();
		ExtensionRepository repository = RepmanMain.instance.getErm().getRepositories().get(info.getRepository());
		Path dest = repository.getVersionLocalLocation(info.getID(), version);

		if(!info.isToolsetDistributedAsJar())
		{
			//zip folder
			Files.createDirectories(dest.getParent());
			Zip.zipProject(task, ext.localExt.getPath(), dest);
		}
		else
		{
			//build jar

			//TODO: support extension dependencies

			var platformDeps = new VersionedPlatformDependencySupplier(info.getStencylTargetVersion());
			var mavenDeps = new MavenDependencySupplier(List.of(MavenDependencySupplier.MAVEN_CENTRAL));
			//var extensionDeps = new ExtensionJavaDependencySupplier(globalManager, globalToolsetExtensionManager);
			var fileDeps = new FileDependencySupplier();

			Map<Class<? extends Dependency>, DependencySupplier> compilerDependencySuppliers = Map.of(
					PlatformDependency.class, platformDeps,
					MavenDependency.class, mavenDeps,
			//		JavaCompiler.ExtensionDependency.class, extensionDeps,
					FileDependency.class, fileDeps
			);

			try
			{
				JavaCompiler.Project project = ExtensionBuilder.readFromExtensionInfo(info);
				JavaCompiler.runCompiler(task, project, compilerDependencySuppliers);
			}
			catch(IOException ex)
			{
				throw task.failWithError("Failed to build .jar", ex.getMessage(), ex);
			}

			//TODO: UPDATE EXPECTED OUTPUT PATH AND MANIFEST INFO
			Path outJar = Path.of("extensions", info.getID() + ".jar");

			Files.createDirectories(dest.getParent());
			//TODO: CORRECT OUTPUT FOR ENGINE+TOOLSET OR TOOLSET-ONLY
			Zip.zipFile(task, outJar, dest);
		}

		ExtensionVersion newVersion = new ExtensionVersion(version, deps);
		callback.accept(newVersion);
	}
}
