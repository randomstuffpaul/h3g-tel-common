package com.android.internal.telephony.imsphone;

import android.telephony.Rlog;
import com.android.ims.ImsCall;
import com.android.ims.ImsException;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import java.util.Iterator;
import java.util.List;

public class ImsPhoneCall extends Call {
    private static final String LOG_TAG = "ImsPhoneCall";
    ImsPhoneCallTracker mOwner;
    private boolean mRingbackTonePlayed = false;

    public void dispose() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Incorrect nodes count for selectOther: B:11:0x004e in [B:7:0x0040, B:11:0x004e, B:10:0x004f, B:9:0x004f]
	at jadx.core.utils.BlockUtils.selectOther(BlockUtils.java:53)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:64)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r6 = this;
        r5 = 14;
        r3 = r6.mOwner;	 Catch:{ CallStateException -> 0x001e, all -> 0x0036 }
        r3.hangup(r6);	 Catch:{ CallStateException -> 0x001e, all -> 0x0036 }
        r1 = 0;
        r3 = r6.mConnections;
        r2 = r3.size();
    L_0x000e:
        if (r1 >= r2) goto L_0x004f;
    L_0x0010:
        r3 = r6.mConnections;
        r0 = r3.get(r1);
        r0 = (com.android.internal.telephony.imsphone.ImsPhoneConnection) r0;
        r0.onDisconnect(r5);
        r1 = r1 + 1;
        goto L_0x000e;
    L_0x001e:
        r3 = move-exception;
        r1 = 0;
        r3 = r6.mConnections;
        r2 = r3.size();
    L_0x0026:
        if (r1 >= r2) goto L_0x004f;
    L_0x0028:
        r3 = r6.mConnections;
        r0 = r3.get(r1);
        r0 = (com.android.internal.telephony.imsphone.ImsPhoneConnection) r0;
        r0.onDisconnect(r5);
        r1 = r1 + 1;
        goto L_0x0026;
    L_0x0036:
        r3 = move-exception;
        r1 = 0;
        r4 = r6.mConnections;
        r2 = r4.size();
    L_0x003e:
        if (r1 >= r2) goto L_0x004e;
    L_0x0040:
        r4 = r6.mConnections;
        r0 = r4.get(r1);
        r0 = (com.android.internal.telephony.imsphone.ImsPhoneConnection) r0;
        r0.onDisconnect(r5);
        r1 = r1 + 1;
        goto L_0x003e;
    L_0x004e:
        throw r3;
    L_0x004f:
        return;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.imsphone.ImsPhoneCall.dispose():void");
    }

    ImsPhoneCall() {
    }

    ImsPhoneCall(ImsPhoneCallTracker owner) {
        this.mOwner = owner;
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

    void attach(Connection conn) {
        clearDisconnected();
        this.mConnections.add(conn);
    }

    void attach(Connection conn, State state) {
        attach(conn);
        this.mState = state;
    }

    void attachFake(Connection conn, State state) {
        attach(conn, state);
    }

    boolean connectionDisconnected(ImsPhoneConnection conn) {
        if (this.mState != State.DISCONNECTED) {
            boolean hasOnlyDisconnectedConnections = true;
            int s = this.mConnections.size();
            for (int i = 0; i < s; i++) {
                if (((Connection) this.mConnections.get(i)).getState() != State.DISCONNECTED) {
                    hasOnlyDisconnectedConnections = false;
                    break;
                }
            }
            if (hasOnlyDisconnectedConnections) {
                this.mState = State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    void detach(ImsPhoneConnection conn) {
        this.mConnections.remove(conn);
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    boolean isFull() {
        return this.mConnections.size() == 5;
    }

    void onHangupLocal() {
        int s = this.mConnections.size();
        for (int i = 0; i < s; i++) {
            ((ImsPhoneConnection) this.mConnections.get(i)).onHangupLocal();
        }
        this.mState = State.DISCONNECTING;
    }

    void clearDisconnected() {
        for (int i = this.mConnections.size() - 1; i >= 0; i--) {
            if (((ImsPhoneConnection) this.mConnections.get(i)).getState() == State.DISCONNECTED) {
                this.mConnections.remove(i);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = State.IDLE;
        }
    }

    ImsPhoneConnection getFirstConnection() {
        if (this.mConnections.size() == 0) {
            return null;
        }
        return (ImsPhoneConnection) this.mConnections.get(0);
    }

    void setMute(boolean mute) {
        ImsCall imsCall = getFirstConnection() == null ? null : getFirstConnection().getImsCall();
        if (imsCall != null) {
            try {
                imsCall.setMute(mute);
            } catch (ImsException e) {
                Rlog.e(LOG_TAG, "setMute failed : " + e.getMessage());
            }
        }
    }

    void merge(ImsPhoneCall that, State state) {
        for (ImsPhoneConnection c : (ImsPhoneConnection[]) that.mConnections.toArray(new ImsPhoneConnection[that.mConnections.size()])) {
            c.update(null, state);
        }
    }

    ImsCall getImsCall() {
        return getFirstConnection() == null ? null : getFirstConnection().getImsCall();
    }

    static boolean isLocalTone(ImsCall imsCall) {
        if (imsCall == null || imsCall.getCallProfile() == null || imsCall.getCallProfile().mMediaProfile == null || imsCall.getCallProfile().mMediaProfile.mAudioDirection != 0) {
            return false;
        }
        return true;
    }

    boolean update(ImsPhoneConnection conn, ImsCall imsCall, State state) {
        State newState = state;
        if (state == State.ALERTING) {
            if (this.mRingbackTonePlayed && !isLocalTone(imsCall)) {
                this.mOwner.mPhone.stopRingbackTone();
                this.mRingbackTonePlayed = false;
            } else if (!this.mRingbackTonePlayed && isLocalTone(imsCall)) {
                this.mOwner.mPhone.startRingbackTone();
                this.mRingbackTonePlayed = true;
            }
        } else if (this.mRingbackTonePlayed) {
            this.mOwner.mPhone.stopRingbackTone();
            this.mRingbackTonePlayed = false;
        }
        if (newState != this.mState && state != State.DISCONNECTED) {
            this.mState = newState;
            return true;
        } else if (state == State.DISCONNECTED) {
            return true;
        } else {
            return false;
        }
    }

    ImsPhoneConnection getHandoverConnection() {
        ImsPhoneConnection conn = (ImsPhoneConnection) getEarliestConnection();
        if (conn != null) {
            conn.setMultiparty(isMultiparty());
        }
        return conn;
    }

    void switchWith(ImsPhoneCall that) {
        synchronized (ImsPhoneCall.class) {
            ImsPhoneCall tmp = new ImsPhoneCall();
            tmp.takeOver(this);
            takeOver(that);
            that.takeOver(tmp);
        }
    }

    private void takeOver(ImsPhoneCall that) {
        this.mConnections = that.mConnections;
        this.mState = that.mState;
        Iterator i$ = this.mConnections.iterator();
        while (i$.hasNext()) {
            ((ImsPhoneConnection) ((Connection) i$.next())).changeParent(this);
        }
    }
}
