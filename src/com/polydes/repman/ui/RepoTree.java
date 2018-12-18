package com.polydes.repman.ui;

import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.polydes.repman.Extension;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.ExtensionType;
import com.polydes.repman.util.NotifierHashMap.HashMapEvent;
import com.polydes.repman.util.NotifierHashMap.Listener;

public class RepoTree extends JPanel
{
	JTree tree;
	DefaultTreeModel model;
	DefaultMutableTreeNode root;
	Map<ExtensionRepository, DefaultMutableTreeNode> repoNodeMap;
	Map<Extension, DefaultMutableTreeNode> extNodeMap;
	RepoListener repoListener;
	
	public RepoTree()
	{
		super(new BorderLayout());
		
		root = new DefaultMutableTreeNode("Repositories");
		model = new DefaultTreeModel(root);
		tree = new JTree(model);
		repoNodeMap = new HashMap<>();
		extNodeMap = new HashMap<>();
		repoListener = new RepoListener();
		
		add(tree, BorderLayout.CENTER);
	}
	
	public JTree getTree()
	{
		return tree;
	}

	public final class RepoListener implements Listener<ExtensionRepository>
	{
		Map<ExtensionRepository, Pair<ExtensionListener, ExtensionListener>> lmap = new HashMap<>();
		
		@Override
		public void mapChanged(HashMapEvent<ExtensionRepository> event)
		{
			DefaultMutableTreeNode node;
			ExtensionRepository repo = event.value;
			switch(event.type)
			{
				case VALUE_ADDED:
					DefaultMutableTreeNode engine = new DefaultMutableTreeNode("engine");
					DefaultMutableTreeNode toolset = new DefaultMutableTreeNode("toolset");
					node = new DefaultMutableTreeNode(repo);
					node.add(engine);
					node.add(toolset);
					repoNodeMap.put(repo, node);
					ExtensionListener engineListener = new ExtensionListener(engine);
					ExtensionListener toolsetListener = new ExtensionListener(toolset);
					root.add(node);
					model.nodeStructureChanged(root);
					repo.getExtensions(ExtensionType.ENGINE).addListener(engineListener);
					repo.getExtensions(ExtensionType.TOOLSET).addListener(toolsetListener);
					lmap.put(repo, new ImmutablePair<>(engineListener, toolsetListener));
					for(Extension ext : repo.getExtensions(ExtensionType.ENGINE).values())
						addExtension(engine, ext);
					for(Extension ext : repo.getExtensions(ExtensionType.TOOLSET).values())
						addExtension(toolset, ext);
					break;
				case VALUE_REMOVED:
					node = repoNodeMap.remove(repo);
					node.removeAllChildren();
					root.remove(node);
					model.nodeStructureChanged(root);
					for(Extension ext : repo.getExtensions(ExtensionType.ENGINE).values())
						removeExtension(ext);
					for(Extension ext : repo.getExtensions(ExtensionType.TOOLSET).values())
						removeExtension(ext);
					repo.getExtensions(ExtensionType.ENGINE).removeListener(lmap.get(repo).getLeft());
					repo.getExtensions(ExtensionType.TOOLSET).removeListener(lmap.remove(repo).getRight());
					break;
			}
		}
	};
	
	public final class ExtensionListener implements Listener<Extension>
	{
		DefaultMutableTreeNode parentNode;
		
		public ExtensionListener(DefaultMutableTreeNode parentNode)
		{
			this.parentNode = parentNode;
		}
		
		@Override
		public void mapChanged(HashMapEvent<Extension> event)
		{
			switch(event.type)
			{
				case VALUE_ADDED:
					addExtension(parentNode, event.value);
					break;
				case VALUE_REMOVED:
					removeExtension(event.value);
					break;
			}
		}
	}
	
	public void addExtension(DefaultMutableTreeNode parentNode, Extension extension)
	{
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(extension);
		extNodeMap.put(extension, node);
		parentNode.add(node);
		model.nodeStructureChanged(parentNode);
	}
	
	public void removeExtension(Extension extension)
	{
		DefaultMutableTreeNode node = extNodeMap.remove(extension);
		DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
		parentNode.remove(node);
		model.nodeStructureChanged(parentNode);
	}
}
