package com.android.internal.telephony;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.ITelephonyRegistry.Stub;
import com.android.internal.telephony.Phone.DataActivityState;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.sec.android.app.CscFeature;
import java.util.List;

public class DefaultPhoneNotifier implements PhoneNotifier {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "DefaultPhoneNotifier";
    protected ITelephonyRegistry mRegistry = Stub.asInterface(ServiceManager.getService("telephony.registry"));

    static /* synthetic */ class C00121 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Call$State = new int[State.values().length];
        static final /* synthetic */ int[] f2x7097fcc7 = new int[DataActivityState.values().length];
        static final /* synthetic */ int[] f3x67a69abf = new int[DataState.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$State = new int[PhoneConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.HOLDING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.ALERTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.INCOMING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.WAITING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.DISCONNECTED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[State.DISCONNECTING.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                f2x7097fcc7[DataActivityState.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e9) {
            }
            try {
                f2x7097fcc7[DataActivityState.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e10) {
            }
            try {
                f2x7097fcc7[DataActivityState.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e11) {
            }
            try {
                f2x7097fcc7[DataActivityState.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e12) {
            }
            try {
                f3x67a69abf[DataState.CONNECTING.ordinal()] = 1;
            } catch (NoSuchFieldError e13) {
            }
            try {
                f3x67a69abf[DataState.CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e14) {
            }
            try {
                f3x67a69abf[DataState.SUSPENDED.ordinal()] = 3;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.RINGING.ordinal()] = 1;
            } catch (NoSuchFieldError e16) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.OFFHOOK.ordinal()] = 2;
            } catch (NoSuchFieldError e17) {
            }
        }
    }

    public interface IDataStateChangedCallback {
        void onDataStateChanged(long j, String str, String str2, String str3, String str4, boolean z);
    }

    protected DefaultPhoneNotifier() {
    }

    public void notifyPhoneState(Phone sender) {
        Call ringingCall = sender.getRingingCall();
        long subId = sender.getSubId();
        String incomingNumber = "";
        if (!(ringingCall == null || ringingCall.getEarliestConnection() == null)) {
            incomingNumber = ringingCall.getEarliestConnection().getAddress();
        }
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCallStateForSubscriber(subId, convertCallState(sender.getState()), incomingNumber);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyServiceState(Phone sender) {
        ServiceState ss = sender.getServiceState();
        int phoneId = sender.getPhoneId();
        long subId = sender.getSubId();
        Rlog.d(LOG_TAG, "nofityServiceState: mRegistry=" + this.mRegistry + " ss=" + ss + " sender=" + sender + " phondId=" + phoneId + " subId=" + subId);
        if (ss == null) {
            ss = new ServiceState();
            ss.setStateOutOfService();
        }
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyServiceStateForPhoneId(phoneId, subId, ss);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifySignalStrength(Phone sender) {
        long subId = sender.getSubId();
        Rlog.d(LOG_TAG, "notifySignalStrength: mRegistry=" + this.mRegistry + " ss=" + sender.getSignalStrength() + " sender=" + sender);
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifySignalStrengthForSubscriber(subId, sender.getSignalStrength());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyMessageWaitingChanged(Phone sender) {
        int phoneId = sender.getPhoneId();
        long subId = sender.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyMessageWaitingChangedForPhoneId(phoneId, subId, sender.getMessageWaitingIndicator());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyCallForwardingChanged(Phone sender) {
        long subId = sender.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCallForwardingChangedForSubscriber(subId, sender.getCallForwardingIndicator());
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataActivity(Phone sender) {
        long subId = sender.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyDataActivityForSubscriber(subId, convertDataActivityState(sender.getDataActivityState()));
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataConnection(Phone sender, String reason, String apnType, DataState state) {
        doNotifyDataConnection(sender, reason, apnType, state);
    }

    private void doNotifyDataConnection(Phone sender, String reason, String apnType, DataState state) {
        long subId = sender.getSubId();
        long dds = SubscriptionController.getInstance().getDefaultDataSubId();
        TelephonyManager telephony = TelephonyManager.getDefault();
        LinkProperties linkProperties = null;
        NetworkCapabilities networkCapabilities = null;
        boolean roaming = false;
        if (state == DataState.CONNECTED) {
            linkProperties = sender.getLinkProperties(apnType);
            networkCapabilities = sender.getNetworkCapabilities(apnType);
        }
        ServiceState ss = sender.getServiceState();
        if (ss != null) {
            roaming = ss.getRoaming();
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableVoicePriority")) {
            sender.notifyDataConnectionStateChanged(convertDataState(state), reason, apnType);
        }
        try {
            if (this.mRegistry != null) {
                int networkType;
                ITelephonyRegistry iTelephonyRegistry = this.mRegistry;
                int convertDataState = convertDataState(state);
                boolean isDataConnectivityPossible = sender.isDataConnectivityPossible(apnType);
                String activeApnHost = sender.getActiveApnHost(apnType);
                if (telephony != null) {
                    networkType = telephony.getNetworkType(subId);
                } else {
                    networkType = 0;
                }
                iTelephonyRegistry.notifyDataConnectionForSubscriber(subId, convertDataState, isDataConnectivityPossible, reason, activeApnHost, apnType, linkProperties, networkCapabilities, networkType, roaming);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataConnectionFailed(Phone sender, String reason, String apnType) {
        long subId = sender.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyDataConnectionFailedForSubscriber(subId, reason, apnType);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyCellLocation(Phone sender) {
        long subId = sender.getSubId();
        Bundle data = new Bundle();
        sender.getCellLocation().fillInNotifierBundle(data);
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCellLocationForSubscriber(subId, data);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyCellInfo(Phone sender, List<CellInfo> cellInfo) {
        long subId = sender.getSubId();
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyCellInfoForSubscriber(subId, cellInfo);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyDataConnectionRealTimeInfo(Phone sender, DataConnectionRealTimeInfo dcRtInfo) {
        try {
            this.mRegistry.notifyDataConnectionRealTimeInfo(dcRtInfo);
        } catch (RemoteException e) {
        }
    }

    public void notifyOtaspChanged(Phone sender, int otaspMode) {
        try {
            if (this.mRegistry != null) {
                this.mRegistry.notifyOtaspChanged(otaspMode);
            }
        } catch (RemoteException e) {
        }
    }

    public void notifyPreciseCallState(Phone sender) {
        Call ringingCall = sender.getRingingCall();
        Call foregroundCall = sender.getForegroundCall();
        Call backgroundCall = sender.getBackgroundCall();
        if (ringingCall != null && foregroundCall != null && backgroundCall != null) {
            try {
                this.mRegistry.notifyPreciseCallState(convertPreciseCallState(ringingCall.getState()), convertPreciseCallState(foregroundCall.getState()), convertPreciseCallState(backgroundCall.getState()));
            } catch (RemoteException e) {
            }
        }
    }

    public void notifyDisconnectCause(int cause, int preciseCause) {
        try {
            this.mRegistry.notifyDisconnectCause(cause, preciseCause);
        } catch (RemoteException e) {
        }
    }

    public void notifyPreciseDataConnectionFailed(Phone sender, String reason, String apnType, String apn, String failCause) {
        try {
            this.mRegistry.notifyPreciseDataConnectionFailed(reason, apnType, apn, failCause);
        } catch (RemoteException e) {
        }
    }

    public void notifyVoLteServiceStateChanged(Phone sender, VoLteServiceState lteState) {
        try {
            this.mRegistry.notifyVoLteServiceStateChanged(lteState);
        } catch (RemoteException e) {
        }
    }

    public void notifyOemHookRawEventForSubscriber(long subId, byte[] rawData) {
        try {
            this.mRegistry.notifyOemHookRawEventForSubscriber(subId, rawData);
        } catch (RemoteException e) {
        }
    }

    public void notifyFdnUpdated(Phone sender) {
        try {
            this.mRegistry.notifyFdnUpdated();
        } catch (RemoteException e) {
        }
    }

    public static int convertCallState(PhoneConstants.State state) {
        switch (C00121.$SwitchMap$com$android$internal$telephony$PhoneConstants$State[state.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            default:
                return 0;
        }
    }

    public static PhoneConstants.State convertCallState(int state) {
        switch (state) {
            case 1:
                return PhoneConstants.State.RINGING;
            case 2:
                return PhoneConstants.State.OFFHOOK;
            case 4:
                return PhoneConstants.State.RINGING;
            case 5:
                return PhoneConstants.State.OFFHOOK;
            default:
                return PhoneConstants.State.IDLE;
        }
    }

    public static int convertDataState(DataState state) {
        switch (C00121.f3x67a69abf[state.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                return 0;
        }
    }

    public static DataState convertDataState(int state) {
        switch (state) {
            case 1:
                return DataState.CONNECTING;
            case 2:
                return DataState.CONNECTED;
            case 3:
                return DataState.SUSPENDED;
            default:
                return DataState.DISCONNECTED;
        }
    }

    public static int convertDataActivityState(DataActivityState state) {
        switch (C00121.f2x7097fcc7[state.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            default:
                return 0;
        }
    }

    public static DataActivityState convertDataActivityState(int state) {
        switch (state) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAOUT;
            case 3:
                return DataActivityState.DATAINANDOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    public static int convertPreciseCallState(State state) {
        switch (C00121.$SwitchMap$com$android$internal$telephony$Call$State[state.ordinal()]) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            case 8:
                return 8;
            default:
                return 0;
        }
    }

    public static State convertPreciseCallState(int state) {
        switch (state) {
            case 1:
                return State.ACTIVE;
            case 2:
                return State.HOLDING;
            case 3:
                return State.DIALING;
            case 4:
                return State.ALERTING;
            case 5:
                return State.INCOMING;
            case 6:
                return State.WAITING;
            case 7:
                return State.DISCONNECTED;
            case 8:
                return State.DISCONNECTING;
            default:
                return State.IDLE;
        }
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    public void notifyDualSimSlotActivationState(Phone sender) {
        try {
            this.mRegistry.notifyDualSimSlotActivationState(sender.getDualSimSlotActivationState());
        } catch (RemoteException e) {
        }
    }
}
