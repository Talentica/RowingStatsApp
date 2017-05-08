package com.talentica.rowingapp.common.data.way;

import android.util.Log;

import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.filter.LowpassFilter;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterListenerOwner;
import com.talentica.rowingapp.common.param.ParameterListenerRegistration;
import com.talentica.rowingapp.common.param.ParameterService;

public class GPSDataFilter implements SensorDataSink, ParameterListenerOwner {

	private double accumulatedDistance = 0;

	private static class LocationData {
		long timestamp;
		double[] values;
		
		LocationData(long timestamp, double[] values) {
			this.timestamp = timestamp;
			this.values = values;
		}
	}
	
	private final LowpassFilter speedChangeDamperFilter;

	private float minDistance;
	private float maxSpeed;
	
	private LocationData lastLocation;
	private LocationData prevLocation;
	private final DistanceResolver distanceResolver;
	
	private final AppEventBus bus;

	/**
	 * force calculation of distance/speed on next Location, even if distance diff smaller than preferences
	 */
	private boolean immediateDistanceRequested;
	private boolean splitRowingOn;
	private LocationData bookMarkedLocation;
	private LocationData lastSensorDataLocation;
	private AppStroke owner;
	private final ParameterService params;
	private boolean straightLineModeOn;
	protected float travelDistance;
	protected LocationData lastStrokeLocation;
	private double totalDistance = 0.0d;
	private double totalTime = 0.0d;
	private double avgSpeed = 0.0d;

	public GPSDataFilter(AppStroke owner, final DistanceResolver distanceResolver) {
		this.owner = owner;
		this.params = owner.getParameters();
		this.bus = owner.getBus();
		
		this.distanceResolver = distanceResolver;
		speedChangeDamperFilter = new LowpassFilter((Float)params.getValue(ParamKeys.PARAM_GPS_SPEED_CHANGE_DAMPER.getId()));
		straightLineModeOn = (Boolean)params.getValue(ParamKeys.PARAM_ROWING_STRAIGHT_LINE_MODE.getId());
		minDistance = (Integer)params.getValue(ParamKeys.PARAM_GPS_MIN_DISTANCE.getId());
		maxSpeed = (Float)params.getValue(ParamKeys.PARAM_GPS_DATA_FILTER_MAX_SPEED.getId());
		params.addListeners(this);

		bus.addBusListener(new BusEventListener() {
			@Override
			public void onBusEvent(DataRecord event) {
				switch (event.type) {
				case ROWING_COUNT: // means DROP_BELOW_ZERO with a valid stroke amplitude - see RowingDetector
					if (splitRowingOn && bookMarkedLocation != null) {
						if (straightLineModeOn) {
							travelDistance = distanceResolver.calcDistance(bookMarkedLocation.values, lastSensorDataLocation.values);
						} else {
							travelDistance += distanceResolver.calcDistance(lastStrokeLocation.values, lastSensorDataLocation.values);
							lastStrokeLocation = lastSensorDataLocation;
						}
						long travelTime = lastSensorDataLocation.timestamp - bookMarkedLocation.timestamp;
						bus.fireEvent(DataRecord.Type.BOOKMARKED_DISTANCE, event.timestamp, travelTime, travelDistance);
					}
					break;
				case ROWING_START:
					immediateDistanceRequested = true;
					splitRowingOn = true;
					travelDistance = 0;
					bookMarkedLocation = lastStrokeLocation = lastSensorDataLocation;
					break;
				case ROWING_STOP:
					splitRowingOn = false;
					bookMarkedLocation = null;
					break;
				}
			}
		});
	}
	
	public static int calcMilisecondsPer500m(float speed) {
		double seconds = 0;
		if (speed > 0) {
			seconds = 500 / speed;	
			if (seconds > 1000) {
				seconds = 0;
			}
		}	
		return (int)seconds * 1000;
	}
	
	private int calcSpeed(float speed) {
		if (speed > maxSpeed) {
			return -1;
		}
		float[] values = {speed};
		speed = speedChangeDamperFilter.filter(values)[0];
		return calcMilisecondsPer500m(speed);
	}
	
	private int calcSpeed(float distance, long timeDiff) { 
		float speed = distance / timeDiff * 1000; // meters/second
		return calcSpeed(speed);
	}

	@Override
	public synchronized void onSensorData(long timestamp, Object value) {
		double[] values = (double[]) value;
		LocationData newLocation = new LocationData(timestamp, values);

		bus.fireEvent(DataRecord.Type.GPS, newLocation.timestamp, value);
//		Log.i("GPSonSensorData","timestamp="+timestamp+" value="+value);
		if (lastSensorDataLocation == null) {
			lastSensorDataLocation = newLocation;
			lastLocation = newLocation;
			prevLocation = newLocation;
		} else {
			lastSensorDataLocation = newLocation;
			if (splitRowingOn && bookMarkedLocation == null) {
				bookMarkedLocation = lastStrokeLocation = lastSensorDataLocation;
			}

			float distance = distanceResolver.calcDistance(lastLocation.values, values);
			float distance2 = distanceResolver.calcDistance(prevLocation.values, values);
			totalDistance = totalDistance + distance2;
			distance = Math.round(distance);
			distance2 = Math.round(distance2);
			totalDistance = Math.round(totalDistance);
			long diff = timestamp - prevLocation.timestamp;
			totalTime = totalTime + diff;

			avgSpeed = totalDistance/totalTime;
			avgSpeed = avgSpeed * 3600.0d;
			avgSpeed = Math.round(avgSpeed);

			Log.d("onSensorData", "ts1>"+prevLocation.timestamp+
					" ts2>"+timestamp+" avgSpeed>"+avgSpeed+" distance="+distance2 +" total>"+totalDistance);
			prevLocation = newLocation;//after you evalutate the distance

			if (distance > minDistance || immediateDistanceRequested) {
				final int finalSpeed = calcSpeed(distance, timestamp - lastLocation.timestamp);
				if (finalSpeed != -1) {
					accumulatedDistance += distance;
					double accuracy = values[DataIdx.GPS_ACCURACY];
					bus.fireEvent(DataRecord.Type.WAY, timestamp, new double[]{distance, finalSpeed, accuracy});
					lastLocation = new LocationData(timestamp, values);
					immediateDistanceRequested = false;
				}
			}
		}
		
		if (!owner.isSeekableDataInput()) { // ACCUM_DISTANCE is replayed when read from recorded file
			bus.fireEvent(DataRecord.Type.ACCUM_DISTANCE, accumulatedDistance);
		}
	}
	
	private final ParameterListenerRegistration[] listenerRegistrations = {
			new ParameterListenerRegistration(ParamKeys.PARAM_GPS_SPEED_CHANGE_DAMPER.getId(), new ParameterChangeListener() {
				@Override
				public void onParameterChanged(Parameter param) {
					float value = (Float)param.getValue();
					Log.i("listenerRegistrations", "setting speedChangeDamperFilter to {} "+ value);
					speedChangeDamperFilter.setFilteringFactor(value);
				}
			}),
			new ParameterListenerRegistration(ParamKeys.PARAM_GPS_MIN_DISTANCE.getId(), new ParameterChangeListener() {
				@Override
				public void onParameterChanged(Parameter param) {
					float value = (Integer)param.getValue();
					Log.i("listenerRegistrations", "setting minDistance to {} "+ value);
					minDistance = value;
				}
			}),
			new ParameterListenerRegistration(ParamKeys.PARAM_GPS_DATA_FILTER_MAX_SPEED.getId(), new ParameterChangeListener() {
				@Override
				public void onParameterChanged(Parameter param) {
					float value = (Float)param.getValue();
					Log.i("listenerRegistrations", "setting maxSpeed to {} "+ value);
					maxSpeed = value;
				}
			}),
			new ParameterListenerRegistration(ParamKeys.PARAM_ROWING_STRAIGHT_LINE_MODE.getId(), new ParameterChangeListener() {
				@Override
				public void onParameterChanged(Parameter param) {
					straightLineModeOn = (Boolean) param.getValue();
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

	public synchronized void reset() {
		accumulatedDistance = 0;
		immediateDistanceRequested = false;
		splitRowingOn = false;
		travelDistance = 0;
		bookMarkedLocation = lastStrokeLocation = lastSensorDataLocation = null;
		speedChangeDamperFilter.reset();
	}
}
