package com.android.internal.telephony;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IPhoneSubInfo.Stub;

public class PhoneSubInfoController extends Stub {
    private static final String TAG = "PhoneSubInfoController";
    private Phone[] mPhone;

    public PhoneSubInfoController(Phone[] phone) {
        this.mPhone = phone;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    public String getDeviceId() {
        return getDeviceIdForSubscriber(getDefaultSubscription());
    }

    public String getDeviceIdForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceId();
        }
        Rlog.e(TAG, "getDeviceId phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getImeiForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getImei();
        }
        Rlog.e(TAG, "getDeviceId phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getDeviceSvn() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getDeviceSvn();
        }
        Rlog.e(TAG, "getDeviceSvn phoneSubInfoProxy is null");
        return null;
    }

    public String getSubscriberId() {
        return getSubscriberIdForSubscriber(getDefaultSubscription());
    }

    public String getSubscriberIdForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getSubscriberId();
        }
        Rlog.e(TAG, "getSubscriberId phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getIccSerialNumber() {
        return getIccSerialNumberForSubscriber(getDefaultSubscription());
    }

    public String getIccSerialNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getIccSerialNumber();
        }
        Rlog.e(TAG, "getIccSerialNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getLine1Number() {
        return getLine1NumberForSubscriber(getDefaultSubscription());
    }

    public String getLine1NumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1Number();
        }
        Rlog.e(TAG, "getLine1Number phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getLine1AlphaTag() {
        return getLine1AlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getLine1AlphaTagForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getLine1AlphaTag();
        }
        Rlog.e(TAG, "getLine1AlphaTag phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getMsisdn() {
        return getMsisdnForSubscriber(getDefaultSubscription());
    }

    public String getMsisdnForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getMsisdn();
        }
        Rlog.e(TAG, "getMsisdn phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getVoiceMailNumber() {
        return getVoiceMailNumberForSubscriber(getDefaultSubscription());
    }

    public String getVoiceMailNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailNumber();
        }
        Rlog.e(TAG, "getVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getCompleteVoiceMailNumber() {
        return getCompleteVoiceMailNumberForSubscriber(getDefaultSubscription());
    }

    public String getCompleteVoiceMailNumberForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getCompleteVoiceMailNumber();
        }
        Rlog.e(TAG, "getCompleteVoiceMailNumber phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return getVoiceMailAlphaTagForSubscriber(getDefaultSubscription());
    }

    public String getVoiceMailAlphaTagForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getVoiceMailAlphaTag();
        }
        Rlog.e(TAG, "getVoiceMailAlphaTag phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    private PhoneSubInfoProxy getPhoneSubInfoProxy(long subId) {
        long phoneId = (long) SubscriptionController.getInstance().getPhoneId(subId);
        if (phoneId < 0 || phoneId >= ((long) TelephonyManager.getDefault().getPhoneCount())) {
            phoneId = 0;
        }
        try {
            return ((PhoneProxy) this.mPhone[(int) phoneId]).getPhoneSubInfoProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subId :" + subId);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e2) {
            Rlog.e(TAG, "Exception is :" + e2.toString() + " For subId :" + subId);
            e2.printStackTrace();
            return null;
        }
    }

    private long getDefaultSubscription() {
        return PhoneFactory.getDefaultSubscription();
    }

    public String getIsimImpi() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimImpi();
    }

    public String getIsimDomain() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimDomain();
    }

    public String[] getIsimImpu() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimImpu();
    }

    public String getIsimIst() throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimIst();
    }

    public String[] getIsimPcscf() throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimPcscf();
    }

    public String getIsimChallengeResponse(String nonce) throws RemoteException {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(long subId, int appType, String data) throws RemoteException {
        return getPhoneSubInfoProxy(subId).getIccSimChallengeResponse(subId, appType, data);
    }

    public String getGroupIdLevel1() {
        return getGroupIdLevel1ForSubscriber(getDefaultSubscription());
    }

    public String getGroupIdLevel1ForSubscriber(long subId) {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(subId);
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.getGroupIdLevel1();
        }
        Rlog.e(TAG, "getGroupIdLevel1 phoneSubInfoProxy is null for Subscription:" + subId);
        return null;
    }

    public boolean isImsRegistered() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).isImsRegistered();
    }

    public boolean isVolteRegistered() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).isVolteRegistered();
    }

    public boolean isWfcRegistered() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).isWfcRegistered();
    }

    public int getImsRegisteredFeature() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getImsRegisteredFeature();
    }

    public boolean hasCall(String callType) {
        return getPhoneSubInfoProxy(getDefaultSubscription()).hasCall(callType);
    }

    public boolean setDrxMode(int drxMode) {
        return getPhoneSubInfoProxy(getDefaultSubscription()).setDrxMode(drxMode);
    }

    public int getDrxValue() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getDrxValue();
    }

    public boolean setReducedCycleTime(int cycleTime) {
        return getPhoneSubInfoProxy(getDefaultSubscription()).setReducedCycleTime(cycleTime);
    }

    public int getReducedCycleTime() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getReducedCycleTime();
    }

    public String getSktImsiM() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getSktImsiM();
    }

    public String getSktIrm() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getSktIrm();
    }

    public String[] getSponImsi() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getSponImsi();
    }

    public boolean isGbaSupported() {
        PhoneSubInfoProxy phoneSubInfoProxy = getPhoneSubInfoProxy(getDefaultSubscription());
        if (phoneSubInfoProxy != null) {
            return phoneSubInfoProxy.isGbaSupported();
        }
        Rlog.e(TAG, "isGbaSupported phoneSubInfoProxy is null for default Subscription");
        return false;
    }

    public String getLine1NumberType(int SimType) {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getLine1NumberType(SimType);
    }

    public String getSubscriberIdType(int SimType) {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getSubscriberIdType(SimType);
    }

    public boolean hasIsim() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).hasIsim();
    }

    public byte[] getPsismsc() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getPsismsc();
    }

    public byte[] getRand() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getRand();
    }

    public String getBtid() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getBtid();
    }

    public String getKeyLifetime() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getKeyLifetime();
    }

    public String getIsimAid() {
        return getPhoneSubInfoProxy(getDefaultSubscription()).getIsimAid();
    }
}
