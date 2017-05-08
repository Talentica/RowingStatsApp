package com.talentica.rowingapp.common.data.stroke;

import android.util.Log;

import com.talentica.rowingapp.common.AppEventBus;
import com.talentica.rowingapp.common.BusEventListener;
import com.talentica.rowingapp.common.Pair;
import com.talentica.rowingapp.common.data.DataRecord;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterChangeListener;
import com.talentica.rowingapp.common.param.ParameterListenerOwner;
import com.talentica.rowingapp.common.param.ParameterListenerRegistration;
import com.talentica.rowingapp.common.param.ParameterService;

import java.util.concurrent.TimeUnit;

/**
 * Rowing detectror. Generates rowing activity start/stop events based on value of parameter PARAM_ROWING_MODE
 */
public class RowingDetector implements SensorDataSink, ParameterListenerOwner {

//	private static final Logger logger = LoggerFactory.getLogger(RowingDetector.class);

    private final ParameterListenerRegistration[] listenerRegistrations = {

            new ParameterListenerRegistration(ParamKeys.PARAM_ROWING_STOP_TIMEOUT.getId(), new ParameterChangeListener() {
                @Override
                public void onParameterChanged(Parameter param) {
                    paramStopTimeout = TimeUnit.SECONDS.toNanos((Integer) param.getValue());
                }
            }),
            new ParameterListenerRegistration(ParamKeys.PARAM_ROWING_RESTART_WAIT_TIME.getId(), new ParameterChangeListener() {

                @Override
                public void onParameterChanged(Parameter param) {
                    paramRestartWaitTime = TimeUnit.SECONDS.toNanos((Integer) param.getValue());
                }
            }),
            new ParameterListenerRegistration(ParamKeys.PARAM_ROWING_MODE.getId(), new ParameterChangeListener() {

                @Override
                public void onParameterChanged(Parameter param) {
                    rowingMode = RowingSplitMode.valueOf((String) param.getValue());
                }
            }),
            new ParameterListenerRegistration(ParamKeys.PARAM_ROWING_START_AMPLITUDE_TRESHOLD.getId(), new ParameterChangeListener() {

                @Override
                public void onParameterChanged(Parameter param) {
                    paramStartMinAmplitude = (Float) param.getValue();
                }
            }),
            new ParameterListenerRegistration(ParamKeys.PARAM_STROKE_RATE_RATE_CHANGE_ACCEPT_FACTOR.getId(), new ParameterChangeListener() {

                @Override
                public void onParameterChanged(Parameter param) {
                    rateChangeAcceptFactor = (Float) param.getValue();
                }
            })
    };

    private RowingSplitMode rowingMode;

    private long paramStopTimeout;

    private float paramStartMinAmplitude;

    private long paramRestartWaitTime;

    private final AppEventBus bus;

    private boolean rowing;

    private Float rateChangeAcceptFactor;

    private int spm;

    private static class SplitData {

        private int strokeCount;

        private long lastStrokeEndTimestamp;

        private long lastTimestamp;

        private long rowingStartTimestamp;

        private long rowingStoppedTimestamp;

        private Pair<Long /* timestamp */, Float /* distance */> lastDistance;

        private Pair<Long, Float> startDistance;


        void reset(long timestamp) {
            strokeCount = 0;
            rowingStartTimestamp = rowingStoppedTimestamp = lastStrokeEndTimestamp = timestamp;
            startDistance = lastDistance = null;
        }
    }


    private boolean hasAmplitude;

    private boolean manuallyTriggered;


    private final ParameterService params;

    private final SplitData splitData = new SplitData();

    public RowingDetector(AppStroke appStroke) {


        ParameterService params = appStroke.getParameters();

        this.params = params;

        rowingMode = RowingSplitMode.valueOf((String) params.getValue(ParamKeys.PARAM_ROWING_MODE.getId()));

        paramStopTimeout = TimeUnit.SECONDS.toNanos((Integer) params.getValue(ParamKeys.PARAM_ROWING_STOP_TIMEOUT.getId()));

        paramStartMinAmplitude = (Float) params.getValue(ParamKeys.PARAM_ROWING_START_AMPLITUDE_TRESHOLD.getId());

        paramRestartWaitTime = TimeUnit.SECONDS.toNanos((Integer) params.getValue(ParamKeys.PARAM_ROWING_RESTART_WAIT_TIME.getId()));

        rateChangeAcceptFactor = (Float) params.getValue(ParamKeys.PARAM_STROKE_RATE_RATE_CHANGE_ACCEPT_FACTOR.getId());

        bus = appStroke.getBus();

        bus.addBusListener(new BusEventListener() {
            @Override
            public void onBusEvent(DataRecord event) {
                switch (event.type) {
                    case STROKE_DROP_BELOW_ZERO:
                        if (rowing) {
                            if (hasAmplitude) {
                                long timestamp = event.timestamp;
                                long msDiff = (timestamp - splitData.lastStrokeEndTimestamp) / 1000000;
                                if (msDiff > 0) { // was: msDiff > 1000 - disallow stroke rate above 60
                                    if (spm > 0 && msDiff < (rateChangeAcceptFactor * (60000 / spm))) {   // check for 'double' stroke
                                        Log.w("addBusListener", "### ignoring drop-below-zero event because it happend {}ms after a previous one " + msDiff);
//                                        logger.warn("### ignoring drop-below-zero event because it happend {}ms after a previous one", msDiff);
                                    } else {
                                        splitData.strokeCount++;
                                        bus.fireEvent(DataRecord.Type.ROWING_COUNT, timestamp, splitData.strokeCount);
                                        splitData.lastStrokeEndTimestamp = timestamp;
                                    }
                                }
                            }
                        }
                        hasAmplitude = false;
                        break;
                    case ROWING_START_TRIGGERED:
                        manuallyTriggered = true;
                        break;
                    case BOOKMARKED_DISTANCE:
                        Object[] values = (Object[]) event.data;
                        splitData.lastDistance = Pair.create((Long) values[0], (Float) values[1]);

                        if (rowing && splitData.startDistance == null) {
                            splitData.startDistance = splitData.lastDistance;
                            bus.fireEvent(DataRecord.Type.ROWING_START_DISTANCE, event.timestamp, splitData.startDistance.first, splitData.startDistance.second);
                        }
                        break;
                    case STROKE_RATE:
                        spm = (Integer) event.data;
                        break;
                }
            }
        });

        params.addListeners(this);
    }

    @Override
    public void onSensorData(long timestamp, Object value) {

        backtimeProtection(timestamp);

        float[] values = (float[]) value;
        float amplitude = values[0];
        final boolean validAmplitude = amplitude > paramStartMinAmplitude;

        hasAmplitude = hasAmplitude || validAmplitude;

        if (!rowing) {

            boolean enableNextStart = true;
            boolean forceStart = false;

            switch (rowingMode) {
                case CONTINUOUS:
                    forceStart = true;
                    break;
                case MANUAL:
                    forceStart = manuallyTriggered;
                    enableNextStart = false;
                    break;
                case SEMI_AUTO:
                    enableNextStart = manuallyTriggered;
                    break;
                case AUTO:
                    enableNextStart = true;
                    break;
            }

            if (forceStart ||
                    (enableNextStart && validAmplitude &&
                            (timestamp > (splitData.rowingStoppedTimestamp + paramRestartWaitTime)))) {
                startRowing(timestamp);
            }
        } else {

            boolean enableStop = true;
            boolean forceStop = false;

            switch (rowingMode) {
                case MANUAL:
                    forceStop = manuallyTriggered;
                /* no break; */
                case CONTINUOUS:
                    enableStop = false;
                    break;
            }

            if (forceStop || (enableStop && timestamp > (splitData.lastStrokeEndTimestamp + paramStopTimeout))) {
                stopRowing(timestamp);
            }
        }

        splitData.lastTimestamp = timestamp;
    }

    private void stopRowing(long timestamp) {
        rowing = hasAmplitude = false;

        float distance = splitData.lastDistance != null ? splitData.lastDistance.second : 0.0f;
        long travelTime = splitData.lastDistance != null ? splitData.lastDistance.first : 0;

        long stopTimestamp = (rowingMode == RowingSplitMode.MANUAL) ? timestamp : splitData.lastStrokeEndTimestamp;             
                
        /* stopTimestamp, distance, splitTime, travelTime, strokeCount */
        bus.fireEvent(DataRecord.Type.ROWING_STOP, timestamp, stopTimestamp, distance, (stopTimestamp - splitData.rowingStartTimestamp), travelTime, splitData.strokeCount);

        splitData.rowingStoppedTimestamp = timestamp;

        manuallyTriggered = false;
    }

    private void startRowing(final long timestamp) {
        rowing = true;

        splitData.reset(timestamp);

        bus.fireEvent(DataRecord.Type.ROWING_START, timestamp, timestamp);

        manuallyTriggered = false;
    }

    private void backtimeProtection(long timestamp) {
        if (timestamp < splitData.lastTimestamp) {
            splitData.rowingStoppedTimestamp = splitData.lastStrokeEndTimestamp = timestamp;
        }
    }

    public ParameterListenerRegistration[] getListenerRegistrations() {
        return listenerRegistrations;
    }

    @Override
    protected void finalize() throws Throwable {
        params.removeListeners(this);
        super.finalize();
    }
}
