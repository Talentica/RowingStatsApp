package com.talentica.rowingapp.ui.graph;

import com.talentica.rowingapp.common.DoubleGenerator;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.utils.PaintStyle;
import com.talentica.rowingapp.ui.utils.RSCanvas;
import com.talentica.rowingapp.ui.utils.RSPaint;
import com.talentica.rowingapp.ui.utils.RSRect;
import com.talentica.rowingapp.ui.utils.UILiaison;

import java.util.concurrent.TimeUnit;

/**
 * subclass of LineGraphView for setting stroke specific parameters
 */
public class StrokeAnalysisGraphSingle extends SensorGraphBase {
	
	private static final long MAX_TIME_CAPTURE = TimeUnit.SECONDS.toNanos(20);
	private static final long MIN_XRANGE = TimeUnit.MILLISECONDS.toNanos(500);
	
	private long timeCaptureStart;
	private final RollOverlayType rollOverlayType = RollOverlayType.TOP;
	
	
	private static final float Y_SCALE = 8f;

	@SuppressWarnings("serial")
	private final CyclicArrayXYSeries rollPanelSeries = new CyclicArrayXYSeries(XYSeries.XMode.GROWING, new XYSeries.Renderer(uiLiaison.createPaint())) {
		{
			setIndependantYAxis(true);				
		}
	};
	
	private final RSPaint rollGraphPaint = uiLiaison.createPaint();
	
	
	private final CyclicArrayXYSeries rollSeries;
	
	private final RollGraphOverlay rollGraph;
	
	@SuppressWarnings("serial")
	public StrokeAnalysisGraphSingle(UILiaison factory, AppStroke roboStroke) {
		super(factory, XYSeries.XMode.GROWING, MIN_XRANGE, Y_SCALE, roboStroke);

		rollGraphPaint.setStyle(PaintStyle.STROKE);
		rollGraphPaint.setColor(uiLiaison.getYellowColor());
		rollGraphPaint.setAlpha(170);

		rollSeries = new CyclicArrayXYSeries(XYSeries.XMode.GROWING, new XYSeries.Renderer(rollGraphPaint)) {
			{
				setIndependantYAxis(true);
				setyAxisSize(Y_SCALE);
				setxRange(MIN_XRANGE);
			}
		};

		multySeries.setxRangeGenerator(new DoubleGenerator() {
			@Override
			public double get() {
				return accelSeries.getMaxX() - accelSeries.getMinX();
			}
		});
		multySeries.addSeries(rollPanelSeries, false);
		multySeries.addSeries(rollSeries, false);
		rollGraph = new RollGraphOverlay(factory, multySeries);
	}

	@Override
	public void setXRange(double val) {
		// disable external call to setXRange()
	}
	
	
	@Override
	protected void drawSeries(RSCanvas canvas, RSRect rect, double xAxisSize,
							  double yAxisSize, XYSeries series) {
		
		if (series != rollPanelSeries) { 
			super.drawSeries(canvas, rect, xAxisSize, yAxisSize, series);
		}
		
	}
	
	@Override
	protected void drawGraph(RSCanvas canvas, RSRect rect, double xAxisSize, double yAxisSize) {
		
		RSRect rollBarsRect = new RSRect(rect);
		
		int rollBarsHeight = (int) (rollOverlayType.clipHeightPercent * rect.height());
		
		switch (rollOverlayType) {
		case BACKGROUND:
			// nothing to do
			break;
		case BOTTOM:
			rollBarsRect.top = rollBarsRect.bottom - rollBarsHeight;
			rect.bottom -= rollBarsHeight;
			break;
		case TOP:
			rollBarsRect.bottom = rollBarsRect.top + rollBarsHeight;
			rect.top += rollBarsHeight;
			break;
		}
		
		rollGraph.drawRollPanels(canvas, rollBarsRect, xAxisSize);
		
		super.drawGraph(canvas, rect, xAxisSize, yAxisSize);
	}
	
	
	@Override
	public void reset() {
		synchronized (multySeries) {
			rollGraph.reset();
			timeCaptureStart = 0;
			super.reset();
		}
	}
	
	private boolean checkCaptureTimeInRange(long timestamp) {
		
		if (timeCaptureStart == 0) {
			timeCaptureStart = timestamp;
			return true;
		} 
		
		return (Math.abs(timestamp - timeCaptureStart) < MAX_TIME_CAPTURE);
	}
	
	private SensorDataSink rollSink = new SensorDataSink() {
		
		@Override
		public void onSensorData(long timestamp, Object value) {
			if (!checkCaptureTimeInRange(timestamp)) {
				return;
			}
			
			rollGraph.onSensorData(timestamp, value);
		}
		
	};
	
	

	private SensorDataSink accelSink;

	{
		accelSink = new SensorDataSink() {

			@Override
			public void onSensorData(long timestamp, Object value) {
				if (!checkCaptureTimeInRange(timestamp)) {
					return;
				}
				accelDataSink.onSensorData(timestamp, value);
			}
		};
	}

	public SensorDataSink getRollSink() {
		return rollSink;
	}
	
	public SensorDataSink getAccelSink() {
		return accelSink;
	}

	@Override
	protected void detachSensors(SensorDataSink lineDataSink) {
	}

	@Override
	protected void attachSensors(SensorDataSink lineDataSink) {
	}
}