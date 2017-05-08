package com.talentica.rowingapp.common.error;

public class ServiceNotExist extends Exception {
    private static final long serialVersionUID = 1L;

    public ServiceNotExist(String detailMessage) {
        super(detailMessage);
    }

}