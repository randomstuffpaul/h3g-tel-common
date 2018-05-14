package com.itsoninc.android;

import android.content.Context;
import android.os.Handler;
import android.telephony.SmsMessage;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.RIL;
import com.itsoninc.android.DeviceCall.CallState;
import java.util.ArrayList;
import java.util.List;

public class ItsOnPhoneClient {
    protected static final int EVENT_POLL_CALLS_RESULT = 1;
    static final boolean IOPC_LOGD = false;
    String LOG_TAG;
    ItsOnOemApi mApi;
    public Handler mHandler;
    RIL mRil;

    class C01401 extends Handler {
        C01401() {
        }
    }

    static /* synthetic */ class C01412 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DriverCall$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ALERTING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.HOLDING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.INCOMING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.WAITING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    class AndroidFramework implements ItsOnFrameworkInterface {
        private Handler mHandler;
        private RIL mRil;

        AndroidFramework(RIL ril, Handler handler) {
            this.mRil = ril;
            this.mHandler = handler;
        }

        public void sendCallStateChanged() {
        }

        public void sendCallRing() {
        }

        public void hangupForegroundCalls() {
            this.mRil.hangupForegroundResumeBackground(this.mHandler.obtainMessage());
        }

        public void hangupIncomingCalls() {
            this.mRil.hangupWaitingOrBackground(this.mHandler.obtainMessage());
        }
    }

    public ItsOnPhoneClient(Context context, RIL ril) {
        this.LOG_TAG = "IOPC";
        this.mApi = null;
        this.mApi = ItsOnOemApi.getInstance();
        this.mApi.initTelephony(context);
        this.mRil = ril;
        this.mHandler = new C01401();
        this.mApi.setFrameworkInterface(new AndroidFramework(this.mRil, this.mHandler));
    }

    public void onNewDataSession(String iface, String apn, String apnType) {
        this.mApi.onNewDataSession(iface, apn, apnType);
    }

    public boolean authorizeIncomingSMS(SmsMessage sms) {
        return this.mApi.authorizeIncomingSms(sms.getPdu());
    }

    public boolean authorizeOutgoingSMS(byte[] pdu, int serial) {
        return this.mApi.authorizeOutgoingSms(pdu, serial);
    }

    public boolean authorizeOutgoingSMS(String address, int serial) {
        return this.mApi.authorizeOutgoingSms(address, serial);
    }

    public void sendSMSDone(int serial) {
        this.mApi.smsDone(serial);
    }

    public void sendSMSError(int serial) {
        this.mApi.smsError(serial);
    }

    public void incomingCallReject() {
        this.mApi.rejectCall();
    }

    public boolean authorizeIncomingVoice(String address) {
        return this.mApi.authorizeIncomingVoice(address);
    }

    public boolean authorizeOutgoingVoice(String address) {
        return this.mApi.authorizeOutgoingVoice(address);
    }

    public void trackCalls(List<DriverCall> callList) {
        this.mApi.processCallList(adaptCallList(callList));
    }

    private List<DeviceCall> adaptCallList(List<DriverCall> callList) {
        List<DeviceCall> deviceCalls = new ArrayList();
        for (DriverCall call : callList) {
            CallState state = null;
            switch (C01412.$SwitchMap$com$android$internal$telephony$DriverCall$State[call.state.ordinal()]) {
                case 1:
                    state = CallState.ACTIVE;
                    break;
                case 2:
                    state = CallState.ALERTING;
                    break;
                case 3:
                    state = CallState.DIALING;
                    break;
                case 4:
                    state = CallState.HOLDING;
                    break;
                case 5:
                    state = CallState.INCOMING;
                    break;
                case 6:
                    state = CallState.WAITING;
                    break;
                default:
                    break;
            }
            deviceCalls.add(new DeviceCall(call.isVoice, state, call.number));
        }
        return deviceCalls;
    }

    public void nitzTimeReceived(String time, long nitzReceiveTime) {
        this.mApi.nitzTimeReceived(time, nitzReceiveTime);
    }

    public boolean dial(String address) {
        return this.mApi.dial(address);
    }

    public boolean flash(String featureCode) {
        return this.mApi.flash(featureCode);
    }

    public void acceptCall() {
        this.mApi.acceptCall();
    }

    public boolean callWaiting(String number) {
        return this.mApi.callWaiting(number);
    }

    public void trackCdmaCalls(List<DriverCall> callList) {
        this.mApi.processCDMACallList(adaptCallList(callList));
    }

    public void setEmergencyMode(boolean inEmergencyMode) {
        this.mApi.setEmergencyMode(inEmergencyMode);
    }
}
