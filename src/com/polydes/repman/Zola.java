package com.polydes.repman;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.polydes.repman.data.LocalRepository;
import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.Prefs;
import com.polydes.repman.data.Sources.Repository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.log4j.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import com.polydes.repman.data.Sources;

import fsindexer.diff.TreeDiff;
import fsindexer.filter.PathFilter;
import fsindexer.tree.IndexedFileTree;
import stencyl.core.ext.ExtensionInfo.ExtensionCategory;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;
import stencyl.core.ext.net.RepositoryManifest.ArtifactEntry;
import stencyl.core.ext.net.RepositoryManifest.PackageEntry;

public class Zola
{
	private static final Logger log = Logger.getLogger(Zola.class);
	
	private static final String CONTENT = "content";
	private static final String STATIC = "static";
	
	private static boolean serving = false;

	private static void prepareZolaInputs(Map<String, LocalRepository> builtRepositories) throws Exception
	{
		log.info("Prepare to build site");

		Path site = Path.of(Prefs.get(Prefs.SITE_PATH));
		Path siteZola = site.resolve("zola");
		Path build = site.resolve("build");
		Path buildZola = build.resolve("zola-stage");
		Path buildZolaFinal = build.resolve("zola");

		if(Files.exists(buildZola))
		{
			FileUtils.deleteDirectory(buildZola.toFile());
		}
		Files.createDirectories(buildZola);
		Files.createDirectories(buildZolaFinal);
		Files.createDirectories(buildZola.resolveSibling("indices"));

		FileUtils.copyDirectory(siteZola.toFile(), buildZola.toFile());

		for(var repo : builtRepositories.values())
		{
			String repoId = StringUtils.substringAfterLast(repo.getUrl(), "/");
			FileUtils.copyDirectory(repo.getPath().toFile(), buildZola.resolve(STATIC, repoId, "v4").toFile());
			buildCategory(repo, buildZola);
		}

		//build/zola-gen.index (index of zola files built by us but not being served)
		Path zolaGenIndexPath = build.resolve("zola-gen.index");
		//build/zola-serve.index (index of zola files that are currently being served)
		Path zolaServeIndexPath = build.resolve("zola-serve.index");

		var zolaGenTree = IndexedFileTree.load(buildZola, zolaGenIndexPath);
		var zolaServeTree = IndexedFileTree.loadWithFilter(buildZolaFinal, zolaServeIndexPath, PathFilter.fromExcludePredicate(s -> s.equals("public")));

		TreeDiff diff = TreeDiff.compare(zolaGenTree, zolaServeTree);

		var dirsToDelete = new ArrayList<Path>();
		for(var result : diff.results.values())
		{
			Path buildPath = buildZola.resolve(result.path);
			Path servePath = buildZolaFinal.resolve(result.path);
			log.info(result.change + ": " + result.path);
			switch(result.change)
			{
				case REMOVED, UPDATED -> {
					if(Files.isDirectory(buildPath)) {
						Files.createDirectories(servePath);
					}
					else {
						Files.createDirectories(servePath.getParent());
						Files.copy(buildPath, servePath, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				case ADDED -> {
					if(Files.isDirectory(servePath))
						dirsToDelete.add(servePath);
					else
						Files.delete(servePath);
				}
			}
		}
		Collections.reverse(dirsToDelete);
		for(Path path : dirsToDelete)
		{
			Files.delete(path);
		}
	}

	public static void serveSite(Map<String, LocalRepository> builtRepositories) throws Exception
	{
		prepareZolaInputs(builtRepositories);

		Path site = Path.of(Prefs.get(Prefs.SITE_PATH));
		Path siteZola = site.resolve("zola");
		Path build = site.resolve("build");
		Path buildZola = build.resolve("zola-stage");
		Path buildZolaFinal = build.resolve("zola");
		Path zolaExe = Path.of(Prefs.get(Prefs.ZOLA_BIN));

		if(!serving)
		{
			serving = true;

			ProcessBuilder pb = new ProcessBuilder(zolaExe.toString(), "serve");
			pb.directory(buildZolaFinal.toFile());
			pb.redirectOutput(Redirect.INHERIT);
			pb.redirectError(Redirect.INHERIT);
			pb.start();

			if(!liveTrees.containsKey("site"))
			{
				liveTrees.put("site", IndexedFileTree.load(siteZola, buildZola.resolveSibling("indices").resolve("zola-site.index")));
			}

			new Timer("Tree-Watcher").schedule(new TimerTask(){

				@Override
				public void run()
				{
					boolean newChanges = false;

					try
					{
//						var pathEntries = livePaths.entrySet().iterator();
//						while(pathEntries.hasNext())
//						{
//							var pathEntry = pathEntries.next();
//
//							if(Files.exists(pathEntry.getValue()))
//							{
//								Path indexPath = buildZola.resolveSibling("indices").resolve(pathEntry.getKey() + "-docs.index");
//								IndexedFileTree tree = IndexedFileTree.load(pathEntry.getValue(), indexPath);
//								liveTrees.put(pathEntry.getKey(), tree);
//								pathEntries.remove();
//								newChanges = true;
//							}
//						}

						for(var tree : liveTrees.values())
						{
							String hash = tree.get("").getHash();
							tree.rescan();
							if(!tree.get("").getHash().equals(hash))
							{
								newChanges = true;
							}
						}

						if(newChanges)
						{
							prepareZolaInputs(builtRepositories);
						}
					}
					catch(Exception ex)
					{
						log.error(ex.getMessage(), ex);
					}
				}

			}, 500, 500);
		}
	}

	public static void buildSite(Map<String, LocalRepository> builtRepositories) throws Exception
	{
		log.info("Build site");

		prepareZolaInputs(builtRepositories);

		Path site = Path.of(Prefs.get(Prefs.SITE_PATH));
		Path build = site.resolve("build");
		Path buildZolaFinal = build.resolve("zola");
		Path zolaExe = Path.of(Prefs.get(Prefs.ZOLA_BIN));

		ProcessBuilder pb = new ProcessBuilder(zolaExe.toString(), "build");
		pb.directory(buildZolaFinal.toFile());
		pb.redirectOutput(Redirect.INHERIT);
		pb.redirectError(Redirect.INHERIT);
		pb.start();
	}
	
	private static Map<String, IndexedFileTree> liveTrees = new HashMap<>();
//	private static Map<String, Path> livePaths = new HashMap<>();
	
	private static void buildCategory(LocalRepository repo, Path buildZola) throws Exception
	{
		String repoId = StringUtils.substringAfterLast(repo.getUrl(), "/");

		Map<String, PackageEntry> pkgEntries = new HashMap<>();
		for(var pkgEntry : repo.getManifest().entries())
		{
			pkgEntries.put(pkgEntry.id(), pkgEntry);
		}

		buildCategoryIndex(repoId, buildZola);
		for(var extension : repo.getPackages().values())
		{
			buildDocsForExtension(extension, pkgEntries.get(extension.id), repoId, buildZola);
		}
	}
	
	public static void buildCategoryIndex(String repoId, Path buildZola) throws Exception
	{
		log.info("  Build index for category: " + repoId + "-" + "extensions");
		
		Map<String, String> replacements = Map.of(
			"title", "Extensions",
			"repo", repoId
		);
		
		String output = """
						+++
						title = "${title}"
						template = "category-landing.html"
						+++
						""";
		
		output = StrSubstitutor.replace(output, replacements);

		Path categoryIndex = buildZola.resolve(CONTENT).resolve(repoId, "extensions", "_index.md");
		Files.createDirectories(categoryIndex.getParent());
		Files.writeString(categoryIndex, output.toString());
	}
	
	private static interface DocumentProcessor
	{
		void process(TomlParseResult frontmatter, String content, StringBuilder sb);
	}
	
	private static String processDocument(Path documentPath, DocumentProcessor processor) throws IOException
	{
		TomlParseResult frontmatter = null;
		String content = null;
		
		String input = Files.notExists(documentPath) ? "" : Files.readString(documentPath);
		String newline = input.contains("\r\n") ? "\r\n" : "\n";
		if(input.startsWith("+++" + newline))
		{
			Matcher frontmatterDelimiterMatcher = Pattern.compile("\\r?\\n\\+\\+\\+(\\r?\\n)?").matcher(input);
			if(frontmatterDelimiterMatcher.find())
			{
				int start = frontmatterDelimiterMatcher.start();
				int end = frontmatterDelimiterMatcher.end();
				
				if(start > 3+newline.length())
				{
					frontmatter = Toml.parse(input.substring(3+newline.length(), start));
					if(!frontmatter.errors().isEmpty())
					{
						log.error("    Encountered errors parsing frontmatter for: " + documentPath);
						frontmatter.errors().forEach(error -> log.error("      " + error.toString())); //do toml errors fit on one line?
						frontmatter = null;
					}
				}
				
				content = input.substring(end);
			}
		}
		else
		{
			log.error("    No front matter found for: " + documentPath);
			content = input;
		}
		
		StringBuilder sb = new StringBuilder();
		processor.process(frontmatter, content, sb);
		return sb.toString();
	}
	
	public static void buildDocsForExtension(NetExtension ext, RepositoryManifest.PackageEntry pkgEntry, String repoId, Path buildZola) throws Exception
	{
		log.info("  Build docs for extension: " + ext.id);

		if(!(Sources.getSources().get(ext.repository) instanceof Repository repository))
		{
			log.error("    No Source found for repository: " + repoId);
			return;
		}
		if(!(repository.sources().get(ext.id) instanceof LocalSource localSource))
		{
			log.error("    No Source found for extension: " + ext.id);
			return;
		}
		if(ext.name.equals("[" + ext.id + "]"))
		{
			log.error("    Dummy extension: " + ext.cat.getCategoryName() + "-" + ext.id);
			return;
		}
		Path extensionSource = localSource.getPath();
		Path docsSource = extensionSource.resolve("site");

		String oldCat = ext.cat == ExtensionCategory.GAME ? "engine" : "toolset";
		String newCat = "extensions";
		String category = newCat;

//		String oldApi = "v3";
//		String newApi = "v4";
//		String apiV = newApi;

		Path extensionOutputFolder = buildZola.resolve(CONTENT, repoId, category, ext.id);
		Files.createDirectories(extensionOutputFolder);
		
		MutableBoolean wroteLandingPage = new MutableBoolean(false);
		MutableBoolean wroteSidebarPage = new MutableBoolean(false);
		MutableBoolean wroteInstallInstructions = new MutableBoolean(false);
		
		if(Files.exists(docsSource))
		{
			Files.walkFileTree(docsSource, new FileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
				{
					Files.createDirectories(extensionOutputFolder.resolve(docsSource.relativize(dir)));
					System.out.println("create " + extensionOutputFolder.resolve(docsSource.relativize(dir)));
					
					Path indexFile = dir.resolve("index.md");
					if(Files.exists(indexFile)) return FileVisitResult.CONTINUE;
					
					indexFile = dir.resolve("_index.md");
					if(Files.exists(indexFile)) return FileVisitResult.CONTINUE;
					
					String title = "";
					
					StringBuilder output = new StringBuilder();
					output.append("+++\n\n");
					
					output.append("title = \"").append(title).append("\"\n");
					output.append("page_template = \"extension-guide.html\"\n\n");
					
					output.append("+++\n");
					
					Path indexTarget = extensionOutputFolder.resolve(docsSource.relativize(indexFile));
					Files.writeString(indexTarget, output.toString());
					System.out.println("write " + extensionOutputFolder.resolve(docsSource.relativize(indexFile)));
					
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					String relative = docsSource.relativize(file).toString().replace(File.separatorChar, '/');
					Path writeTo = extensionOutputFolder.resolve(relative);
					
					if(relative.equals("install.md") || relative.equals("install/index.md"))
					{
						String installPage = processDocument(file,
								(fm, content, sb) -> writeInstallationDocument(fm, content, sb, ext));
						
						Files.writeString(writeTo, installPage);
						System.out.println("write " + writeTo);
						wroteInstallInstructions.setTrue();
					}
					else if(relative.equals("_index.md"))
					{
						String landingPage = processDocument(file,
								(fm, content, sb) -> processLandingPage(fm, content, sb, ext));
						
						Files.writeString(writeTo, landingPage);
						System.out.println("write " + writeTo);
						wroteLandingPage.setTrue();
					}
					else if(relative.equals("_sidebar.md"))
					{
						String sidebarPage = processDocument(file,
								(fm, content, sb) -> processSidebarPage(fm, content, sb, docsSource));
						
						Files.writeString(writeTo, sidebarPage);
						System.out.println("write " + writeTo);
						wroteSidebarPage.setTrue();
					}
					else
					{
						Files.copy(file, writeTo, StandardCopyOption.REPLACE_EXISTING);
						System.out.println("copy to " + writeTo);
					}
					
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException
				{
					throw exc;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
				{
					if(exc != null) throw exc;
					return FileVisitResult.CONTINUE;
				}
			});
		}
		
		StringBuilder output = new StringBuilder();
		output.append("+++\n\n");
		
		output.append("title = \"").append(ext.name + " Versions").append("\"\n");
		output.append("template = \"extension-versions.html\"\n\n");
		
		output.append("+++\n\n");

		Map<String, ArtifactEntry> artifactEntryMap = new HashMap<>();
		for(var artifact : pkgEntry.artifacts())
			artifactEntryMap.put(artifact.version(), artifact);

		int currentMajor = Integer.MAX_VALUE;

		for(var change : localSource.getChanges())
		{
			String version = change.version().toString();
			if(currentMajor > change.version().getMajor())
			{
				currentMajor = change.version().getMajor();
				output.append("#### ").append(currentMajor).append(".x").append("\n\n");
			}
			output.append("##### ");
			if(artifactEntryMap.get(version) instanceof ArtifactEntry artifactEntry)
				output.append("[").append(version).append("](").append(artifactEntry.url()).append(")");
			else
				output.append(version);
			output.append(" ").append(change.label()).append(" {#").append(version).append("}");
			output.append("\n\n").append(change.body());

			output.append("\n\n");
		}

		Path extensionVersionsMd = extensionOutputFolder.resolve("versions.md");
		Files.writeString(extensionVersionsMd, output.toString());
		System.out.println("write " + extensionVersionsMd);
		
		if(wroteLandingPage.isFalse())
		{
			StringBuilder sb = new StringBuilder();
			processLandingPage(null, "", sb, ext);
			Path extensionLandingMd = extensionOutputFolder.resolve("_index.md");
			Files.writeString(extensionLandingMd, sb.toString());
			System.out.println("write " + extensionLandingMd);
		}
		
		if(wroteInstallInstructions.isFalse())
		{
			StringBuilder sb = new StringBuilder();
			writeInstallationDocument(null, "", sb, ext);
			Path extensionInstallationMd = extensionOutputFolder.resolve("install.md");
			Files.writeString(extensionInstallationMd, sb.toString());
			System.out.println("write " + extensionInstallationMd);
		}
		
		if(wroteSidebarPage.isFalse())
		{
			output = new StringBuilder();
			output.append("+++\n");
			output
					.append("[extra]\n")
					.append("exclude_from_sitemap = true\n\n");
			output.append("+++\n");
			
			Files.writeString(extensionOutputFolder.resolve("_sidebar.md"), output.toString());
			System.out.println("write " + extensionOutputFolder.resolve("_sidebar.md"));
		}
		
		String extensionKey = extensionSource.getParent().getParent()
				.relativize(extensionSource)
				.toString().replace(File.separatorChar, '-');
		
		if(!liveTrees.containsKey(extensionKey)/* && !livePaths.containsKey(extensionKey)*/)
		{
			if(Files.exists(docsSource))
			{
				IndexedFileTree tree = IndexedFileTree.load(docsSource, buildZola.resolveSibling("indices").resolve(extensionKey + "-docs.index"));
				liveTrees.put(extensionKey, tree);
			}
//			else
//			{
//				livePaths.put(extensionKey, docsSource);
//			}
		}
	}
	
	private static void processLandingPage(TomlParseResult frontmatter, String content, StringBuilder sb, NetExtension ext)
	{
		String blurb = frontmatter == null ? ext.description : frontmatter.getString("extra.blurb");
		String repoId = StringUtils.substringAfterLast(ext.repository, "/");
		
		sb.append("+++\n\n");
		
		sb.append("title = \"").append(ext.name).append("\"\n");
		sb.append("description = \"").append(ext.description).append("\"\n");
		sb.append("template = \"extension-landing.html\"\n");
		sb.append("page_template = \"extension-guide.html\"\n\n");
		
		sb.append("[extra]\n");
		sb.append("base_url = \"/"+repoId+"/v4/extensions/").append(ext.id).append("\"\n\n");
		sb.append("extension_id = \"").append(ext.id).append("\"\n");
		sb.append("extension_name = \"").append(ext.name).append("\"\n");
		sb.append("extension_description = \"").append(ext.description).append("\"\n");
		sb.append("extension_author = \"").append(ext.author).append("\"\n");
		sb.append("extension_type = \"").append(ext.cat.getCategoryName()).append("\"\n");
		sb.append("extension_tags = [").append(ext.tags.stream().collect(Collectors.joining(", ","\"","\""))).append("]\n");
		sb.append("blurb = \"").append(replaceQuotes(blurb)).append("\"\n\n");
		
		if(frontmatter != null)
		{
			var urls = frontmatter.getArrayOrEmpty("extra.urls");
			for(int i = 0; i < urls.size(); ++i)
			{
				var table = urls.getTable(i);
				sb.append("\t[[extra.urls]]\n");
				sb.append("\tlabel = \"").append(table.getString("label")).append("\"\n");
				sb.append("\tlink = \"").append(table.getString("link")).append("\"\n\n");
			}
		}
		
		sb.append("+++\n");
		
		sb.append(content);
	}
	
	private static void writeInstallationDocument(TomlParseResult frontmatter, String content, StringBuilder sb, NetExtension ext)
	{
		String title = null;
		if(frontmatter != null) title = frontmatter.getString("title");
		if(title == null) title = "Installing " + ext.name;
		
		sb.append("+++\n\n");
		
		sb.append("title = \"").append(title).append("\"\n");
		sb.append("template = \"extension-install-instructions.html\"\n\n");
		
		sb.append("+++\n");
		
		sb.append(content);
	}
	
	record SidebarPageLink(String page) implements SidebarLink {};
	record SidebarAbsoluteLink(String title, String link) implements SidebarLink {};
	record SidebarRelativeLink(String title, String link) implements SidebarLink {};
	interface SidebarLink {};
	private static void writeLink(SidebarLink link, StringBuilder sb)
	{
		if(link instanceof SidebarPageLink pageLink)
		{
			sb.append("\tpage = \"").append(pageLink.page()).append("\"\n");
		}
		else if(link instanceof SidebarAbsoluteLink absLink)
		{
			sb.append("\ttitle = \"").append(absLink.title()).append("\"\n");
			sb.append("\tabsolute = \"").append(absLink.link()).append("\"\n");
		}
		else if(link instanceof SidebarRelativeLink relLink)
		{
			sb.append("\ttitle = \"").append(relLink.title()).append("\"\n");
			sb.append("\trelative = \"").append(relLink.link()).append("\"\n");
		}
	}
	
	private static void processSidebarPage(TomlParseResult frontmatter, String content, StringBuilder sb, Path docsSource)
	{
		sb.append("+++\n\n");

		sb
			.append("[extra]\n")
			.append("exclude_from_sitemap = true\n\n");
		
		List<String> lines = readAllLines(content);
		Map<String, List<SidebarLink>> links = new LinkedHashMap<>();
		
		List<SidebarLink> currentBlock = null;
		
		for(String line : lines)
		{
			if(currentBlock == null && !line.isEmpty())
			{
				currentBlock = new ArrayList<>();
				links.put(line, currentBlock);
				continue;
			}
			
			if(currentBlock != null && line.isEmpty())
			{
				currentBlock = null;
				continue;
			}
			
			if(line.contains("[[") && line.contains("]]"))
			{
				String page = StringUtils.substringBetween(line, "[[", "]]");
				if(Files.exists(docsSource.resolve(page+".md")))
					currentBlock.add(new SidebarPageLink(page));
				else if(Files.exists(docsSource.resolve(page+"/index.md")))
					currentBlock.add(new SidebarPageLink(page+"/index"));
				else
					throw new RuntimeException("No page named: " + page);
			}
			else if(line.contains("[") && line.contains("]"))
			{
				String title = StringUtils.substringBetween(line, "[", "]");
				String link = StringUtils.substringBetween(line, "(", ")");
				currentBlock.add(link.startsWith("http") ?
					new SidebarAbsoluteLink(title, link) :
					new SidebarRelativeLink(title, link));
			}
		}
		
		List<SidebarLink> mainBlock = links.remove("_MAIN_");
		
		int i = 1;
		for(String group : links.keySet())
		{
			sb.append("[[extra.nav.sections]]\n");
			sb.append("    group = \"group" + i++ + "\"\n");
			sb.append("    title = \"" + group + "\"\n\n");
		}
		
		if(mainBlock != null)
		{
			for(SidebarLink sl : mainBlock)
			{
				sb.append("[[extra.nav.section.zero]]\n");
				writeLink(sl, sb);
			}
		}
		
		i = 1;
		for(var linkListEntry : links.entrySet())
		{
			for(SidebarLink sl : linkListEntry.getValue())
			{
				sb.append("[[extra.nav.section.group" + i + "]]\n");
				writeLink(sl, sb);
			}
			++i;
		}
		
		sb.append("\n+++\n");
	}
	
	private static List<String> readAllLines(String string)
	{
		try(
			StringReader sreader = new StringReader(string);
			BufferedReader reader = new BufferedReader(sreader);
		) {
            List<String> result = new ArrayList<>();
            for (;;) {
                String line = reader.readLine();
                if (line == null)
                    break;
                result.add(line);
            }
            return result;
        }
		catch (IOException e)
		{
			//will never happen
			throw new RuntimeException(e);
		}
	}
	
	private static String replaceQuotes(String input)
	{
		return input.replaceAll(Pattern.quote("\""), Matcher.quoteReplacement("\\\""));
	}
}
