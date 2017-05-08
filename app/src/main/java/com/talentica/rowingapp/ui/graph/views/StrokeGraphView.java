package com.talentica.rowingapp.ui.graph.views;

import android.content.Context;

import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.android.AndroidUILiaison;
import com.talentica.rowingapp.ui.graph.StrokeGraph;

/**
 * subclass of LineGraphView for setting stroke specific parameters
 */
public class StrokeGraphView extends AndroidGraphViewBase<StrokeGraph> {
	
	
	public StrokeGraphView(Context context, float xRange, AppStroke roboStroke) {
		
		super(context);
		
		setGraph(new StrokeGraph(new AndroidUILiaison(this), xRange, roboStroke));
	}
}