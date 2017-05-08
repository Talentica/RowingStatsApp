package com.talentica.rowingapp.ui.graph.views;

import android.content.Context;

import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.android.AndroidUILiaison;
import com.talentica.rowingapp.ui.graph.StrokePowerGraph;

/**
 * subclass of LineGraphView for setting acceleration specific parameters
 */
public class StrokePowerGraphView extends AndroidGraphViewBase<StrokePowerGraph> {
		
	public StrokePowerGraphView(Context context, AppStroke roboStroke) {
		super(context);
		
		setGraph(new StrokePowerGraph(new AndroidUILiaison(this), roboStroke));

	}
}