package com.polydes.repman.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtils
{
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
	
	public static int runCommandResult(File workingDir, String... commands) throws IOException
	{
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.directory(workingDir);
		pb.inheritIO();
		Process p = pb.start();
		try
		{
			return p.waitFor();
		}
		catch (InterruptedException e)
		{
			return -1;
		}
	}
}
