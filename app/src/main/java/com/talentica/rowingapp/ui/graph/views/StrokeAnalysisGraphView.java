package com.talentica.rowingapp.ui.graph.views;

import android.content.Context;
import android.widget.FrameLayout;

import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.android.AndroidUILiaison;
import com.talentica.rowingapp.ui.graph.DataUpdatable;
import com.talentica.rowingapp.ui.graph.StrokeAnalysisGraph;

/**
 * subclass of LineGraphView for setting acceleration specific parameters
 */
public class StrokeAnalysisGraphView extends FrameLayout implements DataUpdatable {

	private final StrokeAnalysisGraph graph;
	
	public StrokeAnalysisGraphView(Context context, AppStroke roboStroke) {
		
		super(context);
		
		StrokeAnalysisGraphSingleView g1 = new StrokeAnalysisGraphSingleView(context, roboStroke);
		StrokeAnalysisGraphSingleView g2 = new StrokeAnalysisGraphSingleView(context, roboStroke);
		
		LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

		addView(g1, layoutParams);
		addView(g2, layoutParams);
		
		graph = new StrokeAnalysisGraph(new AndroidUILiaison(this), roboStroke, g1.graph, g2.graph);
		
	}

	@Override
	public boolean isDisabled() {
		return graph.isDisabled();
	}

	@Override
	public void disableUpdate(boolean disable) {
		graph.disableUpdate(disable);
	}

	@Override
	public void reset() {
		graph.reset();
	}
	
	@Override
	protected void onAttachedToWindow() {
		disableUpdate(false);
		super.onAttachedToWindow();
	}
	
	@Override
	protected void onDetachedFromWindow() {
		disableUpdate(true);
		super.onDetachedFromWindow();
	}	
}