package com.talentica.rowingapp;

import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.SensorDataSource;
import com.talentica.rowingapp.common.data.stroke.AppStroke;

import java.util.LinkedList;

public abstract class SensorBinder implements BusEventListener {

	protected final AppStroke appStroke;
	private final LinkedList<SensorDataSourceBinder> sourceBinderList = new LinkedList<SensorDataSourceBinder>();

	private class SensorDataSourceBinder implements SensorDataSink {
		private final SensorDataSource src;
		private final DataRecord.Type type;
		
		SensorDataSourceBinder(SensorDataSource src, DataRecord.Type type) {
			this.src = src;
			this.type = type;
			src.addSensorDataSink(this, 0.1);
		}
		
		@Override
		public void onSensorData(long timestamp, Object data) {
			switch (type) {
			case ACCEL:
				data = ((float[])data).clone();
				break;
			}
			
			SensorBinder.this.onSensorData(DataRecord.create(type, timestamp, data));
		}
		
		void unbind() {
			src.removeSensorDataSink(this);
		}
	}
		
	public SensorBinder(AppStroke appStroke) {
		this.appStroke = appStroke;
	}

	protected synchronized void connect() {
		sourceBinderList.add(new SensorDataSourceBinder(appStroke.getDataInput().getAccelerometerDataSource(), DataRecord.Type.ACCEL));
		sourceBinderList.add(new SensorDataSourceBinder(appStroke.getDataInput().getOrientationDataSource(), DataRecord.Type.ORIENT));
		sourceBinderList.add(new SensorDataSourceBinder(appStroke.getDataInput().getGPSDataSource(), DataRecord.Type.GPS));
		appStroke.getBus().addBusListener(this);
	}

	protected synchronized void disconnect() {
		for (SensorDataSourceBinder binder: sourceBinderList) {
			binder.unbind();
		}
				
		sourceBinderList.clear();

		appStroke.getBus().removeBusListener(this);
	}

	protected abstract void onSensorData(DataRecord record);
}