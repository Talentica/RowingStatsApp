package com.talentica.rowingapp.common.data.remote.remote;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.talentica.rowingapp.R;
import com.talentica.rowingapp.common.error.ServiceNotExist;

import java.util.List;

class AppRemoteServiceHelper {
	
	final Intent service;
	final Context owner;
	
	protected AppRemoteServiceHelper(Context owner, String serviceId) throws ServiceNotExist {
				
		this.owner = owner;
		this.service = new Intent(serviceId);
		
		List<ResolveInfo> res = owner.getPackageManager().queryIntentServices(service, 0);

		if (res.isEmpty()) {
			installTalosRemote();
			throw new ServiceNotExist(serviceId);
		}
	}
	
	private void installTalosRemote() {
		new AlertDialog.Builder(owner)
		.setMessage(owner.getString(R.string.talos_remote_missing_text))
		.setTitle(R.string.talos_remote_missing)
		.setIcon(R.drawable.ic_launcher)
		.setCancelable(true)
		.setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				String appName = "com.talentica.rowingapp.remote";

				try {
					owner.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appName)));
				} catch (android.content.ActivityNotFoundException anfe) {
					owner.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appName)));
				}
			}
		}).show();
	}

}
