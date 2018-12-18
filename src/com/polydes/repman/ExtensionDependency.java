package com.polydes.repman;

import java.util.Locale;

public class ExtensionDependency
{
	public Type type;
	public String id;
	public Version version;
	
	public ExtensionDependency(Type type, String id, Version version)
	{
		this.type = type;
		this.id = id;
		this.version = version;
	}
	
	public static ExtensionDependency[] fromStringList(String s, Type defaultType)
	{
		if(s.isEmpty())
			return new ExtensionDependency[] {};
		else
		{
			String[] depStrings = s.split(",");
			ExtensionDependency[] dependencies = new ExtensionDependency[depStrings.length];
			for(int i = 0; i < depStrings.length; ++i)
				dependencies[i] = fromString(depStrings[i], defaultType);
			return dependencies;
		}
	}
	
	public static ExtensionDependency fromString(String s, Type defaultType)
	{
		String[] parts = s.split("-");
		String depType = parts[0];
		switch(depType)
		{
			case "java":
				return new ExtensionDependency(Type.JAVA, "", new Version(s.substring("java-".length())));
			case "stencyl":
				return new ExtensionDependency(Type.STENCYL, "", new Version(s.substring("stencyl-".length())));
			case "engine":
			case "toolset":
				return new ExtensionDependency(Type.fromString(parts[0]), parts[1], new Version(parts[2]));
			default:
				return new ExtensionDependency(defaultType, parts[0], new Version(parts[1]));
		}
	}
	
	@Override
	public String toString()
	{
		if(!id.isEmpty())
			return type.toString().toLowerCase(Locale.ENGLISH) + "-" + id + "-" + version;
		else
			return type.toString().toLowerCase(Locale.ENGLISH) + "-" + version;
	}
	
	public boolean meetsDependency(ExtensionDependency dep)
	{
		return type == dep.type && id.equals(dep.id) && version.compareTo(dep.version) >= 0;
	}
	
	public String getSimpleName()
	{
		switch(type)
		{
			case JAVA:
				return "Java " + version.getMinor();
			case STENCYL:
				return "Stencyl " + version;
			case TOOLSET:
				return "Toolset Extension " + id + " " + version;
			case ENGINE:
				return "Engine Extension " + id + " " + version;
			case HAXELIB:
				return "Haxelib " + id + " " + version;
		}
		return "";
	}
	
	public String getVersionlessIdentifier()
	{
		return type.toString().toLowerCase(Locale.ENGLISH) + "-" + id;
	}
	
	public static enum Type
	{
		JAVA,
		STENCYL,
		TOOLSET,
		ENGINE,
		HAXELIB;
		
		public static Type fromString(String s)
		{
			return valueOf(s.toUpperCase(Locale.ENGLISH));
		}
		
		public ExtensionType toExtensionType()
		{
			switch(this)
			{
				case TOOLSET:
					return ExtensionType.TOOLSET;
				case ENGINE:
					return ExtensionType.ENGINE;
				default:
					return null;
			}
		}
	}
}