package com.polydes.repman.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import stencyl.core.api.tasks.Task;
import stencyl.core.io.ArchiveHelper;
import stencyl.core.io.ArchiveHelper.FileMap;
import stencyl.core.util.ProcessHelper;

public class Zip
{
	/*-------------------------------------*\
	 * Zip
	\*-------------------------------------*/ 

	public static void zipProject(Task task, Path projectRoot, Path destination) throws IOException
	{
		System.out.println("> zip " + projectRoot.toAbsolutePath() + " " + destination.toAbsolutePath());

		FileMap fileMap = new FileMap();

		if(Files.isDirectory(projectRoot))
		{
			if(Files.exists(projectRoot.resolve(".git")))
			{
				String output = task.io.buildCommand()
						.workingDir(projectRoot)
						.commandLine("git", "ls-tree", "-r", "HEAD", "--name-only")
						.output();
				Stream
						.of(output.split("(\r\n|\r|\n)"))
						.forEach(path -> {
							fileMap.addPath(path, projectRoot.resolve(path));
						});
			}
			else
			{
				try(Stream<Path> paths = Files.walk(projectRoot)) {
					paths
							.filter(Files::isRegularFile)
							.forEach(path -> {
								String entryName = projectRoot.relativize(path).toString().replace('\\', '/');
								fileMap.addPath(entryName, path);
							});
				}
			}
		}

		task.io.createZIPFile(destination.toAbsolutePath().toString(), fileMap);
	}

	public static void zipFile(Task task, Path source, Path destination) throws IOException
	{
		System.out.println("> zip " + source.toAbsolutePath() + " " + destination.toAbsolutePath());

		FileMap fileMap = new FileMap();

		fileMap.addPath(source.getFileName().toString(), source);

		task.io.createZIPFile(destination.toAbsolutePath().toString(), fileMap);
	}
	
	public static void unzip(File source, File destination) throws IOException
	{
		System.out.println("> unzip " + source.getAbsolutePath() + " " + destination.getAbsolutePath());

		ArchiveHelper.extract(source.toPath(), destination.toPath());
	}
}
