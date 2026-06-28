package com.polydes.repman.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.*;

import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.Sources;
import com.polydes.repman.util.GitHubTagFetcher;
import com.polydes.repman.util.GitRemoteParser;
import com.polydes.repman.util.TagParser;
import org.apache.log4j.xml.DOMConfigurator;

import com.formdev.flatlaf.FlatLightLaf;
import com.polydes.repman.ExtensionRepositoryManager;
import com.polydes.repman.data.Prefs;
import com.polydes.repman.ui.comp.MiniSplitPane;
import stencyl.app.lnf.Fonts;
import stencyl.core.Config;
import stencyl.core.SWC;
import stencyl.core.api.tasks.TaskManager;
import stencyl.core.util.ParsingHelper;
import stencyl.sw.app.tasks.SwingTaskManager;

public class RepmanMain extends JFrame
{
	public static final String GIT_GUI_PATH = System.getProperty("git.gui");
	public static final String USER_DIR = System.getProperty("user.dir");
	public static final String REPMAN_DIR = System.getProperty("repman.dir");

	public static void main(String[] args)
	{
		//A number of Stencyl APIs are built to assume we're running from the install folder
		if(!Files.exists(Path.of(USER_DIR, "lang/en")))
		{
			throw new RuntimeException("run from Stencyl install folder");
		}
		if(REPMAN_DIR == null)
		{
			throw new RuntimeException("set repman.dir property to repman project folder");
		}

		DOMConfigurator.configure(Path.of(REPMAN_DIR, "log4j.xml").toString());
		FlatLightLaf.setup();
		UIManager.put("Table.showVerticalLines", true);
		UIManager.put("Table.showHorizontalLines", true);
		UIManager.put("Table.intercellSpacing", new Dimension(1, 1));
		Config.initialize(false);
		new RepmanMain();
	}
	
	public static RepmanMain instance;
	
	ExtensionRepositoryManager erm;
	RepoTree rtree;
	ExtensionView view;
	
	public RepmanMain()
	{
		instance = this;

		Fonts fonts = new Fonts();
		SWC.set(Fonts.class, fonts);
		TaskManager taskManager = new SwingTaskManager(this);
		SWC.set(TaskManager.class, taskManager);

		erm = new ExtensionRepositoryManager();
		rtree = new RepoTree();
		erm.getRepositories().addListener(rtree.netRepoListener);
		Sources.getSources().addListener(rtree.localRepoListener);

		view = new ExtensionView();
		rtree.getTree().addTreeSelectionListener(view);
		
		MiniSplitPane splitPane = new MiniSplitPane();
		splitPane.setLeftComponent(new JScrollPane(rtree));
		splitPane.setRightComponent(new JScrollPane(view));
		splitPane.setDividerLocation(ParsingHelper.parseInt(Prefs.get(Prefs.SPLIT_PANE_WIDTH), 120));
		
		int width = ParsingHelper.parseInt(Prefs.get(Prefs.WINDOW_WIDTH), 640);
		int height = ParsingHelper.parseInt(Prefs.get(Prefs.WINDOW_HEIGHT), 480);
		setSize(width, height);
		add(splitPane, BorderLayout.CENTER);
		setVisible(true);
		
		new SwingWorker<String, Void>(){
			@Override
			protected String doInBackground() throws Exception
			{
				Sources.loadSources();
				erm.loadRepositoriesFromDisk();
				for(var repoEntry : Sources.getSources().values())
				{
					for(var extEntry : repoEntry.sources().values())
					{
						extEntry.addPropertyChangeListener(evt -> {
							SwingUtilities.invokeLater(() -> {
								rtree.refreshState(repoEntry.url(), extEntry.getInfo().getID());
							});
						});
					}
				}
				for(var repoEntry : Sources.getSources().values())
				{
					for(var extEntry : repoEntry.sources().values())
					{
						extEntry.loadGitState();
					}
				}
				for(var repoEntry : Sources.getSources().values())
				{
					List<LocalSource> locals = List.copyOf(repoEntry.sources().values());
					List<String> repos = new ArrayList<>();
					for(var extEntry : locals)
					{
						repos.add(GitRemoteParser.getRepoOwnerAndName(extEntry.getPath().toFile()));
					}
					try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

						List<Future<List<String>>> futures = new ArrayList<>();

						for (String repo : repos) {
							futures.add(executor.submit(() -> GitHubTagFetcher.fetchAllTags(repo)));
						}

						for (int i = 0; i < repos.size(); i++) {
							List<String> pages = futures.get(i).get();
							List<String> tags = TagParser.extractTagNames(pages);
							locals.get(i).loadGitRemoteState(tags);
						}
					}
				}
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
