package com.android.internal.telephony.gsm;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.Phone;
import java.util.List;

class GsmCall extends Call {
    GsmCallTracker mOwner;

    static /* synthetic */ class C01031 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DriverCall$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.HOLDING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DriverCall$State[State.ALERTING.ordinal()] = 4;
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

    static Call.State stateFromDCState(State dcState) {
        switch (C01031.$SwitchMap$com$android$internal$telephony$DriverCall$State[dcState.ordinal()]) {
            case 1:
                return Call.State.ACTIVE;
            case 2:
                return Call.State.HOLDING;
            case 3:
                return Call.State.DIALING;
            case 4:
                return Call.State.ALERTING;
            case 5:
                return Call.State.INCOMING;
            case 6:
                return Call.State.WAITING;
            default:
                throw new RuntimeException("illegal call state:" + dcState);
        }
    }

    GsmCall(GsmCallTracker owner) {
        this.mOwner = owner;
    }

    public void dispose() {
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public Phone getPhone() {
        return this.mOwner.mPhone;
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public void hangup() throws CallStateException {
        this.mOwner.hangup(this);
    }

    public String toString() {
        return this.mState.toString();
    }

    void attach(Connection conn, DriverCall dc) {
        this.mConnections.add(conn);
        this.mState = stateFromDCState(dc.state);
    }

    void attachFake(Connection conn, Call.State state) {
        this.mConnections.add(conn);
        this.mState = state;
    }

    boolean connectionDisconnected(GsmConnection conn) {
        if (this.mState != Call.State.DISCONNECTED) {
            boolean hasOnlyDisconnectedConnections = true;
            int s = this.mConnections.size();
            for (int i = 0; i < s; i++) {
                if (((Connection) this.mConnections.get(i)).getState() != Call.State.DISCONNECTED) {
                    hasOnlyDisconnectedConnections = false;
                    break;
                }
            }
            if (hasOnlyDisconnectedConnections) {
                this.mState = Call.State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    void detach(GsmConnection conn) {
        this.mConnections.remove(conn);
        if (this.mConnections.size() == 0) {
            this.mState = Call.State.IDLE;
        }
    }

    boolean update(GsmConnection conn, DriverCall dc) {
        Call.State newState = stateFromDCState(dc.state);
        if (newState == this.mState) {
            return false;
        }
        this.mState = newState;
        return true;
    }

    boolean isFull() {
        return this.mConnections.size() == 5;
    }

    void onHangupLocal() {
        int s = this.mConnections.size();
        for (int i = 0; i < s; i++) {
            ((GsmConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = Call.State.DISCONNECTING;
    }

    void clearDisconnected() {
        for (int i = this.mConnections.size() - 1; i >= 0; i--) {
            if (((GsmConnection) this.mConnections.get(i)).getState() == Call.State.DISCONNECTED) {
                this.mConnections.remove(i);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = Call.State.IDLE;
        }
    }
}
