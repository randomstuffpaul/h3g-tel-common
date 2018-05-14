package com.itsoninc.android;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import java.util.List;

public abstract class ItsOnOemApiBase {
    private static final String DEFAULT_PATH = "/system/framework/";
    private static String FRAMEWORK_INTEGRATION_VERSION = "15.1-SNAPSHOT";
    private static final String ITSON_RESOURCES_DIR = "/resources";
    static final String OEM_CLASS = "com.itsoninc.android.impl.ItsOnOem";
    private static final String PATH_PROPERTY = "ro.itson.path";
    final String LOGTAG = "ItsOnAPI";
    Context context = null;
    ItsOnFrameworkInterface frameworkIf = null;
    ItsOnOemInterface mIfImpl = null;

    abstract boolean loadOemImpl();

    public void registerDownloadMapping(String url) {
        this.mIfImpl.registerDownloadMapping(url);
    }

    public void registerMediaMapping(String url) {
        this.mIfImpl.registerMediaMapping(url);
    }

    public void registerActivityMapping(String url, int uid) {
        this.mIfImpl.registerActivityMapping(url, uid);
    }

    @Deprecated
    public void setContext(Context context) {
        this.mIfImpl.setContext(context);
        this.context = context;
    }

    public void initFramework(Context context) {
        this.mIfImpl.initFramework(context);
        this.context = context;
    }

    public void initTelephony(Context context) {
        this.mIfImpl.initTelephony(context);
        this.context = context;
    }

    public void destroy() {
        this.mIfImpl.destroy();
        this.context = null;
    }

    public void setFrameworkInterface(ItsOnFrameworkInterface frameworkIf) {
        this.mIfImpl.setFrameworkInterface(frameworkIf);
        this.frameworkIf = frameworkIf;
    }

    public void onNewDataSession(String iface, String apn, String apnType) {
        this.mIfImpl.onNewDataSession(iface, apn, apnType);
    }

    public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        this.mIfImpl.onForegroundActivitiesChanged(pid, uid, foregroundActivities);
    }

    public void onImportanceChanged(int pid, int uid, int importance) {
        this.mIfImpl.onImportanceChanged(pid, uid, importance);
    }

    public void onProcessDied(int pid, int uid) {
        this.mIfImpl.onProcessDied(pid, uid);
    }

    public boolean authorizeIncomingVoice(String address) {
        return this.mIfImpl.authorizeIncomingVoice(address);
    }

    public boolean authorizeOutgoingVoice(String address) {
        return this.mIfImpl.authorizeOutgoingVoice(address);
    }

    public void rejectCall() {
        this.mIfImpl.rejectCall();
    }

    public void processCallList(List<DeviceCall> callList) {
        this.mIfImpl.processCallList(callList);
    }

    boolean authorizeOutgoingSms(byte[] pdu, int serial) {
        return this.mIfImpl.authorizeOutgoingSms(pdu, serial);
    }

    boolean authorizeOutgoingSms(String address, int serial) {
        return this.mIfImpl.authorizeOutgoingSms(address, serial);
    }

    boolean authorizeIncomingSms(String address, SmsType type, String mimeType) {
        return this.mIfImpl.authorizeIncomingSms(address, type, mimeType);
    }

    boolean authorizeIncomingSms(byte[] pdu) {
        return this.mIfImpl.authorizeIncomingSms(pdu);
    }

    public void smsError(int serial) {
        this.mIfImpl.smsError(serial);
    }

    public void smsDone(int serial) {
        this.mIfImpl.smsDone(serial);
    }

    public boolean authorizeOutgoingMms(String address, String transactionId) {
        return this.mIfImpl.authorizeOutgoingMms(address, transactionId);
    }

    public boolean authorizeOutgoingMms(List<String> addresses, String transactionId) {
        return this.mIfImpl.authorizeOutgoingMms((List) addresses, transactionId);
    }

    public boolean authorizeIncomingMms(String address, String transactionId) {
        return this.mIfImpl.authorizeIncomingMms(address, transactionId);
    }

    public void accountMms(String transactionId) {
        this.mIfImpl.accountMms(transactionId);
    }

    public void cleanupMms(String transactionId) {
        this.mIfImpl.cleanupMms(transactionId);
    }

    public void nitzTimeReceived(String time, long nitzReceiveTime) {
        this.mIfImpl.nitzTimeReceived(time, nitzReceiveTime);
    }

    public boolean dial(String address) {
        return this.mIfImpl.dial(address);
    }

    public boolean flash(String featureCode) {
        return this.mIfImpl.flash(featureCode);
    }

    public void acceptCall() {
        this.mIfImpl.acceptCall();
    }

    public boolean callWaiting(String number) {
        return this.mIfImpl.callWaiting(number);
    }

    public void processCDMACallList(List<DeviceCall> callList) {
        this.mIfImpl.processCDMACallList(callList);
    }

    public void setEmergencyMode(boolean inEmergencyMode) {
        this.mIfImpl.setEmergencyMode(inEmergencyMode);
    }

    public static String getJarFilePath() {
        return getItsOnResourcesPath() + "itson-oem.jar";
    }

    public static String getItsOnResourcesPath() {
        String path = SystemProperties.get(PATH_PROPERTY, "");
        if (path == null || path.equals("")) {
            return DEFAULT_PATH;
        }
        return getItsOnPath(path) + ITSON_RESOURCES_DIR + "/";
    }

    public static String getItsOnPath(String path) {
        if (path.charAt(path.length() - 1) == '/') {
            path = new String(path.substring(0, path.length() - 1));
        }
        if (path.endsWith(ITSON_RESOURCES_DIR)) {
            return new String(path.substring(0, path.length() - ITSON_RESOURCES_DIR.length()));
        }
        return path;
    }

    public static String getFrameworkIntegrationVersion() {
        return FRAMEWORK_INTEGRATION_VERSION;
    }

    public boolean isDataAllowed(long systemId, String operatorNumeric) {
        return this.mIfImpl.isDataAllowed(systemId, operatorNumeric);
    }

    public void setDataConnectionHandler(Handler handler, Message trySetupDataMessage) {
        this.mIfImpl.setDataConnectionHandler(handler, trySetupDataMessage);
    }
}
