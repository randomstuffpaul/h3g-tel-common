package com.android.internal.telephony.uicc;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Telephony.TextBasedSmsColumns;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.view.WindowManager.LayoutParams;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.samsung.android.telephony.MultiSimManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

public class UiccCard {
    protected static final boolean DBG = true;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 20;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_SIM_IO_DONE = 19;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    protected static final String LOG_TAG = "UiccCard";
    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";
    private boolean NoSimNotyFlag;
    private RegistrantList mAbsentRegistrants;
    private CardState mCardState;
    private RegistrantList mCarrierPrivilegeRegistrants;
    private UiccCarrierPrivilegeRules mCarrierPrivilegeRules;
    private CatService mCatService;
    private int mCdmaSubscriptionAppIndex;
    private CommandsInterface mCi;
    private Context mContext;
    private boolean mDestroyed;
    private int mGsmUmtsSubscriptionAppIndex;
    protected Handler mHandler;
    private int mImsSubscriptionAppIndex;
    private RadioState mLastRadioState;
    private final Object mLock;
    public PhoneBase mPhone;
    private final BroadcastReceiver mReceiver;
    int mSlotId;
    private UiccCardApplication[] mUiccApplications;
    private PinState mUniversalPinState;
    private boolean updateFlagInserted;
    private boolean updateFlagRemoved;

    class C01331 implements OnClickListener {
        C01331() {
        }

        public void onClick(DialogInterface dialog, int which) {
            synchronized (UiccCard.this.mLock) {
                if (which == -1) {
                    UiccCard.this.log("Reboot due to SIM swap");
                    Intent startIntent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                    startIntent.setAction("android.intent.action.REBOOT");
                    startIntent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                    startIntent.setFlags(268435456);
                    UiccCard.this.mContext.startActivityAsUser(startIntent, UserHandle.CURRENT);
                } else if (which == -2) {
                    UiccCard.this.log("Do not reboot device");
                }
            }
        }
    }

    class C01342 extends Handler {
        C01342() {
        }

        public void handleMessage(Message msg) {
            if (UiccCard.this.mDestroyed) {
                UiccCard.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }
            switch (msg.what) {
                case 13:
                    UiccCard.this.onIccSwap(false);
                    return;
                case 14:
                    UiccCard.this.onIccSwap(true);
                    return;
                case 15:
                case 16:
                case 17:
                case 18:
                case 19:
                    AsyncResult ar = msg.obj;
                    if (ar.exception != null) {
                        UiccCard.this.log("Error in SIM access with exception" + ar.exception);
                    }
                    AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
                    ((Message) ar.userObj).sendToTarget();
                    return;
                case 20:
                    UiccCard.this.onCarrierPriviligesLoadedMessage();
                    return;
                default:
                    UiccCard.this.loge("Unknown Event " + msg.what);
                    return;
            }
        }
    }

    private class UiccCardBroadcastReceiver extends BroadcastReceiver {
        private UiccCardBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if ("com.samsung.intent.action.ICC_CARD_STATE_CHANGED".equals(intent.getAction())) {
                String iccStatus = intent.getStringExtra(TextBasedSmsColumns.STATUS);
                Rlog.d(UiccCard.LOG_TAG, "Receive com.samsung.intent.action.ICC_CARD_STATE_CHANGED");
                if ("INSERTED".equals(iccStatus) && UiccCard.this.updateFlagInserted) {
                    UiccCard.this.updateFlagInserted = false;
                    UiccCard.this.updateFlagRemoved = true;
                    Rlog.d(UiccCard.LOG_TAG, "Receive ICC_CARD_STATE_CHANGED INSERTED");
                    UiccCard.this.onIccSwap(true);
                } else if ("REMOVED".equals(iccStatus) && UiccCard.this.updateFlagRemoved) {
                    UiccCard.this.updateFlagRemoved = false;
                    UiccCard.this.updateFlagInserted = true;
                    Rlog.d(UiccCard.LOG_TAG, "Receive ICC_CARD_STATE_CHANGED REMOVED");
                    UiccCard.this.onIccSwap(false);
                }
            }
        }
    }

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, PhoneBase phone) {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.NoSimNotyFlag = false;
        this.mReceiver = new UiccCardBroadcastReceiver();
        this.updateFlagRemoved = true;
        this.updateFlagInserted = true;
        this.mHandler = new C01342();
        log("Creating");
        this.mCardState = ics.mCardState;
        this.mPhone = phone;
        update(c, ci, ics, phone);
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("com.samsung.intent.action.ICC_CARD_STATE_CHANGED"));
    }

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, PhoneBase phone, int slotId) {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.NoSimNotyFlag = false;
        this.mReceiver = new UiccCardBroadcastReceiver();
        this.updateFlagRemoved = true;
        this.updateFlagInserted = true;
        this.mHandler = new C01342();
        this.mCardState = ics.mCardState;
        this.mSlotId = slotId;
        this.mPhone = phone;
        update(c, ci, ics, phone);
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("com.samsung.intent.action.ICC_CARD_STATE_CHANGED"));
    }

    protected UiccCard() {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mCarrierPrivilegeRegistrants = new RegistrantList();
        this.NoSimNotyFlag = false;
        this.mReceiver = new UiccCardBroadcastReceiver();
        this.updateFlagRemoved = true;
        this.updateFlagInserted = true;
        this.mHandler = new C01342();
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing card");
            if (this.mCatService != null) {
                this.mCatService.dispose();
            }
            for (UiccCardApplication app : this.mUiccApplications) {
                if (app != null) {
                    app.dispose();
                }
            }
            this.mCatService = null;
            this.mUiccApplications = null;
            this.mCarrierPrivilegeRules = null;
            this.mContext.unregisterReceiver(this.mReceiver);
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics, PhoneBase phone) {
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            CardState oldState = this.mCardState;
            this.mCardState = ics.mCardState;
            this.mUniversalPinState = ics.mUniversalPinState;
            this.mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            this.mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            this.mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            this.mContext = c;
            this.mCi = ci;
            this.mPhone = phone;
            log(ics.mApplications.length + " applications");
            for (int i = 0; i < this.mUiccApplications.length; i++) {
                if (this.mUiccApplications[i] == null) {
                    if (i < ics.mApplications.length) {
                        this.mUiccApplications[i] = new UiccCardApplication(this, ics.mApplications[i], this.mContext, this.mCi);
                    }
                } else if (i >= ics.mApplications.length) {
                    this.mUiccApplications[i].dispose();
                    this.mUiccApplications[i] = null;
                } else {
                    this.mUiccApplications[i].update(ics.mApplications[i], this.mContext, this.mCi);
                }
            }
            createAndUpdateCatService(this.mPhone);
            log("Before privilege rules: " + this.mCarrierPrivilegeRules + " : " + this.mCardState);
            if (this.mCarrierPrivilegeRules == null && this.mCardState == CardState.CARDSTATE_PRESENT) {
                this.mCarrierPrivilegeRules = new UiccCarrierPrivilegeRules(this, this.mHandler.obtainMessage(20));
            } else if (!(this.mCarrierPrivilegeRules == null || this.mCardState == CardState.CARDSTATE_PRESENT)) {
                this.mCarrierPrivilegeRules = null;
            }
            sanitizeApplicationIndexes();
            RadioState radioState = this.mCi.getRadioState();
            log("update: radioState=" + radioState + " mLastRadioState=" + this.mLastRadioState);
            if (radioState == RadioState.RADIO_ON && this.mLastRadioState == RadioState.RADIO_ON && !"DCGGS".equals("DGG") && !"DCGS".equals("DGG")) {
                if (oldState != CardState.CARDSTATE_ABSENT && this.mCardState == CardState.CARDSTATE_ABSENT) {
                    log("update: notify card removed");
                    this.mAbsentRegistrants.notifyRegistrants();
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(13, null));
                } else if (oldState == CardState.CARDSTATE_ABSENT && this.mCardState != CardState.CARDSTATE_ABSENT) {
                    log("update: notify card added");
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(14, null));
                }
            }
            this.mLastRadioState = radioState;
        }
    }

    protected void createAndUpdateCatService(PhoneBase phone) {
        if (this.mUiccApplications.length <= 0 || this.mUiccApplications[0] == null) {
            if (this.mCatService != null) {
                this.mCatService.dispose();
            }
            this.mCatService = null;
        } else if (this.mCatService == null) {
            this.mCatService = CatService.getInstance(this.mCi, this.mContext, this, phone, this.mSlotId);
        } else {
            this.mCatService.update(this.mCi, this.mContext, this);
        }
    }

    public CatService getCatService() {
        return this.mCatService;
    }

    protected void finalize() {
        log("UiccCard finalized");
    }

    private void sanitizeApplicationIndexes() {
        this.mGsmUmtsSubscriptionAppIndex = checkIndex(this.mGsmUmtsSubscriptionAppIndex, AppType.APPTYPE_SIM, AppType.APPTYPE_USIM);
        this.mCdmaSubscriptionAppIndex = checkIndex(this.mCdmaSubscriptionAppIndex, AppType.APPTYPE_RUIM, AppType.APPTYPE_CSIM);
        this.mImsSubscriptionAppIndex = checkIndex(this.mImsSubscriptionAppIndex, AppType.APPTYPE_ISIM, null);
    }

    private int checkIndex(int index, AppType expectedAppType, AppType altExpectedAppType) {
        if (this.mUiccApplications == null || index >= this.mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        } else if (index < 0) {
            return -1;
        } else {
            if (this.mUiccApplications[index].getType() == expectedAppType || this.mUiccApplications[index].getType() == altExpectedAppType) {
                return index;
            }
            loge("App index " + index + " is invalid since it's not " + expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }
    }

    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (this.mCardState == CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mCarrierPrivilegeRules == null || this.mCarrierPrivilegeRules.areCarrierPriviligeRulesLoaded();
    }

    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mCarrierPrivilegeRegistrants.add(r);
            if (areCarrierPriviligeRulesLoaded()) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.remove(h);
        }
    }

    private void onIccSwap(boolean isAdded) {
        Throwable th;
        boolean isHotSwapSupported = this.mContext.getResources().getBoolean(17956918);
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");
        synchronized (this.mLock) {
            try {
                OnClickListener listener = new C01331();
                try {
                    AlertDialog dialog;
                    Resources r = Resources.getSystem();
                    int theme = 4;
                    if (SystemProperties.get("ro.build.scafe.cream").equals("white")) {
                        theme = 5;
                    }
                    if (!"NOT_RESTART".equals("") && !"SPR-CDMA".equals("")) {
                        String message;
                        String title = isAdded ? r.getString(17040603) : r.getString(17040600);
                        if (isAdded) {
                            message = r.getString(17040604);
                        } else {
                            message = r.getString(17040601);
                        }
                        dialog = new Builder(this.mContext, theme).setTitle(title).setMessage(message).setPositiveButton(r.getString(17040605), listener).setCancelable(false).create();
                    } else if (isAdded) {
                        dialog = new Builder(this.mContext, theme).setTitle(r.getString(17040603)).setMessage(r.getString(17040604)).setPositiveButton(r.getString(17040605), listener).setNegativeButton(r.getString(17039360), listener).setCancelable(false).create();
                    } else {
                        dialog = new Builder(this.mContext, theme).setTitle(r.getString(17040267)).setMessage(r.getString(17041164)).setNegativeButton(r.getString(17039370), listener).setCancelable(false).create();
                    }
                    KeyguardManager kgm = (KeyguardManager) this.mContext.getSystemService("keyguard");
                    if (kgm == null || !kgm.isKeyguardLocked()) {
                        dialog.getWindow().setType(2008);
                    } else {
                        dialog.getWindow().setType(2009);
                    }
                    LayoutParams attrs = dialog.getWindow().getAttributes();
                    attrs.privateFlags = 16;
                    dialog.getWindow().setAttributes(attrs);
                    dialog.show();
                } catch (Throwable th2) {
                    th = th2;
                    OnClickListener onClickListener = listener;
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    private void onCarrierPriviligesLoadedMessage() {
        synchronized (this.mLock) {
            this.mCarrierPrivilegeRegistrants.notifyRegistrants();
        }
    }

    public boolean isApplicationOnIcc(AppType type) {
        boolean z;
        synchronized (this.mLock) {
            int i = 0;
            while (i < this.mUiccApplications.length) {
                if (this.mUiccApplications[i] != null && this.mUiccApplications[i].getType() == type) {
                    z = true;
                    break;
                }
                i++;
            }
            z = false;
        }
        return z;
    }

    public CardState getCardState() {
        CardState cardState;
        synchronized (this.mLock) {
            cardState = this.mCardState;
        }
        return cardState;
    }

    public PinState getUniversalPinState() {
        PinState pinState;
        synchronized (this.mLock) {
            pinState = this.mUniversalPinState;
        }
        return pinState;
    }

    public UiccCardApplication getApplication(int family) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            int index = 8;
            switch (family) {
                case 1:
                    index = this.mGsmUmtsSubscriptionAppIndex;
                    break;
                case 2:
                    index = this.mCdmaSubscriptionAppIndex;
                    break;
                case 3:
                    index = this.mImsSubscriptionAppIndex;
                    break;
            }
            if (index >= 0) {
                if (index < this.mUiccApplications.length) {
                    uiccCardApplication = this.mUiccApplications[index];
                }
            }
            uiccCardApplication = null;
        }
        return uiccCardApplication;
    }

    public UiccCardApplication getApplicationIndex(int index) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            if (index >= 0) {
                if (index < this.mUiccApplications.length) {
                    uiccCardApplication = this.mUiccApplications[index];
                }
            }
            uiccCardApplication = null;
        }
        return uiccCardApplication;
    }

    public UiccCardApplication getApplicationByType(int type) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            int i = 0;
            while (i < this.mUiccApplications.length) {
                if (this.mUiccApplications[i] != null && this.mUiccApplications[i].getType().ordinal() == type) {
                    uiccCardApplication = this.mUiccApplications[i];
                    break;
                }
                i++;
            }
            uiccCardApplication = null;
        }
        return uiccCardApplication;
    }

    public void iccOpenLogicalChannel(String AID, Message response) {
        this.mCi.iccOpenLogicalChannel(AID, this.mHandler.obtainMessage(15, response));
    }

    public void iccCloseLogicalChannel(int channel, Message response) {
        this.mCi.iccCloseLogicalChannel(channel, this.mHandler.obtainMessage(16, response));
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int command, int p1, int p2, int p3, String data, Message response) {
        this.mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3, data, this.mHandler.obtainMessage(17, response));
    }

    public void iccTransmitApduBasicChannel(int cla, int command, int p1, int p2, int p3, String data, Message response) {
        this.mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3, data, this.mHandler.obtainMessage(18, response));
    }

    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3, String pathID, Message response) {
        this.mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null, this.mHandler.obtainMessage(19, response));
    }

    public void sendEnvelopeWithStatus(String contents, Message response) {
        this.mCi.sendEnvelopeWithStatus(contents, response);
    }

    public int getNumApplications() {
        int count = 0;
        for (UiccCardApplication a : this.mUiccApplications) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(signature, packageName);
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatus(packageManager, packageName);
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        return this.mCarrierPrivilegeRules == null ? -1 : this.mCarrierPrivilegeRules.getCarrierPrivilegeStatusForCurrentTransaction(packageManager);
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        return this.mCarrierPrivilegeRules == null ? null : this.mCarrierPrivilegeRules.getCarrierPackageNamesForIntent(packageManager, intent);
    }

    public boolean setOperatorBrandOverride(String brand) {
        log("setOperatorBrandOverride: " + brand);
        log("current iccId: " + getIccId());
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }
        Editor spEditor = PreferenceManager.getDefaultSharedPreferences(this.mContext).edit();
        String key = OPERATOR_BRAND_OVERRIDE_PREFIX + iccId;
        if (brand == null) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putString(key, brand).commit();
        }
        return true;
    }

    public String getOperatorBrandOverride() {
        String iccId = getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        return PreferenceManager.getDefaultSharedPreferences(this.mContext).getString(OPERATOR_BRAND_OVERRIDE_PREFIX + iccId, null);
    }

    public String getIccId() {
        for (UiccCardApplication app : this.mUiccApplications) {
            if (app != null) {
                IccRecords ir = app.getIccRecords();
                if (!(ir == null || ir.getIccId() == null)) {
                    return ir.getIccId();
                }
            }
        }
        return null;
    }

    private void log(String msg) {
        if (this.mPhone != null) {
            Rlog.d(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.getPhoneId()), msg);
        } else {
            Rlog.d(LOG_TAG, msg);
        }
    }

    private void loge(String msg) {
        if (this.mPhone != null) {
            Rlog.e(MultiSimManager.appendSimSlot(LOG_TAG, this.mPhone.getPhoneId()), msg);
        } else {
            Rlog.e(LOG_TAG, msg);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int i;
        pw.println("UiccCard:");
        pw.println(" mCi=" + this.mCi);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mLastRadioState=" + this.mLastRadioState);
        pw.println(" mCatService=" + this.mCatService);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        for (i = 0; i < this.mCarrierPrivilegeRegistrants.size(); i++) {
            pw.println("  mCarrierPrivilegeRegistrants[" + i + "]=" + ((Registrant) this.mCarrierPrivilegeRegistrants.get(i)).getHandler());
        }
        pw.println(" mCardState=" + this.mCardState);
        pw.println(" mUniversalPinState=" + this.mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + this.mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + this.mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + this.mUiccApplications.length);
        for (i = 0; i < this.mUiccApplications.length; i++) {
            if (this.mUiccApplications[i] == null) {
                pw.println("  mUiccApplications[" + i + "]=" + null);
            } else {
                pw.println("  mUiccApplications[" + i + "]=" + this.mUiccApplications[i].getType() + " " + this.mUiccApplications[i]);
            }
        }
        pw.println();
        for (UiccCardApplication app : this.mUiccApplications) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        for (UiccCardApplication app2 : this.mUiccApplications) {
            if (app2 != null) {
                IccRecords ir = app2.getIccRecords();
                if (ir != null) {
                    ir.dump(fd, pw, args);
                    pw.println();
                }
            }
        }
        pw.flush();
    }
}
