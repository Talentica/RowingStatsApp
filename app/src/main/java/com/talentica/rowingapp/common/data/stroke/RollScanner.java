
package com.talentica.rowingapp.common.data.stroke;

import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.SensorDataFilter;
import com.talentica.rowingapp.common.filter.LowpassFilter;

/**
 * Sensor data pipeline component for detecting boat tilt.
 * StrokeTiltScanner instance is to be added as a stroke listener to {@link StrokePowerScanner} 
 * and as data sink to {@link SensorDataInput#getOrientationDataSource()}
 *
 */
public class RollScanner extends SensorDataFilter implements BusEventListener {
	
	private static final float DEFAULT_TILT_DAMP_FACTOR = .01f;
	
	private final LowpassFilter tiltDamper = new LowpassFilter(DEFAULT_TILT_DAMP_FACTOR);

	private float[] tiltDamperValues = {0,0,0};

	private boolean tiltFrozen;
	
	private boolean insideStrokePower;
	
	private static class Roll {
		int sampleCount;
		float accummulatedRoll;
		float maxRoll;
		
		void add(float val) {
			sampleCount++;
			accummulatedRoll += val;
			
			if (Math.abs(val) > Math.abs(maxRoll)) {
				maxRoll = val;
			}
		}
		
		void reset() {
			sampleCount = 0;
			accummulatedRoll = 0;
			maxRoll = 0;
		}
		
		float[] get() {
			return new float[]{accummulatedRoll / sampleCount, maxRoll};
		}
	}
	
	private final Roll strokeRoll = new Roll();
	private final Roll recoveryRoll = new Roll();
	
	private final AppEventBus bus;

	private boolean hadPower;	

	public RollScanner(AppEventBus bus) {
		this.bus = bus;
		
		bus.addBusListener(this);
	}
	
	@Override
	protected Object filterData(long timestamp, Object value) {
		
		float[] values = (float[]) value;
		
		float[] filtered = new float[values.length];
		
		float unfilteredRoll = values[DataIdx.ORIENT_ROLL];
		
		System.arraycopy(values, 0, filtered, 0, values.length);
				
		float filteredRoll = unfilteredRoll - tiltDamperValues[DataIdx.ORIENT_ROLL];
		
		filtered[DataIdx.ORIENT_ROLL] = filteredRoll;
		
		if (insideStrokePower) {
			strokeRoll.add(filteredRoll);
		} else {
			recoveryRoll.add(filteredRoll);
		}
		
		if (!tiltFrozen) {
			tiltDamperValues  = tiltDamper.filter(values);
		}
		
		return filtered;
	}
	
	@Override
	public void onBusEvent(DataRecord event) {
		switch (event.type) {
		case STROKE_POWER_START:
			insideStrokePower = true;
			
			if (hadPower) {
				bus.fireEvent(DataRecord.Type.RECOVERY_ROLL, event.timestamp, recoveryRoll.get());
			}
			
			hadPower = false;
			recoveryRoll.reset();
			break;
		case STROKE_POWER_END:
			hadPower = (Float)event.data > 0;
			insideStrokePower = false;
			
			if (hadPower) {
				bus.fireEvent(DataRecord.Type.STROKE_ROLL, event.timestamp, strokeRoll.get());
			}
			
			strokeRoll.reset();
			break;
		case FREEZE_TILT:
			tiltFrozen = (Boolean)event.data;
			break;	
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		bus.removeBusListener(this);
		super.finalize();
	}
}