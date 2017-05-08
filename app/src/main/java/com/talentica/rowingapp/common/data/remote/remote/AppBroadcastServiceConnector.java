package com.talentica.rowingapp.common.data.remote.remote;

import android.content.Context;
import android.content.Intent;

import com.talentica.rowingapp.common.data.remote.DataRemote;
import com.talentica.rowingapp.common.data.remote.DataSender;
import com.talentica.rowingapp.common.data.session.SessionRecorderConstants;
import com.talentica.rowingapp.common.error.ServiceNotExist;

public class AppBroadcastServiceConnector implements DataSender {

    private final static String SERVICE_ID = "com.talentica.rowingapp.common.data.remote.remote.AppBroadcastService";

    private final Context context;
    private Intent service;
    private boolean started;
    private int port = SessionRecorderConstants.BROADCAST_PORT;
    private String host = SessionRecorderConstants.BROADCAST_HOST;

    public AppBroadcastServiceConnector(Context context) {
        this.context = context;
    }

    @Override
    public synchronized void start() throws DataRemote.DataRemoteError {
        AppRemoteServiceHelper helper;
        try {
            helper = new AppRemoteServiceHelper(context, SERVICE_ID);
        } catch (ServiceNotExist e) {
            throw new DataRemote.DataRemoteError(e);
        }

        service = helper.service;
        service.putExtra("port", port);
        service.putExtra("host", host);
        context.startService(service);
        started = true;
    }

    @Override
    public synchronized void stop() {

        if (started) {
            context.stopService(service);

            started = false;
        }
    }


    @Override
    public void write(String data) {

        if (started) {
            Intent intent = new Intent(SERVICE_ID);
            intent.putExtra("data", data);
            context.sendBroadcast(intent);
        }
    }

    @Override
    public void setAddress(String address) {
        this.host = address;
        restart();
    }

    @Override
    public synchronized void setPort(int port) {
        this.port = port;
        restart();
    }

    private synchronized void restart() {
        if (started) {
            stop();
            try {
                start();
            } catch (Exception e) {
            }
        }
    }
}
