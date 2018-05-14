package com.android.internal.telephony;

import android.telephony.Rlog;
import com.sec.android.app.CscFeature;
import java.util.ArrayList;
import java.util.List;

public abstract class Call {
    protected final String LOG_TAG = "Call";
    public ArrayList<Connection> mConnections = new ArrayList();
    protected boolean mIsGeneric = false;
    public State mState = State.IDLE;

    public enum CallType {
        NO_CALL,
        CS_CALL_VOICE,
        CS_CALL_VIDEO,
        IMS_CALL_VOICE,
        IMS_CALL_HDVIDEO,
        IMS_CALL_QCIFVIDEO,
        IMS_CALL_QVGAVIDEO,
        IMS_CALL_VIDEO_SHARE_TX,
        IMS_CALL_VIDEO_SHARE_RX,
        IMS_CALL_CONFERENCE,
        IMS_CALL_HDVIDEO_LAND,
        IMS_CALL_HD720VIDEO,
        IMS_CALL_CIFVIDEO
    }

    public enum SrvccState {
        NONE,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELED
    }

    public enum State {
        IDLE,
        ACTIVE,
        HOLDING,
        DIALING,
        ALERTING,
        INCOMING,
        WAITING,
        DISCONNECTED,
        DISCONNECTING;

        public boolean isAlive() {
            return (this == IDLE || this == DISCONNECTED || this == DISCONNECTING) ? false : true;
        }

        public boolean isRinging() {
            return this == INCOMING || this == WAITING;
        }

        public boolean isDialing() {
            return this == DIALING || this == ALERTING;
        }
    }

    public abstract List<Connection> getConnections();

    public abstract Phone getPhone();

    public abstract void hangup() throws CallStateException;

    public abstract boolean isMultiparty();

    public boolean hasConnection(Connection c) {
        return c.getCall() == this;
    }

    public boolean hasConnections() {
        List<Connection> connections = getConnections();
        if (connections != null && connections.size() > 0) {
            return true;
        }
        return false;
    }

    public State getState() {
        return this.mState;
    }

    public boolean isIdle() {
        return !getState().isAlive();
    }

    public Connection getEarliestConnection() {
        long time = Long.MAX_VALUE;
        Connection earliest = null;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = (Connection) l.get(i);
            long t = c.getCreateTime();
            if (t < time) {
                earliest = c;
                time = t;
            }
        }
        return earliest;
    }

    public long getEarliestCreateTime() {
        long time = Long.MAX_VALUE;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return 0;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            long t = ((Connection) l.get(i)).getCreateTime();
            if (t < time) {
                time = t;
            }
        }
        return time;
    }

    public long getEarliestConnectTime() {
        long time = Long.MAX_VALUE;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return 0;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            long t = ((Connection) l.get(i)).getConnectTime();
            if (t < time) {
                time = t;
            }
        }
        return time;
    }

    public boolean isDialingOrAlerting() {
        return getState().isDialing();
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public Connection getLatestConnection() {
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        long time = 0;
        Connection latest = null;
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = (Connection) l.get(i);
            long t = c.getCreateTime();
            if (t > time) {
                latest = c;
                time = t;
            }
        }
        return latest;
    }

    public boolean isGeneric() {
        return this.mIsGeneric;
    }

    public void setGeneric(boolean generic) {
        this.mIsGeneric = generic;
    }

    public void hangupIfAlive() {
        if (getState().isAlive()) {
            try {
                hangup();
            } catch (CallStateException ex) {
                Rlog.w("Call", " hangupIfActive: caught " + ex);
            }
        }
    }

    public CallType getCallType() {
        CallDetails details = getCallDetails();
        if (details == null) {
            return CallType.CS_CALL_VOICE;
        }
        return details.toCallType();
    }

    public void setCallType(CallType type) {
        Rlog.w("Call", "setCallType() is deprecated.");
    }

    public boolean isImsCall() {
        if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte")) {
            return false;
        }
        CallDetails details = getCallDetails();
        if (details == null || details.call_domain != 2) {
            return false;
        }
        return true;
    }

    public CallDetails getCallDetails() {
        Connection conn = getEarliestConnection();
        if (conn == null) {
            return null;
        }
        return conn.getCallDetails();
    }

    public String getCallRadioTech() {
        CallDetails callDetails = getCallDetails();
        if (callDetails == null) {
            return null;
        }
        return callDetails.getExtraValue("radiotech");
    }

    public boolean isVideoCall() {
        Connection conn = getEarliestConnection();
        if (conn == null || conn.getState() == State.DISCONNECTED || conn.getCallDetails() == null || conn.getCallDetails().call_type != 3) {
            return false;
        }
        Rlog.d("Call", "isVideoCall(): CALL_TYPE_VT");
        if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolteVtCall") || conn.getCallDetails().call_domain != 2) {
            return true;
        }
        Rlog.d("Call", "isVideoCall(): PS_CALL_TYPE_VT");
        return false;
    }

    public Connection getCdmaCwActiveConnection() {
        List l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        Connection cwActive = null;
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = (Connection) l.get(i);
            if (c.isCdmaCwActive()) {
                cwActive = c;
            }
        }
        return cwActive;
    }

    public Connection getCdmaCwHoldingConnection() {
        List l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        Connection cwHolding = null;
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = (Connection) l.get(i);
            if (c.isCdmaCwHolding()) {
                cwHolding = c;
            }
        }
        return cwHolding;
    }

    public void connectionDump() {
        List l = getConnections();
        if (l.size() == 0) {
            Rlog.d("Call", "No connection");
            return;
        }
        for (int i = 0; i < l.size(); i++) {
            Rlog.d("Call", "conn[" + i + "]: " + ((Connection) l.get(i)).toString());
        }
    }
}
