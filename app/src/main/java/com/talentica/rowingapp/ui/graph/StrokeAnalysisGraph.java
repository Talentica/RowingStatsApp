package com.talentica.rowingapp.ui.graph;

import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.utils.RSCanvas;
import com.talentica.rowingapp.ui.utils.UILiaison;

/**
 * subclass of LineGraphView for setting acceleration specific parameters
 */
public class StrokeAnalysisGraph implements UpdatableGraphBase {
	
	private static final int MIN_STROKE_RATE = 10;
	
	private int cur = 0;
	private int next = 1;
	
	private final StrokeAnalysisGraphSingle[] graphs;

	private boolean aboveStrokeRateTreshold;

	private final AppStroke roboStroke;
	
	private final UILiaison uiLiaision;
	
	public StrokeAnalysisGraph(UILiaison uiLiaision, AppStroke roboStroke, StrokeAnalysisGraphSingle g1, StrokeAnalysisGraphSingle g2) {
		
		this.uiLiaision = uiLiaision;

		this.roboStroke = roboStroke;
		
		graphs = new StrokeAnalysisGraphSingle[] {
				g1,
				g2
		};
				
		graphs[next].setVisible(false);

	}

	private final SensorDataSink privateRollDataSink = new SensorDataSink() {
		
		@Override
		public void onSensorData(long timestamp, Object value) {
			if (aboveStrokeRateTreshold) {
				synchronized (graphs) {
					graphs[next].getRollSink().onSensorData(timestamp, value);
				}
			}
		}
	};

	private final SensorDataSink privateAccelDataSink = new SensorDataSink() {
		
		@Override
		public void onSensorData(long timestamp, Object value) {
			if (aboveStrokeRateTreshold) {
				synchronized (graphs) {
					graphs[next].getAccelSink().onSensorData(timestamp, value);
				}
			}
		}
	};

	protected boolean needReset;

	
	private final BusEventListener privateBusListener = new BusEventListener() {
		
		@Override
		public void onBusEvent(DataRecord event) {
			switch (event.type) {
			case STROKE_RATE:
				aboveStrokeRateTreshold =  (Integer)event.data > MIN_STROKE_RATE;
				break;
			case STROKE_POWER_END:
				boolean hasPower = (Float)event.data > 0;
				
				if (!hasPower) {
					resetNext();					
				}
				
				if (aboveStrokeRateTreshold) {
					if (!needReset) {
						synchronized (graphs) {


							graphs[cur].reset();

							if (cur == 0) {
								cur = 1;
								next = 0;
							} else {
								cur = 0;
								next = 1;
							}

							graphs[next].setVisible(false);
							graphs[cur].setVisible(true);
							graphs[cur].repaint();
							
						}
					}

					needReset = false;
				}
			}
		}
	};

	private boolean disabled = true;

	private boolean attached;

	public void reset() {
		graphs[cur].reset();
		graphs[next].reset();
	}

	@Override
	public boolean isDisabled() {
		return disabled;		
	}
	
	@Override
	public synchronized void disableUpdate(boolean disable) {
		if (this.disabled != disable) {
			if (!disable) {
				attachSensors();
			} else {
				resetNext();
				detachSensors();
			}	

			this.disabled = disable;
		}
	}

	private void detachSensors() {
		
		if (attached) {
			roboStroke.getAccelerationSource().removeSensorDataSink(privateAccelDataSink);
			roboStroke.getOrientationSource().removeSensorDataSink(privateRollDataSink);
			roboStroke.getBus().removeBusListener(privateBusListener);
			attached = false;
		}
	}

	private void attachSensors() {
		if (!attached) {
			roboStroke.getBus().addBusListener(privateBusListener);
			roboStroke.getAccelerationSource().addSensorDataSink(privateAccelDataSink);
			roboStroke.getOrientationSource().addSensorDataSink(privateRollDataSink);
			
			attached = true;
		}
	}


	private void resetNext() {
		needReset = true;
		graphs[next].reset();
	}

	@Override
	public void draw(RSCanvas canvas) {
		
	}

	@Override
	public void setVisible(boolean visible) {
		uiLiaision.setVisible(visible);
	}

	@Override
	public void repaint() {
		uiLiaision.repaint();
	}
}
