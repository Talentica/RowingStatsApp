package com.talentica.rowingapp.ui.utils;

public interface RSView extends HasVisibility, HasBackgroundColor {
	void setOnLongClickListener(RSLongClickListener listener);
	void setOnClickListener(RSClickListener listener);
	void setOnDoubleClickListener(RSDoubleClickListener listener);
}
