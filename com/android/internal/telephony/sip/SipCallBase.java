package com.android.internal.telephony.sip;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.Connection;
import java.util.Iterator;
import java.util.List;

abstract class SipCallBase extends Call {
    protected abstract void setState(State state);

    SipCallBase() {
    }

    public List<Connection> getConnections() {
        return this.mConnections;
    }

    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public String toString() {
        return this.mState.toString() + ":" + super.toString();
    }

    void clearDisconnected() {
        Iterator<Connection> it = this.mConnections.iterator();
        while (it.hasNext()) {
            if (((Connection) it.next()).getState() == State.DISCONNECTED) {
                it.remove();
            }
        }
        if (this.mConnections.isEmpty()) {
            setState(State.IDLE);
        }
    }
}
