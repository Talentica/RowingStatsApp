package com.talentica.rowingapp.common.filter.acceleration;

import android.util.Log;

import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.SensorDataFilter;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.filter.LowpassFilter;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterListenerOwner;
import com.talentica.rowingapp.common.param.ParameterListenerRegistration;
import com.talentica.rowingapp.common.param.ParameterService;

/**
 * Joins gravity-filtered 3 axis sensor data into acceleration amplitude value
 *
 */
public class AccelerationFilter extends SensorDataFilter implements ParameterListenerOwner {
	
	private final static int ROWER_MODE = 1;
	private final static int COAX_MODE = -1;
	protected int accelMode = ROWER_MODE;
	private final ParameterService params;

	private final LowpassFilter zeroY = new LowpassFilter(0.005f);
	private final LowpassFilter zeroZ = new LowpassFilter(0.005f);

	/**
	 * calculate horizontal acceleration amplitude according to device pitch
	 * @param values accelerometer sensor data
	 * @return acceleration amplitude
	 */
	private float calcAcceleration(final float[] values) {
		
		float y = values[DataIdx.ACCEL_Y];
		float z = values[DataIdx.ACCEL_Z];
		
		final double ay = y - zeroY.filter(new float[]{y})[0];
		final double az = z - zeroZ.filter(new float[]{z})[0];
		
		final double accelOrDecelDeterminer = Math.abs(ay) > Math.abs(az) ? -ay : az; // if device is exactly flat or vertical, one axis has to be ignored
		
		final int accelDir = accelMode * accelOrDecelDeterminer < 0 ? -1 : 1;
		
		final float a = (float)(accelDir * Math.sqrt(ay*ay + az*az));
				
		return a;
	}
	public AccelerationFilter(AppStroke owner) {
		this.params = owner.getParameters();
		
		accelMode = (Boolean)params.getValue(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId()) ? COAX_MODE : ROWER_MODE;

		params.addListeners(this);

	}
	
	@Override
	protected final Object filterData(long timestamp, Object value) {
		float[] values = (float[]) value;
		return new float[] {
				calcAcceleration(values)
		};
	}
	

	private final ParameterListenerRegistration[] listenerRegistrations = {
			new ParameterListenerRegistration(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId(), new ParameterChangeListener() {
				
				@Override
				public void onParameterChanged(Parameter param) {
					boolean coaxMode = (Boolean)param.getValue();
					Log.i("setting coax mode to {}", coaxMode+"");
					accelMode = coaxMode ?  COAX_MODE : ROWER_MODE;
				}
			})
	};
	
	@Override
	public ParameterListenerRegistration[] getListenerRegistrations() {
		return listenerRegistrations;
	}

	@Override
	protected void finalize() throws Throwable {
		params.removeListeners(this);
		super.finalize();
	}
}

