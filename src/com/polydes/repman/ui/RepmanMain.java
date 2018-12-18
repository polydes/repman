package com.polydes.repman.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import org.apache.log4j.xml.DOMConfigurator;

import com.polydes.repman.ExtensionRepositoryManager;
import com.polydes.repman.data.Prefs;
import com.polydes.repman.util.Util;

public class RepmanMain extends JFrame
{
	public static void main(String[] args)
	{
		DOMConfigurator.configure("log4j.xml");
		new RepmanMain();
	}
	
	public static RepmanMain instance;
	
	ExtensionRepositoryManager erm;
	RepoTree rtree;
	ExtensionView view;
	
	public RepmanMain()
	{
		instance = this;
		
		erm = new ExtensionRepositoryManager();
		rtree = new RepoTree();
		erm.getRepositories().addListener(rtree.repoListener);
		
		view = new ExtensionView();
		rtree.getTree().addTreeSelectionListener(view);
		
		MiniSplitPane splitPane = new MiniSplitPane();
		splitPane.setLeftComponent(new JScrollPane(rtree));
		splitPane.setRightComponent(new JScrollPane(view));
		splitPane.setDividerLocation(Util.parseInt(Prefs.get(Prefs.SPLIT_PANE_WIDTH), 120));
		
		int width = Util.parseInt(Prefs.get(Prefs.WINDOW_WIDTH), 640);
		int height = Util.parseInt(Prefs.get(Prefs.WINDOW_HEIGHT), 480);
		setSize(width, height);
		add(splitPane, BorderLayout.CENTER);
		setVisible(true);
		
		new SwingWorker<String, Void>(){
			@Override
			protected String doInBackground() throws Exception
			{
				erm.loadRepositories();
				return null;
			}
		}.execute();
		
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowListener()
		{
			@Override
			public void windowOpened(WindowEvent e)
			{
				
			}
			
			@Override
			public void windowIconified(WindowEvent e)
			{
				
			}
			
			@Override
			public void windowDeiconified(WindowEvent e)
			{
				
			}
			
			@Override
			public void windowDeactivated(WindowEvent e)
			{
				
			}
			
			@Override
			public void windowClosing(WindowEvent e)
			{
				Map<String,String> prefs = Prefs.get();
				prefs.put(Prefs.WINDOW_WIDTH, "" + getWidth());
				prefs.put(Prefs.WINDOW_HEIGHT, "" + getHeight());
				prefs.put(Prefs.SPLIT_PANE_WIDTH, "" + splitPane.getDividerLocation());
				Prefs.save();
				System.exit(0);
			}
			
			@Override
			public void windowClosed(WindowEvent e)
			{
			}
			
			@Override
			public void windowActivated(WindowEvent e)
			{
				
			}
		});
	}
	
	public ExtensionRepositoryManager getErm()
	{
		return erm;
	}
}
