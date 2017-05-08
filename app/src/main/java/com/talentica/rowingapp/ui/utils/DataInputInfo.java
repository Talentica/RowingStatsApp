package com.talentica.rowingapp.ui.utils;

import java.io.File;

/**
 * Created by suyashg on 18/04/17.
 */

public class DataInputInfo {
    public enum InputType {
        FILE,
        REMOTE,
        SENSORS
    }

    public final InputType inputType;
    public final File file;
    public final boolean temporary;
    public final String host;
    public final int port;

    public DataInputInfo(File file, boolean temporary) {
        this.inputType = InputType.FILE;
        this.file = file;
        this.temporary = temporary;
        this.host = null;
        this.port = -1;
    }

    public DataInputInfo(String host, int port) {
        this.inputType = InputType.REMOTE;
        this.file = null;
        this.temporary = false;
        this.host = host;
        this.port = port;
    }

    public DataInputInfo() {
        this.inputType = InputType.SENSORS;
        this.file = null;
        this.temporary = false;
        this.host = null;
        this.port = -1;
    }
}