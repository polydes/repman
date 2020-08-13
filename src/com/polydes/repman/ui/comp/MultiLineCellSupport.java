package com.polydes.repman.ui.comp;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * http://blog.botunge.dk/post/2009/10/09/JTable-multiline-cell-renderer.aspx
 */
public class MultiLineCellSupport extends AbstractCellEditor implements TableCellEditor, TableCellRenderer
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
//				Border b = getBorder();
//				if(b instanceof LineBorder)
//				{
//					int width = getWidth();
//					int height = getHeight();
//					LineBorder lb = (LineBorder) b;
//					
//					Shape clip = g.getClip();
//					g.setClip(null);
//					
//					lb.paintBorder(this, g, 0, 0, width, height);
//					lb.paintBorder(this, g, -1, -1, width+2, height+2);
//					
//					g.setClip(clip);
//				}
//				else
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