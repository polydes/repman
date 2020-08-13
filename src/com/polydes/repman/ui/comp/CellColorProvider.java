package com.polydes.repman.ui.comp;

import javax.swing.JComponent;
import javax.swing.JTable;

public interface CellColorProvider
{
	void colorCellComponent(JComponent c, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column);
}