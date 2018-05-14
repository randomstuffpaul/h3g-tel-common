package com.android.internal.telephony.cat;

import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import java.util.HashMap;

public class NetworkConnectivityListener {
    private static final boolean DBG = true;
    private static final String TAG = "NetworkConnectivityListener";
    private HashMap<Handler, Integer> mHandlers = new HashMap();
    private boolean mIsFailover;
    private boolean mListening;
    private NetworkInfo mNetworkInfo;
    private NetworkInfo mOtherNetworkInfo;
    private String mReason;
    private State mState = State.UNKNOWN;

    public enum State {
        UNKNOWN,
        CONNECTED,
        NOT_CONNECTED
    }

    public void notifyHandler() {
        for (Handler target : this.mHandlers.keySet()) {
            target.sendMessage(Message.obtain(target, ((Integer) this.mHandlers.get(target)).intValue()));
        }
    }

    public synchronized void startListening() {
        if (!this.mListening) {
            this.mListening = true;
        }
    }

    public synchronized void stopListening() {
        if (this.mListening) {
            this.mNetworkInfo = null;
            this.mOtherNetworkInfo = null;
            this.mIsFailover = false;
            this.mReason = null;
            this.mListening = false;
        }
    }

    public void registerHandler(Handler target, int what) {
        this.mHandlers.put(target, Integer.valueOf(what));
    }

    public void unregisterHandler(Handler target) {
        this.mHandlers.remove(target);
    }

    public State getState() {
        return this.mState;
    }

    public void setState(State state) {
        this.mState = state;
    }

    public boolean isListening() {
        return this.mListening;
    }

    public NetworkInfo getNetworkInfo() {
        return this.mNetworkInfo;
    }

    public void setNetworkInfo(NetworkInfo networkInfo) {
        this.mNetworkInfo = networkInfo;
    }

    public NetworkInfo getOtherNetworkInfo() {
        return this.mOtherNetworkInfo;
    }

    public void setOtherNetworkInfo(NetworkInfo networkInfo) {
        this.mOtherNetworkInfo = networkInfo;
    }

    public boolean isFailover() {
        return this.mIsFailover;
    }

    public void setFailover(boolean failover) {
        this.mIsFailover = failover;
    }

    public String getReason() {
        return this.mReason;
    }

    public void setReason(String reason) {
        this.mReason = reason;
    }
}
