package com.talentica.rowingapp.ui.graph.views;

import android.content.Context;

import com.talentica.rowingapp.ui.android.AndroidUILiaison;
import com.talentica.rowingapp.ui.graph.LineGraph;
import com.talentica.rowingapp.ui.graph.MultiXYSeries;
import com.talentica.rowingapp.ui.graph.XYSeries;

/**
 * Simple line graph plot view.
 * 
 */
public class LineGraphView extends AndroidGraphViewBase<LineGraph> {
		
	public LineGraphView(Context context, double xRange, XYSeries.XMode xMode, double yScale,
						 double yGridInterval) {
		this(context, yScale, yGridInterval,  null);
	}
	
	/**
	 * constructor with standard View context, attributes, data window size, y
	 * scale and y data tic mark gap
	 * 
	 * @param context
	 *            the Android Activity
	 */
	public LineGraphView(Context context, double yRange,
			double yGridInterval, MultiXYSeries multiSeries) {
		super(context);
		
		setGraph(new LineGraph(new AndroidUILiaison(this), yRange, yGridInterval, multiSeries));

	}
}
