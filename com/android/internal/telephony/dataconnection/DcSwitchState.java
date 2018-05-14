package com.android.internal.telephony.dataconnection;

import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.HashSet;

public class DcSwitchState extends StateMachine {
    private static final int BASE = 274432;
    private static final boolean DBG = true;
    private static final int EVENT_CLEANUP_ALL = 274434;
    private static final int EVENT_CONNECT = 274432;
    private static final int EVENT_CONNECTED = 274435;
    private static final int EVENT_DETACH_DONE = 274436;
    private static final int EVENT_DISCONNECT = 274433;
    private static final int EVENT_TO_ACTING_DIRECTLY = 274438;
    private static final int EVENT_TO_IDLE_DIRECTLY = 274437;
    private static final String LOG_TAG = "DcSwitchState";
    private static final boolean VDBG = false;
    private AsyncChannel mAc;
    private ActedState mActedState = new ActedState();
    private ActingState mActingState = new ActingState();
    private HashSet<String> mApnTypes = new HashSet();
    private DeactingState mDeactingState = new DeactingState();
    private DefaultState mDefaultState = new DefaultState();
    private int mId;
    private RegistrantList mIdleRegistrants = new RegistrantList();
    private IdleState mIdleState = new IdleState();
    private Phone mPhone;

    private class ActedState extends State {
        private ActedState() {
        }

        public boolean processMessage(Message msg) {
            String type;
            switch (msg.what) {
                case 274432:
                case 278528:
                    type = msg.obj;
                    DcSwitchState.this.log("ActedState: REQ_CONNECT/EVENT_CONNECT(" + msg.what + ") type=" + type);
                    int result = DcSwitchState.this.setupConnection(type);
                    if (msg.what == 278528) {
                        DcSwitchState.this.mAc.replyToMessage(msg, 278529, result);
                    }
                    return true;
                case DcSwitchState.EVENT_CLEANUP_ALL /*274434*/:
                    DcSwitchState.this.log("ActedState: EVENT_CLEANUP_ALL");
                    DcSwitchState.this.requestDataIdle();
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mDeactingState);
                    return true;
                case DcSwitchState.EVENT_CONNECTED /*274435*/:
                    DcSwitchState.this.log("ActedState: EVENT_CONNECTED");
                    return true;
                case 278530:
                    type = (String) msg.obj;
                    DcSwitchState.this.log("ActedState: DcSwitchAsyncChannel.REQ_DISCONNECT type=" + type);
                    DcSwitchState.this.mAc.replyToMessage(msg, 278531, DcSwitchState.this.teardownConnection(type));
                    return true;
                default:
                    return false;
            }
        }
    }

    private class ActingState extends State {
        private ActingState() {
        }

        public boolean processMessage(Message msg) {
            String type;
            switch (msg.what) {
                case 274432:
                case 278528:
                    type = msg.obj;
                    DcSwitchState.this.log("ActingState: REQ_CONNECT/EVENT_CONNECT(" + msg.what + ") type=" + type);
                    int result = DcSwitchState.this.setupConnection(type);
                    if (msg.what == 278528) {
                        DcSwitchState.this.mAc.replyToMessage(msg, 278529, result);
                    }
                    return true;
                case DcSwitchState.EVENT_CLEANUP_ALL /*274434*/:
                    DcSwitchState.this.log("ActingState: EVENT_CLEANUP_ALL");
                    DcSwitchState.this.requestDataIdle();
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mDeactingState);
                    return true;
                case DcSwitchState.EVENT_CONNECTED /*274435*/:
                    DcSwitchState.this.log("ActingState: EVENT_CONNECTED");
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mActedState);
                    return true;
                case 278530:
                    type = (String) msg.obj;
                    DcSwitchState.this.log("ActingState: DcSwitchAsyncChannel.REQ_DISCONNECT type=" + type);
                    DcSwitchState.this.mAc.replyToMessage(msg, 278531, DcSwitchState.this.teardownConnection(type));
                    return true;
                default:
                    return false;
            }
        }
    }

    private class DeactingState extends State {
        private DeactingState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 274432:
                case 278528:
                    String type = msg.obj;
                    DcSwitchState.this.log("DeactingState: REQ_CONNECT/EVENT_CONNECT(" + msg.what + ") type=" + type + ", request is defered.");
                    DcSwitchState.this.deferMessage(DcSwitchState.this.obtainMessage(274432, type));
                    if (msg.what == 278528) {
                        DcSwitchState.this.mAc.replyToMessage(msg, 278529, 1);
                    }
                    return true;
                case DcSwitchState.EVENT_CLEANUP_ALL /*274434*/:
                    DcSwitchState.this.log("DeactingState: EVENT_CLEANUP_ALL, already deacting.");
                    return true;
                case DcSwitchState.EVENT_CONNECTED /*274435*/:
                    DcSwitchState.this.log("DeactingState: Receive invalid event EVENT_CONNECTED!");
                    return true;
                case DcSwitchState.EVENT_DETACH_DONE /*274436*/:
                    DcSwitchState.this.log("DeactingState: EVENT_DETACH_DONE");
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mIdleState);
                    return true;
                case 278530:
                    DcSwitchState.this.log("DeactingState: DcSwitchAsyncChannel.REQ_DISCONNECT type=" + ((String) msg.obj));
                    DcSwitchState.this.mAc.replyToMessage(msg, 278531, 4);
                    return true;
                default:
                    return false;
            }
        }
    }

    private class DefaultState extends State {
        private DefaultState() {
        }

        public boolean processMessage(Message msg) {
            int i = 0;
            boolean val;
            AsyncChannel access$800;
            switch (msg.what) {
                case 69633:
                    if (DcSwitchState.this.mAc == null) {
                        DcSwitchState.this.mAc = new AsyncChannel();
                        DcSwitchState.this.mAc.connected(null, DcSwitchState.this.getHandler(), msg.replyTo);
                        DcSwitchState.this.mAc.replyToMessage(msg, 69634, 0, DcSwitchState.this.mId, "hi");
                        break;
                    }
                    DcSwitchState.this.mAc.replyToMessage(msg, 69634, 3);
                    break;
                case 69635:
                    DcSwitchState.this.mAc.disconnect();
                    break;
                case 69636:
                    DcSwitchState.this.mAc = null;
                    break;
                case DcSwitchState.EVENT_TO_IDLE_DIRECTLY /*274437*/:
                    DcSwitchState.this.log("Just transit to Idle state");
                    do {
                    } while (DcSwitchState.this.mApnTypes.iterator().hasNext());
                    DcSwitchState.this.mApnTypes.clear();
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mIdleState);
                    break;
                case DcSwitchState.EVENT_TO_ACTING_DIRECTLY /*274438*/:
                    DcSwitchState.this.log("Just transit to Acting state");
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mActingState);
                    break;
                case 278532:
                    if (DcSwitchState.this.getCurrentState() == DcSwitchState.this.mIdleState) {
                        val = true;
                    } else {
                        val = false;
                    }
                    access$800 = DcSwitchState.this.mAc;
                    if (val) {
                        i = 1;
                    }
                    access$800.replyToMessage(msg, 278533, i);
                    break;
                case 278534:
                    if (DcSwitchState.this.getCurrentState() == DcSwitchState.this.mIdleState || DcSwitchState.this.getCurrentState() == DcSwitchState.this.mDeactingState) {
                        val = true;
                    } else {
                        val = false;
                    }
                    access$800 = DcSwitchState.this.mAc;
                    if (val) {
                        i = 1;
                    }
                    access$800.replyToMessage(msg, 278535, i);
                    break;
            }
            DcSwitchState.this.log("DefaultState: shouldn't happen but ignore msg.what=0x" + Integer.toHexString(msg.what));
            return true;
        }
    }

    private class IdleState extends State {
        private IdleState() {
        }

        public void enter() {
            DcSwitchState.this.mIdleRegistrants.notifyRegistrants();
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 274432:
                case 278528:
                    String type = msg.obj;
                    DcSwitchState.this.log("IdleState: REQ_CONNECT/EVENT_CONNECT(" + msg.what + ") type=" + type);
                    ((PhoneBase) ((PhoneProxy) DcSwitchState.this.mPhone).getActivePhone()).mCi.setDataAllowed(true, null);
                    int result = DcSwitchState.this.setupConnection(type);
                    if (msg.what == 278528) {
                        DcSwitchState.this.mAc.replyToMessage(msg, 278529, result);
                    }
                    DcSwitchState.this.transitionTo(DcSwitchState.this.mActingState);
                    return true;
                case DcSwitchState.EVENT_CLEANUP_ALL /*274434*/:
                    DcSwitchState.this.log("IdleState: EVENT_CLEANUP_ALL");
                    DcSwitchState.this.requestDataIdle();
                    return true;
                case DcSwitchState.EVENT_CONNECTED /*274435*/:
                    DcSwitchState.this.log("IdleState: Receive invalid event EVENT_CONNECTED!");
                    return true;
                case 278530:
                    DcSwitchState.this.log("IdleState: DcSwitchAsyncChannel.REQ_DISCONNECT type=" + ((String) msg.obj));
                    DcSwitchState.this.mAc.replyToMessage(msg, 278531, 4);
                    return true;
                default:
                    return false;
            }
        }
    }

    protected DcSwitchState(Phone phone, String name, int id) {
        super(name);
        log("DcSwitchState constructor E");
        this.mPhone = phone;
        this.mId = id;
        addState(this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mActingState, this.mDefaultState);
        addState(this.mActedState, this.mDefaultState);
        addState(this.mDeactingState, this.mDefaultState);
        setInitialState(this.mIdleState);
        log("DcSwitchState constructor X");
    }

    private int setupConnection(String type) {
        this.mApnTypes.add(type);
        log("DcSwitchState:setupConnection type = " + type);
        return 1;
    }

    private int teardownConnection(String type) {
        this.mApnTypes.remove(type);
        if (this.mApnTypes.isEmpty()) {
            log("No APN is using, then clean up all");
            requestDataIdle();
            transitionTo(this.mDeactingState);
        }
        return 1;
    }

    private void requestDataIdle() {
        log("requestDataIdle is triggered");
        do {
        } while (this.mApnTypes.iterator().hasNext());
        this.mApnTypes.clear();
        ((PhoneBase) ((PhoneProxy) this.mPhone).getActivePhone()).mCi.setDataAllowed(false, obtainMessage(EVENT_DETACH_DONE));
    }

    public void notifyDataConnection(int phoneId, String state, String reason, String apnName, String apnType, boolean unavailable) {
        if (phoneId == this.mId && TextUtils.equals(state, DataState.CONNECTED.toString())) {
            sendMessage(obtainMessage(EVENT_CONNECTED));
        }
    }

    public void cleanupAllConnection() {
        sendMessage(obtainMessage(EVENT_CLEANUP_ALL));
    }

    public void registerForIdle(Handler h, int what, Object obj) {
        this.mIdleRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForIdle(Handler h) {
        this.mIdleRegistrants.remove(h);
    }

    public void transitToIdleState() {
        sendMessage(obtainMessage(EVENT_TO_IDLE_DIRECTLY));
    }

    public void transitToActingState() {
        sendMessage(obtainMessage(EVENT_TO_ACTING_DIRECTLY));
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[" + getName() + "] " + s);
    }
}
