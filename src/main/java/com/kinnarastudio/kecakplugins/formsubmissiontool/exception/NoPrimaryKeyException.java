package com.kinnarastudio.kecakplugins.formsubmissiontool.exception;

public class NoPrimaryKeyException extends Exception {
    public NoPrimaryKeyException(String message) {
        super(message);
    }

    public NoPrimaryKeyException(Throwable cause) {
        super(cause);
    }
}
