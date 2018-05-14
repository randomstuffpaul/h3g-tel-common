package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.sec.enterprise.DeviceInventory;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.internal.telephony.Call.SrvccState;
import com.android.internal.telephony.CommandException.Error;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public abstract class CallTracker extends Handler {
    private static final boolean DBG_POLL = false;
    protected static final int EVENT_CALL_STATE_CHANGE = 2;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA = 15;
    protected static final int EVENT_CONFERENCE_RESULT = 11;
    protected static final int EVENT_CS_FALLBACK = 104;
    protected static final int EVENT_DEFLECT_RESULT = 101;
    protected static final int EVENT_DELAY_DIAL_PENDING_CALL = 205;
    protected static final int EVENT_ECT_RESULT = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA = 14;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE = 5;
    protected static final int EVENT_HANGUP_FG_RESUME_BG_RESULT = 204;
    protected static final int EVENT_MODIFY_CALL = 102;
    protected static final int EVENT_OPERATION_COMPLETE = 4;
    protected static final int EVENT_POLL_CALLS_RESULT = 1;
    protected static final int EVENT_RADIO_AVAILABLE = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE = 10;
    protected static final int EVENT_REPOLL_AFTER_DELAY = 3;
    protected static final int EVENT_SEPARATE_RESULT = 12;
    protected static final int EVENT_SWITCH_RESULT = 8;
    protected static final int EVENT_THREE_WAY_DIAL_BLANK_FLASH = 20;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;
    protected static final int EVENT_VOICE_SYSTEM_ID = 103;
    protected static final int EVENT_VOLTE_SWITCH_RESULT = 105;
    static final int POLL_DELAY_MSEC = 250;
    private final int VALID_COMPARE_LENGTH;
    public CommandsInterface mCi;
    protected int mCsFallback;
    DeviceInventory mDeviceInfo;
    protected Connection mHandoverConnection;
    public boolean mHasEpdgCall;
    public boolean mHasVideoCall;
    public boolean mHasVolteCall;
    public boolean mIsDmHdvAlarmEvent;
    protected Message mLastRelevantPoll;
    protected boolean mNeedsPoll;
    protected boolean mNumberConverted;
    protected int mPendingOperations;
    PhoneRestrictionPolicy mPhoneRP;
    protected boolean mSkipDisableDataConnection;

    public abstract void handleMessage(Message message);

    protected abstract void handlePollCalls(AsyncResult asyncResult);

    protected abstract void log(String str);

    public abstract void registerForVoiceCallEnded(Handler handler, int i, Object obj);

    public abstract void registerForVoiceCallStarted(Handler handler, int i, Object obj);

    public abstract void unregisterForVoiceCallEnded(Handler handler);

    public abstract void unregisterForVoiceCallStarted(Handler handler);

    public CallTracker() {
        this.mNumberConverted = false;
        this.VALID_COMPARE_LENGTH = 3;
        this.mCsFallback = 0;
        this.mSkipDisableDataConnection = false;
        this.mHasVolteCall = false;
        this.mHasVideoCall = false;
        this.mHasEpdgCall = false;
        this.mIsDmHdvAlarmEvent = false;
        this.mDeviceInfo = null;
        this.mPhoneRP = null;
        this.mDeviceInfo = EnterpriseDeviceManager.getInstance().getDeviceInventory();
        this.mPhoneRP = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
    }

    protected void logCallEvent(String status, String address, long timeStamp, long duration, boolean isIncoming) {
        if (this.mDeviceInfo != null) {
            this.mDeviceInfo.addCallsCount(status);
            if (this.mDeviceInfo.isCallingCaptureEnabled()) {
                this.mDeviceInfo.storeCalling(address, Long.toString(timeStamp), Long.toString(duration), status, isIncoming);
            }
        }
    }

    protected void addNumberOfCalls(boolean isIncoming) {
        if (this.mPhoneRP != null) {
            if (isIncoming) {
                this.mPhoneRP.addNumberOfIncomingCalls();
            } else {
                this.mPhoneRP.addNumberOfOutgoingCalls();
            }
        }
    }

    protected void pollCallsWhenSafe() {
        this.mNeedsPoll = true;
        if (checkNoOperationsPending()) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        }
    }

    protected void pollCallsAfterDelay() {
        Message msg = obtainMessage();
        msg.what = 3;
        sendMessageDelayed(msg, 250);
    }

    protected boolean isCommandExceptionRadioNotAvailable(Throwable e) {
        return e != null && (e instanceof CommandException) && ((CommandException) e).getCommandError() == Error.RADIO_NOT_AVAILABLE;
    }

    protected void notifySrvccState(SrvccState state, Connection c) {
        if (state == SrvccState.STARTED) {
            this.mHandoverConnection = c;
        } else if (state != SrvccState.COMPLETED) {
            this.mHandoverConnection = null;
        }
    }

    protected void handleRadioAvailable() {
        pollCallsWhenSafe();
    }

    protected Message obtainNoPollCompleteMessage(int what) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        return obtainMessage(what);
    }

    private boolean checkNoOperationsPending() {
        return this.mPendingOperations == 0;
    }

    protected String checkForTestEmergencyNumber(String dialString) {
        String testEn = SystemProperties.get("ril.test.emergencynumber");
        if (TextUtils.isEmpty(testEn)) {
            return dialString;
        }
        String[] values = testEn.split(":");
        log("checkForTestEmergencyNumber: values.length=" + values.length);
        if (values.length != 2 || !values[0].equals(PhoneNumberUtils.stripSeparators(dialString))) {
            return dialString;
        }
        this.mCi.testingEmergencyCall();
        log("checkForTestEmergencyNumber: remap " + dialString + " to " + values[1]);
        return values[1];
    }

    protected String convertNumberIfNecessary(PhoneBase phoneBase, String dialNumber) {
        if (dialNumber == null) {
            return dialNumber;
        }
        String[] convertMaps = phoneBase.getContext().getResources().getStringArray(17236027);
        log("convertNumberIfNecessary Roaming convertMaps.length " + convertMaps.length + " dialNumber.length() " + dialNumber.length());
        if (convertMaps.length < 1 || dialNumber.length() < 3) {
            return dialNumber;
        }
        String outNumber = "";
        for (String convertMap : convertMaps) {
            log("convertNumberIfNecessary: " + convertMap);
            String[] entry = convertMap.split(":");
            if (entry.length > 1) {
                String[] tmpArray = entry[1].split(",");
                if (!TextUtils.isEmpty(entry[0]) && dialNumber.equals(entry[0])) {
                    if (tmpArray.length < 2 || TextUtils.isEmpty(tmpArray[1])) {
                        if (outNumber.isEmpty()) {
                            this.mNumberConverted = true;
                        }
                    } else if (compareGid1(phoneBase, tmpArray[1])) {
                        this.mNumberConverted = true;
                    }
                    if (this.mNumberConverted) {
                        if (TextUtils.isEmpty(tmpArray[0]) || !tmpArray[0].endsWith("MDN")) {
                            outNumber = tmpArray[0];
                        } else {
                            outNumber = tmpArray[0].substring(0, tmpArray[0].length() - 3) + phoneBase.getLine1Number();
                        }
                    }
                }
            }
        }
        if (!this.mNumberConverted) {
            return dialNumber;
        }
        log("convertNumberIfNecessary: convert service number");
        return outNumber;
    }

    private boolean compareGid1(PhoneBase phoneBase, String serviceGid1) {
        String gid1 = phoneBase.getGroupIdLevel1();
        int gid_length = serviceGid1.length();
        boolean ret = true;
        if (serviceGid1 == null || serviceGid1.equals("")) {
            log("compareGid1 serviceGid is empty, return " + true);
            return 1;
        }
        if (gid1 == null || gid1.length() < gid_length || !gid1.substring(0, gid_length).equalsIgnoreCase(serviceGid1)) {
            log(" gid1 " + gid1 + " serviceGid1 " + serviceGid1);
            ret = false;
        }
        log("compareGid1 is " + (ret ? "Same" : "Different"));
        return ret;
    }

    public void modifyCallInitiate(Message msg, CallModify callModify) throws CallStateException {
        this.mCi.modifyCallInitiate(callModify, msg);
    }

    public void modifyCallConfirm(Message msg, CallModify callModify) throws CallStateException {
        this.mCi.modifyCallConfirm(callModify, msg);
    }

    protected int getCallType(Call call) {
        Connection conn = call.getEarliestConnection();
        if (conn == null) {
            return 10;
        }
        if (conn.getCallDetails() != null) {
            return conn.getCallDetails().call_type;
        }
        log("getCallType(): callDetails is null. default to CALL_TYPE_VOICE.");
        return 0;
    }

    public boolean isAllActiveCallsOnLTE() {
        return false;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CallTracker:");
        pw.println(" mPendingOperations=" + this.mPendingOperations);
        pw.println(" mNeedsPoll=" + this.mNeedsPoll);
        pw.println(" mLastRelevantPoll=" + this.mLastRelevantPoll);
    }

    public void setDmHdvAlarmEvent(boolean onoff) {
        log("setDmHdvAlarmEvent - " + onoff);
        this.mIsDmHdvAlarmEvent = onoff;
    }

    public boolean getDmHdvAlarmEvent() {
        log("getDmHdvAlarmEvent - " + this.mIsDmHdvAlarmEvent);
        return this.mIsDmHdvAlarmEvent;
    }
}
