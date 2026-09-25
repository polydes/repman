package com.polydes.repman.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;

import com.formdev.flatlaf.util.ColorFunctions;
import com.polydes.repman.data.LocalSource;
import com.polydes.repman.data.RepositoryStore.FileToUpload;
import com.polydes.repman.ui.RepoTree.ExtData;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import stencyl.core.api.tasks.TaskManager;
import stencyl.core.api.Version;
import stencyl.core.ext.ExtensionDependency;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.data.RepositoryStore;
import com.polydes.repman.data.Sources;
import com.polydes.repman.res.Resources;
import com.polydes.repman.ui.comp.CellColorProvider;
import com.polydes.repman.ui.comp.IconProvider;
import com.polydes.repman.ui.comp.MultiLineCellSupport;
import stencyl.core.ext.ExtensionInfo;
import stencyl.core.ext.ExtensionInfo.ExtensionCategory;
import stencyl.core.ext.net.ChangeEntry;
import stencyl.core.ext.net.ExtensionVersion;
import stencyl.core.ext.net.NetExtension;
import stencyl.core.ext.net.RepositoryManifest;
import stencyl.core.util.CollectionHelper;
import stencyl.core.util.ParsingHelper;

public class ExtensionView extends JPanel implements TreeSelectionListener
{
	private static final Logger log = Logger.getLogger(ExtensionView.class);

	ExtData ext;

	List<VersionInfo> versionList;

	record VersionInfo(
			Version version,
			ChangeEntry localChangeEntry, ChangeEntry remoteChangeEntry,
			ExtensionVersion localVersion, ExtensionVersion remoteVersion
	) {}

	JTable versionTable;
	VersionTableModel vtableModel;

	JList commitList;
	CommitListModel commitListModel;
	
	JButton buildNewButton;
	JButton uploadToRepoButton;
	JButton updateRepositoryManifestButton;

	public ExtensionView()
	{
		super(new BorderLayout());

		vtableModel = new VersionTableModel();
		commitListModel = new CommitListModel();
		buildNewButton = new JButton("Build New Version");
		buildNewButton.addActionListener((e) -> {
			if(ext.localExt == null || !ext.localExt.isLoaded())
			{
				JOptionPane.showMessageDialog(RepmanMain.instance, "Can't upload without local source", "Upload Failed", JOptionPane.ERROR_MESSAGE);
				return;
			}
			TaskManager.runTask("Build Extension", task -> {
				try {
					ExtensionInfo info = ext.localExt.getInfo();
					ExtensionRepository repo = RepmanMain.instance.getErm().getRepositories().get(info.getRepository());

					Path sourcePath = ext.localExt.getPath();
					Path mirrorPath = repo.getExtensionLocalLocation(info.getID());

					Files.createDirectories(mirrorPath);

					Image icon = ext.localExt.getInfo().getIcon();
					Path iconFile = mirrorPath.resolve("icon.png");
					BufferedImage bi = new BufferedImage(icon.getWidth(null), icon.getHeight(null), BufferedImage.TYPE_INT_ARGB);
					Graphics g = bi.createGraphics();
					g.drawImage(icon, 0, 0, null);
					g.dispose();
					ImageIO.write(bi, "png", iconFile.toFile());

					NetExtension.writeExtensionData(info, mirrorPath, "info.json");
					Files.copy(sourcePath.resolve("versions.json"), mirrorPath.resolve("versions.json"), StandardCopyOption.REPLACE_EXISTING);
					Files.copy(sourcePath.resolve("changes.md"), mirrorPath.resolve("changes.md"), StandardCopyOption.REPLACE_EXISTING);
					Sources.buildSource(task, ext, (newVersion) -> {
						ext.localExt.removePropertyChangeListener(this::localSourceUpdated);
						ExtData toRefresh = ext;
						ext = null;
						refresh(toRefresh);
					});
				} catch(Exception ex) {
					throw task.failWithError("Build Failed", ex.getMessage(), ex);
				}
			});
		});
		uploadToRepoButton = new JButton("Upload to Repository");
		uploadToRepoButton.addActionListener((e) -> {
			if(ext.localExt == null || !ext.localExt.isLoaded())
			{
				JOptionPane.showMessageDialog(RepmanMain.instance, "Can't upload without local source", "Upload Failed", JOptionPane.ERROR_MESSAGE);
				return;
			}
			TaskManager.runTask("Publish Extension", task -> {
				try
				{
					ExtensionInfo info = ext.localExt.getInfo();
					ExtensionRepository repo = RepmanMain.instance.getErm().getRepositories().get(info.getRepository());
					RepositoryManifest manifest = repo.getManifest();

					Path sourcePath = ext.localExt.getPath();
					Path mirrorPath = repo.getExtensionLocalLocation(info.getID());

					Path revisionFile = mirrorPath.resolve("revision");
					int revision = 0;
					if(Files.exists(revisionFile))
						revision = ParsingHelper.parseInt(Files.readString(revisionFile), 0);
					++revision;
					Files.writeString(revisionFile, "" + revision);

					Image icon = ext.localExt.getInfo().getIcon();
					Path iconFile = mirrorPath.resolve("icon.png");
					BufferedImage bi = new BufferedImage(icon.getWidth(null), icon.getHeight(null), BufferedImage.TYPE_INT_ARGB);
					Graphics g = bi.createGraphics();
					g.drawImage(icon, 0, 0, null);
					g.dispose();
					ImageIO.write(bi, "png", iconFile.toFile());

					NetExtension.writeExtensionData(info, mirrorPath, "info.json");
					Files.copy(sourcePath.resolve("versions.json"), mirrorPath.resolve("versions.json"), StandardCopyOption.REPLACE_EXISTING);
					Files.copy(sourcePath.resolve("changes.md"), mirrorPath.resolve("changes.md"), StandardCopyOption.REPLACE_EXISTING);

                    List<FileToUpload> filesToUpload = new ArrayList<>(Stream.of(
                                    "icon.png",
                                    "info.json",
                                    "versions.json",
                                    "changes.md"
                            )
                            .map(rel -> new FileToUpload(mirrorPath.resolve(rel), rel)).toList());
					for(VersionInfo vi : versionList)
						if(vi.localVersion != null && vi.remoteVersion == null && hasLocal(vi.version))
							filesToUpload.add(new FileToUpload(mirrorPath.resolve(vi.version() + ".zip"), vi.version() + ".zip"));

					RepositoryStore.upload(repo, info.getID(), filesToUpload);

					ext.localExt.removePropertyChangeListener(this::localSourceUpdated);
					ExtData toRefresh = ext;
					ext = null;
					refresh(toRefresh);
				}
				catch(Exception ex)
				{
					throw task.failWithError("Upload Failed", ex.getMessage(), ex);
				}
			});
		});
	}

	private void localSourceUpdated(PropertyChangeEvent propertyChangeEvent)
	{
		if(propertyChangeEvent.getPropertyName().equals(LocalSource.LOCAL_GIT_PROPERTIES))
		{
			commitListModel.listUpdated();
		}
	}

	public static final int COLUMN_VERSION = 0;
	public static final int COLUMN_VERSION_LABEL = 1;
	public static final int COLUMN_CHANGES = 2;
	public static final int COLUMN_DEPENDENCIES = 3;
	public static final int COLUMN_LOCAL = 4;
//	public static final int COLUMN_REMOTE = 3;

	public void refresh(ExtData ext)
	{
		if(this.ext == ext)
			return;

		versionList = new ArrayList<>();

		this.ext = ext;
		boolean useRemoteSource = ext.netExt != null;
		boolean useLocalSource = ext.localExt != null && ext.localExt.isLoaded();
		LocalSource localSource = ext.localExt;

		ExtensionRepository repo = useRemoteSource ? RepmanMain.instance.getErm().getRepositories().get(ext.netExt.repository) : null;
		Path mirrorPath = useRemoteSource ? repo.getExtensionLocalLocation(ext.netExt.id) : null;

		Set<Version> allVersions = new HashSet<>();
		List<ChangeEntry> netChanges = List.of();
		Map<Version, ExtensionVersion> vToMirrorV = new HashMap<>();
		Map<Version, ChangeEntry> vToMirrorCe = new HashMap<>();
		Map<Version, ExtensionVersion> vToLocalV = new HashMap<>();
		Map<Version, ChangeEntry> vToLocalCe = new HashMap<>();

		if(useRemoteSource)
		{
			for(ExtensionVersion v : ext.netExt.versions)
			{
				allVersions.add(v.version());
				vToMirrorV.put(v.version(), v);
			}
		}
		if(useLocalSource)
		{
			for(ExtensionVersion v : localSource.getVersions())
			{
				allVersions.add(v.version());
				vToLocalV.put(v.version(), v);
			}
		}
		if(useRemoteSource)
		{
			try
			{
				netChanges = ExtensionInfo.readChanges(mirrorPath.resolve("changes.md"));
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
			for(ChangeEntry entry : netChanges)
			{
				allVersions.add(entry.version());
				vToMirrorCe.put(entry.version(), entry);
			}
		}
		if(useLocalSource)
		{
			for(ChangeEntry entry : localSource.getChanges())
			{
				allVersions.add(entry.version());
				vToLocalCe.put(entry.version(), entry);
			}
		}

		for(Version version : CollectionHelper.sortForward(allVersions))
		{
			VersionInfo info = new VersionInfo(
					version,
					vToLocalCe.get(version),
					vToMirrorCe.get(version),
					vToLocalV.get(version),
					vToMirrorV.get(version)
			);

			versionList.add(info);
		}

		removeAll();
		
		int pad = 10;
		setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
		
		JPanel extensionInfoPanel = new JPanel();
		extensionInfoPanel.setLayout(new BoxLayout(extensionInfoPanel, BoxLayout.Y_AXIS));

		String extensionName = useLocalSource ? localSource.getInfo().getName() : ext.netExt.name;
		String extensionID = useLocalSource ? localSource.getInfo().getID() : ext.netExt.id;
		Image extensionIcon = useLocalSource ? localSource.getInfo().getIcon() : ext.netExt.icon;
		String extensionAuthor = useLocalSource ? localSource.getInfo().getAuthorName() : ext.netExt.author;
		String extensionDescription = useLocalSource ? localSource.getInfo().getDescription() : ext.netExt.description;
		String extensionWebsite = useLocalSource ? localSource.getInfo().getWebsite() : ext.netExt.website;
		ExtensionCategory extensionType = useLocalSource ? localSource.getInfo().getType() : ext.netExt.cat;

		JLabel extensionLabel = new JLabel("<html>" + extensionName + "<br>(" + extensionID + ")</html>");
		extensionLabel.setIcon(new ImageIcon(extensionIcon));
		extensionLabel.setFont(getFont().deriveFont(14.0f).deriveFont(Font.BOLD));
		
		JLabel infoLabel = new JLabel
		(
			"<html><b>" +
			"Author: " + extensionAuthor + "<br>" +
			"Description: " + extensionDescription + "<br>" +
			"Category: " + extensionType.toString() + "<br>" +
			"Website: " + extensionWebsite + "<br>" +
			"Local Source: " + (useLocalSource ? localSource.getPath() : "<no local source>") +
			"</b></html>"
		);
		infoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		
		JPanel topInfoPanel = new JPanel();
		topInfoPanel.setLayout(new BoxLayout(topInfoPanel, BoxLayout.X_AXIS));
		topInfoPanel.add(extensionLabel);
		topInfoPanel.add(Box.createHorizontalGlue());
		topInfoPanel.add(infoLabel);
		
		extensionInfoPanel.add(topInfoPanel);
		
		extensionInfoPanel.add(Box.createVerticalStrut(5));
		
		JPanel actionsPanel = new JPanel();
		actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.X_AXIS));
		actionsPanel.add(buildNewButton);
		actionsPanel.add(Box.createHorizontalStrut(5));
		actionsPanel.add(uploadToRepoButton);
		actionsPanel.add(Box.createHorizontalGlue());
		extensionInfoPanel.add(actionsPanel);
		
		extensionInfoPanel.add(Box.createVerticalStrut(5));
		
		add(extensionInfoPanel, BorderLayout.NORTH);

		JPanel mainContentPanel = new JPanel(new BorderLayout());

		commitList = new JList<>(commitListModel);
		JScrollPane commitScroll = new JScrollPane(commitList);
		commitScroll.setPreferredSize(new Dimension(0, 100));
		mainContentPanel.add(commitScroll, BorderLayout.NORTH);

		CellColorProvider colorProvider = new ExtensionVersionCellColorProvider();
		
		versionTable = new JTable(vtableModel);
		
		MultiLineCellSupport mlsRender = new MultiLineCellSupport();
		MultiLineCellSupport mlsEdit = new MultiLineCellSupport(mlsRender);

		mlsRender.setColorProvider(colorProvider);
		
		mlsRender.addClassSupport
		(
			Version.class,
			(v) -> {
				if(ext.localExt != null && ext.localExt.isGitStateLoaded() && ext.localExt.isGitRemoteStateLoaded())
				{
					boolean inLocal = ext.localExt.getLocalTags().contains(v.toString()) || ext.localExt.getLocalTags().contains("v" + v);
					boolean inRemote = ext.localExt.getRemoteTags().contains(v.toString()) || ext.localExt.getRemoteTags().contains("v" + v);
					if(!inLocal && !inRemote)
						return "[U]"+v;
					if(inLocal && !inRemote)
						return "[L]"+v;
					if(inRemote && !inLocal)
						return "[R]"+v;
				}
				return v.toString();
			},
			(s) -> null
		);

		mlsRender.addClassSupport(ExtensionDependency[].class,
			(deps) -> StringUtils.join(deps, "\n"),
			(s) -> null
		);

		mlsRender.addClassSupport(ChangeEntry.class,
			(entry) -> entry.body(),
			(s) -> null
		);

		IconCellRenderer iconRenderer = new IconCellRenderer();
		IconToggleEditor iconToggleEditor = new IconToggleEditor(new JCheckBox());
		iconRenderer.setColorProvider(colorProvider);
		iconToggleEditor.setColorProvider(colorProvider);
		
		versionTable.setDefaultRenderer(Version.class, mlsRender);
		versionTable.setDefaultEditor(Version.class, mlsEdit);
		versionTable.setDefaultRenderer(String.class, mlsRender);
		versionTable.setDefaultEditor(String.class, mlsEdit);
		versionTable.setDefaultRenderer(ChangeEntry.class, mlsRender);
		versionTable.setDefaultEditor(ChangeEntry.class, mlsEdit);
		versionTable.setDefaultRenderer(ExtensionDependency[].class, mlsRender);
		versionTable.setDefaultEditor(ExtensionDependency[].class, mlsEdit);
		
		versionTable.setDefaultRenderer(LocalDownloadState.class, iconRenderer);
		versionTable.setDefaultEditor(LocalDownloadState.class, iconToggleEditor);
		versionTable.setDefaultRenderer(DeleteButton.class, iconRenderer);
		versionTable.setDefaultEditor(DeleteButton.class, iconToggleEditor);
		
//		resizeColumnWidth(versionTable);
		versionTable.getTableHeader().getColumnModel().getColumn(COLUMN_VERSION).setMaxWidth(75);
		versionTable.getTableHeader().getColumnModel().getColumn(COLUMN_VERSION_LABEL).setMaxWidth(105);
		versionTable.getTableHeader().getColumnModel().getColumn(COLUMN_VERSION_LABEL).setMinWidth(105);
		versionTable.getTableHeader().getColumnModel().getColumn(COLUMN_LOCAL).setMaxWidth(20);
//		versionTable.getTableHeader().getColumnModel().getColumn(COLUMN_REMOTE).setMaxWidth(20);

		mainContentPanel.add(new JScrollPane(versionTable), BorderLayout.CENTER);

		add(mainContentPanel, BorderLayout.CENTER);

		revalidate();
		repaint();

		ext.localExt.addPropertyChangeListener(this::localSourceUpdated);
	}
	
	public void resizeColumnWidth(JTable table)
	{
		final TableColumnModel columnModel = table.getColumnModel();
		for(int column = 0; column < table.getColumnCount(); column++)
		{
			int width = 50; // Min width
			for(int row = 0; row < table.getRowCount(); row++)
			{
				TableCellRenderer renderer = table.getCellRenderer(row, column);
				Component comp = table.prepareRenderer(renderer, row, column);
				width = Math.max(comp.getPreferredSize().width + 1, width);
			}
			columnModel.getColumn(column).setPreferredWidth(width);
		}
	}

	public class VersionTableModel extends DefaultTableModel
	{
		@Override
		public String getColumnName(int column)
		{
			switch(column)
			{
				case COLUMN_VERSION: return "Version";
				case COLUMN_VERSION_LABEL: return "Label";
				case COLUMN_CHANGES: return "Changes";
				case COLUMN_DEPENDENCIES: return "Dependencies";
				case COLUMN_LOCAL: return "L"; //Local
//				case COLUMN_REMOTE: return "R"; //Remove
				default: return "";
			}
		}
		
		@Override
		public int getColumnCount()
		{
			return 5;
		}
		
		@Override
		public int getRowCount()
		{
			if(ext == null)
				return 0;
			return versionList.size();
		}
		
		@Override
		public Object getValueAt(int row, int column)
		{
			if(ext == null)
				return null;
			VersionInfo vi = versionList.get(versionList.size() - (row + 1));
			switch(column)
			{
				case COLUMN_VERSION: return vi.version();
				case COLUMN_VERSION_LABEL: return
						vi.localChangeEntry  != null ? vi.localChangeEntry.label() :
						vi.remoteChangeEntry != null ? vi.remoteChangeEntry.label() :
						null;
				case COLUMN_CHANGES: return
						vi.localChangeEntry != null ? vi.localChangeEntry :
						vi.remoteChangeEntry != null ? vi.remoteChangeEntry :
						null;
				case COLUMN_DEPENDENCIES: return
						vi.localVersion != null ? vi.localVersion.dependencies() :
						vi.remoteVersion != null ? vi.remoteVersion.dependencies() :
						null;
				case COLUMN_LOCAL: return hasLocal(vi.version) ? LocalDownloadState.Delete : LocalDownloadState.Download;
//				case COLUMN_REMOTE: return DeleteButton.Delete;
				default: return null;
			}
		}
		
		@Override
		public void setValueAt(Object aValue, int row, int column)
		{
			if(ext == null)
				return;
			VersionInfo vi = versionList.get(versionList.size() - (row + 1));
			switch(column)
			{
				case COLUMN_LOCAL: setHasLocal(vi.version, aValue == LocalDownloadState.Download); break;
				default: break;
			}
		}
		
		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			switch(columnIndex)
			{
				case COLUMN_VERSION: return Version.class;
				case COLUMN_VERSION_LABEL: return String.class;
				case COLUMN_CHANGES: return ChangeEntry.class;
				case COLUMN_DEPENDENCIES: return ExtensionDependency[].class;
				case COLUMN_LOCAL: return LocalDownloadState.class;
//				case COLUMN_REMOTE: return DeleteButton.class;
				default: return null;
			}
		}
		
		@Override
		public boolean isCellEditable(int row, int column)
		{
			if(column != COLUMN_LOCAL)
				return false;
			return super.isCellEditable(row, column);
		}
	}

	class ExtensionVersionCellColorProvider implements CellColorProvider
	{
		private static final Color localColor = new Color(0xB8EDB4);
		private static final Color remoteColor = new Color(0x959FCD);
		private static final Color dirtyColor = new Color(0xEACCB2);
		private static final Color missingColor = new Color(0xC85E4A);
		private static final Color alternateColor = new Color(0xEDF5F9);

		public ExtensionVersionCellColorProvider()
		{
		}
		
		@Override
		public void colorCellComponent(JComponent c, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			VersionInfo vi = versionList.get(versionList.size() - (row + 1));

			if(isSelected)
			{
				c.setForeground(table.getSelectionForeground());
				c.setBackground(table.getSelectionBackground());
			}
			else
			{
				c.setForeground(table.getForeground());
				if(row % 2 == 0)
				{
					c.setBackground(table.getBackground());
				}
				else
				{
					c.setBackground(alternateColor);
				}
			}

			boolean changelogOnlyEntry =
					vi.localVersion == null && vi.remoteVersion == null &&
					(vi.localChangeEntry != null || vi.remoteChangeEntry != null);

			if(changelogOnlyEntry)
			{
				c.setForeground(ColorFunctions.mix(c.getForeground(), c.getBackground(), .5f));
			}
			else if(column == COLUMN_CHANGES)
			{
				if(vi.localChangeEntry == null && vi.remoteChangeEntry == null)
					c.setBackground(missingColor);
				else if(vi.remoteChangeEntry == null)
					c.setBackground(localColor);
				else if(vi.localChangeEntry == null)
					c.setBackground(remoteColor);
				else if(!vi.localChangeEntry.equals(vi.remoteChangeEntry))
					c.setBackground(dirtyColor);
			}
			else
			{
				if(vi.localVersion == null && vi.remoteVersion == null)
					c.setBackground(missingColor);
				else if(vi.remoteVersion == null)
					c.setBackground(localColor);
				else if(vi.localVersion == null)
					c.setBackground(remoteColor);
				else if(!versionsAreEqual(vi.localVersion, vi.remoteVersion))
					c.setBackground(dirtyColor);
			}
		}
	}

	private static boolean versionsAreEqual(ExtensionVersion v1, ExtensionVersion v2)
	{
		if(!v1.version().equals(v2.version()))
			return false;
		if(v1.dependencies().length != v2.dependencies().length)
			return false;
		for(int i = 0; i < v1.dependencies().length; ++i)
		{
			if(!Objects.equals(v1.dependencies()[i].type, v2.dependencies()[i].type))
				return false;
			if(!Objects.equals(v1.dependencies()[i].id, v2.dependencies()[i].id))
				return false;
			if(!Objects.equals(v1.dependencies()[i].version, v2.dependencies()[i].version))
				return false;
		}
		return true;
	}
	
	enum LocalDownloadState implements IconProvider
	{
		Download(Resources.loadIcon("arrow_down.png")),
		Delete(Resources.loadIcon("cross.png"));
		
		Icon icon;
		
		private LocalDownloadState(Icon icon)
		{
			this.icon = icon;
		}
		
		@Override
		public Icon get()
		{
			return icon;
		}
	}
	
	enum DeleteButton implements IconProvider
	{
		Delete(Resources.loadIcon("cross.png"));
		
		Icon icon;
		
		private DeleteButton(Icon icon)
		{
			this.icon = icon;
		}
		
		@Override
		public Icon get()
		{
			return icon;
		}
	}
	
	class IconCellRenderer extends JLabel implements TableCellRenderer
	{
		private CellColorProvider colorProvider;
		
		public IconCellRenderer()
		{
			setOpaque(true);
		}
		
		public void setColorProvider(CellColorProvider colorProvider)
		{
			this.colorProvider = colorProvider;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			colorProvider.colorCellComponent(this, table, value, isSelected, hasFocus, row, column);
			if(value instanceof IconProvider)
			{
				setIcon(((IconProvider) value).get());
			}
			return this;
		}
	}

	class IconToggleEditor extends DefaultCellEditor
	{
		protected JButton button;
		protected IconProvider value;
		
		private CellColorProvider colorProvider;
		
		public IconToggleEditor(JCheckBox checkBox)
		{
			super(checkBox);
			
			button = new JButton();
			button.setOpaque(true);
			button.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					fireEditingStopped();
				}
			});
		}
		
		public void setColorProvider(CellColorProvider colorProvider)
		{
			this.colorProvider = colorProvider;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			colorProvider.colorCellComponent(button, table, value, isSelected, true, row, column);
			this.value = (IconProvider) value;
			button.setIcon(this.value.get());
			return button;
		}

		@Override
		public Object getCellEditorValue()
		{
			return value;
		}

		@Override
		public boolean stopCellEditing()
		{
			return super.stopCellEditing();
		}

		@Override
		protected void fireEditingStopped()
		{
			super.fireEditingStopped();
		}
	}

	public class CommitListModel implements ListModel<String>
	{
		List<ListDataListener> listeners = new ArrayList<>();

		@Override
		public int getSize() {
			if(ext != null && ext.localExt != null && ext.localExt.isGitStateLoaded())
			{
				return ext.localExt.getCommitsSinceLastVersionTag().size();
			}
			return 0;
		}

		@Override
		public String getElementAt(int index) {
			if(ext != null && ext.localExt != null && ext.localExt.isGitStateLoaded())
			{
				return ext.localExt.getCommitsSinceLastVersionTag().get(index);
			}
			return "";
		}

		private void listUpdated()
		{
			int size = getSize();
			for(ListDataListener l : listeners)
			{
				l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, size));
			}
		}

		@Override
		public void addListDataListener(ListDataListener l) {
			listeners.add(l);
		}

		@Override
		public void removeListDataListener(ListDataListener l) {
			listeners.remove(l);
		}
	}

	@Override
	public void valueChanged(TreeSelectionEvent e)
	{
		Object o = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();
		if(o instanceof ExtData extData)
		{
			refresh(extData);
		}
	}
	
	public boolean hasLocal(Version v)
	{
		if(ext.netExt == null)
			return false;
		return RepmanMain.instance.getErm().getRepositories().get(ext.netExt.repository).hasVersionLocally(ext.netExt, v);
	}
	
	public void setHasLocal(Version v, boolean value)
	{
		if(ext.netExt == null)
			return;
		String action = value ? "Download" : "Delete";
		Object[] options = {action, "Cancel"};
		int n = JOptionPane.showOptionDialog(RepmanMain.instance,
			action + " this extension version locally?",
			"Local Version",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE,
			null,
			options,
			options[1]);
		
		if(n == JOptionPane.YES_OPTION)
			RepmanMain.instance.getErm().getRepositories().get(ext.netExt.repository).setHasVersionLocally(ext.netExt, v, value, () -> {
				versionTable.repaint();
			});
	}
}
