package com.polydes.repman.util.io;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class FTPHelper
{
	String server;
	String username;
	String password;
	
	FTPConnectionType connectionType;
	
	//apache net FTP / FTPS
	FTPClient ftp;
	
	//jsch sftp
	JSch jsch;
	Session session;
	ChannelSftp sftpChannel;
	
	//cache for performance
	HashSet<String> confirmedFolders = new HashSet<>();
	
	public enum FTPConnectionType
	{
		FTP,
		FTPS,
		SFTP
	}
	
	@SuppressWarnings("unused")
	private boolean isFTP()
	{
		return connectionType == FTPConnectionType.FTP;
	}
	
	private boolean isFTPS()
	{
		return connectionType == FTPConnectionType.FTPS;
	}
	
	private boolean isSFTP()
	{
		return connectionType == FTPConnectionType.SFTP;
	}
	
	public FTPHelper(String server, FTPConnectionType connectionType, String username, String password)
	{
		this.server = server;
		this.username = username;
		this.password = password;
		this.connectionType = connectionType;
		
		if(isSFTP())
		{
			jsch = new JSch();
			session = null;
			try
			{
				session = jsch.getSession(username, server, 22);
				session.setConfig("StrictHostKeyChecking", "no");
				session.setPassword(password);
				session.connect();

				Channel channel = session.openChannel("sftp");
				channel.connect();
				sftpChannel = (ChannelSftp) channel;
			}
			catch(JSchException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			ftp = isFTPS() ? new FTPSClient() : new FTPClient();
			FTPClientConfig config = new FTPClientConfig();
			ftp.configure(config);
			
			// suppress login details
			ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));
			
			try
			{
				int reply;
				ftp.connect(server);
				System.out.println("Connected to " + server + ".");
				System.out.print(ftp.getReplyString());

				reply = ftp.getReplyCode();

				if(!FTPReply.isPositiveCompletion(reply))
				{
					ftp.disconnect();
					System.err.println("FTP server refused connection.");
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public void disconnect()
	{
		if(isSFTP())
		{
			System.out.println("Disconnect SFTP");
			sftpChannel.exit();
			session.disconnect();
		}
		else
		{
			if(ftp.isConnected())
			{
				try
				{
					ftp.disconnect();
				}
				catch(IOException ioe)
				{
				}
			}
		}
	}
	
	private boolean ensureFolderExists(String remote)
	{
		if(doesSingleFolderExist(remote)) return true;
		
		int lastSeparator = remote.indexOf("/");
		while(lastSeparator != -1)
		{
			if(lastSeparator != 0)
			{
				if(!ensureSingleFolderExists(remote.substring(0, lastSeparator)))
					break;
			}
			lastSeparator = remote.indexOf("/", lastSeparator + 1);
		}
		
		return ensureSingleFolderExists(remote);
	}
	
	private boolean doesSingleFolderExist(String remote)
	{
		if(confirmedFolders.contains(remote)) return true;
		
		if(isSFTP())
		{
			try
			{
				sftpChannel.cd(remote);
				confirmedFolders.add(remote);
				return true;
			}
			catch(SftpException e)
			{
				return false;
			}
		}
		else
		{
			try
			{
				if(ftp.changeWorkingDirectory(remote))
				{
					confirmedFolders.add(remote);
					return true;
				}
				else
				{
					return false;
				}
			}
			catch(IOException e)
			{
				return false;
			}
		}
	}
	
	private boolean ensureSingleFolderExists(String remote)
	{
		if(confirmedFolders.contains(remote)) return true;
		
		if(isSFTP())
		{
			try
			{
				sftpChannel.cd(remote);
			}
			catch(SftpException e)
			{
				try
				{
					int separator = remote.lastIndexOf("/");
					sftpChannel.cd(remote.substring(0, separator));
					sftpChannel.mkdir(remote.substring(separator + 1));
				}
				catch(SftpException e1)
				{
					e1.printStackTrace();
				}
				
				try
				{
					sftpChannel.cd(remote);
				}
				catch(SftpException e1)
				{
					e1.printStackTrace();
					return false;
				}
				
				System.out.println("Created remote folder: " + remote);
			}
		}
		else
		{
			try
			{
				boolean existed = ftp.changeWorkingDirectory(remote);
				if(!existed)
				{
					int separator = remote.lastIndexOf("/");
					ftp.changeWorkingDirectory(remote.substring(0, separator));
					ftp.makeDirectory(remote.substring(separator + 1));
					
					if(!ftp.changeWorkingDirectory(remote))
						return false;
					
					System.out.println("Created remote folder: " + remote);
				}
			}
			catch(IOException ioe)
			{
				ioe.printStackTrace();
				return false;
			}
		}
		
		confirmedFolders.add(remote);
		return true;
	}

	public void transfer(boolean storeFile, boolean binaryTransfer, String remote, String local)
	{
		String remoteFolder = remote.substring(0, remote.lastIndexOf("/"));
		
		if(isSFTP())
		{
			try
			{
				if(storeFile)
				{
					ensureFolderExists(remoteFolder);
					sftpChannel.put(local, remote);
					System.out.println("Uploaded file to " + remote);
				}
				else
				{
					sftpChannel.get(remote, local);
					System.out.println("Downloaded file from " + remote);
				}
			}
			catch(SftpException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			try
			{
				if(!ftp.login(username, password))
				{
					ftp.logout();
					return;
				}
	
				System.out.println("Remote system is " + ftp.getSystemType());
	
				ensureFolderExists(remoteFolder);
				
				if(binaryTransfer)
				{
					ftp.setFileType(FTP.BINARY_FILE_TYPE);
				}
				else
				{
					ftp.setFileType(FTP.ASCII_FILE_TYPE);
				}
	
				ftp.enterLocalPassiveMode();
	
				if(storeFile)
				{
					try(InputStream input = new FileInputStream(local))
					{
						ftp.storeFile(remote, input);
						System.out.println("Uploaded file to " + remote);
					}
				}
				else
				{
					try(OutputStream output = new FileOutputStream(local))
					{
						ftp.retrieveFile(remote, output);
						System.out.println("Downloaded file from " + remote);
					}
				}
	
				ftp.noop(); // check that control connection is working OK
			}
			catch(FTPConnectionClosedException e)
			{
				System.err.println("Server closed connection.");
				e.printStackTrace();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					ftp.logout();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
