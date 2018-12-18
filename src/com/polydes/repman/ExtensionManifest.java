package com.polydes.repman;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.polydes.repman.ExtensionDependency.Type;

public class ExtensionManifest
{
	private static final Logger log = Logger.getLogger(ExtensionManifest.class);
	
	public final File file;
	public final Icon icon;
	
	public final String id;
	public final String mainClass;
	public final Version version;
	public final ExtensionCategory category;
	
	public final ExtensionDependency[] dependencies;
	
	public final String name;
	public final String description;
	public final String authorName;
	public final String website;
	public final String repository;
	public final int internalVersion;
	
	public ExtensionManifest
	(
		File file, Icon icon,
		String id, String mainClass, Version version, ExtensionCategory type, ExtensionDependency[] dependencies,
		String name, String description, String authorName, String website, String repository, int internalVersion
	)
	{
		this.file = file;
		this.icon = icon;
		
		this.id = id;
		this.mainClass = mainClass;
		this.version = version;
		this.category = type;
		this.dependencies = dependencies;
		
		this.name = name;
		this.description = description;
		this.authorName = authorName;
		this.website = website;
		this.repository = repository;
		this.internalVersion = internalVersion;
	}
	
	public static ExtensionManifest fromJar(File f) throws IOException
	{
		try(JarFile jar = new JarFile(f))
		{
			Manifest manifest = jar.getManifest();
			Attributes attrs = manifest.getMainAttributes();
			
			String extensionName = FilenameUtils.getBaseName(f.getName());
			
			String id = getString(attrs, "Extension-ID", extensionName);
			String mainClass = getString(attrs, "Extension-Main-Class", extensionName);
			String version = getString(attrs, "Extension-Version", "0.0.0");
			String dependencies = getString(attrs, "Extension-Dependencies", "");
			String iconPath = getString(attrs, "Extension-Icon", "icon.png");
			String extensionType = getString(attrs, "Extension-Type", "normal"); //normal, game, library
			
			String name = getString(attrs, "Extension-Name", extensionName);
			String description = getString(attrs, "Extension-Description", "");
			String authorName = getString(attrs, "Extension-Author", "");
			String website = getString(attrs, "Extension-Website", "");
			String repository = getString(attrs, "Extension-Repository", "");
			int internalVersion = Integer.parseInt(getString(attrs, "Extension-Internal-Version", "1"));
			
			Icon extensionIcon = null;
			
			try
			{
				extensionIcon = new ImageIcon(ImageIO.read(jar.getInputStream(jar.getEntry(iconPath))));
			}
			catch (Exception e2)
			{
				extensionIcon = null;
				log.warn("Failed to load icon " + iconPath + " for extension " + id);
			}
			
			IOUtils.closeQuietly(jar);
			
			return new ExtensionManifest
			(
				f, extensionIcon,
				id, mainClass, new Version(version), ExtensionCategory.fromString(extensionType),
				ExtensionDependency.fromStringList(dependencies, Type.TOOLSET),
				name, description, authorName, website, repository, internalVersion
			);
		}
	}
	
	private static String getString(Attributes attrs, String key, String defaultValue)
	{
		String value = attrs.getValue(key);
		if(value == null)
		{
			value = defaultValue;
		}
		
		return value;
	}
	
	public static enum ExtensionCategory
	{
		NORMAL,
		GAME,
		JAVA_LIBRARY;
		
		public static ExtensionCategory fromString(String s)
		{
			return valueOf(s.toUpperCase(Locale.ENGLISH));
		}
	}
}