package com.talentica.rowingapp.ui.graph;


import com.talentica.rowingapp.common.Pair;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.ui.utils.UILiaison;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * subclass of LineGraphView for setting stroke specific parameters
 */
public abstract class SensorGraphBase extends LineGraph {
	
	private static final int DATA_QUEUE_SIZE = 10;
	private static final float INCR = 1f;
	protected final XYSeries accelSeries;
	protected final AppStroke roboStroke;
	
	private boolean attached;
	
	private class SensorDataSinkQueue extends Thread implements SensorDataSink {
		
		private final ArrayBlockingQueue<Pair<Long,Float>> queue;
				
		public SensorDataSinkQueue(int queueSize) {
			
			super("SensorDataSinkQueue " + SensorGraphBase.this.getClass());
			
			setDaemon(true);
			
			queue = new ArrayBlockingQueue<Pair<Long,Float>>(queueSize);
			
			start();
			
		}
		
		@Override
		public void onSensorData(long timestamp, Object value) {

			try {				
				queue.put(Pair.create(timestamp, ((float[])value)[0]));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		void reset() {
			queue.clear();
		}
		
		@Override
		public void run() {
			while (true) {
							    
				try {

					Pair<Long, Float> p = queue.take();
					accelSeries.add(p.first, p.second);
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			}
		}
	}
	
	protected final SensorDataSink accelDataSink = new SensorDataSinkQueue(DATA_QUEUE_SIZE);
		
	public SensorGraphBase(UILiaison factory, XYSeries.XMode xMode, float xRange, float yRange, AppStroke roboStroke)	{
		super(factory, xRange, xMode, yRange, INCR);
		
		this.roboStroke = roboStroke;
		accelSeries = multySeries.addSeries(new CyclicArrayXYSeries(xMode,  new XYSeries.Renderer(uiLiaison.createPaint())));
	}


	@Override
	public synchronized void disableUpdate(boolean disable) {
		if (isDisabled() != disable) {
			if (!disable) {
				if (!attached) {
					attachSensors();
					attached = true;
				}
			} else {				
				
				if (attached) {
					detachSensors();
					attached = false;
				}
				reset();
			}
			super.disableUpdate(disable);
		}
	}

	@Override
	public void reset() {
		((SensorDataSinkQueue)accelDataSink).reset();
		super.reset();
	}
	
	protected void attachSensors() {
		attachSensors(accelDataSink);
	}

	protected void detachSensors() {
		detachSensors(accelDataSink);
	}

	
	protected abstract void detachSensors(SensorDataSink lineDataSink);


	protected abstract void attachSensors(SensorDataSink lineDataSink);
	
}