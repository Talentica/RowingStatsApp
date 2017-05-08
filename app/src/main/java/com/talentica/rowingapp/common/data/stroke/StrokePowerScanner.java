package com.talentica.rowingapp.common.data.stroke;

import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterListenerOwner;
import com.talentica.rowingapp.common.param.ParameterListenerRegistration;
import com.talentica.rowingapp.common.param.ParameterService;

/**
 * Sensor data pipeline component for detecting stroke rate.
 * Stroke rate detection is done by listening to uni-directional
 * acceleration data (i.e. output from AccelerationFilter) and
 * applying low-pass smoothing filter and stroke-strength change damping filter. 
 * Much of the dynamic stroke power/rate detection is implemented in {@link HalfSinoidDetector}
 * 
 */
public class StrokePowerScanner extends StrokeScannerBase implements BusEventListener, ParameterListenerOwner {
	

	private final ParameterListenerRegistration[] listenerRegistrations = {
			new ParameterListenerRegistration(ParamKeys.PARAM_STROKE_POWER_AMPLITUDE_FILTER_FACTOR.getId(), new ParameterChangeListener() {
				
				@Override
				public void onParameterChanged(Parameter param) {
					setAmplitudeFiltering((Float)param.getValue());
				}
			}),
			new ParameterListenerRegistration(ParamKeys.PARAM_STROKE_POWER_MIN_POWER.getId(), new ParameterChangeListener() {
				
				@Override
				public void onParameterChanged(Parameter param) {
					strokePowerTreshold = (Float)param.getValue();
				}
			})
	};
	
	private static final int POWER_CURVE_GRACE_DATA_COUNT = 4;


	private static final long NOSTROKE_MAX_TIME = 6000;

	private float strokePowerTreshold;
	
	private float strokePower;
	
	private long lastStrokeTimestamp;

	private long lastTimestamp;

	private float lastAccelAmplitued;

	private boolean strokeDone;

	private boolean hasPeak;

	private boolean hasDrop;
		
	private boolean powerDone;
	
	private int graceCounter;
	private int strokeRate;
	
	private final AppEventBus bus;

	private final ParameterService params;

	public StrokePowerScanner(AppStroke owner, StrokeRateScanner rateScanner) {
		super(owner.getBus(), (Float) owner.getParameters().getValue(ParamKeys.PARAM_STROKE_POWER_AMPLITUDE_FILTER_FACTOR.getId()));
		
		this.params = owner.getParameters();
		this.bus = owner.getBus();
		
		strokePowerTreshold = (Float)params.getValue(ParamKeys.PARAM_STROKE_POWER_MIN_POWER.getId());
		
		bus.addBusListener(this);

		params.addListeners(this);
	}

	/*	  
	 * @see SensorDataFilter#filterData(long, float[])
	 */
	@Override
	protected Object filterData(long timestamp, Object value) {
				
		Object filtered = super.filterData(timestamp, value);
		
		float[] values = (float[]) value;
		
		if (strokeDone) {
			return null;
		}
		
		float unfilteredAmplitude = values[0];		
		
		if (powerDone) {
			if (hasDrop && unfilteredAmplitude > 0) {
				powerDone = false;
			}
		} else if (hasPeak && unfilteredAmplitude < 0) {
			powerDone = true;
		}

		if (!powerDone) {
			calcPower(timestamp, unfilteredAmplitude);
		}
		
		if (powerDone && !strokeDone && ++graceCounter > POWER_CURVE_GRACE_DATA_COUNT) {
			endStroke(timestamp);			
		}
		
		return filtered;
	}

	private void newStroke(long timestamp) {
		strokeDone = false;
		if (strokeRate > 0) {
			bus.fireEvent(DataRecord.Type.STROKE_POWER_START, timestamp, (Object[]) null);
		}
	}

	@Override
	protected void onRiseAbove(long timestamp, float minVal) {
		if (strokeDone && hasDrop) {
			newStroke(timestamp);
		}
	}
	
	private void calcPower(long timestamp, float accelAmplitude) {
	    
		this.strokePower += accelAmplitude; // This is assuming accelerometer data already contains filtered moving avarage for a fixed period (e.g. 33ms), otherwise we would need to calculate timestamp - this.lastTimestamp diff
		
		this.lastAccelAmplitued = accelAmplitude;
		this.lastTimestamp = timestamp;
	}

	/**
	 * notify stoke power event
	 * @param timestamp 
	 */
	private void endStroke(long timestamp) {
		strokeDone = true;
		
		long msDiff = (timestamp - lastStrokeTimestamp) / 1000000;

		if (msDiff > NOSTROKE_MAX_TIME) {
			strokePower = 0;
		}
		lastStrokeTimestamp = timestamp;
		
		if (strokeRate > 0) {
			bus.fireEvent(DataRecord.Type.STROKE_POWER_END, timestamp, strokePower > strokePowerTreshold ? strokePower : 0);			
		}
		
		strokePower = 0;
	}


	@Override
	protected void onAccelerationTreshold(long timestamp, float amplitude) {
		
	}
	
	@Override
	protected void onDecelerationTreshold(long timestamp, float amplitude) {

	}
	
	@Override
	protected void onDropBelow(long timestamp, float maxVal) {
		if (!strokeDone && hasPeak) {
			endStroke(timestamp);
		}
	}
	
	@Override
	public void onBusEvent(DataRecord event) {
		switch (event.type) {
		case STROKE_ACCELERATION_TRESHOLD:
			hasPeak = true;
			hasDrop = false;
			break;
		case STROKE_DECELERATION_TRESHOLD:
			hasDrop = true;
			hasPeak = false;
			break;
		case STROKE_RATE:
			strokeRate = (Integer)event.data;
			break;
		}
	}

	public ParameterListenerRegistration[] getListenerRegistrations() {
		return listenerRegistrations;
	}
	
	@Override
	protected void finalize() throws Throwable {
		params.removeListeners(this);
		super.finalize();
	}	
}