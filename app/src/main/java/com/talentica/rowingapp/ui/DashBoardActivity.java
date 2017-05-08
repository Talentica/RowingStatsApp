package com.talentica.rowingapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.talentica.rowingapp.R;
import com.talentica.rowingapp.common.AndroidLocationDistanceResolver;
import com.talentica.rowingapp.common.AppConstants;
import com.talentica.rowingapp.common.FileHelper;
import com.talentica.rowingapp.common.ScreenStayupLock;
import com.talentica.rowingapp.common.SimpleLock;
import com.talentica.rowingapp.common.data.AndroidSensorDataInput;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.FileDataInput;
import com.talentica.rowingapp.common.data.SensorDataInput;
import com.talentica.rowingapp.common.data.notification.NotificationHelper;
import com.talentica.rowingapp.common.data.remote.remote.AppBroadcastServiceConnector;
import com.talentica.rowingapp.common.data.remote.remote.AppReceiverServiceConnector;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.error.ErrorHandler;
import com.talentica.rowingapp.common.error.ErrorListener;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterListenerOwner;
import com.talentica.rowingapp.common.param.ParameterListenerRegistration;
import com.talentica.rowingapp.managers.GraphPanelDisplayManager;
import com.talentica.rowingapp.managers.HXMDataReceiver;
import com.talentica.rowingapp.managers.MetersDisplayManager;
import com.talentica.rowingapp.preferences.PreferencesHelper;
import com.talentica.rowingapp.ui.utils.DataInputInfo;
import com.talentica.rowingapp.ui.utils.DataVersionConverter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DashBoardActivity extends Activity implements AppConstants, ParameterListenerOwner {

    private static final String APP_DATA_DIR = "RowingStatsApp";
    private static final int RECORDING_ON_COLOUR = Color.argb(255, 255, 0, 0);
    private static final int REPLAYING_ON_COLOUR = Color.argb(255, 0, 255, 0);
    private static final int HIGHLIGHT_PADDING_SIZE = 5;

    private final Intent hrmService = new Intent(HRM_SERVICE_ACTION);
    private final ScreenStayupLock screenLock;
    private boolean replayPaused;
    private HXMDataReceiver hxmDataReceiver;
    private boolean stopped = true;
    private DataInputInfo dataInputInfo = new DataInputInfo();

    private final ParameterListenerRegistration[] listenerRegistrations = {
            new ParameterListenerRegistration(ParamKeys.PARAM_SESSION_RECORDING_ON.getId(), new ParameterChangeListener() {
                @Override
                public void onParameterChanged(Parameter param) {
                    final boolean recording = (Boolean) param.getValue();
                    setRecordingOn(recording);
                    final int padding = recording ? HIGHLIGHT_PADDING_SIZE : 0;
                    updateRecordingStateIndication(padding, RECORDING_ON_COLOUR);
                }
            })
    };

    public final Handler handler = new Handler();
    public ScheduledExecutorService scheduler;
    public final AppStroke roboStroke = new AppStroke(new AndroidLocationDistanceResolver(),
            new AppBroadcastServiceConnector(this));
    public NotificationHelper notificationHelper;
    public PreferencesHelper preferencesHelper;
    public MetersDisplayManager metersDisplayManager;
    public GraphPanelDisplayManager graphPanelDisplayManager;
    public static AlertDialog m_AlertDlg;

    public DashBoardActivity() {
        Thread.setDefaultUncaughtExceptionHandler(new ErrorHandler(this));
        screenLock = new ScreenStayupLock(this, getClass().getSimpleName());
        roboStroke.getParameters().addListeners(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.dashboard);
        notificationHelper = new NotificationHelper(this, R.drawable.ic_launcher);

        roboStroke.setErrorListener(new ErrorListener() {
            @Override
            public void onError(Exception e) {
                notificationHelper.notifyError(APP_ERROR,
                        e.getMessage(), "RowingStatsApp error", "RowingStatsApp error");
            }
        });

        metersDisplayManager = new MetersDisplayManager(this);

        graphPanelDisplayManager = new GraphPanelDisplayManager(this);
        graphPanelDisplayManager.init();

        //order inmportant
        preferencesHelper = new PreferencesHelper(this); // handles preferences -> parameter synchronization
        preferencesHelper.init();

        roboStroke.getAccelerationSource().addSensorDataSink(metersDisplayManager);

        View.OnClickListener recordingClickListener = new View.OnClickListener() {
            boolean recording;
            @Override
            public void onClick(View arg0) {
                if (!isReplaying() && FileHelper.hasExternalStorage()) {
                    roboStroke.getParameters().setParam(ParamKeys.PARAM_SESSION_RECORDING_ON.getId(), !recording);
                    recording = !recording;
                }
            }
        };
        findViewById(R.id.distance_meter).setOnClickListener(recordingClickListener);

        Runtime.getRuntime().addShutdownHook(new Thread("cleanTmpDir on exit hook") {
            @Override
            public void run() {
                cleanTmpDir();
            }
        });

        roboStroke.getParameters().setParam(ParamKeys.PARAM_SENSOR_ORIENTATION_LANDSCAPE.getId(),
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        start(new DataInputInfo());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        roboStroke.getParameters().setParam(ParamKeys.PARAM_SENSOR_ORIENTATION_LANDSCAPE.getId(), landscape);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
//        sessionFileHandler.startPreviewIntent(getIntent());
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
//        if (sessionFileHandler.startPreviewIntent(intent)) {
//            startActivity(new Intent(this, DashBoardActivity.class)); // this is to clear the 'sticky' preview intent
//        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        screenLock.start();
    }

    @Override
    protected void onPause() {
        screenLock.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stop();
        roboStroke.destroy();
        notificationHelper.cancel(APP_ERROR);
        super.onDestroy();
    }

    private void setPaused(boolean paused) {
        replayPaused = paused;
        roboStroke.getDataInput().setPaused(replayPaused);
    }

    public void togglePause() {
        setPaused(!replayPaused);
    }

    public synchronized void setRecordingOn(boolean recordingOn) {
//        this.recordingOn = recordingOn;
        if (!stopped) {
//            sessionFileHandler.resetSessionRecording();
        }
    }

    private void reportError(Throwable throwable, String msg) {
        Log.e("reportError", "error:" + msg + " throwable:" + throwable.getMessage());
        notificationHelper.notifyError(APP_ERROR, msg + ": " + throwable.getMessage(),
                "robostroke error", "robostroke error");

        notificationHelper
                .toast(msg + ". See error notification");
    }


    private void registerBpmReceiver() {
        IntentFilter filter = new IntentFilter(HRM_SERVICE_ACTION);
        hxmDataReceiver = new HXMDataReceiver(roboStroke.getBus());
        registerReceiver(hxmDataReceiver, filter);
        startService(hrmService);
    }

    private void unregisterBpmReceiver() {
        if (null != hxmDataReceiver) {
            unregisterReceiver(hxmDataReceiver);
            hxmDataReceiver = null;
        }
    }

    @Override
    protected void onPrepareDialog(int id, final Dialog dialog) {
        switch (id) {
            case R.layout.tilt_freeze_dialog:
                TextView status = (TextView) dialog.findViewById(R.id.tilt_status);
                status.setText("tilt_freeze_dialog");
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        String dialogTitle;

        switch (id) {
            case R.layout.tilt_freeze_dialog:
                dialogTitle = "Tilt Freeze";
                break;
            default:
                return super.onCreateDialog(id);
        }

        final Dialog dialog = new Dialog(this);
        dialog.setContentView(id);
        dialog.setTitle(dialogTitle);

        if (id == R.layout.tilt_freeze_dialog) {
            final ToggleButton tb = (ToggleButton) dialog.findViewById(R.id.tilt_frozen);
            tb.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {


                    if (tb.isChecked()) {
                        final ProgressDialog progress = ProgressDialog.show(DashBoardActivity.this, "",
                                "Calibrating...");
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                graphPanelDisplayManager.tiltFreezeOn = true;
                                roboStroke.getBus().fireEvent(DataRecord.Type.FREEZE_TILT, true);
                                progress.dismiss();
                            }
                        }, TILT_FREEZE_CALIBRATION_TIME, TimeUnit.SECONDS);
                    } else {
                        roboStroke.getBus().fireEvent(DataRecord.Type.FREEZE_TILT, false);
                        graphPanelDisplayManager.tiltFreezeOn = false;
                    }
                    dialog.dismiss();
                }
            });
        }

        return dialog;
    }

    private synchronized void stop() {
        Log.i("stop()", "stopping input type {}" + dataInputInfo.inputType);

        enableScheduler(false);
        unregisterBpmReceiver();
        roboStroke.stop();
        stopped = true;

        if (dataInputInfo.inputType == DataInputInfo.InputType.FILE) { // delete preview/ad-hoc session files
            if (dataInputInfo.temporary) {
                dataInputInfo.file.delete();
            }
        }
    }

    private void enableScheduler(boolean enable) {
        if (enable) {
            Log.d("enableScheduler()", "creating new scheduler");

            if (scheduler != null) {
                throw new AssertionError("scheduler should have been disabled first");
            }

            scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
                private final SimpleLock lock = new SimpleLock();
                private int counter;

                @Override
                public Thread newThread(Runnable r) {
                    synchronized (lock) {
                        return new Thread(r,
                                "RoboStrokeActivity scheduler thread "
                                        + (++counter)) {
                            {
                                setDaemon(true);
                            }
                        };
                    }
                }
            });
        } else {
            Log.d("enableScheduler()", "shutting scheduler");
//			logger.debug("shutting scheduler");
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private synchronized void start(DataInputInfo replayFile) {
        Log.i("start()", "starting input type {}" + replayFile.inputType);

        roboStroke.getParameters().setParam(
                ParamKeys.PARAM_SESSION_RECORDING_ON.getId(), false);
        enableScheduler(true);
        try {
            if (replayFile.inputType == DataInputInfo.InputType.FILE) {
                DataVersionConverter converter = DataVersionConverter.getConvertersFor(replayFile.file);
                if (converter != null) {
                    convertStart(converter, replayFile);
                    return;
                }
            }
        } catch (DataVersionConverter.ConverterError e) {
            reportError(e, "error getting data file converter");
        }

        realStart(replayFile);
    }

    private void convertStart(final DataVersionConverter converter, final DataInputInfo replayFile) {
        final ProgressDialog progress = new ProgressDialog(DashBoardActivity.this);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMessage("Converting...");
        progress.setMax(100);
        progress.setIndeterminate(true);
        progress.setCancelable(true);
        progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                converter.cancel();
                progress.dismiss();
            }
        });

        converter.setProgressListener(new DataVersionConverter.ProgressListener() {

            @Override
            public boolean onProgress(double d) {
                progress.setIndeterminate(false);
                progress.setProgress((int) (100 * d));
                return true;
            }
        });

        progress.show();

        scheduler.submit(new Runnable() {

            @Override
            public void run() {
                DataInputInfo newInfo = new DataInputInfo();
                try {
                    File output = converter.convert(replayFile.file);
                    if (output != null) {
                        newInfo = new DataInputInfo(output, true);
                    }

                    if (replayFile.temporary) {
                        replayFile.file.delete();
                    }
                } catch (Exception e) {
                    reportError(e, "error getting data file converter");
                } finally {
                    progress.dismiss();
                    realStart(newInfo);
                }
            }
        });
    }

    private synchronized void realStart(DataInputInfo dataInputInfo) {
        boolean replay = false;
        int padding = 0;
        int recordingIndicatorHilight = REPLAYING_ON_COLOUR;
        this.dataInputInfo = dataInputInfo;
        SensorDataInput dataInput = new AndroidSensorDataInput(this);
        graphPanelDisplayManager.resetGraphs();
        try {
            roboStroke.setDataLogger(null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            switch (dataInputInfo.inputType) {
                case FILE:
                case REMOTE:
                    SensorDataInput input;

                    if (dataInputInfo.inputType == DataInputInfo.InputType.FILE) {
                        input = new FileDataInput(roboStroke, dataInputInfo.file);
                    } else {
                        input = new AppReceiverServiceConnector(this, roboStroke, dataInputInfo.host, dataInputInfo.port);
                    }

                    this.dataInputInfo = dataInputInfo;
                    dataInput = input;
                    padding = HIGHLIGHT_PADDING_SIZE;
                    replay = true;
                    recordingIndicatorHilight = REPLAYING_ON_COLOUR;
                    break;
            }
        } catch (Exception e) {
            this.dataInputInfo = new DataInputInfo();
            e.printStackTrace();
            Log.e("realStart()", "failed to set input to: " + dataInputInfo.inputType, e);
        }

        roboStroke.setInput(dataInput);

        if (!replay) {
            registerBpmReceiver();
        }

        metersDisplayManager.reset();
        stopped = false;
        updateRecordingStateIndication(padding, recordingIndicatorHilight);
    }

    public boolean isReplaying() {
        return dataInputInfo.inputType != DataInputInfo.InputType.SENSORS;
    }

    public String getVersion() {
        try {
            PackageInfo packageInfo = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (NameNotFoundException ex) {
            return "unknown";
        }
    }

    private void cleanTmpDir() {
        if (FileHelper.hasExternalStorage()) {
            File tmpDir = FileHelper.getFile(APP_DATA_DIR, "tmp");
            tmpDir.mkdir();
            FileHelper.cleanDir(tmpDir, TimeUnit.SECONDS.toMillis(30));
        }
    }

    public ParameterListenerRegistration[] getListenerRegistrations() {
        return listenerRegistrations;
    }

    private void updateRecordingStateIndication(final int padding, final int color) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                View highlight = findViewById(R.id.record_play_state_highlighter);
                highlight.setPadding(padding, 0, padding, 0);
                highlight.setBackgroundColor(color);
            }
        });
    }

    public AppStroke getRoboStroke() {
        return roboStroke;
    }

    public void setLandscapeLayout(boolean landscape) {
        setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}