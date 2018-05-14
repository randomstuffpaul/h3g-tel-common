package com.android.internal.telephony;

import android.content.Context;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UsimServiceTable;
import java.util.List;
import java.util.Map;

public interface Phone {
    public static final int BM_AUS2_BAND = 5;
    public static final int BM_AUS_BAND = 4;
    public static final int BM_BOUNDARY = 6;
    public static final int BM_EURO_BAND = 1;
    public static final int BM_JPN_BAND = 3;
    public static final int BM_UNSPECIFIED = 0;
    public static final int BM_US_BAND = 2;
    public static final int CDMA_OTA_PROVISION_STATUS_A_KEY_EXCHANGED = 2;
    public static final int CDMA_OTA_PROVISION_STATUS_COMMITTED = 8;
    public static final int CDMA_OTA_PROVISION_STATUS_IMSI_DOWNLOADED = 6;
    public static final int CDMA_OTA_PROVISION_STATUS_MDN_DOWNLOADED = 5;
    public static final int CDMA_OTA_PROVISION_STATUS_NAM_DOWNLOADED = 4;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_ABORTED = 11;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_COMMITTED = 12;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_STARTED = 9;
    public static final int CDMA_OTA_PROVISION_STATUS_OTAPA_STOPPED = 10;
    public static final int CDMA_OTA_PROVISION_STATUS_PRL_DOWNLOADED = 7;
    public static final int CDMA_OTA_PROVISION_STATUS_SPC_RETRIES_EXCEEDED = 1;
    public static final int CDMA_OTA_PROVISION_STATUS_SPL_UNLOCKED = 0;
    public static final int CDMA_OTA_PROVISION_STATUS_SSD_UPDATED = 3;
    public static final int CDMA_RM_AFFILIATED = 1;
    public static final int CDMA_RM_ANY = 2;
    public static final int CDMA_RM_AUTOMATIC_A = 3;
    public static final int CDMA_RM_AUTOMATIC_B = 4;
    public static final int CDMA_RM_BLANK = 8;
    public static final int CDMA_RM_DOMESTIC = 5;
    public static final int CDMA_RM_DOMESTIC_INTERNATIONAL = 7;
    public static final int CDMA_RM_HOME = 0;
    public static final int CDMA_RM_INTERNATIONAL = 6;
    public static final int CDMA_SUBSCRIPTION_NV = 1;
    public static final int CDMA_SUBSCRIPTION_RUIM_SIM = 0;
    public static final int CDMA_SUBSCRIPTION_UNKNOWN = -1;
    public static final boolean DEBUG_PHONE = true;
    public static final String EMERGENCY_CALL_STATUS_KEY = "emergencyCallOngoing";
    public static final String FEATURE_ENABLE_BIP = "enableBIP";
    public static final String FEATURE_ENABLE_CBS = "enableCBS";
    public static final String FEATURE_ENABLE_CMDM = "enableCMDM";
    public static final String FEATURE_ENABLE_CMMAIL = "enableCMMAIL";
    public static final String FEATURE_ENABLE_DUN = "enableDUN";
    public static final String FEATURE_ENABLE_DUN_ALWAYS = "enableDUNAlways";
    public static final String FEATURE_ENABLE_EMERGENCY = "enableEmergency";
    public static final String FEATURE_ENABLE_ENT1 = "enableENT1";
    public static final String FEATURE_ENABLE_ENT2 = "enableENT2";
    public static final String FEATURE_ENABLE_FOTA = "enableFOTA";
    public static final String FEATURE_ENABLE_HIPRI = "enableHIPRI";
    public static final String FEATURE_ENABLE_IMS = "enableIMS";
    public static final String FEATURE_ENABLE_MMS = "enableMMS";
    public static final String FEATURE_ENABLE_MMS2 = "enableMMS2";
    public static final String FEATURE_ENABLE_SUPL = "enableSUPL";
    public static final String FEATURE_ENABLE_VZW800 = "enable800APN";
    public static final String FEATURE_ENABLE_WAP = "enableWAP";
    public static final String FEATURE_ENABLE_XCAP = "enableXCAP";
    public static final int NT_MODE_CDMA = 4;
    public static final int NT_MODE_CDMA_NO_EVDO = 5;
    public static final int NT_MODE_EVDO_NO_CDMA = 6;
    public static final int NT_MODE_GLOBAL = 7;
    public static final int NT_MODE_GSM_ONLY = 1;
    public static final int NT_MODE_GSM_UMTS = 3;
    public static final int NT_MODE_LTE_CDMA_AND_EVDO = 8;
    public static final int NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10;
    public static final int NT_MODE_LTE_GSM_WCDMA = 9;
    public static final int NT_MODE_LTE_ONLY = 11;
    public static final int NT_MODE_LTE_WCDMA = 12;
    public static final int NT_MODE_TD_SCDMA_CDMA_EVDO_GSM_WCDMA = 21;
    public static final int NT_MODE_TD_SCDMA_GSM = 16;
    public static final int NT_MODE_TD_SCDMA_GSM_LTE = 17;
    public static final int NT_MODE_TD_SCDMA_GSM_WCDMA = 18;
    public static final int NT_MODE_TD_SCDMA_GSM_WCDMA_LTE = 20;
    public static final int NT_MODE_TD_SCDMA_LTE = 15;
    public static final int NT_MODE_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA = 22;
    public static final int NT_MODE_TD_SCDMA_ONLY = 13;
    public static final int NT_MODE_TD_SCDMA_WCDMA = 14;
    public static final int NT_MODE_TD_SCDMA_WCDMA_LTE = 19;
    public static final int NT_MODE_WCDMA_ONLY = 2;
    public static final int NT_MODE_WCDMA_PREF = 0;
    public static final int PREFERRED_CDMA_SUBSCRIPTION = 1;
    public static final int PREFERRED_NT_MODE = RILConstants.PREFERRED_NETWORK_MODE;
    public static final String REASON_APN_CHANGED = "apnChanged";
    public static final String REASON_APN_FAILED = "apnFailed";
    public static final String REASON_APN_SWITCHED = "apnSwitched";
    public static final String REASON_CDMA_DATA_ATTACHED = "cdmaDataAttached";
    public static final String REASON_CDMA_DATA_DETACHED = "cdmaDataDetached";
    public static final String REASON_CDMA_OTA_PROVISIONED = "cdmaOtaProvisioned";
    public static final String REASON_CONNECTED = "connected";
    public static final String REASON_CSC_UPDATE_STATUS_CHANGED = "cscUpdateStatusChanged";
    public static final String REASON_DATA_ATTACHED = "dataAttached";
    public static final String REASON_DATA_DEPENDENCY_MET = "dependencyMet";
    public static final String REASON_DATA_DEPENDENCY_UNMET = "dependencyUnmet";
    public static final String REASON_DATA_DETACHED = "dataDetached";
    public static final String REASON_DATA_DISABLED = "dataDisabled";
    public static final String REASON_DATA_ENABLED = "dataEnabled";
    public static final String REASON_DATA_SPECIFIC_DISABLED = "specificDisabled";
    public static final String REASON_DATA_STATE_CHANGED = "dataStateChanged";
    public static final String REASON_LOST_DATA_CONNECTION = "lostDataConnection";
    public static final String REASON_LOST_MULTI_APN_SUPPORT = "LostMultiApnSupport";
    public static final String REASON_MULTI_APN_SUPPORT = "MultiApnSupport";
    public static final String REASON_NW_TYPE_CHANGED = "nwTypeChanged";
    public static final String REASON_PDP_RESET = "pdpReset";
    public static final String REASON_PLMN_CHANGED = "plmnChanged";
    public static final String REASON_PS_RESTRICT_DISABLED = "psRestrictDisabled";
    public static final String REASON_PS_RESTRICT_ENABLED = "psRestrictEnabled";
    public static final String REASON_RADIO_TURNED_OFF = "radioTurnedOff";
    public static final String REASON_RAT_CHANGED = "ratChanged";
    public static final String REASON_RESTORE_DEFAULT_APN = "restoreDefaultApn";
    public static final String REASON_ROAMING_OFF = "roamingOff";
    public static final String REASON_ROAMING_ON = "roamingOn";
    public static final String REASON_SIM_LOADED = "simLoaded";
    public static final String REASON_SINGLE_PDN_ARBITRATION = "SinglePdnArbitration";
    public static final String REASON_SLOT_SWITCHED = "slotSwitched";
    public static final String REASON_SLOT_SWITCHED_FOR_MMS = "slotSwitchedForMms";
    public static final String REASON_VOICE_CALL_ENDED = "2GVoiceCallEnded";
    public static final String REASON_VOICE_CALL_STARTED = "2GVoiceCallStarted";
    public static final int TTY_MODE_FULL = 1;
    public static final int TTY_MODE_HCO = 2;
    public static final int TTY_MODE_OFF = 0;
    public static final int TTY_MODE_VCO = 3;

    public enum DataActivityState {
        NONE,
        DATAIN,
        DATAOUT,
        DATAINANDOUT,
        DORMANT
    }

    public enum SuppService {
        UNKNOWN,
        SWITCH,
        SEPARATE,
        TRANSFER,
        CONFERENCE,
        REJECT,
        HANGUP
    }

    boolean IsDomesticRoaming();

    boolean IsInternationalRoaming();

    void SimSlotActivation(boolean z);

    void SimSlotOnOff(int i);

    void acceptCall(int i) throws CallStateException;

    void acceptConnectionTypeChange(Connection connection, Map<String, String> map) throws CallStateException;

    void acquireOwnershipOfImsPhone(ImsPhone imsPhone);

    void activateCellBroadcastSms(int i, Message message);

    boolean canConference();

    boolean canTransfer();

    boolean changeBarringPassword(String str, String str2, String str3, Message message);

    boolean changeBarringPassword(String str, String str2, String str3, String str4, Message message);

    void changeConnectionType(Message message, Connection connection, int i, Map<String, String> map) throws CallStateException;

    void clearDisconnected();

    void conference() throws CallStateException;

    Connection dial(String str, int i) throws CallStateException;

    Connection dial(String str, int i, int i2, int i3, String[] strArr) throws CallStateException;

    Connection dial(String str, UUSInfo uUSInfo, int i) throws CallStateException;

    Connection dial(String str, UUSInfo uUSInfo, int i, int i2, int i3, String[] strArr) throws CallStateException;

    void disableDnsCheck(boolean z);

    void disableLocationUpdates();

    void dispose();

    void enableEnhancedVoicePrivacy(boolean z, Message message);

    void enableLocationUpdates();

    void exitEmergencyCallbackMode();

    void explicitCallTransfer() throws CallStateException;

    String getActiveApnHost(String str);

    String[] getActiveApnTypes();

    List<CellInfo> getAllCellInfo();

    void getAvailableNetworks(Message message);

    Call getBackgroundCall();

    void getCallBarringOption(String str, Message message);

    void getCallBarringOption(String str, String str2, int i, Message message);

    boolean getCallForwardingIndicator();

    void getCallForwardingOption(int i, Message message);

    void getCallForwardingOption(int i, String str, int i2, Message message);

    void getCallWaiting(Message message);

    CatService getCatService();

    int getCdmaEriIconIndex();

    int getCdmaEriIconMode();

    String getCdmaEriText();

    String getCdmaMin();

    String getCdmaPrlVersion();

    void getCellBroadcastSmsConfig(Message message);

    CellLocation getCellLocation();

    Context getContext();

    DataActivityState getDataActivityState();

    void getDataCallList(Message message);

    DataState getDataConnectionState();

    DataState getDataConnectionState(String str);

    boolean getDataEnabled();

    boolean getDataRoamingEnabled();

    int getDataServiceState();

    String getDeviceId();

    String getDeviceSvn();

    int getDrxValue();

    boolean getDualSimSlotActivationState();

    void getEnhancedVoicePrivacy(Message message);

    String getEsn();

    boolean getFDNavailable();

    Call getForegroundCall();

    String getGroupIdLevel1();

    String getHandsetInfo(String str);

    IccCard getIccCard();

    IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager();

    boolean getIccRecordsLoaded();

    String getIccSerialNumber();

    String getImei();

    Phone getImsPhone();

    int getImsRegisteredFeature();

    int getImsRegisteredFeature(int i);

    IsimRecords getIsimRecords();

    LegacyIms getLegacyIms();

    String getLine1AlphaTag();

    String getLine1Number();

    String getLine1NumberType(int i);

    LinkProperties getLinkProperties(String str);

    int getLteOnCdmaMode();

    String getMeid();

    boolean getMessageWaitingIndicator();

    String getMsisdn();

    boolean getMute();

    void getNeighboringCids(Message message);

    NetworkCapabilities getNetworkCapabilities(String str);

    boolean getOCSGLAvailable();

    void getOutgoingCallerIdDisplay(Message message);

    String[] getPcscfAddress(String str);

    List<? extends MmiCode> getPendingMmiCodes();

    int getPhoneId();

    String getPhoneName();

    PhoneSubInfo getPhoneSubInfo();

    int getPhoneType();

    void getPreferredNetworkList(Message message);

    void getPreferredNetworkType(Message message);

    int getProposedConnectionType(Connection connection) throws CallStateException;

    byte[] getPsismsc();

    int getReducedCycleTime();

    Call getRingingCall();

    String getRuimid();

    boolean getSMSPavailable();

    boolean getSMSavailable();

    String getSelectedApn();

    ServiceState getServiceState();

    SignalStrength getSignalStrength();

    SimulatedRadioControl getSimulatedRadioControl();

    String getSktImsiM();

    String getSktIrm();

    void getSmscAddress(Message message);

    String[] getSponImsi();

    State getState();

    long getSubId();

    String getSubscriberId();

    String getSubscriberIdType(int i);

    UiccCard getUiccCard();

    boolean getUnitTestMode();

    UsimServiceTable getUsimServiceTable();

    String getVoiceMailAlphaTag();

    String getVoiceMailNumber();

    int getVoiceMessageCount();

    int getVoicePhoneServiceState();

    boolean handleInCallMmiCommands(String str) throws CallStateException;

    boolean handlePinMmi(String str);

    boolean hasCall(String str);

    boolean hasIsim();

    void holdCall() throws CallStateException;

    void invokeOemRilRequestRaw(byte[] bArr, Message message);

    void invokeOemRilRequestStrings(String[] strArr, Message message);

    boolean isApnTypeAvailable(String str);

    boolean isCspPlmnEnabled();

    boolean isDataConnectivityPossible();

    boolean isDataConnectivityPossible(String str);

    boolean isDnsCheckDisabled();

    boolean isImsRegistered();

    boolean isMinInfoReady();

    boolean isOtaSpNumber(String str);

    boolean isRadioAvailable();

    boolean isVolteRegistered();

    boolean isWfcRegistered();

    boolean needsOtaServiceProvisioning();

    void notifyDataActivity();

    void notifyDataConnectionStateChanged(int i, String str, String str2);

    void nvReadItem(int i, Message message);

    void nvResetConfig(int i, Message message);

    void nvWriteCdmaPrl(byte[] bArr, Message message);

    void nvWriteItem(int i, String str, Message message);

    void queryAvailableBandMode(Message message);

    void queryCdmaRoamingPreference(Message message);

    void queryTTYMode(Message message);

    void registerFoT53ClirlInfo(Handler handler, int i, Object obj);

    void registerForCallWaiting(Handler handler, int i, Object obj);

    void registerForCdmaOtaStatusChange(Handler handler, int i, Object obj);

    void registerForDataConnectionStateChanged(Handler handler, int i, Object obj);

    void registerForDisconnect(Handler handler, int i, Object obj);

    void registerForDisplayInfo(Handler handler, int i, Object obj);

    void registerForEcmTimerReset(Handler handler, int i, Object obj);

    void registerForHandoverStateChanged(Handler handler, int i, Object obj);

    void registerForInCallVoicePrivacyOff(Handler handler, int i, Object obj);

    void registerForInCallVoicePrivacyOn(Handler handler, int i, Object obj);

    void registerForIncomingRing(Handler handler, int i, Object obj);

    void registerForLineControlInfo(Handler handler, int i, Object obj);

    void registerForMmiComplete(Handler handler, int i, Object obj);

    void registerForMmiInitiate(Handler handler, int i, Object obj);

    void registerForModifyCallRequest(Handler handler, int i, Object obj);

    void registerForNewRingingConnection(Handler handler, int i, Object obj);

    void registerForNumberInfo(Handler handler, int i, Object obj);

    void registerForOnHoldTone(Handler handler, int i, Object obj);

    void registerForPreciseCallStateChanged(Handler handler, int i, Object obj);

    void registerForRedirectedNumberInfo(Handler handler, int i, Object obj);

    void registerForResendIncallMute(Handler handler, int i, Object obj);

    void registerForRingbackTone(Handler handler, int i, Object obj);

    void registerForServiceStateChanged(Handler handler, int i, Object obj);

    void registerForSignalInfo(Handler handler, int i, Object obj);

    void registerForSimRecordsLoaded(Handler handler, int i, Object obj);

    void registerForSubscriptionInfoReady(Handler handler, int i, Object obj);

    void registerForSuppServiceFailed(Handler handler, int i, Object obj);

    void registerForSuppServiceNotification(Handler handler, int i, Object obj);

    void registerForT53AudioControlInfo(Handler handler, int i, Object obj);

    void registerForUnknownConnection(Handler handler, int i, Object obj);

    void rejectCall() throws CallStateException;

    void rejectConnectionTypeChange(Connection connection) throws CallStateException;

    ImsPhone relinquishOwnershipOfImsPhone();

    void removeReferences();

    void selectCsg(Message message);

    void selectNetworkManually(OperatorInfo operatorInfo, Message message);

    void sendBurstDtmf(String str, int i, int i2, Message message);

    void sendDtmf(char c);

    void sendUssdResponse(String str);

    void setBandMode(int i, Message message);

    boolean setCallBarringOption(boolean z, String str, String str2, int i, Message message);

    boolean setCallBarringOption(boolean z, String str, String str2, Message message);

    void setCallForwardingOption(int i, int i2, String str, int i3, int i4, Message message);

    void setCallForwardingOption(int i, int i2, String str, int i3, Message message);

    void setCallWaiting(boolean z, Message message);

    void setCdmaRoamingPreference(int i, Message message);

    void setCdmaSubscription(int i, Message message);

    void setCellBroadcastSmsConfig(int[] iArr, Message message);

    void setCellInfoListRate(int i);

    void setDataEnabled(boolean z);

    void setDataRoamingEnabled(boolean z);

    boolean setDmCmdInfo(int i, byte[] bArr);

    boolean setDrxMode(int i);

    void setEchoSuppressionEnabled();

    void setGbaBootstrappingParams(byte[] bArr, String str, String str2, Message message);

    void setImsRegistrationState(boolean z);

    void setLine1Number(String str, String str2, Message message);

    void setLteBandMode(int i, Message message);

    void setMute(boolean z);

    void setNetworkSelectionModeAutomatic(Message message);

    void setOnEcbModeExitResponse(Handler handler, int i, Object obj);

    void setOnPostDialCharacter(Handler handler, int i, Object obj);

    boolean setOperatorBrandOverride(String str);

    void setOutgoingCallerIdDisplay(int i, Message message);

    void setPreferredNetworkList(int i, String str, String str2, int i2, int i3, int i4, int i5, Message message);

    void setPreferredNetworkType(int i, Message message);

    void setRadioPower(boolean z);

    boolean setReducedCycleTime(int i);

    void setSelectedApn();

    void setSmscAddress(String str, Message message);

    void setTTYMode(int i, Message message);

    void setTransmitPower(int i, Message message);

    void setUnitTestMode(boolean z);

    void setVoiceMailNumber(String str, String str2, Message message);

    void setVoiceMessageWaiting(int i, int i2);

    void shutdownRadio();

    void startDtmf(char c);

    void startGlobalNetworkSearchTimer();

    void startGlobalNoSvcChkTimer();

    void stopDtmf();

    void stopGlobalNetworkSearchTimer();

    void stopGlobalNoSvcChkTimer();

    void switchHoldingAndActive() throws CallStateException;

    void unregisterForCallWaiting(Handler handler);

    void unregisterForCdmaOtaStatusChange(Handler handler);

    void unregisterForDataConnectionStateChanged(Handler handler);

    void unregisterForDisconnect(Handler handler);

    void unregisterForDisplayInfo(Handler handler);

    void unregisterForEcmTimerReset(Handler handler);

    void unregisterForHandoverStateChanged(Handler handler);

    void unregisterForInCallVoicePrivacyOff(Handler handler);

    void unregisterForInCallVoicePrivacyOn(Handler handler);

    void unregisterForIncomingRing(Handler handler);

    void unregisterForLineControlInfo(Handler handler);

    void unregisterForMmiComplete(Handler handler);

    void unregisterForMmiInitiate(Handler handler);

    void unregisterForModifyCallRequest(Handler handler);

    void unregisterForNewRingingConnection(Handler handler);

    void unregisterForNumberInfo(Handler handler);

    void unregisterForOnHoldTone(Handler handler);

    void unregisterForPreciseCallStateChanged(Handler handler);

    void unregisterForRedirectedNumberInfo(Handler handler);

    void unregisterForResendIncallMute(Handler handler);

    void unregisterForRingbackTone(Handler handler);

    void unregisterForServiceStateChanged(Handler handler);

    void unregisterForSignalInfo(Handler handler);

    void unregisterForSimRecordsLoaded(Handler handler);

    void unregisterForSubscriptionInfoReady(Handler handler);

    void unregisterForSuppServiceFailed(Handler handler);

    void unregisterForSuppServiceNotification(Handler handler);

    void unregisterForT53AudioControlInfo(Handler handler);

    void unregisterForT53ClirInfo(Handler handler);

    void unregisterForUnknownConnection(Handler handler);

    void unsetOnEcbModeExitResponse(Handler handler);

    void updatePhoneObject(int i);

    void updateServiceLocation();
}
