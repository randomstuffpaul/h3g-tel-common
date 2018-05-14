package com.itsoninc.android;

public class DeviceCall {
    boolean isVoice;
    String number;
    CallState state;

    public enum CallState {
        ACTIVE,
        ALERTING,
        HOLDING,
        DIALING,
        INCOMING,
        WAITING
    }

    public DeviceCall(boolean isVoice, CallState state, String number) {
        this.isVoice = isVoice;
        this.state = state;
        this.number = number;
    }

    public CallState getState() {
        return this.state;
    }

    public boolean isVoice() {
        return this.isVoice;
    }

    public String getNumber() {
        return this.number;
    }
}
