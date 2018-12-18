package com.polydes.repman;

import org.apache.log4j.Logger;

/**
 * Save the original String Version for the toString method.
 */
public class ShortVersion extends Version
{
	private static final Logger log = Logger.getLogger(ShortVersion.class);
	
	private String fromString;
	
	public ShortVersion(String version)
	{
		super(version);
		fromString = version;
	}
	
	public static Version parseVersion(String version)
	{
		return valueOf(version);
	}
	
	public static Version parseVersion(String version, String defaultVersion)
	{
		return valueOf(version, new ShortVersion(defaultVersion));
	}

	public static Version valueOf(String version)
	{
		return valueOf(version, emptyVersion);
	}
	
	public static Version valueOf(String version, Version defaultVersion)
	{
		try
		{
			version = version.trim();
			if (version.length() == 0)
			{
				return defaultVersion;
			}
	
			return new ShortVersion(version);
		}
		catch(Exception ex)
		{
			log.warn("Couldn't parse version string '" + version + "'. Defaulting to '" + defaultVersion + "'.");
			return defaultVersion;
		}
	}

	@Override
	public String toString()
	{
		return fromString;
	}
}
