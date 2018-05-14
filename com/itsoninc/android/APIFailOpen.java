package com.itsoninc.android;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import java.util.List;

public class APIFailOpen implements ItsOnOemInterface {
    final boolean DEBUG = false;
    final String LOGTAG = APIFailOpen.class.getName();

    public void registerDownloadMapping(String url) {
    }

    public void registerMediaMapping(String url) {
    }

    public void registerActivityMapping(String url, int uid) {
    }

    public boolean authorizeOutgoingVoice(String address) {
        return true;
    }

    public void setFrameworkInterface(ItsOnFrameworkInterface fwIf) {
    }

    public void setContext(Context context) {
    }

    public void processCallList(List<DeviceCall> list) {
    }

    public void rejectCall() {
    }

    public boolean authorizeIncomingSms(String address, SmsType type, String mimeType) {
        return true;
    }

    public boolean authorizeIncomingSms(byte[] pdu) {
        return true;
    }

    public boolean authorizeOutgoingSms(String address, int serial) {
        return true;
    }

    public boolean authorizeOutgoingSms(byte[] pdu, int serial) {
        return true;
    }

    public void smsDone(int serial) {
    }

    public void smsError(int serial) {
    }

    public boolean authorizeIncomingVoice(String address) {
        return true;
    }

    public boolean authorizeOutgoingMms(String address, String transactionId) {
        return true;
    }

    public boolean authorizeOutgoingMms(List<String> list, String transactionId) {
        return true;
    }

    public boolean authorizeIncomingMms(String address, String transactionId) {
        return true;
    }

    public void accountMms(String transactionId) {
    }

    public void cleanupMms(String transactionId) {
    }

    public boolean dial(String address) {
        return true;
    }

    public boolean flash(String featureCode) {
        return true;
    }

    public void acceptCall() {
    }

    public boolean callWaiting(String number) {
        return true;
    }

    public void processCDMACallList(List<DeviceCall> list) {
    }

    public void setEmergencyMode(boolean inEmergencyMode) {
    }

    public void destroy() {
    }

    public void nitzTimeReceived(String time, long nitzReceiveTime) {
    }

    public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
    }

    public void onImportanceChanged(int pid, int uid, int importance) {
    }

    public void onProcessDied(int pid, int uid) {
    }

    public void initFramework(Context context) {
    }

    public void initTelephony(Context context) {
    }

    public boolean isDataAllowed(long systemId, String operatorNumeric) {
        return true;
    }

    public void setDataConnectionHandler(Handler handler, Message trySetupDataMessage) {
    }

    public void onNewDataSession(String iface, String apn, String apnType) {
    }
}
