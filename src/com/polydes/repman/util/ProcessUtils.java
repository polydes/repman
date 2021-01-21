package com.polydes.repman.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtils
{
	public static String runCommand(File workingDir, String command, String... args)
	{
		String[] commands = new String[args.length + 1];
		
		commands[0] = command;
		for(int i = 0; i < args.length; ++i)
			commands[i + 1] = args[i];
		
		return runCommand(workingDir, commands);
	}
	
	public static String runCommand(File workingDir, String... commands)
	{
		Runtime rt = Runtime.getRuntime();
		Process proc = null;
		try
		{
			proc = rt.exec(commands, null, workingDir);
			
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			
			StringBuilder sb = new StringBuilder();
			String in = null;
			try
			{
				while ((in = stdInput.readLine()) != null)
					sb.append(in).append("\n");
				
				//Remove the last newline
				if(sb.length() > 0)
					sb.setLength(sb.length() - 1);
				
				return sb.toString();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		return null;
	}
}
