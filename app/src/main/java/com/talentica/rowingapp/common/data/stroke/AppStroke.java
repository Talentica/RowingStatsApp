package com.talentica.rowingapp.common.data.stroke;

import android.util.Log;

import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.SimpleLock;
import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.FileDataInput;
import com.talentica.rowingapp.common.data.SensorDataFilter;
import com.talentica.rowingapp.common.data.SensorDataInput;
import com.talentica.rowingapp.common.data.SensorDataSource;
import com.talentica.rowingapp.common.data.axisfilter.AxisDataReverseFilter;
import com.talentica.rowingapp.common.data.axisfilter.AxisDataSwapFilter;
import com.talentica.rowingapp.common.data.remote.DataRemote;
import com.talentica.rowingapp.common.data.remote.DataSender;
import com.talentica.rowingapp.common.data.session.SessionBroadcaster;
import com.talentica.rowingapp.common.data.session.SessionRecorder;
import com.talentica.rowingapp.common.data.way.DistanceResolver;
import com.talentica.rowingapp.common.data.way.DistanceResolverDefault;
import com.talentica.rowingapp.common.data.way.GPSDataFilter;
import com.talentica.rowingapp.common.error.ErrorListener;
import com.talentica.rowingapp.common.filter.acceleration.AccelerationFilter;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterBusEventData;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterService;

import java.io.File;
import java.io.IOException;

/**
 * An App Stroke engine initializer.
 * This is a handy class for initializing and connecting the various data input
 * filters and processors. A user of this class will usually set event listeners
 * by calling StrokeRateScanner setStrokeListener(StrokeRateListener) strokeRateScanner.setStrokeRateListener}
 * and {@link GPSDataFilter setWayListener(WayListener) gpsFilter.setWayListener}. The client can also register for
 * raw Sensor data events by attaching a SensorDataSink to any {@link SensorDataSource} or
 * {@link SensorDataFilter} object.
 *
 */
public class AppStroke {
	
	/**
	 * wraps the real SensorDataInput and does event recording
	 */
	private SensorDataInput dataInput;
	
	private final SimpleLock inputLock = new SimpleLock();
	
	/**
	 * scans acceleration event to detect stroke-rate
	 */
	private StrokeRateScanner strokeRateScanner;
	
	/**
	 * detects and notify ROWING_START and ROWING_STOP
	 */
	private RowingDetector rowingDetector;
	
	/**
	 * scans acceleration event to detect stroke-power
	 */
	private StrokePowerScanner strokePowerScanner;	

	/**
	 * scans orientation and stroke events to detect boat-roll
	 */
	private RollScanner rollScanner;	

	/**
	 * combines gravity-filtered acceleration forces to uni-directional acceleration/deceleration data
	 */
	private SensorDataFilter accelerationFilter;

	/**
	 * processes GPS/Location sensor data for determining stroking distance and speed 
	 */
	private GPSDataFilter gpsFilter;

	/**
	 * error listener
	 */
	private ErrorListener errorListener;

	/**
	 * Singleton event bus instance
	 */
	private final AppEventBus bus = new AppEventBus();
	
	private final ParameterService parameters = new ParameterService(bus);

	/**
	 * data/event logger when recording is on
	 */
	private final SessionRecorder recorder = new SessionRecorder(this);

	private SessionBroadcaster sessionBroadcaster;

	/**
	 * data/event logger when recording is on
	 */
	private boolean broadcastOn;
	private AxisDataReverseFilter coaxModeOrientationFilter;
	private AxisDataSwapFilter landscapeAccelFilter;
	private AxisDataSwapFilter landscapeOrientationFilter;
	private final BusEventListener sessionParamChangeListener;
	
	private final ParamKeys[] sessionParamList = {
			ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED
	};

	/**
	 * constructor with the <code>DistanceResolverDefault</code>
	 */
	public AppStroke() {
		this(new DistanceResolverDefault());
	}
	
	public AppStroke(DistanceResolver distanceResolver) {
		this(distanceResolver, null);
	}
	
	/**
	 * constructor with the <code>DistanceResolver</code> implementation.
	 * @param distanceResolver a client provided implementation that can extract distance from location events 
	 */
	public AppStroke(DistanceResolver distanceResolver, DataSender dataSenderImpl) {
		ParamRegistration.installParams(parameters);
		
		try {
			sessionBroadcaster = new SessionBroadcaster(this, dataSenderImpl);
		} catch (DataRemote.DataRemoteError e) {
			Log.e("error","failed to create sessionBroadcaster"+ e.getMessage());
		}
		
		initPipeline(distanceResolver);
		
		parameters.addListener(ParamKeys.PARAM_SESSION_BROADCAST_ON.getId(), new ParameterChangeListener() {
			@Override
			public void onParameterChanged(Parameter param) {
				synchronized (inputLock) {
					broadcastOn = (Boolean) param.getValue();
					if (dataInput != null) {
//						if (sessionBroadcaster != null) sessionBroadcaster.enable(broadcastOn);
					}
				}
			}
		});
		
//		parameters.addListener(ParamKeys.PARAM_SESSION_BROADCAST_PORT.getId(), new ParameterChangeListener() {
//
//			@Override
//			public void onParameterChanged(Parameter param) {
//				if (sessionBroadcaster != null)  sessionBroadcaster.setPort((Integer)param.getValue());
//
//			}
//		});
//
//		parameters.addListener(ParamKeys.PARAM_SESSION_BROADCAST_HOST.getId(), new ParameterChangeListener() {
//
//			@Override
//			public void onParameterChanged(Parameter param) {
//				if (sessionBroadcaster != null) sessionBroadcaster.setAddress((String)param.getValue());
//
//			}
//		});

		parameters.addListener(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId(), new ParameterChangeListener() {
			
			@Override
			public void onParameterChanged(Parameter param) {
				setCoaxMode((Boolean)param.getValue());
			}
		});
		
		parameters.addListener(ParamKeys.PARAM_SENSOR_ORIENTATION_LANDSCAPE.getId(), new ParameterChangeListener() {
			@Override
			public void onParameterChanged(Parameter param) {
				setLandscapeMode((Boolean)param.getValue());
			}
		});
		
		setCoaxMode((Boolean)parameters.getValue(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId()));
		sessionParamChangeListener = new BusEventListener() {
			@Override
			public void onBusEvent(DataRecord event) {
				switch (event.type) {
				case SESSION_PARAMETER:
					ParameterBusEventData pd = (ParameterBusEventData) event.data;
					if (pd.id.equals(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId())) {
						parameters.setParam(pd.id, pd.value);
					}
					break;
				}
			}
		};
	}

	private void setLandscapeMode(boolean value) {
		landscapeOrientationFilter.setEnabled(value);
		landscapeAccelFilter.setEnabled(value);
	}

	private void setCoaxMode(boolean value) {
		coaxModeOrientationFilter.setEnabled(value);
	}

	/**
	 * get shared event bus instance
	 * @return global event bus
	 */
	public AppEventBus getBus() {
		return bus;
	}

	/**
	 * sets the error listener of the event pipeline
	 * @param errorListener
	 */
	public void setErrorListener(ErrorListener errorListener) {
		this.errorListener = errorListener;
	}

	/**
	 * initialize and connect the sensor data pipelines
	 * @param distanceResolver
	 */
	private void initPipeline(DistanceResolver distanceResolver) {
		Log.d("initPipeline()","initializing pipeline");
		
		landscapeAccelFilter = new AxisDataSwapFilter(DataIdx.ACCEL_Y, DataIdx.ACCEL_X);
		landscapeOrientationFilter = new AxisDataSwapFilter(DataIdx.ORIENT_PITCH, DataIdx.ORIENT_ROLL);
		coaxModeOrientationFilter = new AxisDataReverseFilter(DataIdx.ORIENT_PITCH, DataIdx.ORIENT_ROLL);
		accelerationFilter = new AccelerationFilter(this);
		strokeRateScanner = new StrokeRateScanner(this);
		rowingDetector = new RowingDetector(this); 
		strokePowerScanner = new StrokePowerScanner(this, strokeRateScanner);
		accelerationFilter.addSensorDataSink(strokeRateScanner);
		accelerationFilter.addSensorDataSink(strokePowerScanner);
		accelerationFilter.addSensorDataSink(rowingDetector);
		gpsFilter = new GPSDataFilter(this, distanceResolver);
		rollScanner = new RollScanner(bus);
	}

	/**
	 * Set the sensor data input to replay from a file.
	 * look at the code in LoggingSensorDataInput#logData} to see what
	 * the data file content should look like.
	 * @param file replay input file
	 * @throws IOException
	 */
	public void setFileInput(File file) throws IOException {
		setInput(new FileDataInput(this, file));
	}

	/**
	 * Set the sensor data input to a real device dependant implementation
	 * @param dataInput device input implementation
	 */
	public void setInput(SensorDataInput dataInput) {
		synchronized (inputLock) {
			stop();

			if (dataInput != null) {
				Log.d("setInput()","setting input type {}"+ dataInput);
				bus.fireEvent(DataRecord.Type.INPUT_START, null);

				if (!dataInput.isLocalSensorInput()) {
					for (ParamKeys k: sessionParamList) {
						parameters.getParam(k.getId()).saveValue();
					}
					bus.addBusListener(sessionParamChangeListener);
				}

				this.dataInput = dataInput;
				connectPipeline();
				sessionBroadcaster.enable(broadcastOn);
				dataInput.start();
			}
		}
	}
	
	public boolean isSeekableDataInput() {
		SensorDataInput di = dataInput;
		return di != null && di.isSeekable();
	}

	/**
	 * Stop processing
	 */
	public void stop() {
		synchronized (inputLock) {
			if (dataInput != null) {
				sessionBroadcaster.enable(false);

				dataInput.setErrorListener(null);
				dataInput.stop();

				coaxModeOrientationFilter.clearSensorDataSinks();
				landscapeAccelFilter.clearSensorDataSinks();
				landscapeOrientationFilter.clearSensorDataSinks();
				gpsFilter.reset();

				if (!dataInput.isLocalSensorInput()) {
					for (ParamKeys k : sessionParamList) {
						parameters.getParam(k.getId()).restoreValue();
					}
					bus.removeBusListener(sessionParamChangeListener);
				}
				bus.fireEvent(DataRecord.Type.INPUT_STOP, null);
			}
		}
	}
	
	/**
	 * get <code>SensorDataInput</code> implemention currently in use
	 * @return SensorDataInput implemention
	 */
	public SensorDataInput getDataInput() {
		return dataInput;
	}

	/**
	 * Get the stroke rate scanner object.
	 * <code>StrokeRateScanner</code> scans acceleration event to detect the stroke-rate
	 * @return StrokeRateScanner object
	 */
	public StrokeRateScanner getStrokeRateScanner() {
		return strokeRateScanner;
	}

	/**
	 * Get the stroke power scanner object.
	 * <code>StrokePowerScanner</code> scans acceleration event to detect the stroke-power
	 * @return StrokePowerScanner object
	 */
	public StrokePowerScanner getStrokePowerScanner() {
		return strokePowerScanner;
	}

	/**
	 * Get the acceleration combiner object.
	 * <code>AccelerationFilter</code> combines the gravity-filtered acceleration forces to uni-directional acceleration/deceleration data
	 * @return AccelerationFilter object
	 */
	public SensorDataSource getAccelerationSource() {
		return accelerationFilter;
	}

	/**
	 * Get GPS data processor.
	 * @return AccelerationFilter object
	 */
	public GPSDataFilter getGpsFilter() {
		return gpsFilter;
	}
	
	
	/**
	 * get roll scanner
	 * @return roll scanner
	 */
	public SensorDataSource getOrientationSource() {
		return rollScanner;
	}

	/**
	 * connects a new DataInputSource to the sensor data pipelines
	 */
	private void connectPipeline() {
		dataInput.setErrorListener(errorListener);
		
		if (dataInput.isLocalSensorInput()) {
			dataInput.getOrientationDataSource().addSensorDataSink(landscapeOrientationFilter, 0.0);
			dataInput.getAccelerometerDataSource().addSensorDataSink(landscapeAccelFilter, 0.0);	
		}
		
		dataInput.getOrientationDataSource().addSensorDataSink(coaxModeOrientationFilter);
		dataInput.getOrientationDataSource().addSensorDataSink(rollScanner);
		dataInput.getAccelerometerDataSource().addSensorDataSink(accelerationFilter);
		dataInput.getGPSDataSource().addSensorDataSink(gpsFilter);
	}

	public void setDataLogger(File logFile) throws IOException {
		recorder.setDataLogger(logFile);	
	}

	public ParameterService getParameters() {
		return parameters;
	}	
	
	@Override
	protected void finalize() throws Throwable {
		destroy();
		super.finalize();
	}

	public void destroy() {
		bus.shutdown();
		try {
			setDataLogger(null);
		} catch (IOException e) {
			Log.e("error","exception thrown when closing session log file"+ e);
		}
	}
}
