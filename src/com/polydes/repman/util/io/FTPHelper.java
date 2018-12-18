package com.polydes.repman.util.io;
import com.jcraft.jsch.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

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

	public void transfer(boolean storeFile, boolean binaryTransfer, String remote, String local)
	{
		if(isSFTP())
		{
			try
			{
				if(storeFile)
					sftpChannel.put(local, remote);
				else
					sftpChannel.get(remote, local);
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
					}
				}
				else
				{
					try(OutputStream output = new FileOutputStream(local))
					{
						ftp.retrieveFile(remote, output);
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
