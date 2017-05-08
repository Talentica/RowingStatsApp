package com.talentica.rowingapp.common.data.session;

import com.talentica.rowingapp.SensorBinder;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.error.ErrorListener;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterBusEventData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SessionRecorder extends SensorBinder implements SessionRecorderConstants {
	
	private ErrorListener errorListener;
	private BufferedWriter logger;
	private boolean initialized;
	
	public SessionRecorder(AppStroke appStroke) {
		super(appStroke);
	}
	
	public synchronized void setDataLogger(File file) throws IOException {
			if (logger != null) {
				disconnect();
				logger.close();
				logger = null;
			}

			initialized = false;
			
			if (file != null) {
				logger = new BufferedWriter(new FileWriter(file));
				connect();
			}
	}

	private void initDataLogger() {
		
		initialized = true;

		logEvent(new DataRecord(DataRecord.Type.LOGFILE_VERSION, -1, LOGFILE_VERSION));

		for (Parameter param: appStroke.getParameters().getParamMap().values()) {

			logEvent(DataRecord.create(DataRecord.Type.SESSION_PARAMETER, -1, 
					new ParameterBusEventData(param.getId() + "|" + param.convertToString())));
		}		
	}

	
	@Override
	protected synchronized void onSensorData(DataRecord record) {
		logEvent(record);
	}

	public void setErrorListener(ErrorListener errorListener) {
		this.errorListener = errorListener;
	}

	@Override
	public synchronized void onBusEvent(DataRecord event) {
		
		if (event.type == DataRecord.Type.RECORDING_START) {
			initDataLogger();
		}
		
		logEvent(event);
	}

	private synchronized void logEvent(DataRecord event) {
		
		if (initialized) {
			StringBuffer sb = new StringBuffer();
			sb.append(System.currentTimeMillis()).append(" ")
			.append(event);

			try {
				logger.write(sb.toString());
				logger.write(END_OF_RECORD + "\n");

				if (event.type == DataRecord.Type.CRASH_STACK) {
					logger.flush();
				}
			} catch (IOException e) {
				if (errorListener != null) {
					errorListener.onError(e);
				}
			}
		}
	}
}
