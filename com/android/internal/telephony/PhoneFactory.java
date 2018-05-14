package com.android.internal.telephony;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.LocalServerSocket;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.uicc.UiccController;
import com.sec.android.app.CscFeature;
import java.io.IOException;

public class PhoneFactory {
    private static ServiceConnection ImsTelephonyServiceConnection = new C00221();
    static final String LOG_TAG = "PhoneFactory";
    static final int SOCKET_OPEN_MAX_RETRY = 3;
    static final int SOCKET_OPEN_RETRY_MILLIS = 2000;
    private static ProxyController mProxyController;
    private static UiccController mUiccController;
    private static CommandsInterface sCommandsInterface = null;
    private static CommandsInterface[] sCommandsInterfaces = null;
    private static Context sContext;
    static final Object sLockProxyPhones = new Object();
    private static boolean sMadeDefaults = false;
    private static PhoneNotifier sPhoneNotifier;
    private static PhoneProxy sProxyPhone = null;
    private static PhoneProxy[] sProxyPhones = null;
    private static SubInfoRecordUpdater sSubInfoRecordUpdater = null;

    static class C00221 implements ServiceConnection {
        C00221() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            Rlog.d(PhoneFactory.LOG_TAG, "ImsTelephonyService onServiceConnected");
        }

        public void onServiceDisconnected(ComponentName arg0) {
            Rlog.d(PhoneFactory.LOG_TAG, "ImsTelephonyService onServiceDisconnected");
        }
    }

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    public static void makeDefaultPhone(Context context) {
        int i;
        synchronized (sLockProxyPhones) {
            if (!sMadeDefaults) {
                sContext = context;
                String salesCode = SystemProperties.get("ro.csc.sales_code");
                TelephonyDevController.create();
                int retryCount = 0;
                while (true) {
                    boolean hasException = false;
                    retryCount++;
                    try {
                        LocalServerSocket localServerSocket = new LocalServerSocket("com.android.internal.telephony");
                    } catch (IOException e) {
                        hasException = true;
                    }
                    if (!hasException) {
                        break;
                    } else if (retryCount > 3) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e2) {
                        }
                    }
                }
                sPhoneNotifier = new DefaultPhoneNotifier();
                int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
                if (TelephonyManager.getLteOnCdmaModeStatic() == 1) {
                    preferredNetworkMode = 7;
                }
                if ("CTC".equals(salesCode)) {
                    Global.putInt(context.getContentResolver(), "preferred_network_mode", 4);
                }
                int networkMode = Global.getInt(context.getContentResolver(), "preferred_network_mode", preferredNetworkMode);
                Rlog.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));
                int cdmaSubscription = CdmaSubscriptionSourceManager.getDefault(context);
                Rlog.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);
                int numPhones = TelephonyManager.getDefault().getPhoneCount();
                int[] networkModes = new int[numPhones];
                sProxyPhones = new PhoneProxy[numPhones];
                sCommandsInterfaces = new RIL[numPhones];
                for (i = 0; i < numPhones; i++) {
                    try {
                        networkModes[i] = TelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", i);
                    } catch (SettingNotFoundException e3) {
                        Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index for Settings.Global.PREFERRED_NETWORK_MODE");
                        networkModes[i] = 3;
                    }
                    Rlog.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkModes[i]));
                    if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_EnableItsOn")) {
                        sCommandsInterfaces[i] = new ItsOnRIL(context, networkModes[i], cdmaSubscription, Integer.valueOf(i));
                    } else {
                        sCommandsInterfaces[i] = new RIL(context, networkModes[i], cdmaSubscription, Integer.valueOf(i));
                    }
                }
                Rlog.i(LOG_TAG, "Creating SubscriptionController");
                SubscriptionController.init(context, sCommandsInterfaces);
                mUiccController = UiccController.make(context, sCommandsInterfaces);
                for (i = 0; i < numPhones; i++) {
                    PhoneBase phone = null;
                    int phoneType = TelephonyManager.getPhoneType(networkModes[i]);
                    SystemProperties.set("persist.radio.initphone-type", String.valueOf(phoneType));
                    if (phoneType == 1) {
                        Rlog.i(LOG_TAG, "Creating GSMPhone");
                        phone = new GSMPhone(context, sCommandsInterfaces[i], sPhoneNotifier, i);
                    } else if (phoneType == 2) {
                        switch (TelephonyManager.getLteOnCdmaModeStatic()) {
                            case 1:
                                Rlog.i(LOG_TAG, "Creating CDMALTEPhone");
                                phone = new CDMALTEPhone(context, sCommandsInterfaces[i], sPhoneNotifier, i);
                                break;
                            default:
                                Rlog.i(LOG_TAG, "Creating CDMAPhone");
                                phone = new CDMAPhone(context, sCommandsInterfaces[i], sPhoneNotifier, i);
                                break;
                        }
                    }
                    Rlog.i(LOG_TAG, "Creating Phone with type = " + phoneType + " sub = " + i);
                    sProxyPhones[i] = new PhoneProxy(phone);
                }
                mProxyController = ProxyController.getInstance(context, sProxyPhones, mUiccController, sCommandsInterfaces);
                sProxyPhone = sProxyPhones[0];
                sCommandsInterface = sCommandsInterfaces[0];
                ComponentName componentName = SmsApplication.getDefaultSmsApplication(context, true);
                String packageName = "NONE";
                if (componentName != null) {
                    packageName = componentName.getPackageName();
                }
                Rlog.i(LOG_TAG, "defaultSmsApplication: " + packageName);
                SmsApplication.initSmsPackageMonitor(context);
                sMadeDefaults = true;
                Rlog.i(LOG_TAG, "Creating SubInfoRecordUpdater ");
                sSubInfoRecordUpdater = new SubInfoRecordUpdater(context, sProxyPhones, sCommandsInterfaces);
                SubscriptionController.getInstance().updatePhonesAvailability(sProxyPhones);
                context.sendBroadcast(new Intent("edm.intent.action.PHONE_READY"), "android.permission.sec.MDM_PHONE_RESTRICTION");
            }
        }
    }

    private static void createImsService() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.sec.ims", "com.sec.ims.ImsTelephonyService");
            Rlog.d(LOG_TAG, "ImsTelephonyService bound request : " + sContext.bindService(intent, ImsTelephonyServiceConnection, 1));
        } catch (NoClassDefFoundError e) {
            Rlog.w(LOG_TAG, "Ignoring ImsTelephonyService class not found exception " + e);
        }
    }

    public static Phone getCdmaPhone(int phoneId) {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            switch (TelephonyManager.getLteOnCdmaModeStatic()) {
                case 1:
                    Rlog.i(LOG_TAG, "Creating new CDMALTEPhone");
                    phone = new CDMALTEPhone(sContext, sCommandsInterfaces[phoneId], sPhoneNotifier, phoneId);
                    break;
                default:
                    Rlog.i(LOG_TAG, "Creating new CDMAPhone");
                    phone = new CDMAPhone(sContext, sCommandsInterfaces[phoneId], sPhoneNotifier, phoneId);
                    break;
            }
        }
        return phone;
    }

    public static Phone getGsmPhone(int phoneId) {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            Rlog.i(LOG_TAG, " Creating new GSMPhone");
            phone = new GSMPhone(sContext, sCommandsInterfaces[phoneId], sPhoneNotifier, phoneId);
        }
        return phone;
    }

    public static Phone getDefaultPhone() {
        Phone phone;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phone = sProxyPhone;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phone;
    }

    public static Phone getPhone(int phoneId) {
        Phone phone;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                if (phoneId == 0 || phoneId == -1000) {
                    Rlog.d(LOG_TAG, "getPhone: phoneId == DEFAULT_PHONE_ID");
                    phone = sProxyPhone;
                } else {
                    Rlog.d(LOG_TAG, "getPhone: phoneId != DEFAULT_PHONE_ID");
                    phone = (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) ? null : sProxyPhones[phoneId];
                }
                Rlog.d(LOG_TAG, "getPhone:- phone=" + phone);
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phone;
    }

    public static Phone[] getPhones() {
        Phone[] phoneArr;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phoneArr = sProxyPhones;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phoneArr;
    }

    public static Phone getCdmaPhone() {
        if (sMadeDefaults) {
            Phone phone;
            synchronized (PhoneProxy.lockForRadioTechnologyChange) {
                switch (TelephonyManager.getLteOnCdmaModeStatic()) {
                    case 1:
                        phone = new CDMALTEPhone(sContext, sCommandsInterface, sPhoneNotifier);
                        break;
                    default:
                        phone = new CDMAPhone(sContext, sCommandsInterface, sPhoneNotifier);
                        break;
                }
            }
            return phone;
        }
        throw new IllegalStateException("Default phones haven't been made yet!");
    }

    public static Phone getGsmPhone() {
        int phoneId = SubscriptionController.getInstance().getPhoneId(getDefaultSubscription());
        if (phoneId < 0 || phoneId >= TelephonyManager.getDefault().getPhoneCount()) {
            phoneId = 0;
        }
        return getGsmPhone(phoneId);
    }

    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier);
    }

    public static void setDefaultSubscription(int subId) {
        SystemProperties.set("persist.radio.default.sub", Integer.toString(subId));
        int phoneId = SubscriptionController.getInstance().getPhoneId((long) subId);
        synchronized (sLockProxyPhones) {
            if (phoneId >= 0) {
                if (phoneId < sProxyPhones.length) {
                    sProxyPhone = sProxyPhones[phoneId];
                    sCommandsInterface = sCommandsInterfaces[phoneId];
                    sMadeDefaults = true;
                }
            }
        }
        String defaultMccMnc = TelephonyManager.getDefault().getSimOperator((long) phoneId);
        Rlog.d(LOG_TAG, "update mccmnc=" + defaultMccMnc);
        MccTable.updateMccMncConfiguration(sContext, defaultMccMnc, false);
        Intent intent = new Intent("android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
        intent.addFlags(536870912);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
        Rlog.d(LOG_TAG, "setDefaultSubscription : " + subId + " Broadcasting Default Subscription Changed...");
        sContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static int calculatePreferredNetworkType(Context context) {
        return calculatePreferredNetworkType(context, getDefaultPhoneId());
    }

    public static int calculatePreferredNetworkType(Context context, int phoneId) {
        int preferredNetworkType = RILConstants.PREFERRED_NETWORK_MODE;
        if (TelephonyManager.getLteOnCdmaModeStatic() == 1) {
            preferredNetworkType = 7;
        }
        try {
            return TelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", phoneId);
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Value At Index " + phoneId + " for Settings.Global.PREFERRED_NETWORK_MODE");
            return preferredNetworkType;
        }
    }

    public static long getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    public static int getVoiceSubscription() {
        int subId = 0;
        try {
            subId = Global.getInt(sContext.getContentResolver(), "multi_sim_voice_call");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId((long) subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + subId + " Set to 0");
        setVoiceSubscription(0);
        return 0;
    }

    public static boolean isPromptEnabled() {
        int value = 0;
        try {
            value = Global.getInt(sContext.getContentResolver(), "multi_sim_voice_prompt");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Voice Prompt Values");
        }
        boolean prompt = value != 0;
        Rlog.d(LOG_TAG, "Prompt option:" + prompt);
        return prompt;
    }

    public static void setPromptEnabled(boolean enabled) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_voice_prompt", !enabled ? 0 : 1);
        Rlog.d(LOG_TAG, "setVoicePromptOption to " + enabled);
    }

    public static boolean isSMSPromptEnabled() {
        int value = 0;
        try {
            value = Global.getInt(sContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        boolean prompt = value != 0;
        Rlog.d(LOG_TAG, "SMS Prompt option:" + prompt);
        return prompt;
    }

    public static void setSMSPromptEnabled(boolean enabled) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_sms_prompt", !enabled ? 0 : 1);
        Rlog.d(LOG_TAG, "setSMSPromptOption to " + enabled);
    }

    public static long getDataSubscription() {
        long subId = 1;
        try {
            subId = Global.getLong(sContext.getContentResolver(), "multi_sim_data_call");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim Data Call Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + 1 + " Set to 0");
        setDataSubscription(1);
        return 1;
    }

    public static int getSMSSubscription() {
        int subId = 0;
        try {
            subId = Global.getInt(sContext.getContentResolver(), "multi_sim_sms");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Values");
        }
        int phoneId = SubscriptionController.getInstance().getPhoneId((long) subId);
        if (phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount()) {
            return subId;
        }
        Rlog.i(LOG_TAG, "Subscription is invalid..." + subId + " Set to 0");
        setSMSSubscription(0);
        return 0;
    }

    public static void setVoiceSubscription(int subId) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_voice_call", subId);
        Rlog.d(LOG_TAG, "setVoiceSubscription : " + subId);
    }

    public static void setDataSubscription(long subId) {
        boolean enabled;
        int i;
        int i2 = 1;
        Global.putLong(sContext.getContentResolver(), "multi_sim_data_call", subId);
        Rlog.d(LOG_TAG, "setDataSubscription: " + subId);
        if (Global.getInt(sContext.getContentResolver(), "mobile_data" + subId, 0) != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        ContentResolver contentResolver = sContext.getContentResolver();
        String str = "mobile_data";
        if (enabled) {
            i = 1;
        } else {
            i = 0;
        }
        Global.putInt(contentResolver, str, i);
        Rlog.d(LOG_TAG, "set mobile_data: " + enabled);
        if (Global.getInt(sContext.getContentResolver(), "data_roaming" + subId, 0) != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        ContentResolver contentResolver2 = sContext.getContentResolver();
        String str2 = "data_roaming";
        if (!enabled) {
            i2 = 0;
        }
        Global.putInt(contentResolver2, str2, i2);
        Rlog.d(LOG_TAG, "set data_roaming: " + enabled);
    }

    public static void setSMSSubscription(int subId) {
        Global.putInt(sContext.getContentResolver(), "multi_sim_sms", subId);
        sContext.sendBroadcast(new Intent("com.android.mms.transaction.SEND_MESSAGE"));
        Rlog.d(LOG_TAG, "setSMSSubscription : " + subId);
    }

    public static ImsPhone makeImsPhone(PhoneNotifier phoneNotifier, Phone defaultPhone) {
        return ImsPhoneFactory.makePhone(sContext, phoneNotifier, defaultPhone);
    }

    private static boolean isValidphoneId(int phoneId) {
        return phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount();
    }

    private static int getDefaultPhoneId() {
        int phoneId = SubscriptionController.getInstance().getPhoneId(getDefaultSubscription());
        if (isValidphoneId(phoneId)) {
            return phoneId;
        }
        return 0;
    }
}
