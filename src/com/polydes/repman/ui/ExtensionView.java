package com.polydes.repman.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.polydes.repman.Extension;
import com.polydes.repman.ExtensionDependency;
import com.polydes.repman.ExtensionRepository;
import com.polydes.repman.ExtensionType;
import com.polydes.repman.LocalRepoBackend.ExtensionVersion;
import com.polydes.repman.Version;
import com.polydes.repman.data.RepositoryFTP;
import com.polydes.repman.data.Sources;
import com.polydes.repman.res.Resources;
import com.polydes.repman.util.Util;

public class ExtensionView extends JPanel implements TreeSelectionListener
{
	private static final Logger log = Logger.getLogger(ExtensionView.class);
	
	Extension ext;
	JTable versionTable;
	VersionTableModel vtableModel;
	
	JButton buildNewButton;
	JButton uploadToRepoButton;
	
	public ExtensionView()
	{
		super(new BorderLayout());
		vtableModel = new VersionTableModel();
		buildNewButton = new JButton("Build New Version");
		buildNewButton.addActionListener((e) -> {
			try
			{
				Sources.buildSource(ext, (newVersion) -> {
					ext.versions.add(newVersion);
					ext.versions.sort(null);
					RepmanMain.instance.getErm().getRepositories().get(ext.repository).refreshInstalledVersions(ext);
//					vtableModel.fireTableDataChanged();
//					repaint();
					Extension toRefresh = ext;
					ext = null;
					refresh(toRefresh);
				});
			}
			catch(Exception ex)
			{
				log.error(ex.getMessage(), ex);
				JOptionPane.showMessageDialog(RepmanMain.instance, ex.getMessage(), "Build Failed", JOptionPane.ERROR_MESSAGE);
			}
		});
		uploadToRepoButton = new JButton("Upload to Repository");
		uploadToRepoButton.addActionListener((e) -> {
			try
			{
				ExtensionRepository repo = RepmanMain.instance.getErm().getRepositories().get(ext.repository);
				
				File extFolder = repo.getExtensionLocalLocation(ext);
				
				File revisionFile = new File(extFolder, "revision");
				int revision = 0;
				if(revisionFile.exists())
					revision = Util.parseInt(FileUtils.readFileToString(revisionFile), 0);
				++revision;
				FileUtils.writeStringToFile(revisionFile, "" + revision);
				
				File iconFile = new File(extFolder, "icon.png");
				BufferedImage bi = new BufferedImage(ext.icon.getIconWidth(), ext.icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics g = bi.createGraphics();
				ext.icon.paintIcon(null, g, 0,0);
				g.dispose();
				ImageIO.write(bi, "png", iconFile);
				
				File infoTxtFile = new File(extFolder, "info.txt");
				List<String> lines = Arrays.asList(
					"Name=" + ext.name,
					"Description=" + ext.description,
					"Author=" + ext.author,
					"Website=" + ext.website,
					(ext.type == ExtensionType.TOOLSET) ?
							"Type=" + ext.cat.toString().toLowerCase(Locale.ENGLISH) : ""
				);
				FileUtils.writeLines(infoTxtFile, lines, "\n");
				
				File versionsFile = new File(extFolder, "versions.json");
				JSONObject j = new JSONObject();
				JSONArray jver = new JSONArray();
				ext.versions.forEach(v -> jver.put(v.toJSON()));
				j.put("versions", jver);
				FileUtils.writeStringToFile(versionsFile, j.toString());
				
				List<String> filesToUpload = new ArrayList<>();
				filesToUpload.addAll(Arrays.asList(
					"revision",
					"icon.png",
					"info.txt",
					"versions.json"
				));
				for(ExtensionVersion v : ext.versions)
					if(v.local)
						filesToUpload.add(v.version + ".zip");
				
				RepositoryFTP.upload(repo, ext, filesToUpload);
				
				for(ExtensionVersion v : ext.versions)
				{
					v.local = false;
					v.dirty = false;
				}
				
				Extension toRefresh = ext;
				ext = null;
				refresh(toRefresh);
			}
			catch(Exception ex)
			{
				log.error(ex.getMessage(), ex);
				JOptionPane.showMessageDialog(RepmanMain.instance, ex.getMessage(), "Upload Failed", JOptionPane.ERROR_MESSAGE);
			}
		});
	}
	
	public void refresh(Extension ext)
	{
		if(this.ext == ext)
			return;
		
		this.ext = ext;
		
		removeAll();
		
		int pad = 10;
		setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
		
		JPanel extensionInfoPanel = new JPanel();
		extensionInfoPanel.setLayout(new BoxLayout(extensionInfoPanel, BoxLayout.Y_AXIS));
		
		JLabel extensionLabel = new JLabel("<html>" + ext.name + "<br>(" + ext.id + ")</html>");
		extensionLabel.setIcon(ext.icon);
		extensionLabel.setFont(getFont().deriveFont(14.0f).deriveFont(Font.BOLD));
		
		JLabel infoLabel = new JLabel
		(
			"<html><b>" +
			"Author: " + ext.author + "<br>" +
			"Description: " + ext.description + "<br>" +
			(ext.type == ExtensionType.TOOLSET ? "Toolset Category: " + ext.cat.toString() + "<br>" : "") +
			"Website: " + ext.website + "<br>" +
			"Local Source: " + Sources.getSource(ext) + 
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
		
		CellColorProvider colorProvider = new ExtensionVersionCellColorProvider();
		
		versionTable = new JTable(vtableModel)
		{
//			JComponent borderDummy = new JLabel();
//			int lastFocusRow = -1;
//			int lastFocusColumn = -1;
//			
//			{
//				borderDummy = new JLabel();
//				borderDummy.setOpaque(false);
//				add(borderDummy);
//				borderDummy.setVisible(false);
//			}
//			
//			@Override
//			protected void paintComponent(Graphics g)
//			{
//				super.paintComponent(g);
//				
//				int focusRow = getSelectionModel().getLeadSelectionIndex();
//				int focusColumn = getColumnModel().getSelectionModel().getLeadSelectionIndex();
//				
//				if(lastFocusRow == focusRow && lastFocusColumn == focusColumn)
//					return;
//				
//				boolean hasFocus = focusRow != -1 && focusColumn != -1;
//				borderDummy.setVisible(hasFocus);
//				
//				if(hasFocus)
//				{
//					Rectangle r = versionTable.getCellRect(focusRow, focusColumn, false);
//					colorProvider.colorCellComponent(borderDummy, this, null, true, true, focusRow, focusColumn);
//					LineBorder b = (LineBorder) borderDummy.getBorder();
//					Color c = b.getLineColor();
//					borderDummy.setBackground(null);
//					borderDummy.setForeground(null);
//					borderDummy.setBorder(new LineBorder(c, 2));
//					r.grow(1, 1);
//					borderDummy.setBounds(r);
//					
//					lastFocusRow = focusRow;
//					lastFocusColumn = focusColumn;
//				}
//			}
		};
		
		MultiLineCellSupport mlsRender = new MultiLineCellSupport();
		MultiLineCellSupport mlsEdit = new MultiLineCellSupport(mlsRender);
		
		
		mlsRender.setColorProvider(colorProvider);
		
		mlsRender.addClassSupport
		(
			Version.class,
			(v) -> v.toString(),
			(s) -> new Version(s)
		);
		
		mlsRender.addClassSupport
		(
			ExtensionDependency[].class,
			(deps) -> StringUtils.join(deps, "\n"),
			(s) -> ExtensionDependency.fromStringList(s.replaceAll("\n", ","), null)
		);
		
		IconCellRenderer iconRenderer = new IconCellRenderer();
		IconToggleEditor iconToggleEditor = new IconToggleEditor(new JCheckBox());
		iconRenderer.setColorProvider(colorProvider);
		iconToggleEditor.setColorProvider(colorProvider);
		
		versionTable.setDefaultRenderer(Version.class, mlsRender);
		versionTable.setDefaultEditor(Version.class, mlsEdit);
		versionTable.setDefaultRenderer(String.class, mlsRender);
		versionTable.setDefaultEditor(String.class, mlsEdit);
		versionTable.setDefaultRenderer(ExtensionDependency[].class, mlsRender);
		versionTable.setDefaultEditor(ExtensionDependency[].class, mlsEdit);
		
		versionTable.setDefaultRenderer(LocalDownloadState.class, iconRenderer);
		versionTable.setDefaultEditor(LocalDownloadState.class, iconToggleEditor);
		versionTable.setDefaultRenderer(LocalInstallState.class, iconRenderer);
		versionTable.setDefaultEditor(LocalInstallState.class, iconToggleEditor);
		versionTable.setDefaultRenderer(DeleteButton.class, iconRenderer);
		versionTable.setDefaultEditor(DeleteButton.class, iconToggleEditor);
		
//		resizeColumnWidth(versionTable);
		versionTable.getTableHeader().getColumnModel().getColumn(0).setMaxWidth(60);
		versionTable.getTableHeader().getColumnModel().getColumn(1).setMaxWidth(80);
		versionTable.getTableHeader().getColumnModel().getColumn(4).setMaxWidth(20);
		versionTable.getTableHeader().getColumnModel().getColumn(5).setMaxWidth(20);
		versionTable.getTableHeader().getColumnModel().getColumn(6).setMaxWidth(20);
		
		versionTable.setGridColor(new Color(0x999999));
		
		add(new JScrollPane(versionTable), BorderLayout.CENTER);
		
		revalidate();
		repaint();
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
				case 0: return "Version";
				case 1: return "Date";
				case 2: return "Changes";
				case 3: return "Dependencies";
				case 4: return "L"; //Local
				case 5: return "I"; //Installed
				case 6: return "R"; //Remove
				default: return "";
			}
		}
		
		@Override
		public int getColumnCount()
		{
			return 7;
		}
		
		@Override
		public int getRowCount()
		{
			if(ext == null)
				return 0;
			return ext.versions.size();
		}
		
		@Override
		public Object getValueAt(int row, int column)
		{
			if(ext == null)
				return null;
			ExtensionVersion v = ext.versions.get(ext.versions.size() - (row + 1));
			switch(column)
			{
				case 0: return v.version;
				case 1: return v.date;
				case 2: return v.changes;
				case 3: return v.dependencies;
				case 4: return hasLocal(v) ? LocalDownloadState.Delete : LocalDownloadState.Download;
				case 5: return isInstalled(v) ? LocalInstallState.Uninstall : LocalInstallState.Install;
				case 6: return DeleteButton.Delete;
				default: return null;
			}
		}
		
		@Override
		public void setValueAt(Object aValue, int row, int column)
		{
			if(ext == null)
				return;
			ExtensionVersion v = ext.versions.get(ext.versions.size() - (row + 1));
			switch(column)
			{
				case 0: v.version = (Version) aValue; ext.versions.sort(null); versionTable.repaint(); break;
				case 1: v.date = (String) aValue; v.dirty = true; break;
				case 2: v.changes = (String) aValue; v.dirty = true; break;
				case 3: v.dependencies = (ExtensionDependency[]) aValue; v.dirty = true; break;
				case 4: setHasLocal(v, aValue == LocalDownloadState.Download); break;
				case 5: setInstalled(v, aValue == LocalInstallState.Install); break;
				case 6: removeVersion(v); break;
				default: break;
			}
		}
		
		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			switch(columnIndex)
			{
				case 0: return Version.class;
				case 1: return String.class;
				case 2: return String.class;
				case 3: return ExtensionDependency[].class;
				case 4: return LocalDownloadState.class;
				case 5: return LocalInstallState.class;
				case 6: return DeleteButton.class;
				default: return null;
			}
		}
		
		@Override
		public boolean isCellEditable(int row, int column)
		{
			if(column == 0)
				return false;
			return super.isCellEditable(row, column);
		}
	}
	
	/**
	 * http://blog.botunge.dk/post/2009/10/09/JTable-multiline-cell-renderer.aspx
	 */
	class MultiLineCellSupport extends AbstractCellEditor implements TableCellEditor, TableCellRenderer
	{
		private JTextArea jtextarea;
		private CellColorProvider colorProvider;
		
		private JTable table;
		private int row;
		private int col;
		private Class<?> valueClass;
		private Map<Class<?>, Pair<Function<Object,String>,Function<String,Object>>> stringTransformers;
		private boolean editing = false;
		
		private List<List<Integer>> rowColHeight;
		
		public MultiLineCellSupport()
		{
			this(null);
		}
		
		public MultiLineCellSupport(MultiLineCellSupport peer)
		{
			if(peer == null)
			{
				rowColHeight = new ArrayList<>();
				stringTransformers = new HashMap<>();
			}
			else
			{
				rowColHeight = peer.rowColHeight;
				stringTransformers = peer.stringTransformers;
			}
			
			jtextarea = new JTextArea()
			{
				@Override
				protected void paintBorder(Graphics g)
				{
//					Border b = getBorder();
//					if(b instanceof LineBorder)
//					{
//						int width = getWidth();
//						int height = getHeight();
//						LineBorder lb = (LineBorder) b;
//						
//						Shape clip = g.getClip();
//						g.setClip(null);
//						
//						lb.paintBorder(this, g, 0, 0, width, height);
//						lb.paintBorder(this, g, -1, -1, width+2, height+2);
//						
//						g.setClip(clip);
//					}
//					else
						super.paintBorder(g);
				}
			};
			jtextarea.setLineWrap(true);
			jtextarea.setWrapStyleWord(true);
			jtextarea.setOpaque(true);
			jtextarea.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override public void removeUpdate(DocumentEvent e) { update(e); }
				@Override public void insertUpdate(DocumentEvent e) { update(e); }
				@Override public void changedUpdate(DocumentEvent e) { update(e); }
				
				public void update(DocumentEvent e)
				{
					if(editing)
					{
						final int thisRow = row, thisCol = col;
						//Don't adjust until later because it doesn't seem to update right away for whatever reason.
						SwingUtilities.invokeLater(()->{
							adjustRowHeight(table, thisRow, thisCol);
						});
					}
				}
			});
		}
		
		@SuppressWarnings({"unchecked", "rawtypes"})
		public <T> void addClassSupport(Class<T> cls, Function<T,String> toStringFunction, Function<String,T> fromStringFunction)
		{
			Function toStringFunctionUntyped = toStringFunction;
			Function fromStringFunctionUntyped = fromStringFunction;
			
			stringTransformers.put(cls, new ImmutablePair<>(toStringFunctionUntyped, fromStringFunctionUntyped));
		}
		
		private String getString(Object value, Class<?> cls)
		{
			if(value == null)
				return "";
			if(value instanceof String)
				return (String) value;
			return stringTransformers.get(cls).getLeft().apply(value);
		}
		
		private Object getValue(String s, Class<?> cls)
		{
			if(cls == String.class)
				return s;
			return stringTransformers.get(cls).getRight().apply(s);
		}
		
		public void setColorProvider(CellColorProvider colorProvider)
		{
			this.colorProvider = colorProvider;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			colorProvider.colorCellComponent(jtextarea, table, value, isSelected, hasFocus, row, column);
			
			jtextarea.setFont(table.getFont());
			jtextarea.setText(getString(value, value.getClass()));
			adjustRowHeight(table, row, column);
			
			this.table = table;
			this.row = row;
			this.col = column;
			
			return jtextarea;
		}
		
		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int rowIndex, int vColIndex)
		{
			valueClass = value.getClass();
			editing = true;
			
			value = getString(value, valueClass);
			
			this.table = table;
			this.row = rowIndex;
			this.col = vColIndex;
			
			jtextarea.setText((String) value);

			jtextarea.setBorder(new LineBorder(Color.BLACK));
			
			return jtextarea;
		}

		@Override
		public Object getCellEditorValue()
		{
			String text = jtextarea.getText();
			
			return getValue(text, valueClass);
		}
		
		@Override
		public void cancelCellEditing()
		{
			editing = false;
			super.cancelCellEditing();
		}
		
		@Override
		public boolean stopCellEditing()
		{
			boolean value = super.stopCellEditing();
			if(value)
				editing = false;
			return value;
		}
		
		@Override
		public boolean isCellEditable(EventObject e)
		{
			if(e instanceof MouseEvent)
			{
				return ((MouseEvent) e).getClickCount() == 2;
			}
			
			return super.isCellEditable(e);
		}
		
		/**
		 * Calculate the new preferred height for a given row, and sets the
		 * height on the table.
		 */
		private void adjustRowHeight(JTable table, int row, int column)
		{
			int cWidth = table.getTableHeader().getColumnModel().getColumn(column).getWidth();
			
			Insets i = jtextarea.getInsets();
			jtextarea.setSize(new Dimension(cWidth, i.top + i.bottom + 1));
			int prefH = jtextarea.getPreferredSize().height;
			while(rowColHeight.size() <= row)
			{
				rowColHeight.add(new ArrayList<Integer>(column));
			}
			List<Integer> colHeights = rowColHeight.get(row);
			while(colHeights.size() <= column)
			{
				colHeights.add(0);
			}
			colHeights.set(column, prefH);
			int maxH = prefH;
			for(Integer colHeight : colHeights)
			{
				if(colHeight > maxH)
				{
					maxH = colHeight;
				}
			}
			if(table.getRowHeight(row) != maxH)
			{
				table.setRowHeight(row, maxH);
			}
			jtextarea.setSize(new Dimension(cWidth, maxH));
		}
	}
	
	interface CellColorProvider
	{
		void colorCellComponent(JComponent c, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column);
	}
	
	class ExtensionVersionCellColorProvider implements CellColorProvider
	{
		private Border noFocusBorder;
		private Border localFocusBorder;
		private Border dirtyFocusBorder;
		
		Color localColor = new Color(0xB8EDB4);
		Color dirtyColor = new Color(0xEACCB2);
		
		public ExtensionVersionCellColorProvider()
		{
			//XXX
			Border focusBorder = UIManager.getBorder("Table.focusCellHighlightBorder");
			if(focusBorder != null)
			{
				Map<String, Object> props = Util.getProps(focusBorder, "thickness");
				int thickness = (Integer) props.get("thickness");
//				Color color = (Color) props.get("color");
				
				noFocusBorder = BorderFactory.createEmptyBorder(thickness, thickness, thickness, thickness);
				localFocusBorder = BorderFactory.createLineBorder(new Color(0x85C685), thickness);
				dirtyFocusBorder = BorderFactory.createLineBorder(new Color(0xE2A87F), thickness);
			}
		}
		
		@Override
		public void colorCellComponent(JComponent c, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			ExtensionVersion ev = ext.versions.get(ext.versions.size() - (row + 1));
			
			if(isSelected)
			{
				c.setForeground(table.getSelectionForeground());
				c.setBackground(table.getSelectionBackground());
			}
			else
			{
				c.setForeground(table.getForeground());
				c.setBackground(table.getBackground());
			}
			
			if(ev.local)
				c.setBackground(localColor);
			else if(ev.dirty)
				c.setBackground(dirtyColor);
			
			if(hasFocus)
			{
				if(ev.local)
					c.setBorder(localFocusBorder);
				else if(ev.dirty)
					c.setBorder(dirtyFocusBorder);
				else
					c.setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
			}
			else
			{
				c.setBorder(noFocusBorder);
			}
		}
	}
	
	interface IconProvider extends Supplier<Icon>{}
	
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
	
	enum LocalInstallState implements IconProvider
	{
		Install(Resources.loadIcon("package.png")),
		Uninstall(Resources.loadIcon("package_link.png"));
		
		Icon icon;
		
		private LocalInstallState(Icon icon)
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
	
	@Override
	public void valueChanged(TreeSelectionEvent e)
	{
		Object o = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();
		if(o instanceof Extension)
		{
			refresh((Extension) o);
		}
	}
	
	public boolean hasLocal(ExtensionVersion v)
	{
		return RepmanMain.instance.getErm().getRepositories().get(ext.repository).hasVersionLocally(ext, v.version);
	}
	
	public void setHasLocal(ExtensionVersion v, boolean value)
	{
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
			RepmanMain.instance.getErm().getRepositories().get(ext.repository).setHasVersionLocally(ext, v.version, value, () -> {
				versionTable.repaint();
			});
	}
	
	public boolean isInstalled(ExtensionVersion v)
	{
		return v.installed;
	}
	
	public void setInstalled(ExtensionVersion v, boolean value)
	{
		ExtensionRepository repo = RepmanMain.instance.getErm().getRepositories().get(ext.repository);
		if(value && !repo.hasVersionLocally(ext, v.version))
		{
			JOptionPane.showMessageDialog(RepmanMain.instance, "Download this version before installing.", "Can't install.", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		String action = value ? "Install" : "Uninstall";
		Object[] options = {action, "Cancel"};
		int n = JOptionPane.showOptionDialog(RepmanMain.instance,
			action + " this extension version in Stencyl?",
			"Installed Version",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE,
			null,
			options,
			options[1]);
		
		if(n == JOptionPane.YES_OPTION)
			repo.setInstalledVersion(ext, v.version, value, () -> {
				versionTable.repaint();
			});
	}
	
	public void removeVersion(ExtensionVersion v)
	{
		Object[] options = {"Delete", "Cancel"};
		int n = JOptionPane.showOptionDialog(RepmanMain.instance,
			"Remove this version from the extension?",
			"Remove Version",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE,
			null,
			options,
			options[1]);
		
		if(n == JOptionPane.YES_OPTION)
		{
			ext.versions.remove(v);
			vtableModel.fireTableDataChanged();
		}
	}
}
