package com.talentica.rowingapp.common.data.session;

import com.talentica.rowingapp.SensorBinder;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.remote.DataRemote;
import com.talentica.rowingapp.common.data.remote.DataSender;
import com.talentica.rowingapp.common.data.remote.DatagramDataSender;
import com.talentica.rowingapp.common.data.remote.RemoteDataHelper;
import com.talentica.rowingapp.common.data.stroke.AppStroke;


public class SessionBroadcaster extends SensorBinder {
					
	private final DataSender dataSender;
	
	private boolean broadcast;
	
	public SessionBroadcaster(AppStroke appStroke) throws DataRemote.DataRemoteError {
		this(appStroke, null);
	}
	
	public SessionBroadcaster(AppStroke appStroke, DataSender dataTransport) throws DataRemote.DataRemoteError {
		
		super(appStroke);
		
		if (dataTransport == null) {
			dataTransport = new DatagramDataSender(RemoteDataHelper.getAddr(appStroke), RemoteDataHelper.getPort(appStroke));
		}
		
		this.dataSender = dataTransport;
	}
	
	public void setPort(int port) {
		dataSender.setPort(port);
	}

	public void setAddress(String address) {
		dataSender.setAddress(address);
	}
	
	public void enable(boolean broadcast) {
				
		if (this.broadcast != broadcast) {
			if (broadcast) {
				connect();
			} else {
				disconnect();
			}
			this.broadcast = broadcast;
		}
	}
	
	public boolean isEnabled() {
		return this.broadcast;
	}
	
	@Override
	protected synchronized void connect() {
		
		super.connect();					
		
		try {
			dataSender.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	protected synchronized void disconnect() {
		
		super.disconnect();
		
		dataSender.stop();
	}
	
	@Override
	protected void onSensorData(DataRecord record) {
		write(record);		
	}


	@Override
	public void onBusEvent(DataRecord record) {
		
		if (record.type.isExportableEvent) {
			write(record);
		}
	}

	public void write(DataRecord record) {
				
		if (dataSender != null) {
			dataSender.write(record.toString());
		}
	}
}
