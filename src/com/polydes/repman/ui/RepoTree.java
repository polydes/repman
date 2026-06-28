package com.polydes.repman.ui;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import com.formdev.flatlaf.util.ColorFunctions;
import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.Sources.Repository;
import stencyl.core.api.struct.NotifierMap.MapEvent;
import stencyl.core.api.struct.NotifierMap.MapListener;
import com.polydes.repman.ExtensionRepository;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.util.CollectionHelper;

public class RepoTree extends JPanel
{
	private final JTree tree;
	private final DefaultTreeModel model;
	private final DefaultMutableTreeNode repositoriesRoot;

	private final Map<String, RepoNodeManager> repoManagers = new HashMap<>();

	public final NetRepoListener netRepoListener = new NetRepoListener();
	public final LocalRepoListener localRepoListener = new LocalRepoListener();

	public RepoTree()
	{
		super(new BorderLayout());

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
		repositoriesRoot = new DefaultMutableTreeNode("Repositories");
		root.add(repositoriesRoot);

		model = new DefaultTreeModel(root);
		tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);

		tree.setCellRenderer(new DefaultTreeCellRenderer()
		{
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				if(value instanceof DefaultMutableTreeNode dmtn && dmtn.getUserObject() instanceof ExtData data)
				{
					if(data.netExt == null)
					{
						Color fg = getForeground();
						if(fg == null) fg = Color.BLACK;
						Color bg = getBackground();
						if(bg == null) bg = Color.WHITE;
						setForeground(ColorFunctions.mix(fg, bg, .5f));
					}
				}
				return this;
			}
		});

		add(tree, BorderLayout.CENTER);
	}

	public JTree getTree()
	{
		return tree;
	}

	// ====================================================================
	//  Core Logic for Managing Combined Nodes
	// ====================================================================

	private RepoNodeManager getOrCreateRepoManager(String url) {
		return repoManagers.computeIfAbsent(url, k -> {
			RepoNodeManager manager = new RepoNodeManager(k);
			repositoriesRoot.add(manager.getNode());
			model.nodeStructureChanged(repositoriesRoot);
			return manager;
		});
	}

	private void checkRepoManagerEmpty(RepoNodeManager manager) {
		if (manager.isEmpty()) {
			repositoriesRoot.remove(manager.getNode());
			repoManagers.remove(manager.getUrl());
			model.nodeStructureChanged(repositoriesRoot);
		}
	}

	// ====================================================================
	//  Repository Level Listeners
	// ====================================================================

	public final class NetRepoListener implements MapListener<ExtensionRepository> {
		@Override
		public void mapChanged(MapEvent<ExtensionRepository> event) {
			String url = event.value.url;

			switch (event.type) {
				case VALUE_ADDED:
					getOrCreateRepoManager(url).setNetRepo(event.value);
					break;
				case VALUE_REMOVED:
					RepoNodeManager manager = repoManagers.get(url);
					if (manager != null) {
						manager.removeNetRepo();
						checkRepoManagerEmpty(manager);
					}
					break;
			}
		}
	}

	public final class LocalRepoListener implements MapListener<Repository> {
		@Override
		public void mapChanged(MapEvent<Repository> event) {
			String url = event.value.url();

			switch (event.type) {
				case VALUE_ADDED:
					getOrCreateRepoManager(url).setLocalRepo(event.value);
					break;
				case VALUE_REMOVED:
					RepoNodeManager manager = repoManagers.get(url);
					if (manager != null) {
						manager.removeLocalRepo();
						checkRepoManagerEmpty(manager);
					}
					break;
			}
		}
	}

	// ====================================================================
	//  Managers for Node State (Combining Net and Local)
	// ====================================================================

	/**
	 * Tracks a single Repository node backed by a NetRepo, LocalRepo, or both.
	 */
	private class RepoNodeManager {
		private final String url;
		private final DefaultMutableTreeNode node;
		private final RepoData data;

		private ExtensionRepository netRepo;
		private Repository localRepo;

		// Tracks extensions within this specific repository by their shared key
		private final Map<String, ExtNodeManager> extManagers = new HashMap<>();

		private final NetExtListener netExtListener = new NetExtListener();
		private final LocalExtListener localExtListener = new LocalExtListener();

		public RepoNodeManager(String url) {
			this.url = url;
			this.data = new RepoData(url);
			this.node = new DefaultMutableTreeNode(data);
		}

		public String getUrl() { return url; }
		public DefaultMutableTreeNode getNode() { return node; }
		public boolean isEmpty() { return netRepo == null && localRepo == null; }

		public void setNetRepo(ExtensionRepository repo) {
			this.netRepo = repo;
			this.data.netRepo = repo;
			model.nodeChanged(node);

			repo.getExtensions().addListener(netExtListener);

			// Add existing extensions upon repo addition.
			for (Map.Entry<String, NetExtension> entry : repo.getExtensions().entrySet()) {
				getOrCreateExtManager(entry.getKey()).setNetExt(entry.getValue());
			}
		}

		public void removeNetRepo() {
			if (this.netRepo != null) {
				this.netRepo.getExtensions().removeListener(netExtListener);
				// Nullify the net portion of all child extensions
				for (ExtNodeManager extMgr : extManagers.values()) {
					extMgr.removeNetExt();
				}
				cleanupEmptyExtensions();

				this.netRepo = null;
				this.data.netRepo = null;
				model.nodeChanged(node);
			}
		}

		public void setLocalRepo(Repository repo) {
			this.localRepo = repo;
			this.data.localRepo = repo;
			model.nodeChanged(node);

			// Assumes Repository has a similar NotifierMap for its sources
			repo.sources().addListener(localExtListener);

			for (Map.Entry<String, LocalSource> entry : repo.sources().entrySet()) {
				getOrCreateExtManager(entry.getKey()).setLocalExt(entry.getValue());
			}
		}

		public void removeLocalRepo() {
			if (this.localRepo != null) {
				this.localRepo.sources().removeListener(localExtListener);
				for (ExtNodeManager extMgr : extManagers.values()) {
					extMgr.removeLocalExt();
				}
				cleanupEmptyExtensions();

				this.localRepo = null;
				this.data.localRepo = null;
				model.nodeChanged(node);
			}
		}

		private void cleanupEmptyExtensions() {
			extManagers.values().removeIf(extMgr -> {
				if (extMgr.isEmpty()) {
					node.remove(extMgr.getNode());
					return true;
				}
				return false;
			});
			model.nodeStructureChanged(node);
		}

		private ExtNodeManager getOrCreateExtManager(String extKey) {
			return extManagers.computeIfAbsent(extKey, k -> {
				ExtNodeManager mgr = new ExtNodeManager(k);
				node.add(mgr.getNode());
				model.nodeStructureChanged(node);
				return mgr;
			});
		}

		private void checkExtManagerEmpty(ExtNodeManager mgr) {
			if (mgr.isEmpty()) {
				node.remove(mgr.getNode());
				extManagers.remove(mgr.getExtensionID());
				model.nodeStructureChanged(node);
			}
		}

		// --- Inner Extension Listeners --- //

		private class NetExtListener implements MapListener<NetExtension> {
			@Override
			public void mapChanged(MapEvent<NetExtension> event) {
				String extensionID = event.value.id;
				switch (event.type) {
					case VALUE_ADDED:
						getOrCreateExtManager(extensionID).setNetExt(event.value);
						break;
					case VALUE_REMOVED:
						ExtNodeManager mgr = extManagers.get(extensionID);
						if (mgr != null) {
							mgr.removeNetExt();
							checkExtManagerEmpty(mgr);
						}
						break;
				}
			}
		}

		private class LocalExtListener implements MapListener<LocalSource> {
			@Override
			public void mapChanged(MapEvent<LocalSource> event) {
				String extensionID = event.value.getInfo().getID();
				switch (event.type) {
					case VALUE_ADDED:
						getOrCreateExtManager(extensionID).setLocalExt(event.value);
						break;
					case VALUE_REMOVED:
						ExtNodeManager mgr = extManagers.get(extensionID);
						if (mgr != null) {
							mgr.removeLocalExt();
							checkExtManagerEmpty(mgr);
						}
						break;
				}
			}
		}
	}

	/**
	 * Tracks a single Extension node backed by a NetExtension, LocalSource, or both.
	 */
	private class ExtNodeManager {
		private final String extensionID;
		private final DefaultMutableTreeNode node;
		private final ExtData data;

		private NetExtension netExt;
		private LocalSource localExt;

		public ExtNodeManager(String extensionID) {
			this.extensionID = extensionID;
			this.data = new ExtData(extensionID);
			this.node = new DefaultMutableTreeNode(data);
		}

		public String getExtensionID() { return extensionID; }
		public DefaultMutableTreeNode getNode() { return node; }
		public boolean isEmpty() { return netExt == null && localExt == null; }

		public void setNetExt(NetExtension ext) {
			this.netExt = ext;
			this.data.netExt = ext;
			model.nodeChanged(node);
		}

		public void removeNetExt() {
			this.netExt = null;
			this.data.netExt = null;
			model.nodeChanged(node);
		}

		public void setLocalExt(LocalSource ext) {
			this.localExt = ext;
			this.data.localExt = ext;
			model.nodeChanged(node);
		}

		public void removeLocalExt() {
			this.localExt = null;
			this.data.localExt = null;
			model.nodeChanged(node);
		}
	}

	public void refreshState(String url, String id) {
		if(!(repoManagers.get(url) instanceof RepoNodeManager rnm))
			return;
		if(!(rnm.extManagers.get(id) instanceof ExtNodeManager enm))
			return;
		if(enm.localExt == null || !enm.localExt.isLoaded() || !enm.localExt.isGitStateLoaded())
		{
			enm.data.gitState1 = "";
			enm.data.gitState2 = "";
			return;
		}
		StringBuilder stateString1 = new StringBuilder();
		StringBuilder stateString2 = new StringBuilder();
		if(enm.localExt.isHasUnstagedChanges())
		{
			if(!stateString1.isEmpty())
				stateString1.append(" ");
			stateString1.append("**");
		}
		if(enm.localExt.isHasUnpushedCommits())
		{
			if(!stateString1.isEmpty())
				stateString1.append(" ");
			stateString1.append("^^");
		}
		if(enm.localExt.isGitRemoteStateLoaded())
		{
			List<String> sortedLocalTags = CollectionHelper.sortForward(enm.localExt.getLocalTags());
			List<String> sortedRemoteTags = CollectionHelper.sortForward(enm.localExt.getRemoteTags());
			if(!sortedLocalTags.equals(sortedRemoteTags))
			{
				if(!stateString1.isEmpty())
					stateString1.append(" ");
				stateString1.append("T");
			}
		}
		if(enm.localExt.getLastVersionTag() != null)
		{
			if(!stateString2.isEmpty())
				stateString2.append(" ");
			stateString2.append("[").append(enm.localExt.getLastVersionTag()).append("]");
		}
		if(!enm.localExt.getCommitsSinceLastVersionTag().isEmpty())
		{
			if(!stateString2.isEmpty())
				stateString2.append(" ");
			stateString2.append("+").append(enm.localExt.getCommitsSinceLastVersionTag().size());
		}
		enm.data.gitState1 = stateString1.isEmpty() ? "" : stateString1 + " ";
		enm.data.gitState2 = stateString2.isEmpty() ? "" : " " + stateString2;
		model.nodeChanged(enm.node);
	}

	// ====================================================================
	//  Data Wrappers (Control how the nodes are rendered in the JTree)
	// ====================================================================

	public static class RepoData {
		public String url;
		public ExtensionRepository netRepo;
		public Repository localRepo;

		public RepoData(String url) { this.url = url; }

		@Override
		public String toString() {
			if (localRepo != null) return localRepo.url();
			if (netRepo != null) return netRepo.url;
			return url;
		}
	}

	public static class ExtData {
		public String extensionID;
		public NetExtension netExt;
		public LocalSource localExt;
		public String gitState1 = "";
		public String gitState2 = "";

		public ExtData(String extensionID) { this.extensionID = extensionID; }

		@Override
		public String toString() {
			String base;
			if (localExt != null && localExt.isLoaded()) base = localExt.getInfo().getName();
			else if (netExt != null) base = netExt.name;
			else base = extensionID;
			return gitState1 + base + gitState2;
		}
	}
}