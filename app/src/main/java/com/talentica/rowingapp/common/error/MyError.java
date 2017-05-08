package com.talentica.rowingapp.common.error;

/**
 * Created by suyashg on 18/04/17.
 */

public class MyError extends Throwable {

    public MyError(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
