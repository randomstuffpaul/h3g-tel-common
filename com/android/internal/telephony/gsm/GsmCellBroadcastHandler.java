package com.android.internal.telephony.gsm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony.Sms.Intents;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.gsm.GsmCellLocation;
import android.util.secutil.Log;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.PhoneBase;
import com.sec.android.emergencymode.EmergencyManager;
import java.util.HashMap;
import java.util.Iterator;

public class GsmCellBroadcastHandler extends CellBroadcastHandler {
    private static final byte ALLRECEIVE_MODE = (byte) 3;
    private static final byte COMMERCIAL_MODE = (byte) 0;
    static final int ETWS_NOTIFICATION = 111;
    private static final byte KDDITEST_MODE = (byte) 2;
    private static final byte MANUFACTURETEST_MODE = (byte) 1;
    private static final String TAG = "GsmCellBroadcastHandler";
    private static final boolean VDBG = false;
    private static int mCid;
    private static boolean mFlagDupCB = false;
    private static int mLac;
    private static String mPlmn = null;
    private static byte[] mSavedPdu;
    private static HashMap<SmsCbDuplicateInfo, SmsCbHeader> mSmsCbDuplicateMap = new HashMap();
    private Notification mNotification;
    private final HashMap<SmsCbConcatInfo, byte[][]> mSmsCbPageMap = new HashMap(4);
    private SmsCbLocation pre_location = new SmsCbLocation(" ", -1, -1);

    private static final class SmsCbConcatInfo {
        private final SmsCbHeader mHeader;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfo(SmsCbHeader header, SmsCbLocation location) {
            this.mHeader = header;
            this.mLocation = location;
        }

        public int hashCode() {
            return (this.mHeader.getSerialNumber() * 31) + this.mLocation.hashCode();
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof SmsCbConcatInfo)) {
                return false;
            }
            SmsCbConcatInfo other = (SmsCbConcatInfo) obj;
            if (this.mHeader.getSerialNumber() == other.mHeader.getSerialNumber() && this.mLocation.equals(other.mLocation)) {
                return true;
            }
            return false;
        }

        public boolean matchesLocation(String plmn, int lac, int cid) {
            return this.mLocation.isInLocationArea(plmn, lac, cid);
        }
    }

    private static final class SmsCbDuplicateInfo {
        private final int mGeographicalScope;
        private final int mMessageIdentifier;
        private final int mPageIndex;
        private final int mPhoneId;
        private final int mSerialNumber;

        public SmsCbDuplicateInfo(SmsCbHeader header, int phoneId) {
            this.mMessageIdentifier = header.getServiceCategory();
            this.mGeographicalScope = header.getGeographicalScope();
            this.mSerialNumber = header.getSerialNumber();
            this.mPageIndex = header.getPageIndex();
            this.mPhoneId = phoneId;
        }

        public int hashCode() {
            return ((((this.mMessageIdentifier * 31) + this.mGeographicalScope) + this.mSerialNumber) + this.mPageIndex) + this.mPhoneId;
        }

        public boolean equals(Object obj) {
            if (obj instanceof SmsCbDuplicateInfo) {
                SmsCbDuplicateInfo other = (SmsCbDuplicateInfo) obj;
                if (this.mMessageIdentifier == other.mMessageIdentifier && this.mGeographicalScope == other.mGeographicalScope && this.mSerialNumber == other.mSerialNumber && this.mPageIndex == other.mPageIndex && this.mPhoneId == other.mPhoneId) {
                    return true;
                }
            }
            return false;
        }

        public String toString() {
            return "SmsCbDuplicateInfo {GS= " + this.mGeographicalScope + ", SerialNumber= " + this.mSerialNumber + ", MessageIdentifier= " + this.mMessageIdentifier + ", PageIndex= " + this.mPageIndex + ", mPhoneId= " + this.mPhoneId + '}';
        }
    }

    protected GsmCellBroadcastHandler(Context context, PhoneBase phone) {
        super(TAG, context, phone);
        phone.mCi.setOnNewGsmBroadcastSms(getHandler(), 1, null);
    }

    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmBroadcastSms(getHandler());
        super.onQuitting();
    }

    public static GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context, PhoneBase phone) {
        GsmCellBroadcastHandler handler = new GsmCellBroadcastHandler(context, phone);
        handler.start();
        return handler;
    }

    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof AsyncResult) {
            SmsCbMessage cbMessage = handleGsmBroadcastSms((byte[]) message.obj.result, false);
            if (cbMessage != null) {
                handleBroadcastSms(cbMessage);
                return true;
            }
        }
        return super.handleSmsMessage(message);
    }

    private SmsCbMessage handleGsmBroadcastSms(byte[] receivedPdu, boolean flagSaved) {
        try {
            Rlog.d(TAG, "handleBroadcastSms mFlagDupCB =" + mFlagDupCB + ", SavedMsg= " + flagSaved);
            PowerManager pm = (PowerManager) this.mContext.getSystemService("power");
            if (!(pm.isScreenOn() || flagSaved)) {
                this.mPhone.mCi.getVoiceRegistrationState(obtainMessage(5, null));
            }
            int lac = -1;
            int cid = -1;
            String plmn = SystemProperties.get("gsm.operator.numeric");
            if (flagSaved) {
                lac = mLac;
                cid = mCid;
                if (!(mPlmn == null || mPlmn.equals(plmn))) {
                    plmn = mPlmn;
                }
            } else {
                CellLocation cl = this.mPhone.getCellLocation();
                if (cl instanceof GsmCellLocation) {
                    GsmCellLocation cellLocation = (GsmCellLocation) cl;
                    lac = cellLocation.getLac();
                    cid = cellLocation.getCid();
                }
            }
            SmsCbHeader header = new SmsCbHeader(receivedPdu);
            SmsCbLocation smsCbLocation;
            switch (header.getGeographicalScope()) {
                case 0:
                case 3:
                    smsCbLocation = new SmsCbLocation(plmn, lac, cid);
                    break;
                case 2:
                    smsCbLocation = new SmsCbLocation(plmn, lac, -1);
                    break;
                default:
                    smsCbLocation = new SmsCbLocation(plmn);
                    break;
            }
            Rlog.d(TAG, "[CB] DuplicatedCbMessage: checking if location is changed");
            Rlog.d(TAG, location.toString());
            if (!location.equals(this.pre_location)) {
                clearDuplicatedCbMessages();
            }
            this.pre_location = location;
            if (isDuplicatedCbMessage(header)) {
                Rlog.d(TAG, "[CB] DuplicatedCbMessage: Duplicated CB message exist.");
                if (!(pm.isScreenOn() || flagSaved)) {
                    mSavedPdu = receivedPdu;
                    mFlagDupCB = true;
                    mLac = lac;
                    mCid = cid;
                }
                return null;
            }
            byte[][] pdus;
            int pageCount = header.getNumberOfPages();
            if (pageCount > 1) {
                SmsCbConcatInfo concatInfo = new SmsCbConcatInfo(header, location);
                pdus = (byte[][]) this.mSmsCbPageMap.get(concatInfo);
                if (pdus == null) {
                    pdus = new byte[pageCount][];
                    this.mSmsCbPageMap.put(concatInfo, pdus);
                }
                pdus[header.getPageIndex() - 1] = receivedPdu;
                for (byte[] pdu : pdus) {
                    if (pdu == null) {
                        return null;
                    }
                }
                this.mSmsCbPageMap.remove(concatInfo);
            } else {
                pdus = new byte[][]{receivedPdu};
            }
            Iterator<SmsCbConcatInfo> iter = this.mSmsCbPageMap.keySet().iterator();
            while (iter.hasNext()) {
                if (!((SmsCbConcatInfo) iter.next()).matchesLocation(plmn, lac, cid)) {
                    iter.remove();
                }
            }
            if (EmergencyManager.isEmergencyMode(this.mContext)) {
                if (!pm.isScreenOn()) {
                    EmergencyManager.getInstance(this.mContext).setforceBlockUserPkg(true, this.mContext);
                }
            } else if (ETWSJudgeDeliveryFromMessageID(header.getServiceCategory())) {
                setNotification();
            }
            return GsmSmsCbMessage.createSmsCbMessage(header, location, pdus);
        } catch (RuntimeException e) {
            loge("Error in decoding SMS CB pdu", e);
            return null;
        }
    }

    public void clearDuplicatedCbMessages() {
        log("[CB] DuplicatedCbMessage: Clear duplicated CB Messages from hash map.");
        mSmsCbDuplicateMap.clear();
    }

    private boolean isDuplicatedCbMessage(SmsCbHeader cbHeader) {
        SmsCbDuplicateInfo duplicateInfo = new SmsCbDuplicateInfo(cbHeader, this.mPhone.getPhoneId());
        Rlog.d(TAG, "[CB] DuplicatedCbMessage: " + duplicateInfo.toString());
        for (SmsCbDuplicateInfo info : mSmsCbDuplicateMap.keySet()) {
            Rlog.d(TAG, "[CB] DuplicatedCbMessage: list of duplicated Map. key value = " + info.toString());
        }
        if (mSmsCbDuplicateMap.containsKey(duplicateInfo)) {
            return true;
        }
        Rlog.d(TAG, "[CB] DuplicatedCbMessage: Add CB header to hash map.");
        mSmsCbDuplicateMap.put(duplicateInfo, cbHeader);
        return false;
    }

    protected void handleLocationInfo(AsyncResult ar) {
        if (ar != null && mFlagDupCB && ar.exception == null) {
            String[] states = (String[]) ar.result;
            int lac = -1;
            int cid = -1;
            if (states.length >= 3) {
                try {
                    if (states[1] != null && states[1].length() > 0) {
                        lac = Integer.parseInt(states[1], 16);
                    }
                    if (states[2] != null && states[2].length() > 0) {
                        cid = Integer.parseInt(states[2], 16);
                    }
                } catch (NumberFormatException ex) {
                    Rlog.w(TAG, "error parsing location: " + ex);
                }
            }
            if (mLac != lac || mCid != cid) {
                Rlog.d(TAG, "Location is changed during LCD off. Before Lac= " + mLac + ", Cid= " + mCid + ". After Lac= " + lac + ", Cid= " + cid);
                mLac = lac;
                mCid = cid;
                this.mPhone.mCi.getOperator(obtainMessage(6, null));
            }
        }
    }

    protected void handleOperatorInfo(AsyncResult ar) {
        if (ar != null && mFlagDupCB) {
            if (ar.exception == null) {
                String[] opNames = (String[]) ar.result;
                if (opNames != null && opNames.length >= 3) {
                    mPlmn = opNames[2];
                }
            }
            SmsCbMessage cbMessage = handleGsmBroadcastSms(mSavedPdu, true);
            if (cbMessage != null) {
                handleBroadcastSms(cbMessage);
            }
            mFlagDupCB = false;
        }
    }

    protected void KddiNotifiyGsmSmsToWIFI(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_CB_RECEIVED_WIFI_ACTION);
        intent.putExtra("pdus", pdus);
        this.mContext.sendBroadcast(intent);
    }

    protected boolean ETWSJudgeDeliveryFromMessageID(int messageIdentifier) {
        return false;
    }

    protected boolean kddiJudgeDeliveryFromMessageID(int messageIdentifier) {
        boolean isDelivery = false;
        switch (null) {
            case null:
                if (messageIdentifier == 4352 || messageIdentifier == SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING || ((messageIdentifier >= SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE && messageIdentifier <= 4359) || messageIdentifier == 40963 || (messageIdentifier >= 43009 && messageIdentifier <= 43263))) {
                    isDelivery = true;
                    break;
                }
            case 1:
                if (messageIdentifier == SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE || (messageIdentifier >= 43521 && messageIdentifier <= 43775)) {
                    isDelivery = true;
                    break;
                }
            case 2:
                if (messageIdentifier >= 43776 && messageIdentifier <= 44031) {
                    isDelivery = true;
                    break;
                }
            case 3:
                if (messageIdentifier == 4352 || messageIdentifier == SmsCbConstants.MESSAGE_ID_ETWS_TSUNAMI_WARNING || ((messageIdentifier >= SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE && messageIdentifier <= 4359) || messageIdentifier == 40963 || ((messageIdentifier >= 43009 && messageIdentifier <= 43263) || messageIdentifier == SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE || ((messageIdentifier >= 43521 && messageIdentifier <= 43775) || (messageIdentifier >= 43776 && messageIdentifier <= 44031))))) {
                    isDelivery = true;
                    break;
                }
        }
        Log.d(TAG, "kddiJudgeDeliveryFromMessageID maintenanceMode : " + 0 + " isDelivery: " + isDelivery);
        return isDelivery;
    }

    protected boolean kddiJudgeDeliveryFromMessageIDForWIFI(int messageIdentifier) {
        boolean isDelivery = false;
        switch (0) {
            case 0:
                if (messageIdentifier == 4352 || messageIdentifier == 40963) {
                    isDelivery = true;
                    break;
                }
            default:
                if (messageIdentifier == SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE || messageIdentifier == 43523) {
                    isDelivery = true;
                    break;
                }
        }
        Log.d(TAG, "kddiJudgeDeliveryFromMessageIDForWIFI messageIdentifier : " + messageIdentifier + " isDelivery: " + isDelivery);
        return isDelivery;
    }

    private void setNotification() {
        Log.d(TAG, "setNotification: create notification ");
        this.mNotification = new Notification();
        this.mNotification.when = System.currentTimeMillis();
        this.mNotification.flags = 16;
        this.mNotification.icon = 17301642;
        Intent intent = new Intent("android.intent.action.EMERGENCY_START_SERVICE_BY_ORDER");
        intent.putExtra("enabled", true);
        intent.putExtra("flag", 16);
        this.mNotification.contentIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
        CharSequence title = "";
        CharSequence details = "";
        Log.d(TAG, "setNotification: put notification " + title + " / " + details);
        this.mNotification.tickerText = title;
        this.mNotification.setLatestEventInfo(this.mContext, title, details, this.mNotification.contentIntent);
        Context context = this.mContext;
        Context context2 = this.mContext;
        ((NotificationManager) context.getSystemService("notification")).notify(ETWS_NOTIFICATION, this.mNotification);
    }
}
