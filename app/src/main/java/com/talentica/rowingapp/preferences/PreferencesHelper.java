/*
 * Copyright (c) 2011 Tal Shalif
 * 
 * This file is part of Talos-Rowing.
 * 
 * Talos-Rowing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Talos-Rowing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Talos-Rowing.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.talentica.rowingapp.preferences;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.talentica.rowingapp.R;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterService;
import com.talentica.rowingapp.ui.DashBoardActivity;

import java.util.UUID;

public class PreferencesHelper {
	public static final int PREFERENCES_VERSION = 1;
	public static final String PREFERENCES_VERSION_RESET_KEY = "preferencesVersionReset" + PREFERENCES_VERSION;
	public static final String TALOS_APP_VERSION_KEY = "talosAppVersion";
	public static final String PREFERENCE_KEY_HRM_ENABLE = "com.talentica.rowing.android.hrm.enable";
	public static final String PREFERENCE_KEY_PREFERENCES_RESET = "com.talentica.rowing.android.preferences.reset";
	public static final String METERS_RESET_ON_START_PREFERENCE_KEY = "com.talentica.rowing.android.stroke.detector.resetOnStart";
	public static final String GRAPHS_SHOW_PREFERENCE_KEY = "com.talentica.rowing.android.layout.graphs.show";
	public static final String METERS_LAYOUT_MODE_KEY = "com.talentica.rowing.android.layout.meters.layoutMode";
	public static final String PREFERENCE_KEY_LAYOUT_MODE_LANDSCAPE = "com.talentica.rowing.android.layout.mode.landscape";

	private final SharedPreferences preferences;
	private final DashBoardActivity activity;
	private final String uuid;

	// must keep onSharedPreferenceChangeListener as field, never as local variable or it will be garbage collected!
	private final SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			setParameterFromPreferences(key); 
			
			if (key.equals(PREFERENCE_KEY_HRM_ENABLE)) {
				activity.graphPanelDisplayManager.setEnableHrm(preferences.getBoolean(PREFERENCE_KEY_HRM_ENABLE, false), true);
			} else if (key.equals(PREFERENCE_KEY_PREFERENCES_RESET)) {
				preferences.edit().putBoolean(PREFERENCES_VERSION_RESET_KEY, true).commit();
				activity.graphPanelDisplayManager.resetNextRun();
			} else 	if (key.equals(METERS_RESET_ON_START_PREFERENCE_KEY)) {
				activity.metersDisplayManager.setResetOnStart(preferences.getBoolean(METERS_RESET_ON_START_PREFERENCE_KEY, true));
			} else if (key.equals(METERS_LAYOUT_MODE_KEY)) {				
				String defaultValue = activity.getString(R.string.defaults_layout_meter_mode);
				String val = preferences.getString(METERS_LAYOUT_MODE_KEY, defaultValue);
				activity.metersDisplayManager.setLayoutMode(val);
			} else if (key.equals(PREFERENCE_KEY_LAYOUT_MODE_LANDSCAPE)) {				
				activity.setLandscapeLayout(preferences.getBoolean(PREFERENCE_KEY_LAYOUT_MODE_LANDSCAPE, false));
			} else if (key.equals(GRAPHS_SHOW_PREFERENCE_KEY)) {
				boolean defaultValue = new Boolean(activity.getString(R.string.defaults_layout_show_graphs));
				boolean val = preferences.getBoolean(GRAPHS_SHOW_PREFERENCE_KEY, defaultValue);
				activity.graphPanelDisplayManager.setShowGraphs(val);
			} 
		}
	};

	public PreferencesHelper(DashBoardActivity activity) {
		this.activity = activity;
		preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		
		// create UUID, if no exist
		String tmpUuid = preferences.getString("uuid", null);
		uuid =  tmpUuid == null ? UUID.randomUUID().toString() : tmpUuid;

		resetPreferencesIfNeeded();
		initializePrefs();
	}

	public void init() {
		syncParametersFromPreferences();
		applyPreferences();
		attachPreferencesListener();
	}

	private void resetPreferencesIfNeeded() {
		boolean firstRun = preferences.getString(TALOS_APP_VERSION_KEY, "").equals("");
		boolean newVersion = !preferences.getString(TALOS_APP_VERSION_KEY, "").equals(activity.getVersion());
		boolean resetRequested = preferences.getBoolean(PREFERENCES_VERSION_RESET_KEY, false);
		
		boolean resetPending = resetRequested || (newVersion && !firstRun);
		final Runnable runAtEnd = new Runnable() {
			@Override
			public void run() {
				preferences.edit().putBoolean(PREFERENCE_KEY_PREFERENCES_RESET, false).commit();
				preferences.edit().putBoolean(PREFERENCES_VERSION_RESET_KEY, false).commit();
				preferences.edit().putString(TALOS_APP_VERSION_KEY, activity.getVersion()).commit();
			}
		};
		
		if (newVersion) {
//			activity.showAbout();
		}

		if (resetPending) {
			new AlertDialog.Builder(activity)
			.setMessage(R.string.preference_reset_dialog_message)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                		preferences.edit().clear().commit();
                		runAtEnd.run();
                   }
                })
            .setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	runAtEnd.run();        		 
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
					
					@Override
					public void onCancel(DialogInterface dialog) {
                    	runAtEnd.run();        		 
					}
				})
			.show();
		}
		runAtEnd.run();
	}

	public String getUUID() {
		return uuid;
	}
	
	private void initializePrefs() {
		if (preferences.getString("uuid", null) == null) {
			preferences.edit().putString("uuid", uuid).commit();
		}		
	}

	@SuppressWarnings("unchecked")
	public <T> T getPref(String key, T defValue) {
		
		if (defValue instanceof Boolean) {
			return (T)(Boolean)preferences.getBoolean(key, (Boolean) defValue);
		} else if (defValue instanceof Integer) {
			return (T)(Integer)preferences.getInt(key, (Integer) defValue);
		} else if (defValue instanceof Long) {
			return (T)(Long)preferences.getLong(key, (Long) defValue);
		} else if (defValue instanceof Float) {
			return (T)(Float)preferences.getFloat(key, (Float) defValue);
		} else {
			return  (T)preferences.getString(key, (String) defValue);
		}
	}
	
	private void applyPreferences() {
		String[] keys = {
				METERS_RESET_ON_START_PREFERENCE_KEY,
				METERS_LAYOUT_MODE_KEY,
				GRAPHS_SHOW_PREFERENCE_KEY
		};
		
		for (String key: keys) {
			onSharedPreferenceChangeListener.onSharedPreferenceChanged(preferences, key);
		}
		
		activity.graphPanelDisplayManager.setEnableHrm(preferences.getBoolean(PREFERENCE_KEY_HRM_ENABLE, false), false);
		activity.setLandscapeLayout(preferences.getBoolean(PREFERENCE_KEY_LAYOUT_MODE_LANDSCAPE, false));
	}

	private void syncParametersFromPreferences() {
		Log.i("syncParasFromPrefs","synchronizing Android preferences to back-end");
		
		for (String key: preferences.getAll().keySet()) {
			setParameterFromPreferences(key); 
		}
	}
	
	private void attachPreferencesListener() {
		preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
		activity.getRoboStroke().getParameters().addListener(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId(), new ParameterChangeListener() {
			
			@Override
			public void onParameterChanged(Parameter param) {
				preferences.edit().putBoolean(ParamKeys.PARAM_SENSOR_ORIENTATION_REVERSED.getId(), (Boolean)param.getValue()).commit();
			}
		});
	}
	
	private void setParameterFromPreferences(String key) {
		if (key.startsWith("com.talentica.rowing") && !key.startsWith("com.talentica.rowing.android")) {
			Log.i("setParasFromPrefs","setting back-end parameter {} >>"+ key);

			final ParameterService ps = activity.getRoboStroke().getParameters();
			try {
				Parameter param = ps.getParam(key);
				Object defaultValue = param.getDefaultValue();
				Object val = preferences.getAll().get(key);
				String value = val == null ? defaultValue.toString() : val.toString(); //preferences.edit().remove(key).commit()
				
				ps.setParam(key, value);

				Log.i("setParasFromPrefs","done setting back-end parameter {} key="+key+" with value {} <<"+ value);
			} catch (Exception e) {
				Log.e("setParasFromPrefs","error while trying to set back-end parameter from an Android preference <<", e);
			}
		}
	}
}
