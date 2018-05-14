package com.android.internal.telephony.test;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallModify;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.ArrayList;

public final class SimulatedCommands extends BaseCommands implements CommandsInterface, SimulatedRadioControl {
    private static final String DEFAULT_SIM_PIN2_CODE = "5678";
    private static final String DEFAULT_SIM_PIN_CODE = "1234";
    private static final SimFdnState INITIAL_FDN_STATE = SimFdnState.NONE;
    private static final SimLockState INITIAL_LOCK_STATE = SimLockState.NONE;
    private static final String LOG_TAG = "SimulatedCommands";
    private static final String SIM_PUK2_CODE = "87654321";
    private static final String SIM_PUK_CODE = "12345678";
    HandlerThread mHandlerThread = new HandlerThread(LOG_TAG);
    int mNetworkType;
    int mNextCallFailCause = 16;
    int mPausedResponseCount;
    ArrayList<Message> mPausedResponses = new ArrayList();
    String mPin2Code;
    int mPin2UnlockAttempts;
    String mPinCode;
    int mPinUnlockAttempts;
    int mPuk2UnlockAttempts;
    int mPukUnlockAttempts;
    boolean mSimFdnEnabled;
    SimFdnState mSimFdnEnabledState;
    boolean mSimLockEnabled;
    SimLockState mSimLockedState;
    boolean mSsnNotifyOn = false;
    SimulatedGsmCallState simulatedCallState;

    private enum SimFdnState {
        NONE,
        REQUIRE_PIN2,
        REQUIRE_PUK2,
        SIM_PERM_LOCKED
    }

    private enum SimLockState {
        NONE,
        REQUIRE_PIN,
        REQUIRE_PUK,
        SIM_PERM_LOCKED
    }

    public SimulatedCommands() {
        boolean z = true;
        super(null);
        this.mHandlerThread.start();
        this.simulatedCallState = new SimulatedGsmCallState(this.mHandlerThread.getLooper());
        setRadioState(RadioState.RADIO_OFF);
        this.mSimLockedState = INITIAL_LOCK_STATE;
        this.mSimLockEnabled = this.mSimLockedState != SimLockState.NONE;
        this.mPinCode = DEFAULT_SIM_PIN_CODE;
        this.mSimFdnEnabledState = INITIAL_FDN_STATE;
        if (this.mSimFdnEnabledState == SimFdnState.NONE) {
            z = false;
        }
        this.mSimFdnEnabled = z;
        this.mPin2Code = DEFAULT_SIM_PIN2_CODE;
    }

    public void getIccCardStatus(Message result) {
        unimplemented(result);
    }

    public void supplyIccPin(String pin, Message result) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PIN) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        } else if (pin != null && pin.equals(this.mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: success!");
            this.mPinUnlockAttempts = 0;
            this.mSimLockedState = SimLockState.NONE;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            this.mPinUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: failed! attempt=" + this.mPinUnlockAttempts);
            if (this.mPinUnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: set state to REQUIRE_PUK");
                this.mSimLockedState = SimLockState.REQUIRE_PUK;
            }
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void supplyIccPuk(String puk, String newPin, Message result) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PUK) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        } else if (puk != null && puk.equals(SIM_PUK_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: success!");
            this.mSimLockedState = SimLockState.NONE;
            this.mPukUnlockAttempts = 0;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            this.mPukUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: failed! attempt=" + this.mPukUnlockAttempts);
            if (this.mPukUnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: set state to SIM_PERM_LOCKED");
                this.mSimLockedState = SimLockState.SIM_PERM_LOCKED;
            }
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void supplyIccPin2(String pin2, Message result) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PIN2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: wrong state, state=" + this.mSimFdnEnabledState);
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        } else if (pin2 != null && pin2.equals(this.mPin2Code)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: success!");
            this.mPin2UnlockAttempts = 0;
            this.mSimFdnEnabledState = SimFdnState.NONE;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            this.mPin2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: failed! attempt=" + this.mPin2UnlockAttempts);
            if (this.mPin2UnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: set state to REQUIRE_PUK2");
                this.mSimFdnEnabledState = SimFdnState.REQUIRE_PUK2;
            }
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PUK2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: wrong state, state=" + this.mSimLockedState);
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        } else if (puk2 != null && puk2.equals(SIM_PUK2_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: success!");
            this.mSimFdnEnabledState = SimFdnState.NONE;
            this.mPuk2UnlockAttempts = 0;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            this.mPuk2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: failed! attempt=" + this.mPuk2UnlockAttempts);
            if (this.mPuk2UnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: set state to SIM_PERM_LOCKED");
                this.mSimFdnEnabledState = SimFdnState.SIM_PERM_LOCKED;
            }
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void changeIccPin(String oldPin, String newPin, Message result) {
        if (oldPin != null && oldPin.equals(this.mPinCode)) {
            this.mPinCode = newPin;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin: pin failed!");
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        if (oldPin2 != null && oldPin2.equals(this.mPin2Code)) {
            this.mPin2Code = newPin2;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin2: pin2 failed!");
            AsyncResult.forMessage(result, null, new CommandException(Error.PASSWORD_INCORRECT));
            result.sendToTarget();
        }
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        unimplemented(result);
    }

    public void setSuppServiceNotifications(boolean enable, Message result) {
        resultSuccess(result, null);
        if (enable && this.mSsnNotifyOn) {
            Rlog.w(LOG_TAG, "Supp Service Notifications already enabled!");
        }
        this.mSsnNotifyOn = enable;
    }

    public void queryFacilityLock(String facility, String pin, int serviceClass, Message result) {
        queryFacilityLockForApp(facility, pin, serviceClass, null, result);
    }

    public void queryFacilityLockForApp(String facility, String pin, int serviceClass, String appId, Message result) {
        int i = 1;
        int[] r;
        if (facility == null || !facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (facility == null || !facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
                unimplemented(result);
            } else if (result != null) {
                r = new int[1];
                if (!this.mSimFdnEnabled) {
                    i = 0;
                }
                r[0] = i;
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: FDN is " + (r[0] == 0 ? "disabled" : "enabled"));
                AsyncResult.forMessage(result, r, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            r = new int[1];
            if (!this.mSimLockEnabled) {
                i = 0;
            }
            r[0] = i;
            Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: SIM is " + (r[0] == 0 ? "unlocked" : "locked"));
            AsyncResult.forMessage(result, r, null);
            result.sendToTarget();
        }
    }

    public void setFacilityLock(String facility, boolean lockEnabled, String pin, int serviceClass, Message result) {
        setFacilityLockForApp(facility, lockEnabled, pin, serviceClass, null, result);
    }

    public void setFacilityLockForApp(String facility, boolean lockEnabled, String pin, int serviceClass, String appId, Message result) {
        if (facility == null || !facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (facility == null || !facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
                unimplemented(result);
            } else if (pin != null && pin.equals(this.mPin2Code)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 is valid");
                this.mSimFdnEnabled = lockEnabled;
                if (result != null) {
                    AsyncResult.forMessage(result, null, null);
                    result.sendToTarget();
                }
            } else if (result != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 failed!");
                AsyncResult.forMessage(result, null, new CommandException(Error.GENERIC_FAILURE));
                result.sendToTarget();
            }
        } else if (pin != null && pin.equals(this.mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin is valid");
            this.mSimLockEnabled = lockEnabled;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }
        } else if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin failed!");
            AsyncResult.forMessage(result, null, new CommandException(Error.GENERIC_FAILURE));
            result.sendToTarget();
        }
    }

    public void supplyNetworkDepersonalization(String netpin, Message result) {
        unimplemented(result);
    }

    public void getCurrentCalls(Message result) {
        if (this.mState != RadioState.RADIO_ON || isSimLocked()) {
            resultFail(result, new CommandException(Error.RADIO_NOT_AVAILABLE));
        } else {
            resultSuccess(result, this.simulatedCallState.getDriverCalls());
        }
    }

    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    public void getDataCallList(Message result) {
        resultSuccess(result, new ArrayList(0));
    }

    public void dial(String address, int clirMode, Message result) {
        this.simulatedCallState.onDial(address);
        resultSuccess(result, null);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, CallDetails callDetails, Message result) {
        dial(address, clirMode, result);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        this.simulatedCallState.onDial(address);
        resultSuccess(result, null);
    }

    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    public void getIMSIForApp(String aid, Message result) {
        resultSuccess(result, "012345678901234");
    }

    public void getIMEI(Message result) {
        resultSuccess(result, "012345678901234");
    }

    public void getIMEISV(Message result) {
        resultSuccess(result, "99");
    }

    public void hangupConnection(int gsmIndex, Message result) {
        if (this.simulatedCallState.onChld('1', (char) (gsmIndex + 48))) {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultSuccess");
            resultSuccess(result, null);
            return;
        }
        Rlog.i("GSM", "[SimCmd] hangupConnection: resultFail");
        resultFail(result, new RuntimeException("Hangup Error"));
    }

    public void hangupWaitingOrBackground(Message result) {
        if (this.simulatedCallState.onChld('0', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void hangupForegroundResumeBackground(Message result) {
        if (this.simulatedCallState.onChld('1', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void switchWaitingOrHoldingAndActive(Message result) {
        if (this.simulatedCallState.onChld('2', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void conference(Message result) {
        if (this.simulatedCallState.onChld('3', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void explicitCallTransfer(Message result) {
        if (this.simulatedCallState.onChld('4', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void separateConnection(int gsmIndex, Message result) {
        if (this.simulatedCallState.onChld('2', (char) (gsmIndex + 48))) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void acceptCall(Message result) {
        if (this.simulatedCallState.onAnswer()) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void acceptCall(int type, Message result) {
        acceptCall(result);
    }

    public void rejectCall(Message result) {
        if (this.simulatedCallState.onChld('0', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void getLastCallFailCause(Message result) {
        resultSuccess(result, new int[]{this.mNextCallFailCause});
    }

    @Deprecated
    public void getLastPdpFailCause(Message result) {
        unimplemented(result);
    }

    public void getLastDataCallFailCause(Message result) {
        unimplemented(result);
    }

    public void setMute(boolean enableMute, Message result) {
        unimplemented(result);
    }

    public void getMute(Message result) {
        unimplemented(result);
    }

    public void getSignalStrength(Message result) {
        resultSuccess(result, new int[]{23, 0});
    }

    public void setBandMode(int bandMode, Message result) {
        resultSuccess(result, null);
    }

    public void queryAvailableBandMode(Message result) {
        resultSuccess(result, new int[]{4, 2, 3, 4});
    }

    public void setLteBandMode(int bandMode, Message result) {
        resultSuccess(result, null);
    }

    public void sendTerminalResponse(String contents, Message response) {
        resultSuccess(response, null);
    }

    public void sendEnvelope(String contents, Message response) {
        resultSuccess(response, null);
    }

    public void sendEnvelopeWithStatus(String contents, Message response) {
        resultSuccess(response, null);
    }

    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        resultSuccess(response, null);
    }

    public void getVoiceRegistrationState(Message result) {
        resultSuccess(result, new String[]{"5", null, null, null, null, null, null, null, null, null, null, null, null, null});
    }

    public void getDataRegistrationState(Message result) {
        resultSuccess(result, new String[]{"5", null, null, "2"});
    }

    public void getOperator(Message result) {
        resultSuccess(result, new String[]{"El Telco Loco", "Telco Loco", "001001"});
    }

    public void sendDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    public void startDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    public void stopDtmf(Message result) {
        resultSuccess(result, null);
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        resultSuccess(result, null);
    }

    public void sendSMS(String smscPDU, String pdu, Message result) {
        unimplemented(result);
    }

    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        unimplemented(result);
    }

    public void deleteSmsOnSim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete message at index " + index);
        unimplemented(response);
    }

    public void deleteSmsOnRuim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete RUIM message at index " + index);
        unimplemented(response);
    }

    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to SIM with status " + status);
        unimplemented(response);
    }

    public void writeSmsToRuim(int status, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to RUIM with status " + status);
        unimplemented(response);
    }

    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        unimplemented(result);
    }

    public void deactivateDataCall(int cid, int reason, Message result) {
        unimplemented(result);
    }

    public void setPreferredNetworkType(int networkType, Message result) {
        this.mNetworkType = networkType;
        resultSuccess(result, null);
    }

    public void getPreferredNetworkType(Message result) {
        resultSuccess(result, new int[]{this.mNetworkType});
    }

    public void getNeighboringCids(Message result) {
        int[] ret = new int[7];
        ret[0] = 6;
        for (int i = 1; i < 7; i++) {
            ret[i] = i;
        }
        resultSuccess(result, ret);
    }

    public void setLocationUpdates(boolean enable, Message response) {
        unimplemented(response);
    }

    public void getSmscAddress(Message result) {
        unimplemented(result);
    }

    public void setSmscAddress(String address, Message result) {
        unimplemented(result);
    }

    public void reportSmsMemoryStatus(boolean available, Message result) {
        unimplemented(result);
    }

    public void reportStkServiceIsRunning(Message result) {
        resultSuccess(result, null);
    }

    public void getCdmaSubscriptionSource(Message result) {
        unimplemented(result);
    }

    private boolean isSimLocked() {
        if (this.mSimLockedState != SimLockState.NONE) {
            return true;
        }
        return false;
    }

    public void setRadioPower(boolean on, Message result) {
        if (on) {
            setRadioState(RadioState.RADIO_ON);
        } else {
            setRadioState(RadioState.RADIO_OFF);
        }
    }

    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        unimplemented(result);
    }

    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        unimplemented(result);
    }

    public void queryCLIP(Message response) {
        unimplemented(response);
    }

    public void getCLIR(Message result) {
        unimplemented(result);
    }

    public void setCLIR(int clirMode, Message result) {
        unimplemented(result);
    }

    public void queryCallWaiting(int serviceClass, Message response) {
        unimplemented(response);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        unimplemented(response);
    }

    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message result) {
        unimplemented(result);
    }

    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message result) {
        unimplemented(result);
    }

    public void setNetworkSelectionModeAutomatic(Message result) {
        unimplemented(result);
    }

    public void exitEmergencyCallbackMode(Message result) {
        unimplemented(result);
    }

    public void setNetworkSelectionModeManual(String operatorNumeric, Message result) {
        unimplemented(result);
    }

    public void getNetworkSelectionMode(Message result) {
        resultSuccess(result, new int[]{0});
    }

    public void getAvailableNetworks(Message result) {
        unimplemented(result);
    }

    public void getBasebandVersion(Message result) {
        resultSuccess(result, LOG_TAG);
    }

    public void triggerIncomingUssd(String statusCode, String message) {
        if (this.mUSSDRegistrant != null) {
            this.mUSSDRegistrant.notifyResult(new String[]{statusCode, message});
        }
    }

    public void sendUSSD(String ussdString, Message result) {
        if (ussdString.equals("#646#")) {
            resultSuccess(result, null);
            triggerIncomingUssd("0", "You have NNN minutes remaining.");
            return;
        }
        resultSuccess(result, null);
        triggerIncomingUssd("0", "All Done");
    }

    public void cancelPendingUssd(Message response) {
        resultSuccess(response, null);
    }

    public void resetRadio(Message result) {
        unimplemented(result);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        if (response != null) {
            AsyncResult.forMessage(response).result = data;
            response.sendToTarget();
        }
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        if (response != null) {
            AsyncResult.forMessage(response).result = strings;
            response.sendToTarget();
        }
    }

    public void triggerRing(String number) {
        this.simulatedCallState.triggerRing(number);
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void progressConnectingCallState() {
        this.simulatedCallState.progressConnectingCallState();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void progressConnectingToActive() {
        this.simulatedCallState.progressConnectingToActive();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void setAutoProgressConnectingCall(boolean b) {
        this.simulatedCallState.setAutoProgressConnectingCall(b);
    }

    public void setNextDialFailImmediately(boolean b) {
        this.simulatedCallState.setNextDialFailImmediately(b);
    }

    public void setNextCallFailCause(int gsmCause) {
        this.mNextCallFailCause = gsmCause;
    }

    public void triggerHangupForeground() {
        this.simulatedCallState.triggerHangupForeground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerHangupBackground() {
        this.simulatedCallState.triggerHangupBackground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerSsn(int type, int code) {
        SuppServiceNotification not = new SuppServiceNotification();
        not.notificationType = type;
        not.code = code;
        this.mSsnRegistrant.notifyRegistrant(new AsyncResult(null, not, null));
    }

    public void shutdown() {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
        Looper looper = this.mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    public void triggerHangupAll() {
        this.simulatedCallState.triggerHangupAll();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    public void triggerIncomingSMS(String message) {
    }

    public void pauseResponses() {
        this.mPausedResponseCount++;
    }

    public void resumeResponses() {
        this.mPausedResponseCount--;
        if (this.mPausedResponseCount == 0) {
            int s = this.mPausedResponses.size();
            for (int i = 0; i < s; i++) {
                ((Message) this.mPausedResponses.get(i)).sendToTarget();
            }
            this.mPausedResponses.clear();
            return;
        }
        Rlog.e("GSM", "SimulatedCommands.resumeResponses < 0");
    }

    private void unimplemented(Message result) {
        if (result != null) {
            AsyncResult.forMessage(result).exception = new RuntimeException("Unimplemented");
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultSuccess(Message result, Object ret) {
        if (result != null) {
            AsyncResult.forMessage(result).result = ret;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultFail(Message result, Throwable tr) {
        if (result != null) {
            AsyncResult.forMessage(result).exception = tr;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    public void getDeviceIdentity(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void getCDMASubscription(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void queryCdmaRoamingPreference(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void setPhoneType(int phoneType) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    public void getPreferredVoicePrivacy(Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    public void setTTYMode(int ttyMode, Message response) {
        Rlog.w(LOG_TAG, "Not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void queryTTYMode(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    public void sendCdmaSms(byte[] pdu, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);
    }

    public void getCdmaBroadcastConfig(Message response) {
        unimplemented(response);
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        unimplemented(response);
    }

    public void forceDataDormancy(Message response) {
        unimplemented(response);
    }

    public void setGsmBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        unimplemented(response);
    }

    public void getGsmBroadcastConfig(Message response) {
        unimplemented(response);
    }

    public void supplyIccPinForApp(String pin, String aid, Message response) {
        unimplemented(response);
    }

    public void supplyIccPukForApp(String puk, String newPin, String aid, Message response) {
        unimplemented(response);
    }

    public void supplyIccPin2ForApp(String pin2, String aid, Message response) {
        unimplemented(response);
    }

    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message response) {
        unimplemented(response);
    }

    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message response) {
        unimplemented(response);
    }

    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message response) {
        unimplemented(response);
    }

    public void requestIsimAuthentication(String nonce, Message response) {
        unimplemented(response);
    }

    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        unimplemented(response);
    }

    public void getVoiceRadioTechnology(Message response) {
        unimplemented(response);
    }

    public void getCellInfoList(Message response) {
        unimplemented(response);
    }

    public void setCellInfoListRate(int rateInMillis, Message response) {
        unimplemented(response);
    }

    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
    }

    public void setDataProfile(DataProfile[] dps, Message result) {
    }

    public void getImsRegistrationState(Message response) {
        unimplemented(response);
    }

    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {
        unimplemented(response);
    }

    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message response) {
        unimplemented(response);
    }

    public void iccOpenLogicalChannel(String AID, Message response) {
        unimplemented(response);
    }

    public void iccCloseLogicalChannel(int channel, Message response) {
        unimplemented(response);
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        unimplemented(response);
    }

    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message response) {
        unimplemented(response);
    }

    public void nvReadItem(int itemID, Message response) {
        unimplemented(response);
    }

    public void nvWriteItem(int itemID, String itemValue, Message response) {
        unimplemented(response);
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        unimplemented(response);
    }

    public void nvResetConfig(int resetType, Message response) {
        unimplemented(response);
    }

    public void getHardwareConfig(Message result) {
        unimplemented(result);
    }

    public void requestShutdown(Message result) {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
    }

    public void modifyCallInitiate(CallModify callModify, Message result) {
        unimplemented(result);
    }

    public void modifyCallConfirm(CallModify callModify, Message result) {
        unimplemented(result);
    }

    public void dialEmergencyCall(String address, int clirMode, Message result) {
        unimplemented(result);
    }

    public void dialEmergencyCall(String address, int clirMode, CallDetails callDetails, Message result) {
        unimplemented(result);
    }

    public void supplyNetworkDepersonalization(String netpin, int lockState, Message result) {
        unimplemented(result);
    }

    public void getPhoneBookStorageInfo(int fileid, Message result) {
        unimplemented(result);
    }

    public void getPhoneBookEntry(int command, int fileid, int index, String pin2, Message result) {
        unimplemented(result);
    }

    public void accessPhoneBookEntry(int command, int fileid, int index, String alphTag, String number, String email, String pin2, Message result) {
        unimplemented(result);
    }

    public void accessPhoneBookEntry(int command, int fileid, int index, AdnRecord adn, String pin2, Message result) {
        unimplemented(result);
    }

    public void getPreferredNetworkList(Message response) {
    }

    public void setPreferredNetworkList(int index, String operator, String plmn, int gsmAct, int gsmCompactAct, int utranAct, int mode, Message response) {
    }

    public void getSIMLockInfo(int num_lock_type, int lock_type, Message result) {
        unimplemented(result);
    }

    public void getUsimPBCapa(Message result) {
        unimplemented(result);
    }

    public void setSimInitEvent(Message result) {
        unimplemented(result);
    }

    public void setTransmitPower(int powerLevel, Message result) {
        unimplemented(result);
    }

    public void sendEncodedUSSD(byte[] ussdString, int length, int dcsCode, Message response) {
        unimplemented(response);
    }

    public void getCbConfig(Message result) {
        unimplemented(result);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newPwdAgain, Message result) {
        unimplemented(result);
    }

    public void changeIccSimPerso(String oldPass, String newPass, Message response) {
        unimplemented(response);
    }

    public void supplyIccPerso(String passwd, Message response) {
        unimplemented(response);
    }

    public void setSimPower(int on, Message result) {
        unimplemented(result);
    }

    public void sendSMSmore(String smscPDU, String pdu, Message result) {
        unimplemented(result);
    }

    public void sendCdmaSmsMore(byte[] pdu, Message result) {
        unimplemented(result);
    }

    public void hangupVT(int errCause, Message result) {
        if (this.simulatedCallState.onChld('0', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("HangupVT Error"));
        }
    }

    public void holdCall(Message result) {
        if (this.simulatedCallState.onChld('2', '\u0000')) {
            resultSuccess(result, null);
        } else {
            resultFail(result, new RuntimeException("Hangup Error"));
        }
    }

    public void setVoiceDomainPref(int on, Message result) {
        unimplemented(result);
    }
}
