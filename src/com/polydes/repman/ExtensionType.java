package com.polydes.repman;

import java.util.Locale;

public enum ExtensionType
{
	TOOLSET,
	ENGINE;
	
	@Override
	public String toString()
	{
		return super.toString().toLowerCase(Locale.ENGLISH);
	}
	
	public ExtensionDependency.Type toExtensionDependencyType()
	{
		switch(this)
		{
			case TOOLSET:
				return ExtensionDependency.Type.TOOLSET;
			case ENGINE:
				return ExtensionDependency.Type.ENGINE;
			default:
				return null;
		}
	}
}