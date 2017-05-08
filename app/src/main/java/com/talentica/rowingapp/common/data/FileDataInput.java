package com.talentica.rowingapp.common.data;

import android.util.Log;

import com.talentica.rowingapp.common.Pair;
import com.talentica.rowingapp.common.data.session.SessionFileVersionError;
import com.talentica.rowingapp.common.data.session.SessionRecorderConstants;
import com.talentica.rowingapp.common.data.stroke.AppStroke;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * SensorDataInput implementation for replaying data from a file 
 *
 */
public class FileDataInput extends RecordDataInput implements Runnable {

    private static final int MAX_VALIDATION_LINE_COUNT = 100;
    private final boolean batchMode = Boolean.getBoolean(FileDataInput.class.getName() + ".batchMode");
    private static final int SKIP_BYTES = 300;
    protected final RandomAccessFile reader;
    private float skipRequested = 0;
    private double setPosRequested = -1;
    private boolean paused;
    private Thread runThread;
    private boolean requestStop;
    protected final long fileLength;
    private long lastProgressNotifyTime;
    private final File dataFile;
    private ClockProvider clockProvider = new SystemClockProvider();
    private boolean resetClockRequired;
    private long startTimeOffset;
    private final long firstTimestamp;
    private final String uuid;
    
    public FileDataInput(AppStroke appStroke, File dataFile) throws IOException {
        super(appStroke);
        this.dataFile = dataFile;
        this.reader = new RandomAccessFile(dataFile, "r");
        fileLength = dataFile.length();
        setSeakable(true);
        Pair<String, Long> p = checkVersion();
        uuid = p.first;
        firstTimestamp = p.second;
    }

    public long getFirstTimestamp() {
        return firstTimestamp;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setClock(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }
    
    public ClockProvider getClock() {
        return clockProvider;
    }
    
    public File getDataFile() {
        return dataFile;
    }

    protected void setStartTimeOffset(long startTimeOffset) {
        this.startTimeOffset = startTimeOffset;
    }

    private Pair<String /* UUID */, Long /* firstTimestamp */> checkVersion() throws IOException, SessionFileVersionError {
        int version = -1;
        long firstTimestamp = 0;
        String uuid = null;
        boolean validVersion = false;
        int lineNum = 0;
        
        do {
            String line = reader.readLine();
            
            lineNum++;
            
            if (line == null) {
                break;
            }
            
            String[] vals = readRecordLine(line);

            if (vals != null) {
                final DataRecord.Type type;

                try {
                    type = DataRecord.Type.valueOf(vals[1]);
                } catch (IllegalArgumentException e) {
                    continue; // SessionFileVersionError() is thrown later
                }

                switch (type) {
                    case LOGFILE_VERSION:

                        if (lineNum != 1) {
                            throw new IllegalArgumentException("LOGFILE_VERSION must appear in the first line of the data file");
                        }
                        
                        version = new Integer(vals[3]);
                        firstTimestamp = new Long(vals[0]);
                        if (version == SessionRecorderConstants.LOGFILE_VERSION) {
                            validVersion = true;
                        }

                        break;
                    case UUID:
                        uuid = vals[0];
                        break;
                    default:
                        break;
                }
            }
        } while (uuid == null && lineNum < MAX_VALIDATION_LINE_COUNT);

        if (!validVersion) {
            throw new SessionFileVersionError(version);
        }		
        
        if (uuid == null) {
            throw new SessionFileVersionError("UUID was not found within the first " + MAX_VALIDATION_LINE_COUNT + " lines of data log file");
        }
        
        return Pair.create(uuid, firstTimestamp);
    }

    @Override
    public void run() {
        String l = "";


        while (!requestStop) {
            try {
                Thread.yield();

                long pos = reader.getFilePointer();

                if (setPosRequested != -1 || skipRequested != 0) {

                    if (setPosRequested != -1) {
                        pos = (long)(fileLength * setPosRequested);
                    } else {
                        assert skipRequested != 0;

                        pos += -skipRequested * SKIP_BYTES;
                    }

                    pos = Math.max(Math.min(reader.length() - 1,pos), 0);
                    reader.seek(pos);
                    reader.readLine();
                    skipRequested = 0;
                    setPosRequested = -1;

                    resetClockRequired = true;

                    if (bus != null) bus.fireEvent(DataRecord.Type.REPLAY_SKIPPED, null);

                    continue;
                }

                long currentTimeMillis = System.currentTimeMillis();

                if (currentTimeMillis - lastProgressNotifyTime > 500) {

                    lastProgressNotifyTime = currentTimeMillis;

                    double progress = calcProgress();

                    if (bus != null) bus.fireEvent(DataRecord.Type.REPLAY_PROGRESS, progress);

                }

                long lastFilePos = reader.getFilePointer();

                if ((l = reader.readLine()) == null) { 
                    continue;
                }

                handleRecord(l, lastFilePos);

            } catch (IOException e) {
                errorListener.onError(new Exception("can not read data", e));
                break;
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) { // probably corrupt record, try to continue anyway
                long pos;
                try {
                    pos = reader.getFilePointer();
                } catch (IOException e1) {
                    pos = -1;
                }
                Log.e("run()", String.format("error while reading record from %s near byte offset %d [%s]", getDataFile(), pos, l), e);
                continue;
            }
        }
    }

    protected double calcProgress() throws IOException {
        return reader.getFilePointer() / (double)fileLength;
    }

    public static Pair<Long /* record timestamp */, DataRecord> parseRecord(String line) {
        return parseRecord(line, false);
    }

    public static Pair<Long /* record timestamp */, DataRecord> parseRecord(String line, boolean force) {

        String[] vals = readRecordLine(line);

        if (vals != null) {

            Long logTimestamp = new Long(vals[0]);

            DataRecord.Type type = DataRecord.Type.valueOf(vals[1]);

            if ((type.isReplayableEvent || force) && type.isParsableEvent) {
                return Pair.create(logTimestamp, DataRecord.create(type, Long.parseLong(vals[2]), vals[3]));
            }			
        }

        return null;
    }

    private void handleRecord(String line, long lastReaderPos) throws Exception {

        Pair<Long /* record timestamp */, DataRecord> p = parseRecord(line);

        if (p != null) {

            handleRecord(p.first, p.second, lastReaderPos);
        }
    }

    private void handleRecord(long logTimestamp, DataRecord record, long lastReaderPos) throws Exception {

        long normalizedLogfileTime = logTimestamp - firstTimestamp + startTimeOffset;

        if (resetClockRequired) {
            clockProvider.reset(normalizedLogfileTime);
            resetClockRequired = false;
        }
        
        final long currentTime = getCurrentTime();

        long timeDiff = batchMode ? 0 : normalizedLogfileTime - currentTime;
        
        if (timeDiff > 20) {					
            Log.d("handleRecord()","data time {"+normalizedLogfileTime+"} later than current time {"+currentTime+"} - too soon to play, putting data back in reader");
            reader.seek(lastReaderPos);
            Thread.sleep(30);
            return;
        } else if (!batchMode) {
            Thread.yield();
        }

        playRecord(record);
    }

    private long getCurrentTime() {
        return clockProvider.getTime();
    }

    private static String[] readRecordLine(String line) {
        int eorIdx;
        if ((eorIdx = line.lastIndexOf(SessionRecorderConstants.END_OF_RECORD)) == -1) {
            return null;
        }

        String s = line.substring(0, eorIdx);

        String[] vals = s.split(" +");

        return vals;
    }


    @Override
    public void skipReplayTime(float velocityX) {
        if (!paused) {
            skipRequested = velocityX;
        }

    }

    @Override
    protected void onSetPosFinish(double pos) {

        if (pos < 0 || pos > 1.0) {
            throw new IllegalArgumentException("pos must be a float between 0 and 1.0");
        }

        setPosRequested = pos;		
    }

    @Override
    public synchronized void setPaused(boolean paused) {

        if (this.paused != paused) {
            Log.i("setPaused()", "setting paused = {}"+ paused);
            
            
            this.paused = paused;

            DataRecord.Type event;
            
            if (paused) {
                event = DataRecord.Type.REPLAY_PAUSED;
                clockProvider.stop();
            } else {
                event = DataRecord.Type.REPLAY_PLAYING;
                clockProvider.run();
            }

            if (bus != null) bus.fireEvent(event, null);
        }
    }

    @Override
    public synchronized void start() {

        super.start();

        runThread = new Thread(this, "MocDataFeeder") {
            {
                setDaemon(true);
            }
        };

        runThread.start();
        
        clockProvider.reset(startTimeOffset);
        clockProvider.run();
    }

    @Override
    public synchronized void stop() {
        if (runThread != null) {
            requestStop = true;
            runThread.interrupt();
            try {
                runThread.join();
            } catch (InterruptedException e) {
            }

            runThread = null;
        }

        super.stop();
    }
}
