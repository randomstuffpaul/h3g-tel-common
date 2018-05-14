package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.telephony.ServiceState;
import android.util.Log;
import com.android.internal.telephony.Call.State;
import java.util.Iterator;
import java.util.LinkedList;

public class RcsCallTracker extends Handler {
    static final int ACTIVE = 2;
    public static final int CALL_CONNECTED = 2;
    public static final int CALL_DISCONECTED = 1;
    public static final int CALL_HOLD = 3;
    public static final int CALL_RESUMED = 4;
    public static final String CALL_STATE_BROADCAST = "com.samsung.rcs.CALL_STATE_CHANGED";
    static final int DISCONECTED = 4;
    static final int EVENT_CALL_STATE_CHANGED = 1;
    static final int EVENT_SERVICE_STATE_CHANGED = 2;
    public static final String EXTRA_CALL_EVENT = "EXTRA_CALL_EVENT";
    public static final String EXTRA_IS_INCOMING = "EXTRA_IS_INCOMING";
    public static final String EXTRA_NETWORK_TYPE = "EXTRA_NETWORK_TYPE";
    public static final String EXTRA_SERVICE_STATE = "EXTRA_SERVICE_STATE";
    public static final String EXTRA_TEL_NUMBER = "EXTRA_TEL_NUMBER";
    static final int HOLD = 3;
    static final int NEW = 1;
    static final String PERMISSION = "android.permission.READ_PHONE_STATE";
    public static final String SERVICE_STATE_BROADCAST = "com.samsung.rcs.SERVICE_STATE_CHANGED";
    static final String TAG = "RcsCallTracker";
    private static final boolean VDBG = false;
    LinkedList<Connection> availableConnections = new LinkedList();
    LinkedList<Connection> mActiveConnections = new LinkedList();
    Context mContext;
    LinkedList<Connection> mHoldConnections = new LinkedList();
    PhoneBase mPhone;

    public RcsCallTracker(PhoneBase phone) {
        Log.v(TAG, "Created");
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mPhone.registerForPreciseCallStateChanged(this, 1, null);
        this.mPhone.registerForServiceStateChanged(this, 2, null);
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            switch (msg.what) {
                case 1:
                    this.availableConnections.clear();
                    this.availableConnections.addAll(this.mPhone.getForegroundCall().getConnections());
                    this.availableConnections.addAll(this.mPhone.getRingingCall().getConnections());
                    this.availableConnections.addAll(this.mPhone.getBackgroundCall().getConnections());
                    analizeAndSendEvents();
                    return;
                case 2:
                    Log.v(TAG, "Service state changed");
                    ServiceState ss = this.mPhone.getServiceState();
                    Intent i = new Intent(SERVICE_STATE_BROADCAST);
                    i.putExtra(EXTRA_SERVICE_STATE, ss);
                    i.putExtra(EXTRA_NETWORK_TYPE, getNetworkType(ss));
                    this.mContext.sendStickyBroadcast(i);
                    return;
                default:
                    return;
            }
        }
        Log.v(TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
    }

    private void analizeAndSendEvents() {
        Iterator<Connection> i = this.mActiveConnections.iterator();
        while (i.hasNext()) {
            Connection ic = (Connection) i.next();
            if (!this.availableConnections.contains(ic)) {
                i.remove();
                notifyTransition(2, 4, ic);
            }
        }
        i = this.mHoldConnections.iterator();
        while (i.hasNext()) {
            ic = (Connection) i.next();
            if (!this.availableConnections.contains(ic)) {
                i.remove();
                notifyTransition(3, 4, ic);
            }
        }
        Iterator i$ = this.availableConnections.iterator();
        while (i$.hasNext()) {
            int src;
            Connection c = (Connection) i$.next();
            int dest = 0;
            Log.v(TAG, "Connection state  " + c.toString());
            if (this.mActiveConnections.contains(c)) {
                src = 2;
            } else if (this.mHoldConnections.contains(c)) {
                src = 3;
            } else {
                src = 1;
            }
            if (!c.isAlive()) {
                dest = 4;
            } else if (c.getState() == State.HOLDING) {
                dest = 3;
            } else if (c.getState() == State.ACTIVE) {
                dest = 2;
            }
            if (dest != src || (dest == 2 && src == 2)) {
                this.mHoldConnections.remove(c);
                this.mActiveConnections.remove(c);
                switch (dest) {
                    case 2:
                        this.mActiveConnections.add(c);
                        break;
                    case 3:
                        this.mHoldConnections.add(c);
                        break;
                }
                notifyTransition(src, dest, c);
            } else {
                Log.w(TAG, "Dual notification from modem... skipping notification");
            }
        }
    }

    private void notifyTransition(int src, int dest, Connection c) {
        if (src != dest || (dest == 2 && src == 2)) {
            Intent i = new Intent(CALL_STATE_BROADCAST);
            i.putExtra(EXTRA_IS_INCOMING, c.isIncoming());
            i.putExtra(EXTRA_TEL_NUMBER, c.getAddress());
            if (dest == 4) {
                i.putExtra(EXTRA_CALL_EVENT, 1);
                this.mContext.sendBroadcast(i, PERMISSION);
            } else if (dest == 3) {
                i.putExtra(EXTRA_CALL_EVENT, 3);
                this.mContext.sendBroadcast(i, PERMISSION);
            } else if (dest == 2 && src == 1) {
                i.putExtra(EXTRA_CALL_EVENT, 2);
                this.mContext.sendBroadcast(i, PERMISSION);
            } else if (dest == 2 && src == 3) {
                i.putExtra(EXTRA_CALL_EVENT, 4);
                this.mContext.sendBroadcast(i, PERMISSION);
            } else if (dest == 2 && src == 2) {
                i.putExtra(EXTRA_CALL_EVENT, 4);
                Log.v(TAG, "dest == ACTIVE && src == ACTIVE");
                this.mContext.sendBroadcast(i, PERMISSION);
            }
        }
    }

    private void removeDisconnected() {
        Iterator i$ = this.availableConnections.iterator();
        while (i$.hasNext()) {
            Connection c = (Connection) i$.next();
            if (!c.isAlive() && this.mActiveConnections.contains(c)) {
                this.mActiveConnections.remove(c);
                Intent i = new Intent(CALL_STATE_BROADCAST);
                i.putExtra(EXTRA_CALL_EVENT, 1);
                i.putExtra(EXTRA_IS_INCOMING, c.isIncoming());
                i.putExtra(EXTRA_TEL_NUMBER, c.getAddress());
                this.mContext.sendBroadcast(i, PERMISSION);
            }
        }
    }

    void addNewConnections() {
        Iterator i$ = this.availableConnections.iterator();
        while (i$.hasNext()) {
            Connection c = (Connection) i$.next();
            if (c.getState() == State.ACTIVE && !this.mActiveConnections.contains(c)) {
                this.mActiveConnections.add(c);
                Intent i = new Intent(CALL_STATE_BROADCAST);
                i.putExtra(EXTRA_CALL_EVENT, 2);
                i.putExtra(EXTRA_IS_INCOMING, c.isIncoming());
                i.putExtra(EXTRA_TEL_NUMBER, c.getAddress());
                this.mContext.sendBroadcast(i, PERMISSION);
            }
        }
    }

    protected void dispose() {
        this.mPhone.unregisterForPreciseCallStateChanged(this);
        this.mPhone.unregisterForServiceStateChanged(this);
    }

    private int getNetworkType(ServiceState ss) {
        switch (ss.getRadioTechnology()) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
            case 5:
                return 4;
            case 6:
                return 7;
            case 7:
                return 5;
            case 8:
                return 6;
            case 9:
                return 8;
            case 10:
                return 9;
            case 11:
                return 10;
            default:
                return 0;
        }
    }
}
