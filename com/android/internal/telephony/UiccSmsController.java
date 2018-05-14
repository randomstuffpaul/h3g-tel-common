package com.android.internal.telephony;

import android.app.PendingIntent;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.ISms.Stub;
import java.util.List;

public class UiccSmsController extends Stub {
    static final String LOG_TAG = "RIL_UiccSmsController";
    protected Phone[] mPhone;

    protected UiccSmsController(Phone[] phone) {
        this.mPhone = phone;
        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    public boolean updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) throws RemoteException {
        return updateMessageOnIccEfForSubscriber(getPreferredSmsSubscription(), callingPackage, index, status, pdu);
    }

    public boolean updateMessageOnIccEfForSubscriber(long subId, String callingPackage, int index, int status, byte[] pdu) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.updateMessageOnIccEf(callingPackage, index, status, pdu);
        }
        Rlog.e(LOG_TAG, "updateMessageOnIccEf iccSmsIntMgr is null for Subscription: " + subId);
        return false;
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc) throws RemoteException {
        return copyMessageToIccEfForSubscriber(getPreferredSmsSubscription(), callingPackage, status, pdu, smsc);
    }

    public boolean copyMessageToIccEfForSubscriber(long subId, String callingPackage, int status, byte[] pdu, byte[] smsc) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.copyMessageToIccEf(callingPackage, status, pdu, smsc);
        }
        Rlog.e(LOG_TAG, "copyMessageToIccEf iccSmsIntMgr is null for Subscription: " + subId);
        return false;
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage) throws RemoteException {
        return getAllMessagesFromIccEfForSubscriber(getPreferredSmsSubscription(), callingPackage);
    }

    public List<SmsRawData> getAllMessagesFromIccEfForSubscriber(long subId, String callingPackage) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getAllMessagesFromIccEf(callingPackage);
        }
        Rlog.e(LOG_TAG, "getAllMessagesFromIccEf iccSmsIntMgr is null for Subscription: " + subId);
        return null;
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendDataForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
    }

    public void sendDataForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendData(callingPackage, destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent);
    }

    public void sendTextForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendText(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) throws RemoteException {
        sendMultipartTextForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, parts, sentIntents, deliveryIntents);
    }

    public void sendMultipartTextForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendMultipartText(callingPackage, destAddr, scAddr, parts, sentIntents, deliveryIntents);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartText iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public boolean enableCellBroadcast(int messageIdentifier) throws RemoteException {
        return enableCellBroadcastForSubscriber(getPreferredSmsSubscription(), messageIdentifier);
    }

    public boolean enableCellBroadcastForSubscriber(long subId, int messageIdentifier) throws RemoteException {
        return enableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) throws RemoteException {
        return enableCellBroadcastRangeForSubscriber(getPreferredSmsSubscription(), startMessageId, endMessageId);
    }

    public boolean enableCellBroadcastRangeForSubscriber(long subId, int startMessageId, int endMessageId) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.enableCellBroadcastRange(startMessageId, endMessageId);
        }
        Rlog.e(LOG_TAG, "enableCellBroadcast iccSmsIntMgr is null for Subscription: " + subId);
        return false;
    }

    public boolean disableCellBroadcast(int messageIdentifier) throws RemoteException {
        return disableCellBroadcastForSubscriber(getPreferredSmsSubscription(), messageIdentifier);
    }

    public boolean disableCellBroadcastForSubscriber(long subId, int messageIdentifier) throws RemoteException {
        return disableCellBroadcastRangeForSubscriber(subId, messageIdentifier, messageIdentifier);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) throws RemoteException {
        return disableCellBroadcastRangeForSubscriber(getPreferredSmsSubscription(), startMessageId, endMessageId);
    }

    public boolean disableCellBroadcastRangeForSubscriber(long subId, int startMessageId, int endMessageId) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.disableCellBroadcastRange(startMessageId, endMessageId);
        }
        Rlog.e(LOG_TAG, "disableCellBroadcast iccSmsIntMgr is null for Subscription:" + subId);
        return false;
    }

    public int getPremiumSmsPermission(String packageName) {
        return getPremiumSmsPermissionForSubscriber(getPreferredSmsSubscription(), packageName);
    }

    public int getPremiumSmsPermissionForSubscriber(long subId, String packageName) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getPremiumSmsPermission(packageName);
        }
        Rlog.e(LOG_TAG, "getPremiumSmsPermission iccSmsIntMgr is null");
        return 0;
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        setPremiumSmsPermissionForSubscriber(getPreferredSmsSubscription(), packageName, permission);
    }

    public void setPremiumSmsPermissionForSubscriber(long subId, String packageName, int permission) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.setPremiumSmsPermission(packageName, permission);
        } else {
            Rlog.e(LOG_TAG, "setPremiumSmsPermission iccSmsIntMgr is null");
        }
    }

    public boolean isImsSmsSupported() {
        return isImsSmsSupportedForSubscriber(getPreferredSmsSubscription());
    }

    public boolean isImsSmsSupportedForSubscriber(long subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.isImsSmsSupported();
        }
        Rlog.e(LOG_TAG, "isImsSmsSupported iccSmsIntMgr is null");
        return false;
    }

    public String getImsSmsFormat() {
        return getImsSmsFormatForSubscriber(getPreferredSmsSubscription());
    }

    public String getImsSmsFormatForSubscriber(long subId) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getImsSmsFormat();
        }
        Rlog.e(LOG_TAG, "getImsSmsFormat iccSmsIntMgr is null");
        return null;
    }

    public void updateSmsSendStatus(int messageRef, boolean success) {
        getIccSmsInterfaceManager(SubscriptionManager.getDefaultSmsSubId()).updateSmsSendStatus(messageRef, success);
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        injectSmsPdu(SubscriptionManager.getDefaultSmsSubId(), pdu, format, receivedIntent);
    }

    public void injectSmsPdu(long subId, byte[] pdu, String format, PendingIntent receivedIntent) {
        getIccSmsInterfaceManager(subId).injectSmsPdu(pdu, format, receivedIntent);
    }

    private IccSmsInterfaceManager getIccSmsInterfaceManager(long subId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId) || phoneId == 0) {
            phoneId = 0;
        }
        try {
            return ((PhoneProxy) this.mPhone[phoneId]).getIccSmsInterfaceManager();
        } catch (NullPointerException e) {
            Rlog.e(LOG_TAG, "Exception is :" + e.toString() + " For subscription :" + subId);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e2) {
            Rlog.e(LOG_TAG, "Exception is :" + e2.toString() + " For subscription :" + subId);
            e2.printStackTrace();
            return null;
        }
    }

    public long getPreferredSmsSubscription() {
        return SubscriptionManager.getDefaultSmsSubId();
    }

    public boolean isSMSPromptEnabled() {
        return PhoneFactory.isSMSPromptEnabled();
    }

    public void sendStoredText(long subId, String callingPkg, Uri messageUri, String scAddress, PendingIntent sentIntent, PendingIntent deliveryIntent) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendStoredText(callingPkg, messageUri, scAddress, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendStoredText iccSmsIntMgr is null for subscription: " + subId);
        }
    }

    public void sendStoredMultipartText(long subId, String callingPkg, Uri messageUri, String scAddress, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendStoredMultipartText(callingPkg, messageUri, scAddress, sentIntents, deliveryIntents);
        } else {
            Rlog.e(LOG_TAG, "sendStoredMultipartText iccSmsIntMgr is null for subscription: " + subId);
        }
    }

    public boolean useLte3GPPSms() {
        return false;
    }

    public boolean getSMSAvailable() {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getSMSAvailable();
        }
        Rlog.e(LOG_TAG, "getSMSAvailable iccSmsIntMgr is null");
        return false;
    }

    public boolean getSMSPAvailable() {
        return true;
    }

    public boolean getSimFullStatus() {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getSimFullStatus();
        }
        Rlog.e(LOG_TAG, "getSimFullStatus iccSmsIntMgr is null");
        return false;
    }

    public void sendscptResult(String destAddr, int noOfOccur, int scptCategory, int scptLanguage, int scptCategoryResult, PendingIntent sentIntent, PendingIntent deliveryIntent) {
    }

    public byte[] getCbSettings() {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getCbSettings();
        }
        Rlog.e(LOG_TAG, "getCbSettings iccSmsIntMgr is null");
        return null;
    }

    public void resetSimFullStatus() {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.resetSimFullStatus();
        } else {
            Rlog.e(LOG_TAG, "resetSimFullStatus iccSmsIntMgr is null");
        }
    }

    public void setCDMASmsReassembly(boolean onOff) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.setCDMASmsReassembly(onOff);
        } else {
            Rlog.d(LOG_TAG, "setCDMASmsReassembly iccSmsIntMgr is null");
        }
    }

    public String getSmsc() {
        long subId = getPreferredSmsSubscription();
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.getSmsc();
        }
        Rlog.e(LOG_TAG, "getSmsc in iccSmsIntMgr is null for subscription: " + subId);
        return null;
    }

    public void sendRawPduSat(byte[] smsc, byte[] pdu, String format, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendRawPduSat(smsc, pdu, format, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendRawPduSat iccSmsIntMgr is null");
        }
    }

    public boolean getToddlerMode() {
        return false;
    }

    public boolean enableCdmaBroadcast(int messageIdentifier) {
        return false;
    }

    public boolean disableCdmaBroadcast(int messageIdentifier) {
        return false;
    }

    public boolean enableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        return true;
    }

    public boolean disableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        return false;
    }

    public void sendMultipartTextwithCBP(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendMultipartTextwithCBP(callingPackage, destAddr, scAddr, parts, sentIntents, deliveryIntents, callbackNumber, priority);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + getPreferredSmsSubscription());
        }
    }

    public void sendTextKdi(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean is7bitAlphabet) {
    }

    public void sendTextWithPriority(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority) {
    }

    public void sendOTADomestic(String callingPackage, String destinationAddress, String scAddress, String text) {
        sendOTADomesticForSubscriber(getPreferredSmsSubscription(), callingPackage, destinationAddress, scAddress, text);
    }

    public void sendOTADomesticForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, String text) {
        Rlog.d(LOG_TAG, "sendOTADomesticForSubscriber in UiccSmsController");
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendOTADomestic(callingPackage, destAddr, scAddr, text);
        } else {
            Rlog.e(LOG_TAG, "sendOTADomesticForSubscriber iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendTextwithOptions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType) {
        sendTextwithOptionsForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType);
    }

    public void sendTextwithOptionsForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType) {
        Rlog.d(LOG_TAG, "sendTextwithOptionsForSubscriber in UiccSmsController with options");
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextwithOptions(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType);
        } else {
            Rlog.e(LOG_TAG, "sendTextwithOptions iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendMultipartTextwithOptions(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, boolean replyPath, int expiry, int serviceType, int encodingType) throws RemoteException {
        sendMultipartTextwithOptionsForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, parts, sentIntents, deliveryIntents, replyPath, expiry, serviceType, encodingType);
    }

    public void sendMultipartTextwithOptionsForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, boolean replyPath, int expiry, int serviceType, int encodingType) throws RemoteException {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendMultipartTextwithOptions(callingPackage, destAddr, scAddr, parts, sentIntents, deliveryIntents, replyPath, expiry, serviceType, encodingType);
        } else {
            Rlog.e(LOG_TAG, "sendMultipartText iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendTextwithCBP(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextwithCBP(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, callbackNumber, priority);
        } else {
            Rlog.e(LOG_TAG, "sendText iccSmsIntMgr is null for Subscription: " + getPreferredSmsSubscription());
        }
    }

    public void sendDatawithOrigPort(String callingPackage, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendDatawithOrigPortForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
    }

    public void sendDatawithOrigPortForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendDatawithOrigPort(callingPackage, destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        } else {
            Rlog.e(LOG_TAG, "sendDatawithOrigPort iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendTextwithOptionsReadconfirm(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        sendTextwithOptionsReadconfirmForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType, confirmId);
    }

    public void sendTextwithOptionsReadconfirmForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(LOG_TAG, "sendTextwithOptionsForSubscriber in UiccSmsController with confirmId");
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextwithOptionsReadconfirm(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType, confirmId);
        } else {
            Rlog.e(LOG_TAG, "sendTextwithOptionsReadconfirm iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public void sendTextNSRI(String callingPackage, String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        sendTextNSRIForSubscriber(getPreferredSmsSubscription(), callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, msgCount, msgTotal);
    }

    public void sendTextNSRIForSubscriber(long subId, String callingPackage, String destAddr, String scAddr, byte[] text, PendingIntent sentIntent, PendingIntent deliveryIntent, int msgCount, int msgTotal) {
        Rlog.d(LOG_TAG, "sendTextNSRIForSubscriber in UiccSmsController");
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(subId);
        if (iccSmsIntMgr != null) {
            iccSmsIntMgr.sendTextNSRI(callingPackage, destAddr, scAddr, text, sentIntent, deliveryIntent, msgCount, msgTotal);
        } else {
            Rlog.e(LOG_TAG, "sendTextNSRIForSubscriber iccSmsIntMgr is null for Subscription: " + subId);
        }
    }

    public boolean updateSmsServiceCenterOnSimEf(byte[] smsc) {
        IccSmsInterfaceManager iccSmsIntMgr = getIccSmsInterfaceManager(getPreferredSmsSubscription());
        if (iccSmsIntMgr != null) {
            return iccSmsIntMgr.updateSmsServiceCenterOnSimEf(smsc);
        }
        Rlog.e(LOG_TAG, "updateSmsServiceCenterOnSimEf iccSmsIntMgr is null for Subscription: " + getPreferredSmsSubscription());
        return false;
    }
}
