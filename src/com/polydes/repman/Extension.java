package com.polydes.repman;

import java.util.List;

import javax.swing.Icon;

import com.polydes.repman.ExtensionManifest.ExtensionCategory;
import com.polydes.repman.LocalRepoBackend.ExtensionVersion;

public class Extension
{
	public ExtensionType type;
	public String id;
	
	public Icon icon;
	public String name;
	public String description;
	public String author;
	public String website;
	public String repository;
	public List<ExtensionVersion> versions;
	
	//toolset extensions only
	public ExtensionCategory cat;
	
	public Extension(ExtensionType type, String id)
	{
		this.type = type;
		this.id = id;
	}
	
	public ExtensionVersion getVersion(Version v)
	{
		for(ExtensionVersion version : versions)
		{
			if(version.version.compareTo(v) >= 0)
				return version;
		}
		return null;
	}
	
	public String getSimpleName()
	{
		switch(type)
		{
			case ENGINE:
				return name + " (Engine)";
			case TOOLSET:
				return name + " (Toolset)";
		}
		
		return name;
	}
	
	@Override
	public String toString()
	{
		return name;
	}
	
	public String getInfo()
	{
		return "Extension [type=" + type + ", id=" + id + ", icon=" + icon + ", name=" + name + ", description="
				+ description + ", author=" + author + ", website=" + website + ", repository=" + repository
				+ ", versions=" + versions + ", cat=" + cat + "]";
	}
}
