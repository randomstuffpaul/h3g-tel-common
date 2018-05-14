package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.dataconnection.DctController;
import com.android.internal.telephony.uicc.UiccController;

public class ProxyController {
    static final String LOG_TAG = "ProxyController";
    private static DctController mDctController;
    private static ProxyController sProxyController;
    private CommandsInterface[] mCi;
    private Context mContext;
    private PhoneSubInfoController mPhoneSubInfoController = new PhoneSubInfoController(this.mProxyPhones);
    private Phone[] mProxyPhones;
    private UiccController mUiccController;
    private UiccPhoneBookController mUiccPhoneBookController = new UiccPhoneBookController(this.mProxyPhones);
    private UiccSmsController mUiccSmsController = new UiccSmsController(this.mProxyPhones);

    public static ProxyController getInstance(Context context, Phone[] phoneProxy, UiccController uiccController, CommandsInterface[] ci) {
        if (sProxyController == null) {
            sProxyController = new ProxyController(context, phoneProxy, uiccController, ci);
        }
        return sProxyController;
    }

    public static ProxyController getInstance() {
        return sProxyController;
    }

    private ProxyController(Context context, Phone[] phoneProxy, UiccController uiccController, CommandsInterface[] ci) {
        logd("Constructor - Enter");
        this.mContext = context;
        this.mProxyPhones = phoneProxy;
        this.mUiccController = uiccController;
        this.mCi = ci;
        mDctController = DctController.makeDctController((PhoneProxy[]) phoneProxy);
        logd("Constructor - Exit");
    }

    public void updateDataConnectionTracker(int sub) {
        ((PhoneProxy) this.mProxyPhones[sub]).updateDataConnectionTracker();
    }

    public void enableDataConnectivity(int sub) {
        ((PhoneProxy) this.mProxyPhones[sub]).setInternalDataEnabled(true);
    }

    public void disableDataConnectivity(int sub, Message dataCleanedUpMsg) {
        ((PhoneProxy) this.mProxyPhones[sub]).setInternalDataEnabled(false, dataCleanedUpMsg);
    }

    public void updateCurrentCarrierInProvider(int sub) {
        ((PhoneProxy) this.mProxyPhones[sub]).updateCurrentCarrierInProvider();
    }

    public void registerForAllDataDisconnected(long subId, Handler h, int what, Object obj) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            ((PhoneProxy) this.mProxyPhones[phoneId]).registerForAllDataDisconnected(h, what, obj);
        }
    }

    public void unregisterForAllDataDisconnected(long subId, Handler h) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            ((PhoneProxy) this.mProxyPhones[phoneId]).unregisterForAllDataDisconnected(h);
        }
    }

    public boolean isDataDisconnected(long subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            return true;
        }
        return ((PhoneBase) ((PhoneProxy) this.mProxyPhones[phoneId]).getActivePhone()).mDcTracker.isDisconnected();
    }

    private void logd(String string) {
        Rlog.d(LOG_TAG, string);
    }
}
