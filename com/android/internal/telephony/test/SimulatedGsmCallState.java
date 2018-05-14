package com.android.internal.telephony.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.DriverCall;
import java.util.ArrayList;
import java.util.List;

class SimulatedGsmCallState extends Handler {
    static final int CONNECTING_PAUSE_MSEC = 500;
    static final int EVENT_PROGRESS_CALL_STATE = 1;
    static final int MAX_CALLS = 7;
    private boolean mAutoProgressConnecting = true;
    CallInfo[] mCalls = new CallInfo[7];
    private boolean mNextDialFailImmediately;

    public SimulatedGsmCallState(Looper looper) {
        super(looper);
    }

    public void handleMessage(Message msg) {
        synchronized (this) {
            switch (msg.what) {
                case 1:
                    progressConnectingCallState();
                    break;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean triggerRing(java.lang.String r8) {
        /*
        r7 = this;
        r4 = 0;
        monitor-enter(r7);
        r1 = -1;
        r3 = 0;
        r2 = 0;
    L_0x0005:
        r5 = r7.mCalls;	 Catch:{ all -> 0x003c }
        r5 = r5.length;	 Catch:{ all -> 0x003c }
        if (r2 >= r5) goto L_0x0031;
    L_0x000a:
        r5 = r7.mCalls;	 Catch:{ all -> 0x003c }
        r0 = r5[r2];	 Catch:{ all -> 0x003c }
        if (r0 != 0) goto L_0x0016;
    L_0x0010:
        if (r1 >= 0) goto L_0x0016;
    L_0x0012:
        r1 = r2;
    L_0x0013:
        r2 = r2 + 1;
        goto L_0x0005;
    L_0x0016:
        if (r0 == 0) goto L_0x002d;
    L_0x0018:
        r5 = r0.mState;	 Catch:{ all -> 0x003c }
        r6 = com.android.internal.telephony.test.CallInfo.State.INCOMING;	 Catch:{ all -> 0x003c }
        if (r5 == r6) goto L_0x0024;
    L_0x001e:
        r5 = r0.mState;	 Catch:{ all -> 0x003c }
        r6 = com.android.internal.telephony.test.CallInfo.State.WAITING;	 Catch:{ all -> 0x003c }
        if (r5 != r6) goto L_0x002d;
    L_0x0024:
        r5 = "ModelInterpreter";
        r6 = "triggerRing failed; phone already ringing";
        android.telephony.Rlog.w(r5, r6);	 Catch:{ all -> 0x003c }
        monitor-exit(r7);	 Catch:{ all -> 0x003c }
    L_0x002c:
        return r4;
    L_0x002d:
        if (r0 == 0) goto L_0x0013;
    L_0x002f:
        r3 = 1;
        goto L_0x0013;
    L_0x0031:
        if (r1 >= 0) goto L_0x003f;
    L_0x0033:
        r5 = "ModelInterpreter";
        r6 = "triggerRing failed; all full";
        android.telephony.Rlog.w(r5, r6);	 Catch:{ all -> 0x003c }
        monitor-exit(r7);	 Catch:{ all -> 0x003c }
        goto L_0x002c;
    L_0x003c:
        r4 = move-exception;
        monitor-exit(r7);	 Catch:{ all -> 0x003c }
        throw r4;
    L_0x003f:
        r4 = r7.mCalls;	 Catch:{ all -> 0x003c }
        r5 = android.telephony.PhoneNumberUtils.extractNetworkPortion(r8);	 Catch:{ all -> 0x003c }
        r5 = com.android.internal.telephony.test.CallInfo.createIncomingCall(r5);	 Catch:{ all -> 0x003c }
        r4[r1] = r5;	 Catch:{ all -> 0x003c }
        if (r3 == 0) goto L_0x0055;
    L_0x004d:
        r4 = r7.mCalls;	 Catch:{ all -> 0x003c }
        r4 = r4[r1];	 Catch:{ all -> 0x003c }
        r5 = com.android.internal.telephony.test.CallInfo.State.WAITING;	 Catch:{ all -> 0x003c }
        r4.mState = r5;	 Catch:{ all -> 0x003c }
    L_0x0055:
        monitor-exit(r7);	 Catch:{ all -> 0x003c }
        r4 = 1;
        goto L_0x002c;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.test.SimulatedGsmCallState.triggerRing(java.lang.String):boolean");
    }

    public void progressConnectingCallState() {
        synchronized (this) {
            int i = 0;
            while (i < this.mCalls.length) {
                CallInfo call = this.mCalls[i];
                if (call == null || call.mState != State.DIALING) {
                    if (call != null && call.mState == State.ALERTING) {
                        call.mState = State.ACTIVE;
                        break;
                    }
                    i++;
                } else {
                    call.mState = State.ALERTING;
                    if (this.mAutoProgressConnecting) {
                        sendMessageDelayed(obtainMessage(1, call), 500);
                    }
                }
            }
        }
    }

    public void progressConnectingToActive() {
        synchronized (this) {
            for (CallInfo call : this.mCalls) {
                if (call != null && (call.mState == State.DIALING || call.mState == State.ALERTING)) {
                    call.mState = State.ACTIVE;
                    break;
                }
            }
        }
    }

    public void setAutoProgressConnectingCall(boolean b) {
        this.mAutoProgressConnecting = b;
    }

    public void setNextDialFailImmediately(boolean b) {
        this.mNextDialFailImmediately = b;
    }

    public boolean triggerHangupForeground() {
        boolean found;
        synchronized (this) {
            int i;
            found = false;
            for (i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && (call.mState == State.INCOMING || call.mState == State.WAITING)) {
                    this.mCalls[i] = null;
                    found = true;
                }
            }
            for (i = 0; i < this.mCalls.length; i++) {
                call = this.mCalls[i];
                if (call != null && (call.mState == State.DIALING || call.mState == State.ACTIVE || call.mState == State.ALERTING)) {
                    this.mCalls[i] = null;
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean triggerHangupBackground() {
        boolean found;
        synchronized (this) {
            found = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (call != null && call.mState == State.HOLDING) {
                    this.mCalls[i] = null;
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean triggerHangupAll() {
        boolean found;
        synchronized (this) {
            found = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                CallInfo call = this.mCalls[i];
                if (this.mCalls[i] != null) {
                    found = true;
                }
                this.mCalls[i] = null;
            }
        }
        return found;
    }

    public boolean onAnswer() {
        synchronized (this) {
            int i = 0;
            while (i < this.mCalls.length) {
                CallInfo call = this.mCalls[i];
                if (call == null || !(call.mState == State.INCOMING || call.mState == State.WAITING)) {
                    i++;
                } else {
                    boolean switchActiveAndHeldOrWaiting = switchActiveAndHeldOrWaiting();
                    return switchActiveAndHeldOrWaiting;
                }
            }
            return false;
        }
    }

    public boolean onHangup() {
        boolean found = false;
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo call = this.mCalls[i];
            if (!(call == null || call.mState == State.WAITING)) {
                this.mCalls[i] = null;
                found = true;
            }
        }
        return found;
    }

    public boolean onChld(char c0, char c1) {
        int callIndex = 0;
        if (c1 != '\u0000') {
            callIndex = c1 - 49;
            if (callIndex < 0 || callIndex >= this.mCalls.length) {
                return false;
            }
        }
        switch (c0) {
            case '0':
                return releaseHeldOrUDUB();
            case '1':
                if (c1 <= '\u0000') {
                    return releaseActiveAcceptHeldOrWaiting();
                }
                if (this.mCalls[callIndex] == null) {
                    return false;
                }
                this.mCalls[callIndex] = null;
                return true;
            case '2':
                if (c1 <= '\u0000') {
                    return switchActiveAndHeldOrWaiting();
                }
                return separateCall(callIndex);
            case '3':
                return conference();
            case '4':
                return explicitCallTransfer();
            case '5':
                return false;
            default:
                return false;
        }
    }

    public boolean releaseHeldOrUDUB() {
        int i;
        boolean found = false;
        for (i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null && c.isRinging()) {
                found = true;
                this.mCalls[i] = null;
                break;
            }
        }
        if (!found) {
            for (i = 0; i < this.mCalls.length; i++) {
                c = this.mCalls[i];
                if (c != null && c.mState == State.HOLDING) {
                    this.mCalls[i] = null;
                }
            }
        }
        return true;
    }

    public boolean releaseActiveAcceptHeldOrWaiting() {
        int i;
        boolean foundHeld = false;
        boolean foundActive = false;
        for (i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null && c.mState == State.ACTIVE) {
                this.mCalls[i] = null;
                foundActive = true;
            }
        }
        if (!foundActive) {
            for (i = 0; i < this.mCalls.length; i++) {
                c = this.mCalls[i];
                if (c != null && (c.mState == State.DIALING || c.mState == State.ALERTING)) {
                    this.mCalls[i] = null;
                }
            }
        }
        for (CallInfo c2 : this.mCalls) {
            if (c2 != null && c2.mState == State.HOLDING) {
                c2.mState = State.ACTIVE;
                foundHeld = true;
            }
        }
        if (!foundHeld) {
            for (CallInfo c22 : this.mCalls) {
                if (c22 != null && c22.isRinging()) {
                    c22.mState = State.ACTIVE;
                    break;
                }
            }
        }
        return true;
    }

    public boolean switchActiveAndHeldOrWaiting() {
        boolean hasHeld = false;
        for (CallInfo c : this.mCalls) {
            if (c != null && c.mState == State.HOLDING) {
                hasHeld = true;
                break;
            }
        }
        for (CallInfo c2 : this.mCalls) {
            if (c2 != null) {
                if (c2.mState == State.ACTIVE) {
                    c2.mState = State.HOLDING;
                } else if (c2.mState == State.HOLDING) {
                    c2.mState = State.ACTIVE;
                } else if (!hasHeld && c2.isRinging()) {
                    c2.mState = State.ACTIVE;
                }
            }
        }
        return true;
    }

    public boolean separateCall(int index) {
        try {
            CallInfo c = this.mCalls[index];
            if (c == null || c.isConnecting() || countActiveLines() != 1) {
                return false;
            }
            c.mState = State.ACTIVE;
            c.mIsMpty = false;
            for (int i = 0; i < this.mCalls.length; i++) {
                int countHeld = 0;
                int lastHeld = 0;
                if (i != index) {
                    CallInfo cb = this.mCalls[i];
                    if (cb != null && cb.mState == State.ACTIVE) {
                        cb.mState = State.HOLDING;
                        countHeld = 0 + 1;
                        lastHeld = i;
                    }
                }
                if (countHeld == 1) {
                    this.mCalls[lastHeld].mIsMpty = false;
                }
            }
            return true;
        } catch (InvalidStateEx e) {
            return false;
        }
    }

    public boolean conference() {
        int countCalls = 0;
        for (CallInfo c : this.mCalls) {
            if (c != null) {
                countCalls++;
                if (c.isConnecting()) {
                    return false;
                }
            }
        }
        for (CallInfo c2 : this.mCalls) {
            if (c2 != null) {
                c2.mState = State.ACTIVE;
                if (countCalls > 0) {
                    c2.mIsMpty = true;
                }
            }
        }
        return true;
    }

    public boolean explicitCallTransfer() {
        int countCalls = 0;
        for (CallInfo c : this.mCalls) {
            if (c != null) {
                countCalls++;
                if (c.isConnecting()) {
                    return false;
                }
            }
        }
        return triggerHangupAll();
    }

    public boolean onDial(String address) {
        int freeSlot = -1;
        Rlog.d("GSM", "SC> dial '" + address + "'");
        if (this.mNextDialFailImmediately) {
            this.mNextDialFailImmediately = false;
            Rlog.d("GSM", "SC< dial fail (per request)");
            return false;
        }
        String phNum = PhoneNumberUtils.extractNetworkPortion(address);
        if (phNum.length() == 0) {
            Rlog.d("GSM", "SC< dial fail (invalid ph num)");
            return false;
        } else if (phNum.startsWith("*99") && phNum.endsWith("#")) {
            Rlog.d("GSM", "SC< dial ignored (gprs)");
            return true;
        } else {
            try {
                if (countActiveLines() > 1) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                int i = 0;
                while (i < this.mCalls.length) {
                    if (freeSlot < 0 && this.mCalls[i] == null) {
                        freeSlot = i;
                    }
                    if (this.mCalls[i] == null || this.mCalls[i].isActiveOrHeld()) {
                        if (this.mCalls[i] != null && this.mCalls[i].mState == State.ACTIVE) {
                            this.mCalls[i].mState = State.HOLDING;
                        }
                        i++;
                    } else {
                        Rlog.d("GSM", "SC< dial fail (invalid call state)");
                        return false;
                    }
                }
                if (freeSlot < 0) {
                    Rlog.d("GSM", "SC< dial fail (invalid call state)");
                    return false;
                }
                this.mCalls[freeSlot] = CallInfo.createOutgoingCall(phNum);
                if (this.mAutoProgressConnecting) {
                    sendMessageDelayed(obtainMessage(1, this.mCalls[freeSlot]), 500);
                }
                Rlog.d("GSM", "SC< dial (slot = " + freeSlot + ")");
                return true;
            } catch (InvalidStateEx e) {
                Rlog.d("GSM", "SC< dial fail (invalid call state)");
                return false;
            }
        }
    }

    public List<DriverCall> getDriverCalls() {
        ArrayList<DriverCall> ret = new ArrayList(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                ret.add(c.toDriverCall(i + 1));
            }
        }
        Rlog.d("GSM", "SC< getDriverCalls " + ret);
        return ret;
    }

    public List<String> getClccLines() {
        ArrayList<String> ret = new ArrayList(this.mCalls.length);
        for (int i = 0; i < this.mCalls.length; i++) {
            CallInfo c = this.mCalls[i];
            if (c != null) {
                ret.add(c.toCLCCLine(i + 1));
            }
        }
        return ret;
    }

    private int countActiveLines() throws InvalidStateEx {
        boolean hasMpty = false;
        boolean hasHeld = false;
        boolean hasActive = false;
        boolean hasConnecting = false;
        boolean hasRinging = false;
        boolean mptyIsHeld = false;
        for (CallInfo call : this.mCalls) {
            if (call != null) {
                int i;
                if (hasMpty || !call.mIsMpty) {
                    if (call.mIsMpty && mptyIsHeld && call.mState == State.ACTIVE) {
                        Rlog.e("ModelInterpreter", "Invalid state");
                        throw new InvalidStateEx();
                    } else if (!call.mIsMpty && hasMpty && mptyIsHeld && call.mState == State.HOLDING) {
                        Rlog.e("ModelInterpreter", "Invalid state");
                        throw new InvalidStateEx();
                    }
                } else if (call.mState == State.HOLDING) {
                    mptyIsHeld = true;
                } else {
                    mptyIsHeld = false;
                }
                hasMpty |= call.mIsMpty;
                if (call.mState == State.HOLDING) {
                    i = 1;
                } else {
                    i = 0;
                }
                hasHeld |= i;
                if (call.mState == State.ACTIVE) {
                    i = 1;
                } else {
                    i = 0;
                }
                hasActive |= i;
                hasConnecting |= call.isConnecting();
                hasRinging |= call.isRinging();
            }
        }
        int ret = 0;
        if (hasHeld) {
            ret = 0 + 1;
        }
        if (hasActive) {
            ret++;
        }
        if (hasConnecting) {
            ret++;
        }
        if (hasRinging) {
            return ret + 1;
        }
        return ret;
    }
}
