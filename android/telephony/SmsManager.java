package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.Mms.Intents;
import android.telephony.SmsMessage.DeliverPdu;
import android.telephony.SmsMessage.SubmitPdu;
import android.telephony.gsm.CbConfig;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import com.android.internal.telephony.IMms;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.ISms.Stub;
import com.android.internal.telephony.SmsRawData;
import com.google.android.mms.pdu.PduHeaders;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SmsManager {
    protected static final int COPY_TO_SIM_FAIL = 1;
    protected static final int COPY_TO_SIM_NOT_AVAILABLE = 2;
    protected static final int COPY_TO_SIM_SIM_FULL = 3;
    protected static final int COPY_TO_SIM_SUCCESS = 0;
    private static final int DEFAULT_SUB_ID = -1002;
    public static final String EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA";
    static final int GSM_SMS_FAIL_CAUSE_DSAC_FAIL = 214;
    public static final int ICC_TYPE_AUTO = -1;
    public static final int ICC_TYPE_CSIM = 4;
    public static final int ICC_TYPE_CSIM_DEACTIVE = 10;
    public static final int ICC_TYPE_ISIM = 5;
    public static final int ICC_TYPE_RUIM = 3;
    public static final int ICC_TYPE_SIM = 1;
    public static final int ICC_TYPE_UNKNOW = 0;
    public static final int ICC_TYPE_USIM = 2;
    private static final String LOG_TAG = "SmsManager";
    public static final String MESSAGE_STATUS_READ = "read";
    public static final String MESSAGE_STATUS_SEEN = "seen";
    public static final String MMS_CONFIG_ALIAS_ENABLED = "aliasEnabled";
    public static final String MMS_CONFIG_ALIAS_MAX_CHARS = "aliasMaxChars";
    public static final String MMS_CONFIG_ALIAS_MIN_CHARS = "aliasMinChars";
    public static final String MMS_CONFIG_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    public static final String MMS_CONFIG_APPEND_TRANSACTION_ID = "enabledTransID";
    public static final String MMS_CONFIG_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    public static final String MMS_CONFIG_GROUP_MMS_ENABLED = "enableGroupMms";
    public static final String MMS_CONFIG_HTTP_PARAMS = "httpParams";
    public static final String MMS_CONFIG_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    public static final String MMS_CONFIG_MAX_IMAGE_HEIGHT = "maxImageHeight";
    public static final String MMS_CONFIG_MAX_IMAGE_WIDTH = "maxImageWidth";
    public static final String MMS_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize";
    public static final String MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    public static final String MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    public static final String MMS_CONFIG_MMS_ENABLED = "enabledMMS";
    public static final String MMS_CONFIG_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    public static final String MMS_CONFIG_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    public static final String MMS_CONFIG_NAI_SUFFIX = "naiSuffix";
    public static final String MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    public static final String MMS_CONFIG_RECIPIENT_LIMIT = "recipientLimit";
    public static final String MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES = "sendMultipartSmsAsSeparateMessages";
    public static final String MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD = "smsToMmsTextLengthThreshold";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD = "smsToMmsTextThreshold";
    public static final String MMS_CONFIG_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    public static final String MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION = "supportMmsContentDisposition";
    public static final String MMS_CONFIG_UA_PROF_TAG_NAME = "uaProfTagName";
    public static final String MMS_CONFIG_UA_PROF_URL = "uaProfUrl";
    public static final String MMS_CONFIG_USER_AGENT = "userAgent";
    public static final int MMS_ERROR_CONFIGURATION_ERROR = 7;
    public static final int MMS_ERROR_HTTP_FAILURE = 4;
    public static final int MMS_ERROR_INVALID_APN = 2;
    public static final int MMS_ERROR_IO_ERROR = 5;
    public static final int MMS_ERROR_RETRY = 6;
    public static final int MMS_ERROR_UNABLE_CONNECT_MMS = 3;
    public static final int MMS_ERROR_UNSPECIFIED = 1;
    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    public static final int RESULT_ERROR_DSAC_FAILURE = 7;
    public static final int RESULT_ERROR_FDN_CHECK_FAILURE = 6;
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    public static final int RESULT_ERROR_LIMIT_EXCEEDED = 5;
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    public static final int RESULT_ERROR_NULL_PDU = 3;
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    public static final int SMS_TYPE_INCOMING = 0;
    public static final int SMS_TYPE_OUTGOING = 1;
    public static final int STATUS_ON_ICC_FREE = 0;
    public static final int STATUS_ON_ICC_READ = 1;
    public static final int STATUS_ON_ICC_SENT = 5;
    public static final int STATUS_ON_ICC_UNREAD = 3;
    public static final int STATUS_ON_ICC_UNSENT = 7;
    public static final int VALUE_INPUT_MODE_AUTO = 2;
    public static final int VALUE_INPUT_MODE_GSM7BIT = 0;
    public static final int VALUE_INPUT_MODE_UCS2 = 1;
    static int mMsgEncodingType = 0;
    private static final SmsManager sInstance = new SmsManager(-1002);
    private static final Object sLockObject = new Object();
    private static final Map<Long, SmsManager> sSubInstances = new ArrayMap();
    private long mSubId;

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (!TextUtils.isEmpty(text) || CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            try {
                if (MultiSimManager.getSimSlotCount() > 1) {
                    boolean isFromGear = false;
                    if (sentIntent != null) {
                        isFromGear = sentIntent.getIntent().getBooleanExtra("gear", false);
                    }
                    if (isFromGear) {
                        long[] mSubId = MultiSimManager.getSubId(MultiSimManager.getMultiSimPhoneId(1));
                        if (mSubId != null && mSubId.length > 0) {
                            MultiSimManager.setDefaultSubId(2, mSubId[0]);
                        }
                    }
                }
                getISmsServiceOrThrow().sendText(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent);
            } catch (RemoteException e) {
            }
        } else {
            throw new IllegalArgumentException("Invalid message body");
        }
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        if (format.equals(SmsMessage.FORMAT_3GPP) || format.equals(SmsMessage.FORMAT_3GPP2)) {
            try {
                ISms iccISms = Stub.asInterface(ServiceManager.getService("isms"));
                if (iccISms != null) {
                    iccISms.injectSmsPdu(pdu, format, receivedIntent);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid pdu format. format must be either 3gpp or 3gpp2");
    }

    public void updateSmsSendStatus(int messageRef, boolean success) {
        try {
            ISms iccISms = Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.updateSmsSendStatus(messageRef, success);
            }
        } catch (RemoteException e) {
        }
    }

    public ArrayList<String> divideMessage(String text) {
        if (text != null) {
            return SmsMessage.fragmentText(text, this);
        }
        throw new IllegalArgumentException("text is null");
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (parts == null) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() < 1 && !CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() > 1) {
            try {
                getISmsServiceOrThrow().sendMultipartText(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = (PendingIntent) sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, (String) parts.get(0), sentIntent, deliveryIntent);
        }
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, String callbackNumber, int priority) {
        Log.secD(LOG_TAG, "sendMultipartTextMessage in SmsManager with options.");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (parts == null) {
            throw new IllegalArgumentException("Invalid parts");
        } else if (parts.size() < 1 && !CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (getToddlerMode()) {
            throw new IllegalArgumentException("toddler mode on");
        } else if (parts.size() > 1) {
            try {
                ISms iccISms = getISmsServiceOrThrow();
                if (iccISms != null) {
                    iccISms.sendMultipartTextwithCBP(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, callbackNumber, priority);
                }
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = (PendingIntent) sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, (String) parts.get(0), sentIntent, deliveryIntent, callbackNumber, priority);
        }
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        } else {
            try {
                getISmsServiceOrThrow().sendData(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, data, sentIntent, deliveryIntent);
            } catch (RemoteException e) {
            }
        }
    }

    public static SmsManager getDefault() {
        return sInstance;
    }

    public static SmsManager getSmsManagerForSubscriber(long subId) {
        SmsManager smsManager;
        synchronized (sLockObject) {
            smsManager = (SmsManager) sSubInstances.get(Long.valueOf(subId));
            if (smsManager == null) {
                smsManager = new SmsManager(subId);
                sSubInstances.put(Long.valueOf(subId), smsManager);
            }
        }
        return smsManager;
    }

    private SmsManager(long subId) {
        this.mSubId = subId;
    }

    public long getSubId() {
        if (this.mSubId == -1002) {
            return getDefaultSmsSubId();
        }
        return this.mSubId;
    }

    private static ISms getISmsServiceOrThrow() {
        ISms iccISms = getISmsService();
        if (iccISms != null) {
            return iccISms;
        }
        throw new UnsupportedOperationException("Sms is not supported");
    }

    private static ISms getISmsService() {
        return Stub.asInterface(ServiceManager.getService("isms"));
    }

    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status) {
        boolean success = false;
        if (pdu == null) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(), status, pdu, smsc);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public boolean deleteMessageFromIcc(int messageIndex) {
        boolean success = false;
        byte[] pdu = new byte[PduHeaders.START];
        Arrays.fill(pdu, (byte) -1);
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, 0, pdu);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu) {
        boolean success = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, newStatus, pdu);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public static ArrayList<SmsMessage> getAllMessagesFromIcc() {
        List<SmsRawData> records = null;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName());
            }
        } catch (RemoteException e) {
        }
        return createMessageListFromRawRecords(records);
    }

    public ArrayList<SmsMessage> getAllMessagesFromIccSimType(int iccType) {
        List<SmsRawData> records = null;
        String format = SmsMessage.FORMAT_3GPP;
        String getFormat = getCurrentFormat();
        long subId = SubscriptionManager.getDefaultSmsSubId();
        if (iccType == -1 || iccType == 0) {
            try {
                TelephonyManager.getDefault();
                iccType = Integer.parseInt(TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", subId, ""));
            } catch (Exception e) {
                Rlog.d(LOG_TAG, "IccType is invalid");
                iccType = 0;
            }
        }
        if (iccType == 0) {
            Rlog.d(LOG_TAG, "IccType is Unknown");
            return createMessageListFromRawRecords(null, format);
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                if (iccType == 10) {
                }
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName());
            }
            TelephonyManager.isSelecttelecomDF = false;
        } catch (RemoteException e2) {
            Rlog.d(LOG_TAG, "getAllMessagesFromIccSimType - exception - iccType:" + iccType);
        } finally {
            TelephonyManager.isSelecttelecomDF = false;
        }
        if (iccType == 4) {
            format = getFormat;
        } else if (iccType == 10) {
            if (SmsMessage.FORMAT_3GPP.equals(getFormat)) {
                format = SmsMessage.FORMAT_3GPP2;
            }
        } else if (iccType == 3) {
            format = SmsMessage.FORMAT_3GPP2;
        }
        Rlog.d(LOG_TAG, "getAllMessagesFromIccSimType, subId = " + subId + " format = " + format + " iccType = " + iccType);
        return createMessageListFromRawRecords(records, format);
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        boolean success = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.enableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        boolean success = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.disableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        boolean success = false;
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.enableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        boolean success = false;
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.disableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records) {
        ArrayList<SmsMessage> messages = new ArrayList();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = (SmsRawData) records.get(i);
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i + 1, data.getBytes());
                    if (sms != null) {
                        messages.add(sms);
                    }
                }
            }
        }
        return messages;
    }

    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records, String format) {
        ArrayList<SmsMessage> messages = new ArrayList();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = (SmsRawData) records.get(i);
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i + 1, data.getBytes(), format);
                    messages.add(sms);
                    if (sms == null) {
                        Log.d(LOG_TAG, "createFromEfRecord NULL:" + format + "index:" + i);
                    }
                } else {
                    messages.add(null);
                }
            }
        }
        return messages;
    }

    public boolean isImsSmsSupported() {
        boolean boSupported = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                boSupported = iccISms.isImsSmsSupported();
            }
        } catch (RemoteException e) {
        }
        return boSupported;
    }

    boolean useLte3GPPSms() {
        boolean boSupported = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                boSupported = iccISms.useLte3GPPSms();
            }
        } catch (RemoteException e) {
        }
        return boSupported;
    }

    public String getImsSmsFormat() {
        String format = "unknown";
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                format = iccISms.getImsSmsFormat();
            }
        } catch (RemoteException e) {
        }
        return format;
    }

    public static long getDefaultSmsSubId() {
        long j = -1002;
        try {
            j = Stub.asInterface(ServiceManager.getService("isms")).getPreferredSmsSubscription();
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
        return j;
    }

    public boolean isSMSPromptEnabled() {
        boolean z = false;
        try {
            z = Stub.asInterface(ServiceManager.getService("isms")).isSMSPromptEnabled();
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
        return z;
    }

    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl, Bundle configOverrides, PendingIntent sentIntent) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                context.grantUriPermission(PHONE_PACKAGE_NAME, contentUri, 1);
                grantCarrierPackageUriPermission(context, contentUri, Intents.MMS_SEND_ACTION, 1);
                iMms.sendMessage(getSubId(), ActivityThread.currentPackageName(), contentUri, locationUrl, configOverrides, sentIntent);
            }
        } catch (RemoteException e) {
        }
    }

    private void grantCarrierPackageUriPermission(Context context, Uri contentUri, String action, int permission) {
        List<String> carrierPackages = ((TelephonyManager) context.getSystemService("phone")).getCarrierPackageNamesForIntent(new Intent(action));
        if (carrierPackages != null && carrierPackages.size() == 1) {
            context.grantUriPermission((String) carrierPackages.get(0), contentUri, permission);
        }
    }

    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri, Bundle configOverrides, PendingIntent downloadedIntent) {
        if (TextUtils.isEmpty(locationUrl)) {
            throw new IllegalArgumentException("Empty MMS location URL");
        } else if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        } else {
            try {
                IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
                if (iMms != null) {
                    context.grantUriPermission(PHONE_PACKAGE_NAME, contentUri, 2);
                    grantCarrierPackageUriPermission(context, contentUri, Intents.MMS_DOWNLOAD_ACTION, 2);
                    iMms.downloadMessage(getSubId(), ActivityThread.currentPackageName(), locationUrl, contentUri, configOverrides, downloadedIntent);
                }
            } catch (RemoteException e) {
            }
        }
    }

    public void updateMmsSendStatus(Context context, int messageRef, byte[] pdu, int status, Uri contentUri) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.updateMmsSendStatus(messageRef, pdu, status);
                if (contentUri != null) {
                    context.revokeUriPermission(contentUri, 1);
                }
            }
        } catch (RemoteException e) {
        }
    }

    public void updateMmsDownloadStatus(Context context, int messageRef, int status, Uri contentUri) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.updateMmsDownloadStatus(messageRef, status);
                if (contentUri != null) {
                    context.revokeUriPermission(contentUri, 2);
                }
            }
        } catch (RemoteException e) {
        }
    }

    public Uri importTextMessage(String address, int type, String text, long timestampMillis, boolean seen, boolean read) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.importTextMessage(ActivityThread.currentPackageName(), address, type, text, timestampMillis, seen, read);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public Uri importMultimediaMessage(Uri contentUri, String messageId, long timestampSecs, boolean seen, boolean read) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.importMultimediaMessage(ActivityThread.currentPackageName(), contentUri, messageId, timestampSecs, seen, read);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public boolean deleteStoredMessage(Uri messageUri) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.deleteStoredMessage(ActivityThread.currentPackageName(), messageUri);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean deleteStoredConversation(long conversationId) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.deleteStoredConversation(ActivityThread.currentPackageName(), conversationId);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean updateStoredMessageStatus(Uri messageUri, ContentValues statusValues) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.updateStoredMessageStatus(ActivityThread.currentPackageName(), messageUri, statusValues);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean archiveStoredConversation(long conversationId, boolean archived) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.archiveStoredConversation(ActivityThread.currentPackageName(), conversationId, archived);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public Uri addTextMessageDraft(String address, String text) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.addTextMessageDraft(ActivityThread.currentPackageName(), address, text);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public Uri addMultimediaMessageDraft(Uri contentUri) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.addMultimediaMessageDraft(ActivityThread.currentPackageName(), contentUri);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public void sendStoredTextMessage(Uri messageUri, String scAddress, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            getISmsServiceOrThrow().sendStoredText(getSubId(), ActivityThread.currentPackageName(), messageUri, scAddress, sentIntent, deliveryIntent);
        } catch (RemoteException e) {
        }
    }

    public void sendStoredMultipartTextMessage(Uri messageUri, String scAddress, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            getISmsServiceOrThrow().sendStoredMultipartText(getSubId(), ActivityThread.currentPackageName(), messageUri, scAddress, sentIntents, deliveryIntents);
        } catch (RemoteException e) {
        }
    }

    public void sendStoredMultimediaMessage(Uri messageUri, Bundle configOverrides, PendingIntent sentIntent) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.sendStoredMessage(getSubId(), ActivityThread.currentPackageName(), messageUri, configOverrides, sentIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void setAutoPersisting(boolean enabled) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.setAutoPersisting(ActivityThread.currentPackageName(), enabled);
            }
        } catch (RemoteException e) {
        }
    }

    public boolean getAutoPersisting() {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getAutoPersisting();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public Bundle getCarrierConfigValues() {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getCarrierConfigValues(getSubId());
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public boolean updateSmsServiceCenterOnSim(String scAddress) {
        byte[] encodedScAddress;
        Rlog.d(LOG_TAG, "updateSmsServiceCenterOnSim");
        boolean success = false;
        if (scAddress == null) {
            encodedScAddress = null;
        } else {
            int numberLenEffective = scAddress.length();
            if (scAddress.indexOf(43) != -1) {
                numberLenEffective--;
            }
            if (numberLenEffective > 20) {
                return 0;
            }
            encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.updateSmsServiceCenterOnSimEf(encodedScAddress);
            }
        } catch (RemoteException e) {
        }
        return success;
    }

    public String getSmsc() {
        String smsc = null;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                smsc = iccISms.getSmsc();
            }
        } catch (RemoteException e) {
        }
        return smsc;
    }

    public boolean getToddlerMode() {
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                return iccISms.getToddlerMode();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public ArrayList<String> divideMessage(String text, int encodingType) {
        if (text != null) {
            return SmsMessage.fragmentText(text, encodingType, this);
        }
        throw new IllegalArgumentException("text is null");
    }

    public int MakeSimPdu(String body, String scAddress, String Address, int SmsType, String date) {
        byte[] smsc = null;
        byte[] pdu = null;
        if (getSMSAvailable()) {
            int status;
            String format = getCurrentFormat();
            if (SmsType == 1) {
                try {
                    DeliverPdu pdus = SmsMessage.getSimDeliverPdu(scAddress, Address, body, date, null, format);
                    if (pdus != null) {
                        smsc = pdus.encodedScAddress;
                        pdu = pdus.encodedMessage;
                    }
                    status = 1;
                } catch (Exception e) {
                    Rlog.d(LOG_TAG, "getSimDeliverPdu Encoding ERR: " + e);
                    return 1;
                }
            }
            try {
                SubmitPdu pdus2 = SmsMessage.getSimSubmitPdu(scAddress, Address, body, null, format);
                if (pdus2 != null) {
                    smsc = pdus2.encodedScAddress;
                    pdu = pdus2.encodedMessage;
                }
                if (SmsType == 2) {
                    status = 5;
                } else {
                    status = 7;
                }
            } catch (Exception e2) {
                Rlog.d(LOG_TAG, "getSimSubmitPdu Encoding ERR: " + e2);
                return 1;
            }
            if (smsc == null && pdu == null) {
                return 1;
            }
            if (copyMessageToIcc(smsc, pdu, status)) {
                resetSimFullStatus();
                return 0;
            } else if (!getSimFullStatus()) {
                return 1;
            } else {
                Rlog.d(LOG_TAG, "getSimFullStatus: 3");
                return 3;
            }
        }
        Rlog.d(LOG_TAG, "getSimDeliverPdu : COPY_TO_SIM_NOT_AVAILABLE");
        return 2;
    }

    protected boolean getSimFullStatus() {
        Rlog.d(LOG_TAG, "getSimFullStatus in SmsManager");
        boolean ret = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                ret = iccISms.getSimFullStatus();
            }
        } catch (RemoteException e) {
            Rlog.d(LOG_TAG, "Exception In getSimFullStatus() of SmsManager.java    ");
        }
        return ret;
    }

    protected boolean getSMSAvailable() {
        Rlog.d(LOG_TAG, "getSMSAvailable in SmsManager");
        boolean ret = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                ret = iccISms.getSMSAvailable();
            }
        } catch (RemoteException e) {
            Rlog.d(LOG_TAG, "[CB ] Exception In getSMSAvailable() of SmsManager.java  ");
        }
        return ret;
    }

    protected void resetSimFullStatus() {
        Rlog.d(LOG_TAG, "resetSimFullStatus in SmsManager");
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                iccISms.resetSimFullStatus();
            }
        } catch (RemoteException e) {
            Rlog.d(LOG_TAG, "Exception In resetSimFullStatus() of SmsManager.java  ");
        }
    }

    public boolean getSMSPAvailable() {
        Rlog.d(LOG_TAG, "getSMSPAvailable in SmsManager");
        boolean ret = false;
        if (TelephonyManager.getDefault().getSimState() == 5) {
            try {
                ISms iccISms = getISmsService();
                if (iccISms != null) {
                    ret = iccISms.getSMSPAvailable();
                }
            } catch (RemoteException e) {
                Rlog.d(LOG_TAG, "[CB ] Exception In getSMSPAvailable() of SmsManager.java  ");
            }
        }
        return ret;
    }

    public CbConfig getCbSettings() {
        Rlog.d(LOG_TAG, "[CB] In getCbConfig");
        byte[] out = null;
        CbConfig cbConfig = new CbConfig();
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                out = iccISms.getCbSettings();
                if (out == null) {
                    Rlog.e(LOG_TAG, "[CB] out is null. Return null.");
                    return null;
                }
                if (out[0] == (byte) 1) {
                    cbConfig.bCBEnabled = true;
                } else {
                    cbConfig.bCBEnabled = false;
                }
                cbConfig.selectedId = (char) out[1];
                cbConfig.msgIdMaxCount = 'Ϩ';
                cbConfig.msgIdCount = out[3];
                short[] msgIDs = new short[cbConfig.msgIdCount];
                int i = 4;
                for (int j = 0; j < msgIDs.length; j++) {
                    msgIDs[j] = (short) ((out[i] & 255) | ((out[i + 1] & 255) << 8));
                    i += 2;
                }
                cbConfig.msgIDs = msgIDs;
                Rlog.d(LOG_TAG, "[SmsManger- CB] bCBEnabled = " + cbConfig.bCBEnabled + " selectedId = " + cbConfig.selectedId + " msgIdMaxCount = " + cbConfig.msgIdMaxCount + " msgIdCount = " + cbConfig.msgIdCount);
                for (short s : cbConfig.msgIDs) {
                    Rlog.d(LOG_TAG, "[CB] msgIDs =  " + s);
                }
                return cbConfig;
            }
            Rlog.e(LOG_TAG, "[CB] iccISms == null.");
            return null;
        } catch (RemoteException e) {
            Rlog.d(LOG_TAG, "[CB ] Exception In getCbConfig of SmsManager.java  ");
        } catch (NullPointerException e2) {
            Rlog.d(LOG_TAG, "[CB ] NULL Exception In getCbConfig of SmsManager.java  ");
        }
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, short originationPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        } else {
            try {
                getISmsServiceOrThrow().sendDatawithOrigPort(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, originationPort & 65535, data, sentIntent, deliveryIntent);
            } catch (RemoteException e) {
            }
        }
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, boolean replyPath, int expiry, int serviceType, int encodingType) {
        Rlog.d(LOG_TAG, "sendMultipartTextMessage in SmsManager with options");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (parts == null) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() < 1 && !CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() > 1) {
            try {
                getISmsServiceOrThrow().sendMultipartTextwithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, replyPath, expiry, serviceType, encodingType);
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = (PendingIntent) sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, (String) parts.get(0), sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType);
        }
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(LOG_TAG, "sendMultipartTextMessage in SmsManager with confirmId");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (parts == null) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() < 1 && !CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            throw new IllegalArgumentException("Invalid message body");
        } else if (parts.size() > 1) {
            try {
                getISmsServiceOrThrow().sendMultipartTextwithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, replyPath, expiry, serviceType, encodingType);
            } catch (RemoteException e) {
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = (PendingIntent) sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, (String) parts.get(0), sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType, confirmId);
        }
    }

    public void sendscptResultMessage(String destinationAddress, int NoOfOccur, int Category, int Language, int categoryResult, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        PendingIntent sentIntent = null;
        PendingIntent deliveryIntent = null;
        if (sentIntents != null && sentIntents.size() > 0) {
            sentIntent = (PendingIntent) sentIntents.get(0);
        }
        if (deliveryIntents != null && deliveryIntents.size() > 0) {
            deliveryIntent = (PendingIntent) deliveryIntents.get(0);
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                iccISms.sendscptResult(destinationAddress, NoOfOccur, Category, Language, categoryResult, sentIntent, deliveryIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void setCDMASmsReassembly(boolean onOff) {
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                iccISms.setCDMASmsReassembly(onOff);
            }
        } catch (RemoteException ex) {
            Rlog.d(LOG_TAG, "expcetion in setCDMASmsReassembly " + ex);
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType) {
        Rlog.d(LOG_TAG, "sendTextMessage in SmsManager with options");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (!TextUtils.isEmpty(text) || CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            try {
                getISmsServiceOrThrow().sendTextwithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType);
            } catch (RemoteException e) {
            }
        } else {
            throw new IllegalArgumentException("Invalid message body");
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, String callbackNumber, int priority) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (getToddlerMode()) {
            throw new IllegalArgumentException("toddler mode on");
        } else {
            try {
                ISms iccISms = getISmsServiceOrThrow();
                if (iccISms != null) {
                    iccISms.sendTextwithCBP(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, callbackNumber, priority);
                }
            } catch (RemoteException e) {
            }
        }
    }

    public void sendOTADomestic(String destinationAddress, String scAddress, String text) {
        Rlog.d(LOG_TAG, "sendOTADomestic in SmsManager");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        } else {
            try {
                getISmsServiceOrThrow().sendOTADomestic(ActivityThread.currentPackageName(), destinationAddress, scAddress, text);
            } catch (RemoteException e) {
            }
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean replyPath, int expiry, int serviceType, int encodingType, int confirmId) {
        Rlog.d(LOG_TAG, "sendTextMessage in SmsManager with confirmId");
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        } else if (!TextUtils.isEmpty(text) || CscFeature.getInstance().getEnableStatus("CscFeature_Message_EnableSendingEmptySms")) {
            try {
                getISmsServiceOrThrow().sendTextwithOptionsReadconfirm(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, replyPath, expiry, serviceType, encodingType, confirmId);
            } catch (RemoteException e) {
            }
        } else {
            throw new IllegalArgumentException("Invalid message body");
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int phoneType) {
        sendTextMessage(destinationAddress, scAddress, text, sentIntent, deliveryIntent);
    }

    public void sendTextMessageNSRI(String destinationAddress, String scAddress, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, String from, int msgCount, int msgTotal) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        Log.secD(LOG_TAG, "[NSRI_SMS] sendTextMessageNSRI   Addr=" + destinationAddress + " Smsc=" + scAddress + " textLen=" + data.length + " from=" + from + " msgCount=" + msgCount + " msgTotal=" + msgTotal);
        try {
            ISms iccISms = Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.sendTextNSRI(ActivityThread.currentPackageName(), destinationAddress, scAddress, data, sentIntent, deliveryIntent, msgCount, msgTotal);
            }
        } catch (RemoteException e) {
        }
    }

    public String getCurrentFormat() {
        long subId = SubscriptionManager.getDefaultSmsSubId();
        TelephonyManager.getDefault();
        String mode = TelephonyManager.getTelephonyProperty("gsm.current.phone-type", subId, String.valueOf(1));
        Rlog.d(LOG_TAG, "getCurrentFormat, subId = " + subId + " mode = " + mode);
        switch (Integer.parseInt(mode)) {
            case 1:
                return SmsMessage.FORMAT_3GPP;
            case 2:
                return SmsMessage.FORMAT_3GPP2;
            default:
                return SmsMessage.FORMAT_3GPP;
        }
    }
}
