package com.talentica.rowingapp.ui.graph;

import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.utils.UILiaison;

/**
 * subclass of LineGraphView for setting stroke specific parameters
 */
public class StrokeGraph extends SensorGraphBase {
	private static final float Y_RANGE = 4f;

	public StrokeGraph(UILiaison factory, float xRange, AppStroke roboStroke)	{
		super(factory, XYSeries.XMode.ROLLING, xRange, Y_RANGE, roboStroke);
	}
	
	@Override
	protected synchronized void attachSensors(SensorDataSink lineDataSink) {
		roboStroke.getStrokeRateScanner().addSensorDataSink(lineDataSink);
	}
	
	@Override
	protected void detachSensors(SensorDataSink lineDataSink) {
		roboStroke.getStrokeRateScanner().removeSensorDataSink(lineDataSink);
	}
}