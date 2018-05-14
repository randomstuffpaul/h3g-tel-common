package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface.RadioState;

public abstract class BaseCommands implements CommandsInterface {
    protected RegistrantList mAvailRegistrants = new RegistrantList();
    protected RegistrantList mCallStateRegistrants = new RegistrantList();
    protected RegistrantList mCallWaitingInfoRegistrants = new RegistrantList();
    protected Registrant mCatCallControlResultRegistrant;
    protected Registrant mCatCallSetUpRegistrant;
    protected Registrant mCatEventRegistrant;
    protected Registrant mCatProCmdRegistrant;
    protected Registrant mCatSendSmsResultRegistrant;
    protected Registrant mCatSessionEndRegistrant;
    protected RegistrantList mCdmaPrlChangedRegistrants = new RegistrantList();
    protected Registrant mCdmaSmsRegistrant;
    protected int mCdmaSubscription;
    protected RegistrantList mCdmaSubscriptionChangedRegistrants = new RegistrantList();
    protected Context mContext;
    protected Registrant mCsFallbackRegistant;
    protected RegistrantList mDataNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected Registrant mEmergencyCallbackModeRegistrant;
    protected RegistrantList mExitEmergencyCallbackModeRegistrants = new RegistrantList();
    protected Registrant mGsmBroadcastSmsRegistrant;
    protected Registrant mGsmSmsRegistrant;
    protected RegistrantList mHardwareConfigChangeRegistrants = new RegistrantList();
    protected Registrant mHomeNetworkRegistant;
    protected Registrant mIccRefreshRegistrant;
    protected RegistrantList mIccRefreshRegistrants = new RegistrantList();
    protected Registrant mIccSmsFullRegistrant;
    protected RegistrantList mIccStatusChangedRegistrants = new RegistrantList();
    protected RegistrantList mImsNetworkStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mImsRegistrationRetryOver = new RegistrantList();
    protected RegistrantList mImsRegistrationStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mLineControlInfoRegistrants = new RegistrantList();
    protected RegistrantList mModifyCallRegistrants = new RegistrantList();
    protected Registrant mNITZTimeRegistrant;
    protected RegistrantList mNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mNumberInfoRegistrants = new RegistrantList();
    protected Registrant mO2HomeZoneInfoRegistrant;
    protected RegistrantList mOffOrNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mOnRegistrants = new RegistrantList();
    protected RegistrantList mOtaProvisionRegistrants = new RegistrantList();
    protected Registrant mPbInitCompleteRegistrant;
    protected int mPhoneType;
    protected int mPreferredNetworkType;
    protected RegistrantList mRadioStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mRedirNumInfoRegistrants = new RegistrantList();
    protected Registrant mReleaseCompleteMessageRegistrant;
    protected RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected Registrant mRestrictedStateRegistrant;
    protected RegistrantList mRilCellInfoListRegistrants = new RegistrantList();
    protected RegistrantList mRilConnectedRegistrants = new RegistrantList();
    protected int mRilVersion = -1;
    protected Registrant mRingRegistrant;
    protected RegistrantList mRingbackToneRegistrants = new RegistrantList();
    protected Registrant mSSRegistrant;
    protected Registrant mSapRegistant;
    protected Registrant mSendDTMFResultRegistrant;
    protected RegistrantList mSignalInfoRegistrants = new RegistrantList();
    protected Registrant mSignalStrengthRegistrant;
    protected Registrant mSimPbReadyRegistrant;
    protected Registrant mSmsDeviceReadyRegistrant;
    protected Registrant mSmsOnSimRegistrant;
    protected Registrant mSmsStatusRegistrant;
    protected RegistrantList mSrvccStateRegistrants = new RegistrantList();
    protected Registrant mSsnRegistrant;
    protected RadioState mState = RadioState.RADIO_UNAVAILABLE;
    protected Object mStateMonitor = new Object();
    protected Registrant mStkSetupCallStatus;
    protected RegistrantList mSubscriptionStatusRegistrants = new RegistrantList();
    protected RegistrantList mT53AudCntrlInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53ClirInfoRegistrants = new RegistrantList();
    protected Registrant mUSSDRegistrant;
    protected Registrant mUnsolOemHookRawRegistrant;
    protected RegistrantList mVoiceNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOffRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOnRegistrants = new RegistrantList();
    protected RegistrantList mVoiceRadioTechChangedRegistrants = new RegistrantList();
    protected Registrant mVoiceSystemIdRegistrant;

    public BaseCommands(Context context) {
        this.mContext = context;
    }

    public RadioState getRadioState() {
        return this.mState;
    }

    public void registerForRadioStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mStateMonitor) {
            this.mRadioStateChangedRegistrants.add(r);
            r.notifyRegistrant();
        }
    }

    public void unregisterForRadioStateChanged(Handler h) {
        synchronized (this.mStateMonitor) {
            this.mRadioStateChangedRegistrants.remove(h);
        }
    }

    public void registerForImsNetworkStateChanged(Handler h, int what, Object obj) {
        this.mImsNetworkStateChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForImsNetworkStateChanged(Handler h) {
        this.mImsNetworkStateChangedRegistrants.remove(h);
    }

    public void registerForOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mStateMonitor) {
            this.mOnRegistrants.add(r);
            if (this.mState.isOn()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForOn(Handler h) {
        synchronized (this.mStateMonitor) {
            this.mOnRegistrants.remove(h);
        }
    }

    public void registerForAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mStateMonitor) {
            this.mAvailRegistrants.add(r);
            if (this.mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForAvailable(Handler h) {
        synchronized (this.mStateMonitor) {
            this.mAvailRegistrants.remove(h);
        }
    }

    public void registerForNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mStateMonitor) {
            this.mNotAvailRegistrants.add(r);
            if (!this.mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForNotAvailable(Handler h) {
        synchronized (this.mStateMonitor) {
            this.mNotAvailRegistrants.remove(h);
        }
    }

    public void registerForOffOrNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mStateMonitor) {
            this.mOffOrNotAvailRegistrants.add(r);
            if (this.mState == RadioState.RADIO_OFF || !this.mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public void unregisterForOffOrNotAvailable(Handler h) {
        synchronized (this.mStateMonitor) {
            this.mOffOrNotAvailRegistrants.remove(h);
        }
    }

    public void registerForCallStateChanged(Handler h, int what, Object obj) {
        this.mCallStateRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCallStateChanged(Handler h) {
        this.mCallStateRegistrants.remove(h);
    }

    public void registerForVoiceNetworkStateChanged(Handler h, int what, Object obj) {
        this.mVoiceNetworkStateRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForVoiceNetworkStateChanged(Handler h) {
        this.mVoiceNetworkStateRegistrants.remove(h);
    }

    public void registerForDataNetworkStateChanged(Handler h, int what, Object obj) {
        this.mDataNetworkStateRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDataNetworkStateChanged(Handler h) {
        this.mDataNetworkStateRegistrants.remove(h);
    }

    public void registerForVoiceRadioTechChanged(Handler h, int what, Object obj) {
        this.mVoiceRadioTechChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForVoiceRadioTechChanged(Handler h) {
        this.mVoiceRadioTechChangedRegistrants.remove(h);
    }

    public void registerForIccStatusChanged(Handler h, int what, Object obj) {
        this.mIccStatusChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForIccStatusChanged(Handler h) {
        this.mIccStatusChangedRegistrants.remove(h);
    }

    public void setOnNewGsmSms(Handler h, int what, Object obj) {
        this.mGsmSmsRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnNewGsmSms(Handler h) {
        if (this.mGsmSmsRegistrant != null && this.mGsmSmsRegistrant.getHandler() == h) {
            this.mGsmSmsRegistrant.clear();
            this.mGsmSmsRegistrant = null;
        }
    }

    public void setOnNewCdmaSms(Handler h, int what, Object obj) {
        this.mCdmaSmsRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnNewCdmaSms(Handler h) {
        if (this.mCdmaSmsRegistrant != null && this.mCdmaSmsRegistrant.getHandler() == h) {
            this.mCdmaSmsRegistrant.clear();
            this.mCdmaSmsRegistrant = null;
        }
    }

    public void setOnNewGsmBroadcastSms(Handler h, int what, Object obj) {
        this.mGsmBroadcastSmsRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnNewGsmBroadcastSms(Handler h) {
        if (this.mGsmBroadcastSmsRegistrant != null && this.mGsmBroadcastSmsRegistrant.getHandler() == h) {
            this.mGsmBroadcastSmsRegistrant.clear();
            this.mGsmBroadcastSmsRegistrant = null;
        }
    }

    public void setOnSmsOnSim(Handler h, int what, Object obj) {
        this.mSmsOnSimRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSmsOnSim(Handler h) {
        if (this.mSmsOnSimRegistrant != null && this.mSmsOnSimRegistrant.getHandler() == h) {
            this.mSmsOnSimRegistrant.clear();
            this.mSmsOnSimRegistrant = null;
        }
    }

    public void setOnSmsStatus(Handler h, int what, Object obj) {
        this.mSmsStatusRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSmsStatus(Handler h) {
        if (this.mSmsStatusRegistrant != null && this.mSmsStatusRegistrant.getHandler() == h) {
            this.mSmsStatusRegistrant.clear();
            this.mSmsStatusRegistrant = null;
        }
    }

    public void setOnSignalStrengthUpdate(Handler h, int what, Object obj) {
        this.mSignalStrengthRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSignalStrengthUpdate(Handler h) {
        if (this.mSignalStrengthRegistrant != null && this.mSignalStrengthRegistrant.getHandler() == h) {
            this.mSignalStrengthRegistrant.clear();
            this.mSignalStrengthRegistrant = null;
        }
    }

    public void setOnNITZTime(Handler h, int what, Object obj) {
        this.mNITZTimeRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnNITZTime(Handler h) {
        if (this.mNITZTimeRegistrant != null && this.mNITZTimeRegistrant.getHandler() == h) {
            this.mNITZTimeRegistrant.clear();
            this.mNITZTimeRegistrant = null;
        }
    }

    public void setOnUSSD(Handler h, int what, Object obj) {
        this.mUSSDRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnUSSD(Handler h) {
        if (this.mUSSDRegistrant != null && this.mUSSDRegistrant.getHandler() == h) {
            this.mUSSDRegistrant.clear();
            this.mUSSDRegistrant = null;
        }
    }

    public void setOnSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSuppServiceNotification(Handler h) {
        if (this.mSsnRegistrant != null && this.mSsnRegistrant.getHandler() == h) {
            this.mSsnRegistrant.clear();
            this.mSsnRegistrant = null;
        }
    }

    public void setOnCatSessionEnd(Handler h, int what, Object obj) {
        this.mCatSessionEndRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatSessionEnd(Handler h) {
        if (this.mCatSessionEndRegistrant != null && this.mCatSessionEndRegistrant.getHandler() == h) {
            this.mCatSessionEndRegistrant.clear();
            this.mCatSessionEndRegistrant = null;
        }
    }

    public void setOnCatProactiveCmd(Handler h, int what, Object obj) {
        this.mCatProCmdRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatProactiveCmd(Handler h) {
        if (this.mCatProCmdRegistrant != null && this.mCatProCmdRegistrant.getHandler() == h) {
            this.mCatProCmdRegistrant.clear();
            this.mCatProCmdRegistrant = null;
        }
    }

    public void setOnCatEvent(Handler h, int what, Object obj) {
        this.mCatEventRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatEvent(Handler h) {
        if (this.mCatEventRegistrant != null && this.mCatEventRegistrant.getHandler() == h) {
            this.mCatEventRegistrant.clear();
            this.mCatEventRegistrant = null;
        }
    }

    public void setOnCatCallSetUp(Handler h, int what, Object obj) {
        this.mCatCallSetUpRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatCallSetUp(Handler h) {
        if (this.mCatCallSetUpRegistrant != null && this.mCatCallSetUpRegistrant.getHandler() == h) {
            this.mCatCallSetUpRegistrant.clear();
            this.mCatCallSetUpRegistrant = null;
        }
    }

    public void setOnIccSmsFull(Handler h, int what, Object obj) {
        this.mIccSmsFullRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnIccSmsFull(Handler h) {
        if (this.mIccSmsFullRegistrant != null && this.mIccSmsFullRegistrant.getHandler() == h) {
            this.mIccSmsFullRegistrant.clear();
            this.mIccSmsFullRegistrant = null;
        }
    }

    public void registerForIccRefresh(Handler h, int what, Object obj) {
        this.mIccRefreshRegistrants.add(new Registrant(h, what, obj));
    }

    public void setOnIccRefresh(Handler h, int what, Object obj) {
        registerForIccRefresh(h, what, obj);
    }

    public void setEmergencyCallbackMode(Handler h, int what, Object obj) {
        this.mEmergencyCallbackModeRegistrant = new Registrant(h, what, obj);
    }

    public void unregisterForIccRefresh(Handler h) {
        this.mIccRefreshRegistrants.remove(h);
    }

    public void unsetOnIccRefresh(Handler h) {
        unregisterForIccRefresh(h);
    }

    public void setOnCallRing(Handler h, int what, Object obj) {
        this.mRingRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCallRing(Handler h) {
        if (this.mRingRegistrant != null && this.mRingRegistrant.getHandler() == h) {
            this.mRingRegistrant.clear();
            this.mRingRegistrant = null;
        }
    }

    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mVoicePrivacyOnRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mVoicePrivacyOnRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mVoicePrivacyOffRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mVoicePrivacyOffRegistrants.remove(h);
    }

    public void setOnRestrictedStateChanged(Handler h, int what, Object obj) {
        this.mRestrictedStateRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnRestrictedStateChanged(Handler h) {
        if (this.mRestrictedStateRegistrant != null && this.mRestrictedStateRegistrant.getHandler() != h) {
            this.mRestrictedStateRegistrant.clear();
            this.mRestrictedStateRegistrant = null;
        }
    }

    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mDisplayInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDisplayInfo(Handler h) {
        this.mDisplayInfoRegistrants.remove(h);
    }

    public void registerForCallWaitingInfo(Handler h, int what, Object obj) {
        this.mCallWaitingInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCallWaitingInfo(Handler h) {
        this.mCallWaitingInfoRegistrants.remove(h);
    }

    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mSignalInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void setOnUnsolOemHookRaw(Handler h, int what, Object obj) {
        this.mUnsolOemHookRawRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnUnsolOemHookRaw(Handler h) {
        if (this.mUnsolOemHookRawRegistrant != null && this.mUnsolOemHookRawRegistrant.getHandler() == h) {
            this.mUnsolOemHookRawRegistrant.clear();
            this.mUnsolOemHookRawRegistrant = null;
        }
    }

    public void unregisterForSignalInfo(Handler h) {
        this.mSignalInfoRegistrants.remove(h);
    }

    public void registerForCdmaOtaProvision(Handler h, int what, Object obj) {
        this.mOtaProvisionRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCdmaOtaProvision(Handler h) {
        this.mOtaProvisionRegistrants.remove(h);
    }

    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mNumberInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForNumberInfo(Handler h) {
        this.mNumberInfoRegistrants.remove(h);
    }

    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mRedirNumInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mRedirNumInfoRegistrants.remove(h);
    }

    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mLineControlInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForLineControlInfo(Handler h) {
        this.mLineControlInfoRegistrants.remove(h);
    }

    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mT53ClirInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForT53ClirInfo(Handler h) {
        this.mT53ClirInfoRegistrants.remove(h);
    }

    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mT53AudCntrlInfoRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mT53AudCntrlInfoRegistrants.remove(h);
    }

    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mRingbackToneRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForRingbackTone(Handler h) {
        this.mRingbackToneRegistrants.remove(h);
    }

    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mResendIncallMuteRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForResendIncallMute(Handler h) {
        this.mResendIncallMuteRegistrants.remove(h);
    }

    public void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj) {
        this.mCdmaSubscriptionChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCdmaSubscriptionChanged(Handler h) {
        this.mCdmaSubscriptionChangedRegistrants.remove(h);
    }

    public void registerForCdmaPrlChanged(Handler h, int what, Object obj) {
        this.mCdmaPrlChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCdmaPrlChanged(Handler h) {
        this.mCdmaPrlChangedRegistrants.remove(h);
    }

    public void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj) {
        this.mExitEmergencyCallbackModeRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForExitEmergencyCallbackMode(Handler h) {
        this.mExitEmergencyCallbackModeRegistrants.remove(h);
    }

    public void registerForHardwareConfigChanged(Handler h, int what, Object obj) {
        this.mHardwareConfigChangeRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForHardwareConfigChanged(Handler h) {
        this.mHardwareConfigChangeRegistrants.remove(h);
    }

    public void registerForRilConnected(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRilConnectedRegistrants.add(r);
        if (this.mRilVersion != -1) {
            r.notifyRegistrant(new AsyncResult(null, new Integer(this.mRilVersion), null));
        }
    }

    public void unregisterForRilConnected(Handler h) {
        this.mRilConnectedRegistrants.remove(h);
    }

    public void registerForSubscriptionStatusChanged(Handler h, int what, Object obj) {
        this.mSubscriptionStatusRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForSubscriptionStatusChanged(Handler h) {
        this.mSubscriptionStatusRegistrants.remove(h);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected void setRadioState(com.android.internal.telephony.CommandsInterface.RadioState r4) {
        /*
        r3 = this;
        r2 = r3.mStateMonitor;
        monitor-enter(r2);
        r0 = r3.mState;	 Catch:{ all -> 0x0071 }
        r3.mState = r4;	 Catch:{ all -> 0x0071 }
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        if (r0 != r1) goto L_0x000d;
    L_0x000b:
        monitor-exit(r2);	 Catch:{ all -> 0x0071 }
    L_0x000c:
        return;
    L_0x000d:
        r1 = r3.mRadioStateChangedRegistrants;	 Catch:{ all -> 0x0071 }
        r1.notifyRegistrants();	 Catch:{ all -> 0x0071 }
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        r1 = r1.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x0028;
    L_0x001a:
        r1 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 != 0) goto L_0x0028;
    L_0x0020:
        r1 = r3.mAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r1.notifyRegistrants();	 Catch:{ all -> 0x0071 }
        r3.onRadioAvailable();	 Catch:{ all -> 0x0071 }
    L_0x0028:
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        r1 = r1.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 != 0) goto L_0x003b;
    L_0x0030:
        r1 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x003b;
    L_0x0036:
        r1 = r3.mNotAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r1.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x003b:
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        r1 = r1.isOn();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x004e;
    L_0x0043:
        r1 = r0.isOn();	 Catch:{ all -> 0x0071 }
        if (r1 != 0) goto L_0x004e;
    L_0x0049:
        r1 = r3.mOnRegistrants;	 Catch:{ all -> 0x0071 }
        r1.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x004e:
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        r1 = r1.isOn();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x005e;
    L_0x0056:
        r1 = r3.mState;	 Catch:{ all -> 0x0071 }
        r1 = r1.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 != 0) goto L_0x006f;
    L_0x005e:
        r1 = r0.isOn();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x006f;
    L_0x0064:
        r1 = r0.isAvailable();	 Catch:{ all -> 0x0071 }
        if (r1 == 0) goto L_0x006f;
    L_0x006a:
        r1 = r3.mOffOrNotAvailRegistrants;	 Catch:{ all -> 0x0071 }
        r1.notifyRegistrants();	 Catch:{ all -> 0x0071 }
    L_0x006f:
        monitor-exit(r2);	 Catch:{ all -> 0x0071 }
        goto L_0x000c;
    L_0x0071:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0071 }
        throw r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.BaseCommands.setRadioState(com.android.internal.telephony.CommandsInterface$RadioState):void");
    }

    protected void onRadioAvailable() {
    }

    public int getLteOnCdmaMode() {
        return TelephonyManager.getLteOnCdmaModeStatic();
    }

    public void registerForCellInfoList(Handler h, int what, Object obj) {
        this.mRilCellInfoListRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForCellInfoList(Handler h) {
        this.mRilCellInfoListRegistrants.remove(h);
    }

    public void registerForSrvccStateChanged(Handler h, int what, Object obj) {
        this.mSrvccStateRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForSrvccStateChanged(Handler h) {
        this.mSrvccStateRegistrants.remove(h);
    }

    public void registerForCsFallback(Handler h, int what, Object obj) {
        this.mCsFallbackRegistant = new Registrant(h, what, obj);
    }

    public void unregisterForCsFallback(Handler h) {
        this.mCsFallbackRegistant.clear();
    }

    public void registerForModifyCall(Handler h, int what, Object obj) {
        this.mModifyCallRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForModifyCall(Handler h) {
        this.mModifyCallRegistrants.remove(h);
    }

    public void registerForVoiceSystemIdNotified(Handler h, int what, Object obj) {
        this.mVoiceSystemIdRegistrant = new Registrant(h, what, obj);
    }

    public void unregisterForVoiceSystemIdNotified(Handler h) {
        this.mVoiceSystemIdRegistrant.clear();
    }

    public void setOnSmsDeviceReady(Handler h, int what, Object obj) {
        this.mSmsDeviceReadyRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSmsDeviceReady(Handler h) {
        this.mSmsDeviceReadyRegistrant.clear();
    }

    public void testingEmergencyCall() {
    }

    public int getRilVersion() {
        return this.mRilVersion;
    }

    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message response) {
    }

    public void setDataAllowed(boolean allowed, Message response) {
    }

    public void requestShutdown(Message result) {
    }

    public void setOnReleaseCompleteMessageRegistrant(Handler h, int what, Object obj) {
        this.mReleaseCompleteMessageRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnReleaseCompleteMessageRegistrant(Handler h) {
        this.mReleaseCompleteMessageRegistrant.clear();
    }

    public void setOnSendDTMFResult(Handler h, int what, Object obj) {
        this.mSendDTMFResultRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSendDTMFResult(Handler h) {
        this.mSendDTMFResultRegistrant.clear();
    }

    public void setOnCatSendSmsResult(Handler h, int what, Object obj) {
        this.mCatSendSmsResultRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatSendSmsResult(Handler h) {
        this.mCatSendSmsResultRegistrant.clear();
    }

    public void setOnCatCallControlResult(Handler h, int what, Object obj) {
        this.mCatCallControlResultRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatCallControlResult(Handler h) {
        this.mCatCallControlResultRegistrant.clear();
    }

    public void registerForHomeNetwork(Handler h, int what, Object obj) {
        this.mHomeNetworkRegistant = new Registrant(h, what, obj);
    }

    public void unregisterForHomeNetwork(Handler h) {
        this.mHomeNetworkRegistant.clear();
    }

    public void setOnStkCallStatusResult(Handler h, int what, Object obj) {
        this.mStkSetupCallStatus = new Registrant(h, what, obj);
    }

    public void unSetOnStkCallStatusResult(Handler h, int what, Object obj) {
        this.mStkSetupCallStatus.clear();
    }

    public void registerForImsRegistrationStateChanged(Handler h, int what, Object obj) {
        this.mImsRegistrationStateChangedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForImsRegistrationStateChanged(Handler h) {
        this.mImsRegistrationStateChangedRegistrants.remove(h);
    }

    public void registerForImsRetryOver(Handler h, int what, Object obj) {
        this.mImsRegistrationRetryOver.add(new Registrant(h, what, obj));
    }

    public void unregisterForImsImsRetryOver(Handler h) {
        this.mImsRegistrationRetryOver.remove(h);
    }

    public void setOnSS(Handler h, int what, Object obj) {
        this.mSSRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSS(Handler h) {
        this.mSSRegistrant.clear();
    }

    public void setOnPbInitComplete(Handler h, int what, Object obj) {
        this.mPbInitCompleteRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnPbInitComplete(Handler h) {
        this.mPbInitCompleteRegistrant.clear();
    }

    public void setOnSimPbReady(Handler h, int what, Object obj) {
        this.mSimPbReadyRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnSimPbReady(Handler h) {
        this.mSimPbReadyRegistrant.clear();
    }

    public void registerForSap(Handler h, int what, Object obj) {
        this.mSapRegistant = new Registrant(h, what, obj);
    }

    public void unregisterForSap(Handler h) {
        this.mSapRegistant.clear();
    }
}
