package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import com.android.internal.telephony.Call.State;
import java.util.HashMap;

public class CallStateBroadcaster {
    private static final String ACTION_DETAILED_CALL_STATE = "diagandroid.phone.detailedCallState";
    private static final String CALL_STATE_ENDED = "ENDED";
    private static final String EXTRA_CALL_CODE = "CallCode";
    private static final String EXTRA_CALL_NUMBER = "CallNumber";
    private static final String EXTRA_CALL_STATE = "CallState";
    private static final String PERMISSION_RECEIVE_DETAILED_CALL_STATE = "diagandroid.phone.receiveDetailedCallState";
    private static CallStateBroadcaster sInstance;
    private static final HashMap<String, String> sStatusCodes = new C00111();
    private Context mContext;

    static class C00111 extends HashMap<String, String> {
        C00111() {
            put(State.IDLE.toString(), "IDLE");
            put(State.ACTIVE.toString(), "CONNECTED");
            put(State.HOLDING.toString(), "HELD");
            put(State.DIALING.toString(), "ATTEMPTING");
            put(State.ALERTING.toString(), "ESTABLISHED");
            put(State.INCOMING.toString(), "ATTEMPTING");
            put(State.WAITING.toString(), "ATTEMPTING");
            put(State.DISCONNECTED.toString(), "FAILED");
            put(State.DISCONNECTING.toString(), "DISCONNECTING");
        }
    }

    public static class InstanceLock {
        private static int sLockCount = 0;
        private static Object sMutex = new Object();

        public InstanceLock(Context context) {
            synchronized (sMutex) {
                if (sLockCount == 0) {
                    CallStateBroadcaster.sInstance = new CallStateBroadcaster(context);
                }
                sLockCount++;
            }
        }

        protected void finalize() {
            synchronized (sMutex) {
                int i = sLockCount - 1;
                sLockCount = i;
                if (i == 0) {
                    CallStateBroadcaster.sInstance = null;
                }
            }
        }
    }

    public static void SendCallStatus(String number, State status) {
        if (sInstance != null && number != null && status != null) {
            sInstance.SendCallStatus(number, (String) sStatusCodes.get(status.toString()));
        }
    }

    public static void SendCallDisconnected(String number, int cause) {
        if (sInstance != null) {
            sInstance.SendCallDisconnected(number, Integer.toString(cause));
        }
    }

    private static Intent CreateIntent(String callState, String number) {
        Intent intent = new Intent(ACTION_DETAILED_CALL_STATE);
        intent.putExtra(EXTRA_CALL_STATE, callState);
        intent.putExtra(EXTRA_CALL_NUMBER, number);
        return intent;
    }

    private CallStateBroadcaster(Context context) {
        this.mContext = context;
    }

    private void SendCallStatus(String number, String statusString) {
        if (statusString != null) {
            Broadcast(CreateIntent(statusString, number));
        }
    }

    private void SendCallDisconnected(String number, String cause) {
        if (cause != null) {
            Intent intent = CreateIntent(CALL_STATE_ENDED, number);
            intent.putExtra(EXTRA_CALL_CODE, cause);
            Broadcast(intent);
        }
    }

    private void Broadcast(Intent intent) {
        this.mContext.sendBroadcastAsUser(intent, Process.myUserHandle(), PERMISSION_RECEIVE_DETAILED_CALL_STATE);
    }
}
