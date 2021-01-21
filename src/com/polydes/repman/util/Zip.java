package com.polydes.repman.util;

import static com.polydes.repman.util.ProcessUtils.runCommand;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;

public class Zip
{
	/*-------------------------------------*\
	 * Zip
	\*-------------------------------------*/ 
	
	public static void zip(File source, File destination)
	{
		System.out.println("> zip " + source.getAbsolutePath() + " " + destination.getAbsolutePath());
		
		Collection<File> fileList = null;
		if(source.isDirectory())
		{
			if(new File(source, ".git").exists())
			{
				final File projectRoot = source;
				String output = runCommand(source, "git", new String[] {"ls-tree", "-r", "HEAD", "--name-only"});
				fileList = Stream
					.of(output.split("(\r\n|\r|\n)"))
					.map(filepath -> new File(projectRoot, filepath))
					.collect(Collectors.toList());
			}
			else
			{
				fileList = FileUtils.listFiles(source, null, true);
			}
		}
		else
		{
			fileList = Arrays.asList(source);
			source = source.getParentFile();
		}
		
		try(ZipOutputStream zipFile = new ZipOutputStream(destination))
		{
			try
			{
				for (File file : fileList)
				{
					String entryName = getEntryName(source, file);
					ZipEntry entry = new ZipEntry(entryName);
					zipFile.putNextEntry(entry);
					
					try(BufferedInputStream input = new BufferedInputStream(new FileInputStream(file)))
					{
						IOUtils.copy(input, zipFile);
					}
					
					zipFile.closeEntry();
				}
			}
			finally
			{
				zipFile.finish();
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private static String getEntryName(File source, File file) throws IOException
	{
		int index = source.getAbsolutePath().length() + 1;
		String path = file.getCanonicalPath();

		return path.substring(index);
	}
	
	public static void unzip(File source, File destination)
	{
		System.out.println("> unzip " + source.getAbsolutePath() + " " + destination.getAbsolutePath());
		
		try
		{
			ZipFile zipFile = new ZipFile(source);
			try
			{
				Enumeration<? extends ZipEntry> entries = zipFile.getEntries();
				while (entries.hasMoreElements())
				{
					ZipEntry entry = entries.nextElement();
					File entryDestination = new File(destination, entry.getName());
					if (entry.isDirectory())
						entryDestination.mkdirs();
					else
					{
						entryDestination.getParentFile().mkdirs();
						try
						(
							InputStream in = zipFile.getInputStream(entry);
							OutputStream out = new FileOutputStream(entryDestination)
						)
						{
							IOUtils.copy(in, out);
							IOUtils.closeQuietly(in);
						}
					}
				}
			}
			finally
			{
				zipFile.close();
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
}
