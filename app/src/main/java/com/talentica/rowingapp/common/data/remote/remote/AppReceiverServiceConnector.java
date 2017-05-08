package com.talentica.rowingapp.common.data.remote.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.talentica.rowingapp.common.ThreadedQueue;
import com.talentica.rowingapp.common.data.RecordDataInput;
import com.talentica.rowingapp.common.data.session.SessionRecorderConstants;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.error.ServiceNotExist;

public class AppReceiverServiceConnector extends RecordDataInput {

	private final static String SERVICE_ID = "com.talentica.rowingapp.common.data.remote.remote.AppBroadcastService";
		
	private final Context owner;

	private final Intent service;

	private final ThreadedQueue<String> recordQueue;

	private final BroadcastReceiver receiver;

	private boolean started;	
		
	public AppReceiverServiceConnector(Context owner, AppStroke roboStroke, String host) throws ServiceNotExist {
		this(owner, roboStroke, host, SessionRecorderConstants.BROADCAST_PORT);
	}
	
	public AppReceiverServiceConnector(Context owner, AppStroke roboStroke, String host, int port) throws ServiceNotExist {
		
		super(roboStroke);
		
		AppRemoteServiceHelper helper = new AppRemoteServiceHelper(owner, SERVICE_ID);
		
		this.owner = owner;
		
   		service = helper.service;
   		
   		service.putExtra("host", host);
   		service.putExtra("port", port);   
   		
   		recordQueue = new ThreadedQueue<String>(getClass().getSimpleName(), 100) {
			
			@Override
			protected void handleItem(String o) {
				playRecord(o, SessionRecorderConstants.END_OF_RECORD);
			}
		};
		
		receiver = new BroadcastReceiver() {


			@Override
			public void onReceive(Context context, Intent intent) {
				Bundle data = intent.getExtras();
				String l = data.getString("data");
				recordQueue.put(l);
			}
		};
		
	}

	@Override
	public synchronized void stop() {
		
		if (started) {
			recordQueue.setEnabled(false);
			owner.unregisterReceiver(receiver);
			owner.stopService(service);
			super.stop();
			started = false;
		}
	}

	@Override
	public synchronized void start() {
		
		if (!started) {
			super.start();
			recordQueue.setEnabled(true);
			owner.registerReceiver(receiver, new IntentFilter(SERVICE_ID));
			owner.startService(service);
			started = true;
		}

	}

	@Override
	public void skipReplayTime(float velocityX) {
	}

	@Override
	public void setPaused(boolean pause) {
	}

	@Override
	protected void onSetPosFinish(double pos) {
	}
}
