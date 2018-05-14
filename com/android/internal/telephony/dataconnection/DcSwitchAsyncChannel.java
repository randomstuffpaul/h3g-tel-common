package com.android.internal.telephony.dataconnection;

import android.os.Message;
import android.util.Log;
import com.android.internal.util.AsyncChannel;

public class DcSwitchAsyncChannel extends AsyncChannel {
    private static final int BASE = 278528;
    private static final int CMD_TO_STRING_COUNT = 8;
    private static final boolean DBG = true;
    private static final String LOG_TAG = "DcSwitchAsyncChannel";
    static final int REQ_CONNECT = 278528;
    static final int REQ_DISCONNECT = 278530;
    static final int REQ_IS_IDLE_OR_DEACTING_STATE = 278534;
    static final int REQ_IS_IDLE_STATE = 278532;
    static final int RSP_CONNECT = 278529;
    static final int RSP_DISCONNECT = 278531;
    static final int RSP_IS_IDLE_OR_DEACTING_STATE = 278535;
    static final int RSP_IS_IDLE_STATE = 278533;
    private static final boolean VDBG = false;
    private static String[] sCmdToString = new String[8];
    private DcSwitchState mDcSwitchState;
    private int tagId = 0;

    static {
        sCmdToString[0] = "REQ_CONNECT";
        sCmdToString[1] = "RSP_CONNECT";
        sCmdToString[2] = "REQ_DISCONNECT";
        sCmdToString[3] = "RSP_DISCONNECT";
        sCmdToString[4] = "REQ_IS_IDLE_STATE";
        sCmdToString[5] = "RSP_IS_IDLE_STATE";
        sCmdToString[6] = "REQ_IS_IDLE_OR_DEACTING_STATE";
        sCmdToString[7] = "RSP_IS_IDLE_OR_DEACTING_STATE";
    }

    protected static String cmdToString(int cmd) {
        cmd -= 278528;
        if (cmd < 0 || cmd >= sCmdToString.length) {
            return AsyncChannel.cmdToString(cmd + 278528);
        }
        return sCmdToString[cmd];
    }

    public DcSwitchAsyncChannel(DcSwitchState dcSwitchState, int id) {
        this.mDcSwitchState = dcSwitchState;
        this.tagId = id;
    }

    public void reqConnect(String type) {
        sendMessage(278528, type);
        log("reqConnect");
    }

    public int rspConnect(Message response) {
        int retVal = response.arg1;
        log("rspConnect=" + retVal);
        return retVal;
    }

    public int connectSync(String type) {
        Message response = sendMessageSynchronously(278528, type);
        if (response != null && response.what == RSP_CONNECT) {
            return rspConnect(response);
        }
        log("rspConnect error response=" + response);
        return 3;
    }

    public void reqDisconnect(String type) {
        sendMessage(REQ_DISCONNECT, type);
        log("reqDisconnect");
    }

    public int rspDisconnect(Message response) {
        int retVal = response.arg1;
        log("rspDisconnect=" + retVal);
        return retVal;
    }

    public int disconnectSync(String type) {
        Message response = sendMessageSynchronously(REQ_DISCONNECT, type);
        if (response != null && response.what == RSP_DISCONNECT) {
            return rspDisconnect(response);
        }
        log("rspDisconnect error response=" + response);
        return 3;
    }

    public void reqIsIdle() {
        sendMessage(REQ_IS_IDLE_STATE);
        log("reqIsIdle");
    }

    public boolean rspIsIdle(Message response) {
        boolean retVal = true;
        if (response.arg1 != 1) {
            retVal = false;
        }
        log("rspIsIdle=" + retVal);
        return retVal;
    }

    public boolean isIdleSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_STATE);
        if (response != null && response.what == RSP_IS_IDLE_STATE) {
            return rspIsIdle(response);
        }
        log("rspIsIndle error response=" + response);
        return false;
    }

    public void reqIsIdleOrDeacting() {
        sendMessage(REQ_IS_IDLE_OR_DEACTING_STATE);
        log("reqIsIdleOrDeacting");
    }

    public boolean rspIsIdleOrDeacting(Message response) {
        boolean retVal = true;
        if (response.arg1 != 1) {
            retVal = false;
        }
        log("rspIsIdleOrDeacting=" + retVal);
        return retVal;
    }

    public boolean isIdleOrDeactingSync() {
        Message response = sendMessageSynchronously(REQ_IS_IDLE_OR_DEACTING_STATE);
        if (response != null && response.what == RSP_IS_IDLE_OR_DEACTING_STATE) {
            return rspIsIdleOrDeacting(response);
        }
        log("rspIsIndleOrDeacting error response=" + response);
        return false;
    }

    public String toString() {
        return this.mDcSwitchState.getName();
    }

    private void log(String s) {
        Log.d(LOG_TAG, "[DcSwitchAsyncChannel-" + this.tagId + "]: " + s);
    }
}
