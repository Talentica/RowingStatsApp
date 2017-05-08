package com.talentica.rowingapp.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.talentica.rowingapp.common.AppConstants;
import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.data.DataRecord;

import java.util.concurrent.TimeUnit;

public class HXMDataReceiver extends BroadcastReceiver implements AppConstants {
	private boolean hadError;
	private final AppEventBus bus;
	
	public HXMDataReceiver(AppEventBus bus) {
		this.bus = bus;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle data = intent.getExtras();
		String error = data.getString("error");
		
		if (null != error) {
			if (!hadError) {
				int errorCode = data.getInt("errorCode");
				Log.w(TAG, String.format("received HRM errorCode %d: %s", errorCode, error));
				hadError = true;
			}
			
		} else {
			int bpm = data.getInt("bpm");
			hadError = false;
			bus.fireEvent(DataRecord.Type.HEART_BPM, TimeUnit.MILLISECONDS.toNanos(SystemClock.uptimeMillis()), bpm);
		}
		
	}
}
