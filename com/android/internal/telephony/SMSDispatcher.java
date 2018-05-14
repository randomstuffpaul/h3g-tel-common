package com.android.internal.telephony;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.Sms.Outbox;
import android.provider.Telephony.Sms.Sent;
import android.provider.Telephony.TextBasedSmsColumns;
import android.sec.enterprise.DeviceInventory;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.sec.enterprise.auditlog.AuditLog;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.EventLog;
import android.util.secutil.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.SmsHeader.ConcatRef;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SMSDispatcher extends Handler {
    protected static final String ACTION_LTE_SMS_STATUS = "android.intent.action.LTE_SMS_STATUS";
    public static final String ACTION_SSMS_STATE_FILE_UPDATE = "com.sec.android.app.mms.SSMS_STATE_FILE_UPDATE";
    static final boolean DBG = false;
    private static final byte DELIMITER = (byte) 11;
    private static final int EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE = 8;
    private static final int EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE = 9;
    protected static final int EVENT_HANDLE_STATUS_REPORT = 10;
    protected static final int EVENT_ICC_CHANGED = 15;
    protected static final int EVENT_IMS_STATE_CHANGED = 12;
    protected static final int EVENT_IMS_STATE_DONE = 13;
    protected static final int EVENT_NEW_ICC_SMS = 14;
    protected static final int EVENT_RADIO_ON = 11;
    protected static final int EVENT_RADIO_STATE_CHANGED = 19;
    static final int EVENT_SEND_CONFIRMED_SMS = 5;
    private static final int EVENT_SEND_LIMIT_REACHED_CONFIRMATION = 4;
    private static final int EVENT_SEND_RETRY = 3;
    protected static final int EVENT_SEND_SMS_COMPLETE = 2;
    static final int EVENT_STOP_SENDING = 7;
    private static final String GCF_MODE_ACTION = "com.sec.android.app.GCF_MODE_ACTION";
    private static final int GET_SMSC_DELAY = 5000;
    public static final String LAST_SENT_MSG_EXTRA = "LastSentMsg";
    private static final int MAX_SEND_RETRIES = 3;
    private static final int MAX_SEND_RETRIES_SPR = 1;
    private static final int MAX_SEND_RETRIES_VZW = 0;
    private static final int MO_MSG_QUEUE_LIMIT = 5;
    private static final int PREMIUM_RULE_USE_BOTH = 3;
    private static final int PREMIUM_RULE_USE_NETWORK = 2;
    private static final int PREMIUM_RULE_USE_SIM = 1;
    public static final String PRODUCT_CODE = "product_code";
    public static final String PRODUCT_INFO = "product_info";
    protected static final String[] RAW_PROJECTION = new String[]{"pdu", "reference_number", "sequence", "destination_port"};
    private static final String SEC_SMS_PACKAGE_NAME = "com.android.mms";
    public static final int SENDING = 1;
    private static final int SEND_DAN_RETRY_DELAY = 10000;
    private static final String SEND_NEXT_MSG_EXTRA = "SendNextMsg";
    private static final String SEND_RESPOND_VIA_MESSAGE_PERMISSION = "android.permission.SEND_RESPOND_VIA_MESSAGE";
    private static final int SEND_RETRY_DELAY = 2000;
    public static final int SENT = 2;
    private static final int SINGLE_PART_SMS = 1;
    private static final String SKT_CARRIERLOCK_MODE_FILE = "/efs/sms/sktcarrierlockmode";
    private static final String SKT_CARRIERLOCK_MODE_FOLDER = "/efs/sms";
    protected static int SMSC_ADDRESS_LENGTH = 21;
    public static final String SSMS_AUTHORITY = "com.android.mms.SSMSInfoProvider";
    public static final Uri SSMS_CONTENT_URI = Uri.parse("content://com.android.mms.SSMSInfoProvider");
    public static final String SSMS_ENABLE = "ssms_enable";
    public static final String SSMS_STATE = "ssms_state";
    public static final String SSMS_TABLE_NAME = "ssms";
    static final String TAG = "SMSDispatcher";
    public static final int TO_BE_SENT = 0;
    private static boolean gcf_flag = false;
    protected static final String hexDigitChars = "0123456789abcdef";
    public static int retryGetSmsc = 0;
    private static int sConcatenatedRef = new Random().nextInt(256);
    protected final String ACTION_SIM_REFRESH_INIT = "com.android.action.SIM_REFRESH_INIT";
    protected final int EVENT_GET_SMSC_DONE = 17;
    protected final int EVENT_GET_SMSC_DONE_EXTEND = 18;
    protected final int EVENT_SMS_DEVICE_READY = 16;
    protected String Sim_Smsc = null;
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList();
    private String mApplicationID;
    private String mApplicationName;
    private byte[] mApplicationSpecificData;
    protected final CommandsInterface mCi;
    private String mCommand;
    protected final Context mContext;
    protected String mDcnAddress = "4437501000";
    private BroadcastReceiver mGcfModeReceiver = new C00251();
    protected ImsSMSDispatcher mImsSMSDispatcher;
    protected boolean mIsDisposed = false;
    protected int mLteSmsStatus = 0;
    private int mPendingTrackerCount;
    protected PhoneBase mPhone;
    private final AtomicInteger mPremiumSmsRule = new AtomicInteger(1);
    protected final ContentResolver mResolver;
    protected final BroadcastReceiver mResultReceiver = new C00262();
    private final SettingsObserver mSettingsObserver;
    protected boolean mSmsCapable = true;
    protected boolean mSmsSendDisabled;
    protected final TelephonyManager mTelephonyManager;
    private String mUI;
    protected UiccController mUiccController = null;
    private SmsUsageMonitor mUsageMonitor;
    protected final List<SmsTracker> sendPendingList = Collections.synchronizedList(new ArrayList());

    class C00251 extends BroadcastReceiver {
        C00251() {
        }

        public void onReceive(Context context, Intent intent) {
            if (SMSDispatcher.GCF_MODE_ACTION.equals(intent.getAction()) && intent.getExtra("key") != null && intent.getExtra("key").toString().equals("gcf_mode") && intent.getExtra("mode") != null) {
                SystemProperties.set("ril.sms.gcf-mode", "enabled".equals(intent.getExtra("mode").toString()) ? "On" : "Off");
                Log.d(SMSDispatcher.TAG, "GCF sms memory full mode = " + SystemProperties.get("ril.sms.gcf-mode"));
            }
        }
    }

    class C00262 extends BroadcastReceiver {
        C00262() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
            }
            if (intent.getAction().equals("com.android.action.SIM_REFRESH_INIT")) {
                Rlog.d(SMSDispatcher.TAG, "ACTION_SIM_REFRESH_INIT");
                SMSDispatcher.this.Sim_Smsc = null;
                SMSDispatcher.retryGetSmsc = 0;
                SMSDispatcher.this.mCi.getSmscAddress(SMSDispatcher.this.obtainMessage(17));
            } else if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                if ("ZTO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms")) || "ARO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms"))) {
                    SMSDispatcher.this.initSelloutSms(context);
                }
            } else if (intent.getAction().equals(SMSDispatcher.ACTION_SSMS_STATE_FILE_UPDATE)) {
                if ("ZTO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms")) || "ARO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms"))) {
                    SMSDispatcher.this.updateSelloutSmsFile(intent.getIntExtra(SMSDispatcher.SSMS_STATE, 0));
                }
            } else if (intent.getAction().equals(SMSDispatcher.ACTION_LTE_SMS_STATUS) && intent.hasExtra("ltesms")) {
                SMSDispatcher.this.mLteSmsStatus = intent.getIntExtra("ltesms", 0);
                Rlog.d(SMSDispatcher.TAG, "lte sms status is updated : " + SMSDispatcher.this.mLteSmsStatus);
            }
        }
    }

    private final class ConfirmDialogListener implements OnCancelListener, OnClickListener, OnCheckedChangeListener {
        private Button mNegativeButton;
        private Button mPositiveButton;
        private boolean mRememberChoice;
        private final TextView mRememberUndoInstruction;
        private final SmsTracker mTracker;

        ConfirmDialogListener(SmsTracker tracker, TextView textView) {
            this.mTracker = tracker;
            this.mRememberUndoInstruction = textView;
        }

        void setPositiveButton(Button button) {
            this.mPositiveButton = button;
        }

        void setNegativeButton(Button button) {
            this.mNegativeButton = button;
        }

        public void onClick(DialogInterface dialog, int which) {
            int i = -1;
            int newSmsPermission = 1;
            if (which == -1) {
                Rlog.d(SMSDispatcher.TAG, "CONFIRM sending SMS");
                if (this.mTracker.mAppInfo.applicationInfo != null) {
                    i = this.mTracker.mAppInfo.applicationInfo.uid;
                }
                EventLog.writeEvent(EventLogTags.EXP_DET_SMS_SENT_BY_USER, i);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(5, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 3;
                }
            } else if (which == -2) {
                Rlog.d(SMSDispatcher.TAG, "DENY sending SMS");
                if (this.mTracker.mAppInfo.applicationInfo != null) {
                    i = this.mTracker.mAppInfo.applicationInfo.uid;
                }
                EventLog.writeEvent(EventLogTags.EXP_DET_SMS_DENIED_BY_USER, i);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 2;
                }
            }
            SMSDispatcher.this.setPremiumSmsPermission(this.mTracker.mAppInfo.packageName, newSmsPermission);
        }

        public void onCancel(DialogInterface dialog) {
            Rlog.d(SMSDispatcher.TAG, "dialog dismissed: don't send SMS");
            SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
        }

        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Rlog.d(SMSDispatcher.TAG, "remember this choice: " + isChecked);
            this.mRememberChoice = isChecked;
            buttonView.playSoundEffect(0);
            if (SystemProperties.get("ro.build.scafe.cream").equals("black") && this.mRememberUndoInstruction != null) {
                this.mRememberUndoInstruction.setTextColor(-7829368);
            }
            if (isChecked) {
                this.mPositiveButton.setText(17040598);
                this.mNegativeButton.setText(17040599);
                if (this.mRememberUndoInstruction != null) {
                    this.mRememberUndoInstruction.setText(17040597);
                    this.mRememberUndoInstruction.setPadding(0, 0, 0, 32);
                    return;
                }
                return;
            }
            this.mPositiveButton.setText(17040594);
            this.mNegativeButton.setText(17040595);
            if (this.mRememberUndoInstruction != null) {
                this.mRememberUndoInstruction.setText("");
                this.mRememberUndoInstruction.setPadding(0, 0, 0, 0);
            }
        }
    }

    protected class KoreanAddressSeparator {
        static final String SEND_ADDRESS_SEPARATOR = "/";
        public int mCurIndex = 1;
        public String mDestAddr = null;
        public String mSenderAddr = null;
        public int mTID = 0;
        public int mTotalCnt = 1;

        public KoreanAddressSeparator(String addr) {
            String[] tokens = addr.split(SEND_ADDRESS_SEPARATOR);
            if (tokens.length == 1) {
                this.mDestAddr = tokens[0];
            } else if (tokens.length == 2) {
                this.mDestAddr = tokens[0];
                this.mSenderAddr = tokens[1];
            } else if (tokens.length == 3) {
                this.mDestAddr = tokens[0];
                this.mCurIndex = Integer.parseInt(tokens[1]);
                this.mTotalCnt = Integer.parseInt(tokens[2]);
            } else if (tokens.length == 4) {
                this.mDestAddr = tokens[0];
                this.mSenderAddr = tokens[1];
                this.mCurIndex = Integer.parseInt(tokens[2]);
                this.mTotalCnt = Integer.parseInt(tokens[3]);
            } else {
                Rlog.e(SMSDispatcher.TAG, "KoreanAddressSeparator: Illegal format. " + addr);
                return;
            }
            if (this.mSenderAddr == null || this.mSenderAddr.isEmpty()) {
                Rlog.d(SMSDispatcher.TAG, "No sender address info. Set to getLine1Number()");
                try {
                    String sendAddr = SMSDispatcher.this.mPhone.getLine1Number();
                    if (sendAddr.startsWith("+82")) {
                        sendAddr = "0" + sendAddr.substring(3);
                    }
                    this.mSenderAddr = sendAddr;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected final class SMSDispatcherReceiver extends BroadcastReceiver {
        private final SmsTracker mTracker;

        public SMSDispatcherReceiver(SmsTracker tracker) {
            this.mTracker = tracker;
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(Intents.SMS_SEND_ACTION)) {
                Rlog.e(SMSDispatcher.TAG, "unexpected BroadcastReceiver action: " + action);
            } else if (getResultCode() == -1) {
                Rlog.d(SMSDispatcher.TAG, "Sending SMS by IP pending.");
                Bundle resultExtras = getResultExtras(false);
                if (resultExtras == null || !resultExtras.containsKey("messageref")) {
                    Rlog.e(SMSDispatcher.TAG, "Can't find messageref in result extras.");
                } else {
                    this.mTracker.mMessageRef = resultExtras.getInt("messageref");
                    Rlog.d(SMSDispatcher.TAG, "messageref = " + this.mTracker.mMessageRef);
                }
                SMSDispatcher.this.sendPendingList.add(this.mTracker);
            } else {
                Rlog.d(SMSDispatcher.TAG, "Sending SMS by IP failed.");
                SMSDispatcher.this.sendSmsByPstn(this.mTracker);
            }
        }
    }

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicInteger mPremiumSmsRule;

        SettingsObserver(Handler handler, AtomicInteger premiumSmsRule, Context context) {
            super(handler);
            this.mPremiumSmsRule = premiumSmsRule;
            this.mContext = context;
            onChange(false);
        }

        public void onChange(boolean selfChange) {
            this.mPremiumSmsRule.set(Global.getInt(this.mContext.getContentResolver(), "sms_short_code_rule", 1));
        }
    }

    protected static final class SmsTracker {
        private AtomicBoolean mAnyPartFailed;
        public final PackageInfo mAppInfo;
        public final HashMap<String, Object> mData;
        public final PendingIntent mDeliveryIntent;
        public final String mDestAddress;
        public int mErrorClass;
        public boolean mExpectMore;
        String mFormat;
        public int mImsRetry;
        public String mImsi;
        public int mMessageRef;
        public Uri mMessageUri;
        public String mOrigAddr;
        public int mPhoneId;
        public int mRetryCount;
        public final PendingIntent mSentIntent;
        public final SmsHeader mSmsHeader;
        private long mTimestamp;
        private AtomicInteger mUnsentPartCount;

        private SmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, PackageInfo appInfo, String destAddr, String format, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, SmsHeader smsHeader, boolean isExpectMore) {
            this.mTimestamp = System.currentTimeMillis();
            this.mData = data;
            this.mSentIntent = sentIntent;
            this.mDeliveryIntent = deliveryIntent;
            this.mRetryCount = 0;
            this.mAppInfo = appInfo;
            this.mDestAddress = destAddr;
            this.mFormat = format;
            this.mExpectMore = isExpectMore;
            this.mImsRetry = 0;
            this.mMessageRef = 0;
            this.mUnsentPartCount = unsentPartCount;
            this.mAnyPartFailed = anyPartFailed;
            this.mMessageUri = messageUri;
            this.mSmsHeader = smsHeader;
        }

        boolean isMultipart() {
            return this.mData.containsKey("parts");
        }

        void writeSentMessage(Context context) {
            String text = (String) this.mData.get(Part.TEXT);
            if (text != null) {
                this.mMessageUri = Sms.addMessageToUri(context.getContentResolver(), Sent.CONTENT_URI, this.mDestAddress, text, null, Long.valueOf(this.mTimestamp), true, this.mDeliveryIntent != null, 0);
            }
        }

        void writeSentMessage(Context context, int mSimSlot, String imsi) {
            String text = (String) this.mData.get(Part.TEXT);
            if (text != null) {
                this.mMessageUri = Sms.addMessageToUri(context.getContentResolver(), Sent.CONTENT_URI, this.mDestAddress, text, null, imsi, mSimSlot, Long.valueOf(this.mTimestamp), true, this.mDeliveryIntent != null, 0);
            }
        }

        public void updateSentMessageStatus(Context context, int status) {
            if (this.mMessageUri != null) {
                ContentValues values = new ContentValues(1);
                values.put(TextBasedSmsColumns.STATUS, Integer.valueOf(status));
                SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, values, null, null);
            }
        }

        private void updateMessageErrorCode(Context context, int errorCode) {
            if (this.mMessageUri != null) {
                ContentValues values = new ContentValues(1);
                values.put(TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(errorCode));
                long identity = Binder.clearCallingIdentity();
                try {
                    if (SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, values, null, null) != 1) {
                        Rlog.e(SMSDispatcher.TAG, "Failed to update message error code");
                    }
                    Binder.restoreCallingIdentity(identity);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }

        private void setMessageFinalState(Context context, int messageType) {
            if (this.mMessageUri != null) {
                ContentValues values = new ContentValues(1);
                values.put("type", Integer.valueOf(messageType));
                if (SMSDispatcher.getEnableMultiSim()) {
                    values.put("sim_slot", Integer.valueOf(this.mPhoneId));
                    if (this.mImsi != null) {
                        values.put("sim_imsi", this.mImsi);
                    }
                }
                long identity = Binder.clearCallingIdentity();
                try {
                    if (SqliteWrapper.update(context, context.getContentResolver(), this.mMessageUri, values, null, null) != 1) {
                        Rlog.e(SMSDispatcher.TAG, "Failed to move message to " + messageType);
                    }
                    Binder.restoreCallingIdentity(identity);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }

        public void onFailed(Context context, int error, int errorCode, int phoneId, String imsi) {
            this.mPhoneId = phoneId;
            this.mImsi = imsi;
            onFailed(context, error, errorCode);
        }

        public void onFailed(Context context, int error, int errorCode) {
            if (this.mAnyPartFailed != null) {
                this.mAnyPartFailed.set(true);
            }
            boolean isSinglePartOrLastPart = true;
            if (this.mUnsentPartCount != null) {
                isSinglePartOrLastPart = this.mUnsentPartCount.decrementAndGet() == 0;
            }
            if (errorCode != 0) {
                updateMessageErrorCode(context, errorCode);
            }
            if (isSinglePartOrLastPart) {
                setMessageFinalState(context, 5);
            }
            if (this.mSentIntent != null) {
                try {
                    Rlog.d(SMSDispatcher.TAG, "mMessageUri : " + this.mMessageUri + "isSinglePartOrLastPart : " + isSinglePartOrLastPart);
                    Intent fillIn = new Intent();
                    if (this.mMessageUri != null) {
                        fillIn.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (errorCode != 0) {
                        fillIn.putExtra("errorCode", errorCode);
                    }
                    if (this.mUnsentPartCount != null && isSinglePartOrLastPart) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                        fillIn.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                    } else if (this.mUnsentPartCount == null) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                        fillIn.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                    }
                    SubscriptionManager.putPhoneIdAndSubIdExtra(fillIn, this.mPhoneId);
                    this.mSentIntent.send(context, error, fillIn);
                } catch (CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }

        public void onSent(Context context, int phoneId, String imsi) {
            this.mPhoneId = phoneId;
            this.mImsi = imsi;
            onSent(context);
        }

        public void onSent(Context context) {
            boolean isSinglePartOrLastPart = true;
            if (this.mUnsentPartCount != null) {
                isSinglePartOrLastPart = this.mUnsentPartCount.decrementAndGet() == 0;
            }
            if (isSinglePartOrLastPart) {
                boolean success = true;
                if (this.mAnyPartFailed != null && this.mAnyPartFailed.get()) {
                    success = false;
                }
                if (success) {
                    setMessageFinalState(context, 2);
                } else {
                    setMessageFinalState(context, 5);
                }
            }
            if (this.mSentIntent != null) {
                Rlog.d(SMSDispatcher.TAG, "mMessageUri : " + this.mMessageUri + "isSinglePartOrLastPart : " + isSinglePartOrLastPart);
                try {
                    Intent fillIn = new Intent();
                    if (this.mMessageUri != null) {
                        fillIn.putExtra("uri", this.mMessageUri.toString());
                    }
                    if (this.mUnsentPartCount != null && isSinglePartOrLastPart) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                        fillIn.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                    } else if (this.mUnsentPartCount == null) {
                        fillIn.putExtra(SMSDispatcher.SEND_NEXT_MSG_EXTRA, true);
                        fillIn.putExtra(SMSDispatcher.LAST_SENT_MSG_EXTRA, true);
                    }
                    SubscriptionManager.putPhoneIdAndSubIdExtra(fillIn, this.mPhoneId);
                    this.mSentIntent.send(context, -1, fillIn);
                } catch (CanceledException e) {
                    Rlog.e(SMSDispatcher.TAG, "Failed to send result");
                }
            }
        }
    }

    protected abstract TextEncodingDetails calculateLength(CharSequence charSequence, boolean z);

    public abstract void clearDuplicatedCbMessages();

    protected abstract void dispatchSmsServiceCenter(String str);

    protected abstract String getFormat();

    protected abstract void injectSmsPdu(byte[] bArr, String str, PendingIntent pendingIntent);

    protected abstract void sendData(String str, String str2, int i, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2);

    protected abstract void sendDatawithOrigPort(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2);

    public abstract void sendDomainChangeSms(byte b);

    protected abstract void sendMultipartText(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3, String str3, int i);

    protected abstract void sendMultipartTextwithOptions(String str, String str2, ArrayList<String> arrayList, ArrayList<PendingIntent> arrayList2, ArrayList<PendingIntent> arrayList3, Uri uri, String str3, boolean z, int i, int i2, int i3);

    protected abstract void sendNewSubmitPdu(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, AtomicInteger atomicInteger, AtomicBoolean atomicBoolean, Uri uri);

    protected abstract void sendOTADomestic(String str, String str2, String str3);

    protected abstract void sendSms(SmsTracker smsTracker);

    protected abstract void sendSmsByPstn(SmsTracker smsTracker);

    protected abstract void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4);

    protected abstract void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, String str4, int i);

    protected abstract void sendTextNSRI(String str, String str2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i, int i2);

    protected abstract void sendTextwithOptions(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, Uri uri, String str4, boolean z, int i, int i2, int i3, int i4);

    protected abstract void updateSmsSendStatus(int i, boolean z);

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef++;
        return sConcatenatedRef;
    }

    protected SMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        this.mPhone = phone;
        this.mImsSMSDispatcher = imsSMSDispatcher;
        this.mContext = phone.getContext();
        this.mResolver = this.mContext.getContentResolver();
        this.mCi = phone.mCi;
        this.mUsageMonitor = usageMonitor;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mSettingsObserver = new SettingsObserver(this, this.mPremiumSmsRule, this.mContext);
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("sms_short_code_rule"), false, this.mSettingsObserver);
        this.mSmsCapable = this.mContext.getResources().getBoolean(17956934);
        this.mSmsSendDisabled = !SystemProperties.getBoolean("telephony.sms.send", this.mSmsCapable);
        Rlog.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + this.mSmsCapable + " format=" + getFormat() + " mSmsSendDisabled=" + this.mSmsSendDisabled);
        this.mContext.registerReceiver(this.mGcfModeReceiver, new IntentFilter(GCF_MODE_ACTION));
    }

    protected void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mUsageMonitor = phone.mSmsUsageMonitor;
        Rlog.d(TAG, "Active phone changed to " + this.mPhone.getPhoneName());
    }

    public void dispose() {
        this.mIsDisposed = true;
        this.mContext.getContentResolver().unregisterContentObserver(this.mSettingsObserver);
    }

    protected void handleStatusReport(Object o) {
        Rlog.d(TAG, "handleStatusReport() called with no subclass.");
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 2:
                handleSendComplete((AsyncResult) msg.obj);
                return;
            case 3:
                Rlog.d(TAG, "SMS retry..");
                sendRetrySms((SmsTracker) msg.obj);
                return;
            case 4:
                handleReachSentLimit((SmsTracker) msg.obj);
                return;
            case 5:
                SmsTracker tracker = msg.obj;
                if (tracker.isMultipart()) {
                    sendMultipartSms(tracker);
                } else {
                    if (this.mPendingTrackerCount > 1) {
                        tracker.mExpectMore = true;
                    } else {
                        tracker.mExpectMore = false;
                    }
                    sendSms(tracker);
                }
                this.mPendingTrackerCount--;
                return;
            case 7:
                ((SmsTracker) msg.obj).onFailed(this.mContext, 5, 0);
                this.mPendingTrackerCount--;
                return;
            case 8:
                handleConfirmShortCode(false, (SmsTracker) msg.obj);
                return;
            case 9:
                handleConfirmShortCode(true, (SmsTracker) msg.obj);
                return;
            case 10:
                handleStatusReport(msg.obj);
                return;
            case 16:
                retryGetSmsc = 0;
                this.mCi.getSmscAddress(obtainMessage(17));
                broadcastCbSettingsAvailable();
                getTpmr();
                return;
            case 17:
                AsyncResult ar = msg.obj;
                if (ar.exception != null) {
                    sendMessageDelayed(obtainMessage(18, ar), 5000);
                    return;
                }
                this.Sim_Smsc = (String) ar.result;
                dispatchSmsServiceCenter((String) ar.result);
                return;
            case 18:
                Rlog.d(TAG, "EVENT_GET_SMSC_DONE_EXTEND");
                if (((AsyncResult) msg.obj).exception != null && retryGetSmsc < 6) {
                    this.mCi.getSmscAddress(obtainMessage(17));
                    retryGetSmsc++;
                    Rlog.e(TAG, "retryGetSmsc count = " + retryGetSmsc);
                    return;
                }
                return;
            case 19:
                handleRadioStateChanged();
                return;
            default:
                Rlog.e(TAG, "handleMessage() ignoring message of unexpected type " + msg.what);
                return;
        }
    }

    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = ar.userObj;
        PendingIntent sentIntent = tracker.mSentIntent;
        boolean isIms = isIms();
        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            long mVoicesubId = SubscriptionManager.getDefaultVoiceSubId();
            if (mVoicesubId != SubscriptionManager.getDefaultSmsSubId()) {
                Rlog.d(TAG, "restore to SMS subId from voice subId");
                SubscriptionManager.setDefaultSmsSubId(mVoicesubId);
            }
        }
        if (ar.result != null) {
            tracker.mMessageRef = ((SmsResponse) ar.result).mMessageRef;
        } else {
            Rlog.d(TAG, "SmsResponse was null");
        }
        if (ar.exception == null) {
            Rlog.d(TAG, "Requested Application : " + tracker.mAppInfo.applicationInfo.packageName);
            if (getEnableMultiSim()) {
                tracker.onSent(this.mContext, this.mPhone.getPhoneId(), MultiSimManager.getSubscriberId(this.mPhone.getPhoneId()));
            } else {
                tracker.onSent(this.mContext);
            }
            if (tracker.mDeliveryIntent != null) {
                this.deliveryPendingList.add(tracker);
            }
            AuditLog.log(5, 5, true, Process.myPid(), TAG, "Sending sms succeeded.");
            int errorCode = 0;
            return;
        }
        AuditLog.log(5, 5, false, Process.myPid(), TAG, "Sending SMS failed.");
        PhoneRestrictionPolicy edm = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (edm != null && edm.isLimitNumberOfSmsEnabled()) {
            edm.decreaseNumberOfOutgoingSms();
        }
        int ss = this.mPhone.getServiceState().getState();
        if (tracker.mImsRetry > 0 && ss != 0) {
            tracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS= " + this.mPhone.getServiceState().getState());
        }
        if (isIms() || ss == 0) {
            if (((CommandException) ar.exception).getCommandError() == Error.SMS_FAIL_RETRY && tracker.mRetryCount < 3) {
                tracker.mRetryCount++;
                sendMessageDelayed(obtainMessage(3, tracker), 2000);
                errorCode = 0;
            } else if (tracker.mSentIntent != null) {
                int error = 1;
                if (((CommandException) ar.exception).getCommandError() == Error.FDN_CHECK_FAILURE) {
                    error = 6;
                }
                try {
                    Intent fillIn = new Intent();
                    if (ar.result != null) {
                        errorCode = ((SmsResponse) ar.result).mErrorCode;
                        try {
                            HashMap map = tracker.mData;
                            if (map.containsKey("destAddr")) {
                                tracker.mOrigAddr = (String) map.get("destAddr");
                            }
                        } catch (Exception e) {
                            return;
                        }
                    }
                    errorCode = 0;
                    if (getEnableMultiSim()) {
                        tracker.onFailed(this.mContext, error, errorCode, this.mPhone.getPhoneId(), MultiSimManager.getSubscriberId(this.mPhone.getPhoneId()));
                        return;
                    }
                    tracker.onFailed(this.mContext, error, errorCode);
                } catch (Exception e2) {
                    errorCode = 0;
                }
            } else {
                if (ar.result != null) {
                    errorCode = ((SmsResponse) ar.result).mErrorCode;
                } else {
                    errorCode = 0;
                }
                tracker.onFailed(this.mContext, 1, errorCode);
            }
        } else if (getEnableMultiSim()) {
            tracker.onFailed(this.mContext, 5, 0, this.mPhone.getPhoneId(), MultiSimManager.getSubscriberId(this.mPhone.getPhoneId()));
            errorCode = 0;
        } else {
            tracker.onFailed(this.mContext, 5, 0);
            errorCode = 0;
        }
    }

    protected void handleNotInService(int ss, PendingIntent sentIntent) {
        if (sentIntent == null) {
            return;
        }
        if (ss == 3) {
            try {
                sentIntent.send(2);
                return;
            } catch (CanceledException e) {
                return;
            }
        }
        sentIntent.send(4);
    }

    protected static int getNotInServiceError(int ss) {
        if (ss == 3) {
            return 2;
        }
        return 4;
    }

    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, Uri messageUri, String callingPkg) {
        int i;
        if (messageUri == null) {
            if (SmsApplication.shouldWriteMessageForPackage(callingPkg, this.mContext)) {
                long subId = getSubId();
                String multipartMessageText = getMultipartMessageText(parts);
                boolean z = deliveryIntents != null && deliveryIntents.size() > 0;
                messageUri = writeOutboxMessage(subId, destAddr, multipartMessageText, z, callingPkg);
            }
        } else {
            moveToOutbox(getSubId(), messageUri, callingPkg);
        }
        int refNumber = getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (i = 0; i < msgCount; i++) {
            TextEncodingDetails details = calculateLength((CharSequence) parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }
        AtomicInteger unsentPartCount = new AtomicInteger(msgCount);
        AtomicBoolean anyPartFailed = new AtomicBoolean(false);
        i = 0;
        while (i < msgCount) {
            boolean z2;
            ConcatRef concatRef = new ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == 1) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = (PendingIntent) deliveryIntents.get(i);
            }
            String str = (String) parts.get(i);
            if (i == msgCount - 1) {
                z2 = true;
            } else {
                z2 = false;
            }
            sendNewSubmitPdu(destAddr, scAddr, str, smsHeader, encoding, sentIntent, deliveryIntent, z2, unsentPartCount, anyPartFailed, messageUri);
            i++;
        }
    }

    protected void sendRawPdu(SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.mData.get("pdu");
        if (this.mSmsSendDisabled) {
            Rlog.e(TAG, "Device does not support sending sms.");
            tracker.onFailed(this.mContext, 4, 0);
        } else if (pdu == null) {
            Rlog.e(TAG, "Empty PDU");
            tracker.onFailed(this.mContext, 3, 0);
        } else {
            PackageManager pm = this.mContext.getPackageManager();
            String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
            if (packageNames == null || packageNames.length == 0) {
                Rlog.e(TAG, "Can't get calling app package name: refusing to send SMS");
                Rlog.e(TAG, "CallingUid = " + Binder.getCallingUid());
                tracker.onFailed(this.mContext, 1, 0);
                return;
            }
            try {
                PackageInfo appInfo = pm.getPackageInfo(packageNames[0], 64);
                if (!checkDestination(tracker)) {
                    return;
                }
                if (this.mUsageMonitor.check(appInfo.packageName, 1)) {
                    String isCallBlock = SystemProperties.get("ril.call_block", "false");
                    if (("slot1call".equals(isCallBlock) && 1 == this.mPhone.getPhoneId()) || ("slot2call".equals(isCallBlock) && this.mPhone.getPhoneId() == 0)) {
                        Rlog.e(TAG, "Reject send sms ,  DSDS isCallBlock = " + isCallBlock);
                        tracker.onFailed(this.mContext, 1, 0);
                        return;
                    }
                    sendSms(tracker);
                    return;
                }
                sendMessage(obtainMessage(4, tracker));
            } catch (NameNotFoundException e) {
                Rlog.e(TAG, "Can't get calling app package info: refusing to send SMS");
                tracker.onFailed(this.mContext, 1, 0);
                Rlog.e(TAG, "CallingUid = " + Binder.getCallingUid());
                Rlog.e(TAG, "packageNames = " + packageNames[0]);
            }
        }
    }

    boolean checkDestination(SmsTracker tracker) {
        if (this.mContext.checkCallingOrSelfPermission(SEND_RESPOND_VIA_MESSAGE_PERMISSION) == 0 && tracker.mAppInfo != null && tracker.mAppInfo.packageName != null && !SEC_SMS_PACKAGE_NAME.equals(tracker.mAppInfo.packageName)) {
            return true;
        }
        int rule = this.mPremiumSmsRule.get();
        int smsCategory = 0;
        if (rule == 1 || rule == 3) {
            String simCountryIso = this.mTelephonyManager.getSimCountryIso();
            if (simCountryIso == null || simCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get SIM country Iso: trying network country Iso");
                simCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            }
            this.mUsageMonitor.setSimOperator(this.mTelephonyManager.getSimOperator());
            smsCategory = this.mUsageMonitor.checkDestination(tracker.mDestAddress, simCountryIso);
        }
        if (rule == 2 || rule == 3) {
            String networkCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            if (networkCountryIso == null || networkCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get Network country Iso: trying SIM country Iso");
                networkCountryIso = this.mTelephonyManager.getSimCountryIso();
            }
            smsCategory = SmsUsageMonitor.mergeShortCodeCategories(smsCategory, this.mUsageMonitor.checkDestination(tracker.mDestAddress, networkCountryIso));
        }
        if (smsCategory == 0 || smsCategory == 1 || smsCategory == 2) {
            return true;
        }
        int premiumSmsPermission = this.mUsageMonitor.getPremiumSmsPermission(tracker.mAppInfo.packageName);
        if (premiumSmsPermission == 0) {
            premiumSmsPermission = 1;
        }
        switch (premiumSmsPermission) {
            case 2:
                Rlog.w(TAG, "User denied this app from sending to premium SMS");
                sendMessage(obtainMessage(7, tracker));
                return false;
            case 3:
                Rlog.d(TAG, "User approved this app to send to premium SMS");
                return true;
            default:
                int event;
                if (smsCategory == 3) {
                    event = 8;
                } else {
                    event = 9;
                }
                sendMessage(obtainMessage(event, tracker));
                return false;
        }
    }

    private boolean denyIfQueueLimitReached(SmsTracker tracker) {
        if (this.mPendingTrackerCount >= 5) {
            Rlog.e(TAG, "Denied because queue limit reached");
            tracker.onFailed(this.mContext, 5, 0);
            return true;
        }
        this.mPendingTrackerCount++;
        return false;
    }

    private CharSequence getAppLabel(String appPackage) {
        PackageManager pm = this.mContext.getPackageManager();
        try {
            appPackage = pm.getApplicationInfo(appPackage, 0).loadLabel(pm);
        } catch (NameNotFoundException e) {
            Rlog.e(TAG, "PackageManager Name Not Found for package " + appPackage);
        }
        return appPackage;
    }

    protected void handleReachSentLimit(SmsTracker tracker) {
        if (!denyIfQueueLimitReached(tracker)) {
            AlertDialog d;
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(17040588, new Object[]{appLabel}));
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, null);
            if (SystemProperties.get("ro.build.scafe.cream").equals("black")) {
                d = new Builder(this.mContext, 4).setTitle(17040587).setIcon(17301642).setMessage(messageText).setPositiveButton(r.getString(17040589), listener).setNegativeButton(r.getString(17040590), listener).setOnCancelListener(listener).create();
            } else {
                d = new Builder(this.mContext, 5).setTitle(17040587).setIcon(17301642).setMessage(messageText).setPositiveButton(r.getString(17040589), listener).setNegativeButton(r.getString(17040590), listener).setOnCancelListener(listener).create();
            }
            d.getWindow().setType(2003);
            d.show();
        }
    }

    protected void handleConfirmShortCode(boolean isPremium, SmsTracker tracker) {
        if (!denyIfQueueLimitReached(tracker)) {
            int detailsId;
            AlertDialog d;
            if (isPremium) {
                detailsId = 17040593;
            } else {
                detailsId = 17040592;
            }
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(17040591, new Object[]{appLabel, tracker.mDestAddress}));
            View layout = ((LayoutInflater) this.mContext.getSystemService("layout_inflater")).inflate(17367283, null);
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, (TextView) layout.findViewById(16909407));
            TextView messageView = (TextView) layout.findViewById(16909401);
            messageView.setText(messageText);
            TextView detailsView = (TextView) ((ViewGroup) layout.findViewById(16909402)).findViewById(16909404);
            detailsView.setText(detailsId);
            CheckBox rememberChoice = (CheckBox) layout.findViewById(16909405);
            rememberChoice.setOnCheckedChangeListener(listener);
            TextView rememberTextView = (TextView) layout.findViewById(16909406);
            TextView rememberInstructionView = (TextView) layout.findViewById(16909407);
            if (SystemProperties.get("ro.build.scafe.cream").equals("black")) {
                messageView.setTextColor(-1);
                detailsView.setTextColor(-7829368);
                rememberTextView.setTextColor(-1);
                rememberChoice.setTextColor(-7829368);
                d = new Builder(this.mContext, 4).setView(layout).setPositiveButton(r.getString(17040594), listener).setNegativeButton(r.getString(17040595), listener).setOnCancelListener(listener).create();
            } else {
                messageView.setTextColor(-16777216);
                SpannableStringBuilder sp = new SpannableStringBuilder(detailsView.getText());
                sp.setSpan(new ForegroundColorSpan(-16777216), 0, detailsView.getText().length(), 33);
                detailsView.setText(sp);
                rememberTextView.setTextColor(-16777216);
                rememberInstructionView.setTextColor(-16777216);
                d = new Builder(this.mContext, 5).setView(layout).setPositiveButton(r.getString(17040594), listener).setNegativeButton(r.getString(17040595), listener).setOnCancelListener(listener).create();
            }
            d.getWindow().setType(2003);
            d.show();
            listener.setPositiveButton(d.getButton(-1));
            listener.setNegativeButton(d.getButton(-2));
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mUsageMonitor.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mUsageMonitor.setPremiumSmsPermission(packageName, permission);
    }

    public void sendRetrySms(SmsTracker tracker) {
        if (this.mImsSMSDispatcher != null) {
            this.mImsSMSDispatcher.sendRetrySms(tracker);
        } else {
            Rlog.e(TAG, this.mImsSMSDispatcher + " is null. Retry failed");
        }
    }

    private void sendMultipartSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        String destinationAddress = (String) map.get("destination");
        String scAddress = (String) map.get("scaddress");
        ArrayList parts = (ArrayList) map.get("parts");
        ArrayList sentIntents = (ArrayList) map.get("sentIntents");
        ArrayList deliveryIntents = (ArrayList) map.get("deliveryIntents");
        int ss = this.mPhone.getServiceState().getState();
        if (isIms() || ss == 0) {
            sendMultipartText(destinationAddress, scAddress, parts, sentIntents, deliveryIntents, null, null);
            return;
        }
        int i = 0;
        int count = parts.size();
        while (i < count) {
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = (PendingIntent) sentIntents.get(i);
            }
            handleNotInService(ss, sentIntent);
            i++;
        }
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, AtomicInteger unsentPartCount, AtomicBoolean anyPartFailed, Uri messageUri, SmsHeader smsHeader, boolean isExpectMore) {
        PackageManager pm = this.mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
        PackageInfo appInfo = null;
        if (packageNames != null && packageNames.length > 0) {
            try {
                appInfo = pm.getPackageInfo(packageNames[0], 64);
            } catch (NameNotFoundException e) {
            }
        }
        return new SmsTracker(data, sentIntent, deliveryIntent, appInfo, PhoneNumberUtils.extractNetworkPortion((String) data.get("destAddr")), format, unsentPartCount, anyPartFailed, messageUri, smsHeader, isExpectMore);
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, Uri messageUri, boolean isExpectMore) {
        return getSmsTracker(data, sentIntent, deliveryIntent, format, null, null, messageUri, null, isExpectMore);
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, String text, SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put(Part.TEXT, text);
        if (pdu != null) {
            map.put("smsc", pdu.encodedScAddress);
            map.put("pdu", pdu.encodedMessage);
        }
        return map;
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, int destPort, byte[] data, SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("destPort", Integer.valueOf(destPort));
        map.put("data", data);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    protected HashMap getSmsTrackerMap(String destAddr, String scAddr, int destPort, int origPort, byte[] data, SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("destPort", Integer.valueOf(destPort));
        map.put("origPort", Integer.valueOf(origPort));
        map.put("data", data);
        if (pdu != null) {
            map.put("smsc", pdu.encodedScAddress);
            map.put("pdu", pdu.encodedMessage);
        }
        return map;
    }

    public boolean isIms() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.isIms();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public boolean setDanFail(boolean danFail) {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.setDanFail(danFail);
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public boolean restartDanTimer() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.restartDanTimer();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public String getImsSmsFormat() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.getImsSmsFormat();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return null;
    }

    public String getSmsc() {
        if (this.Sim_Smsc == null) {
            this.mCi.getSmscAddress(obtainMessage(17));
        }
        return this.Sim_Smsc;
    }

    public String getSmscNumber(byte[] a, boolean garbage_value) {
        StringBuffer buf = new StringBuffer(SMSC_ADDRESS_LENGTH);
        boolean international = false;
        if (a[0] == (byte) 0) {
            return "NotSet";
        }
        String smsc;
        if (a[1] == (byte) -111) {
            buf.append("+");
            international = true;
        }
        byte[] temp2 = new byte[10];
        System.arraycopy(a, 1 + 1, temp2, 0, a.length - 2);
        for (int cx = 0; cx < temp2.length; cx++) {
            if (temp2[cx] != (byte) -1) {
                int hn = (temp2[cx] & 255) / 16;
                buf.append(hexDigitChars.charAt(temp2[cx] & 15));
                buf.append(hexDigitChars.charAt(hn));
            }
        }
        String temp_smsc = buf.toString();
        int smsc_length = (a[0] - 1) * 2;
        if (international) {
            smsc = temp_smsc.substring(0, smsc_length + 1);
            Rlog.d(TAG, "international even smsc = " + smsc);
        } else {
            smsc = temp_smsc.substring(0, smsc_length);
        }
        if (garbage_value) {
            smsc = smsc.substring(0, smsc.length() - 1);
        }
        Rlog.d(TAG, "smsc = " + smsc);
        return smsc;
    }

    private void broadcastCbSettingsAvailable() {
        Rlog.d(TAG, "[CB] broadcastCbSettingsAvailable method");
        Intent intent = new Intent(Intents.CB_SETTINGS_AVAILABLE_ACTION);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mPhone.getContext().sendBroadcast(intent);
    }

    private void getTpmr() {
        Rlog.d(TAG, "getTpmr");
    }

    protected void handleRadioStateChanged() {
        Rlog.d(TAG, "[SMSDispatcher] handleRadioStateChanged: " + this.mCi.getRadioState());
        if (!this.mCi.getRadioState().isOn()) {
            clearDuplicatedCbMessages();
        }
    }

    protected Uri writeOutboxMessage(long subId, String address, String text, boolean requireDeliveryReport, String creator) {
        Uri uri;
        ContentValues values = new ContentValues(8);
        values.put("sub_id", Long.valueOf(subId));
        values.put("address", address);
        values.put("body", text);
        values.put("date", Long.valueOf(System.currentTimeMillis()));
        values.put("seen", Integer.valueOf(1));
        values.put("read", Integer.valueOf(1));
        if (!TextUtils.isEmpty(creator)) {
            values.put("creator", creator);
        }
        if (requireDeliveryReport) {
            values.put(TextBasedSmsColumns.STATUS, Integer.valueOf(32));
        }
        if (getEnableMultiSim()) {
            values.put("sim_slot", Integer.valueOf(this.mPhone.getPhoneId()));
        }
        long identity = Binder.clearCallingIdentity();
        try {
            uri = this.mContext.getContentResolver().insert(Outbox.CONTENT_URI, values);
        } catch (Exception e) {
            Rlog.e(TAG, "writeOutboxMessage: Failed to persist outbox message", e);
            uri = null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return uri;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected void moveToOutbox(long r10, android.net.Uri r12, java.lang.String r13) {
        /*
        r9 = this;
        r8 = 4;
        r1 = new android.content.ContentValues;
        r1.<init>(r8);
        r4 = "sub_id";
        r5 = java.lang.Long.valueOf(r10);
        r1.put(r4, r5);
        r4 = android.text.TextUtils.isEmpty(r13);
        if (r4 != 0) goto L_0x001a;
    L_0x0015:
        r4 = "creator";
        r1.put(r4, r13);
    L_0x001a:
        r4 = "date";
        r6 = java.lang.System.currentTimeMillis();
        r5 = java.lang.Long.valueOf(r6);
        r1.put(r4, r5);
        r4 = "type";
        r5 = java.lang.Integer.valueOf(r8);
        r1.put(r4, r5);
        r2 = android.os.Binder.clearCallingIdentity();
        r4 = r9.mContext;	 Catch:{ Exception -> 0x005f }
        r4 = r4.getContentResolver();	 Catch:{ Exception -> 0x005f }
        r5 = 0;
        r6 = 0;
        r4 = r4.update(r12, r1, r5, r6);	 Catch:{ Exception -> 0x005f }
        r5 = 1;
        if (r4 == r5) goto L_0x005b;
    L_0x0043:
        r4 = "SMSDispatcher";
        r5 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x005f }
        r5.<init>();	 Catch:{ Exception -> 0x005f }
        r6 = "moveToOutbox: failed to update message ";
        r5 = r5.append(r6);	 Catch:{ Exception -> 0x005f }
        r5 = r5.append(r12);	 Catch:{ Exception -> 0x005f }
        r5 = r5.toString();	 Catch:{ Exception -> 0x005f }
        android.telephony.Rlog.e(r4, r5);	 Catch:{ Exception -> 0x005f }
    L_0x005b:
        android.os.Binder.restoreCallingIdentity(r2);
    L_0x005e:
        return;
    L_0x005f:
        r0 = move-exception;
        r4 = "SMSDispatcher";
        r5 = "moveToOutbox: Failed to update message";
        android.telephony.Rlog.e(r4, r5, r0);	 Catch:{ all -> 0x006b }
        android.os.Binder.restoreCallingIdentity(r2);
        goto L_0x005e;
    L_0x006b:
        r4 = move-exception;
        android.os.Binder.restoreCallingIdentity(r2);
        throw r4;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SMSDispatcher.moveToOutbox(long, android.net.Uri, java.lang.String):void");
    }

    protected String getMultipartMessageText(ArrayList<String> parts) {
        StringBuilder sb = new StringBuilder();
        Iterator i$ = parts.iterator();
        while (i$.hasNext()) {
            String part = (String) i$.next();
            if (part != null) {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    protected String getCarrierAppPackageName(Intent intent) {
        UiccCard card = UiccController.getInstance().getUiccCard();
        if (card == null) {
            return null;
        }
        List<String> carrierPackages = card.getCarrierPackageNamesForIntent(this.mContext.getPackageManager(), intent);
        if (carrierPackages == null || carrierPackages.size() != 1) {
            return null;
        }
        return (String) carrierPackages.get(0);
    }

    protected long getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(this.mPhone.mPhoneId);
    }

    public static boolean getCDMASmsReassembly() {
        boolean onOff = SystemProperties.getBoolean("ril.sms.reassembly", false);
        Rlog.d(TAG, "getCDMASmsReassembly = " + onOff);
        return onOff;
    }

    public static void setCDMASmsReassembly(boolean onOff) {
        Rlog.d(TAG, "setCDMASmsReassembly = " + onOff);
        if (onOff) {
            SystemProperties.set("ril.sms.reassembly", "true");
        } else {
            SystemProperties.set("ril.sms.reassembly", "false");
        }
    }

    protected boolean isSMSBlocked(String phoneNumber, boolean send) {
        boolean result = false;
        PhoneRestrictionPolicy restrictionPolicy = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (restrictionPolicy != null) {
            result = restrictionPolicy.getEmergencyCallOnly(true);
            if (!result) {
                if (!send) {
                    result = (restrictionPolicy.isIncomingSmsAllowed() && restrictionPolicy.canIncomingSms(phoneNumber)) ? false : true;
                } else if (restrictionPolicy.isOutgoingSmsAllowed() && restrictionPolicy.canOutgoingSms(phoneNumber)) {
                    result = false;
                } else {
                    result = true;
                }
            }
        }
        Rlog.d(TAG, "isSMSBlocked=" + result);
        if (result) {
            AuditLog.log(5, 5, false, Process.myPid(), getClass().getSimpleName(), "Sending sms failed. Blocked by phone restriction policy.");
        }
        return result;
    }

    protected void storeSMS(String address, String timeStamp, String message, boolean isInbound) {
        DeviceInventory deviceInventory = EnterpriseDeviceManager.getInstance().getDeviceInventory();
        PhoneRestrictionPolicy phoneRestriction = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
        if (!isInbound && phoneRestriction.isLimitNumberOfSmsEnabled()) {
            phoneRestriction.addNumberOfOutgoingSms();
        }
        if (deviceInventory != null && deviceInventory.isSMSCaptureEnabled()) {
            deviceInventory.storeSMS(address, timeStamp, message, isInbound);
        }
        AuditLog.log(5, 5, true, Process.myPid(), getClass().getSimpleName(), "Sending sms succeeded.");
    }

    protected void initSelloutSms(Context context) {
        FileNotFoundException e;
        File file;
        IOException e2;
        Cursor cursor;
        ContentValues contentValue;
        boolean sEnableSSMS;
        String sSSMSOperator;
        String productCode;
        int productInfo;
        String model;
        Throwable th;
        FileInputStream in = null;
        FileOutputStream out = null;
        byte[] buffer = new byte[1];
        int ssms_state = 0;
        try {
            File folder = new File("/efs/SMS");
            if (!folder.exists()) {
                boolean status = folder.mkdirs();
                folder.setReadable(true, false);
                folder.setWritable(true, true);
                folder.setExecutable(true, false);
                Log.d(TAG, "initSelloutSms make folder /efs/SMS  directory creation status: " + status);
            }
            File file2 = new File("/efs/SMS/sellout_sms");
            if (file2 == null) {
                try {
                    Log.d(TAG, "initSelloutSms NullPointer");
                } catch (FileNotFoundException e3) {
                    e = e3;
                    file = file2;
                    try {
                        Log.e(TAG, "initSelloutSms file new File error " + e);
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e22) {
                                Log.e(TAG, "IOException caught while closing fileOutputStream", e22);
                            }
                        }
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e222) {
                                Log.e(TAG, "IOException caught while closing FileInputStream", e222);
                            }
                        }
                        cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                        if (cursor != null) {
                            try {
                                if (cursor.getCount() == 0) {
                                    contentValue = new ContentValues();
                                    sEnableSSMS = false;
                                    sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                                    sEnableSSMS = true;
                                    contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                                    Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                                    contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                                    productCode = SystemProperties.get("ril.product_code", "none");
                                    productInfo = 0;
                                    if (!"Not Active".equals(productCode)) {
                                    }
                                    model = SystemProperties.get("ro.product.model");
                                    if (model.length() > 8) {
                                        model = model.substring(0, 8);
                                    }
                                    productCode = model + "BRTEST";
                                    productInfo = 1;
                                    contentValue.put(PRODUCT_CODE, productCode);
                                    contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                                    context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                                }
                                cursor.close();
                            } catch (Throwable th2) {
                                cursor.close();
                            }
                        }
                    } catch (Throwable th3) {
                        th = th3;
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e2222) {
                                Log.e(TAG, "IOException caught while closing fileOutputStream", e2222);
                            }
                        }
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e22222) {
                                Log.e(TAG, "IOException caught while closing FileInputStream", e22222);
                            }
                        }
                        throw th;
                    }
                } catch (IOException e4) {
                    e22222 = e4;
                    file = file2;
                    Log.e(TAG, "initSelloutSms createNewFile error " + e22222);
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e222222) {
                            Log.e(TAG, "IOException caught while closing fileOutputStream", e222222);
                        }
                    }
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e2222222) {
                            Log.e(TAG, "IOException caught while closing FileInputStream", e2222222);
                        }
                    }
                    cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.getCount() == 0) {
                            contentValue = new ContentValues();
                            sEnableSSMS = false;
                            sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                            sEnableSSMS = true;
                            contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                            Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                            contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                            productCode = SystemProperties.get("ril.product_code", "none");
                            productInfo = 0;
                            if ("Not Active".equals(productCode)) {
                            }
                            model = SystemProperties.get("ro.product.model");
                            if (model.length() > 8) {
                                model = model.substring(0, 8);
                            }
                            productCode = model + "BRTEST";
                            productInfo = 1;
                            contentValue.put(PRODUCT_CODE, productCode);
                            contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                            context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                        }
                        cursor.close();
                    }
                } catch (Throwable th4) {
                    th = th4;
                    file = file2;
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    throw th;
                }
            } else if (file2.exists()) {
                FileInputStream fileInputStream = new FileInputStream(file2);
                try {
                    fileInputStream.read(buffer);
                    ssms_state = buffer[0] & 255;
                    Log.d(TAG, "initSelloutSms [Read] ssms_state: " + ssms_state);
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    in = fileInputStream;
                } catch (FileNotFoundException e5) {
                    e = e5;
                    in = fileInputStream;
                    file = file2;
                    Log.e(TAG, "initSelloutSms file new File error " + e);
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.getCount() == 0) {
                            contentValue = new ContentValues();
                            sEnableSSMS = false;
                            sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                            sEnableSSMS = true;
                            contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                            Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                            contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                            productCode = SystemProperties.get("ril.product_code", "none");
                            productInfo = 0;
                            if ("Not Active".equals(productCode)) {
                            }
                            model = SystemProperties.get("ro.product.model");
                            if (model.length() > 8) {
                                model = model.substring(0, 8);
                            }
                            productCode = model + "BRTEST";
                            productInfo = 1;
                            contentValue.put(PRODUCT_CODE, productCode);
                            contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                            context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                        }
                        cursor.close();
                    }
                } catch (IOException e6) {
                    e2222222 = e6;
                    in = fileInputStream;
                    file = file2;
                    Log.e(TAG, "initSelloutSms createNewFile error " + e2222222);
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.getCount() == 0) {
                            contentValue = new ContentValues();
                            sEnableSSMS = false;
                            sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                            sEnableSSMS = true;
                            contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                            Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                            contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                            productCode = SystemProperties.get("ril.product_code", "none");
                            productInfo = 0;
                            if ("Not Active".equals(productCode)) {
                            }
                            model = SystemProperties.get("ro.product.model");
                            if (model.length() > 8) {
                                model = model.substring(0, 8);
                            }
                            productCode = model + "BRTEST";
                            productInfo = 1;
                            contentValue.put(PRODUCT_CODE, productCode);
                            contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                            context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                        }
                        cursor.close();
                    }
                } catch (Throwable th5) {
                    th = th5;
                    in = fileInputStream;
                    file = file2;
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    throw th;
                }
            } else {
                Log.d(TAG, "initSelloutSms file.exists() == false");
                file2.createNewFile();
                Log.d(TAG, "initSelloutSms file is created");
                file2.setReadable(true, false);
                FileOutputStream fileOutputStream = new FileOutputStream(file2);
                try {
                    buffer[0] = (byte) 0;
                    fileOutputStream.write(buffer);
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                        out = fileOutputStream;
                    } else {
                        out = fileOutputStream;
                    }
                } catch (FileNotFoundException e7) {
                    e = e7;
                    out = fileOutputStream;
                    file = file2;
                    Log.e(TAG, "initSelloutSms file new File error " + e);
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.getCount() == 0) {
                            contentValue = new ContentValues();
                            sEnableSSMS = false;
                            sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                            sEnableSSMS = true;
                            contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                            Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                            contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                            productCode = SystemProperties.get("ril.product_code", "none");
                            productInfo = 0;
                            if ("Not Active".equals(productCode)) {
                            }
                            model = SystemProperties.get("ro.product.model");
                            if (model.length() > 8) {
                                model = model.substring(0, 8);
                            }
                            productCode = model + "BRTEST";
                            productInfo = 1;
                            contentValue.put(PRODUCT_CODE, productCode);
                            contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                            context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                        }
                        cursor.close();
                    }
                } catch (IOException e8) {
                    e2222222 = e8;
                    out = fileOutputStream;
                    file = file2;
                    Log.e(TAG, "initSelloutSms createNewFile error " + e2222222);
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
                    if (cursor != null) {
                        if (cursor.getCount() == 0) {
                            contentValue = new ContentValues();
                            sEnableSSMS = false;
                            sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                            sEnableSSMS = true;
                            contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                            Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                            contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                            productCode = SystemProperties.get("ril.product_code", "none");
                            productInfo = 0;
                            if ("Not Active".equals(productCode)) {
                            }
                            model = SystemProperties.get("ro.product.model");
                            if (model.length() > 8) {
                                model = model.substring(0, 8);
                            }
                            productCode = model + "BRTEST";
                            productInfo = 1;
                            contentValue.put(PRODUCT_CODE, productCode);
                            contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                            context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                        }
                        cursor.close();
                    }
                } catch (Throwable th6) {
                    th = th6;
                    out = fileOutputStream;
                    file = file2;
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    throw th;
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e22222222) {
                    Log.e(TAG, "IOException caught while closing fileOutputStream", e22222222);
                }
            }
            if (in != null) {
                try {
                    in.close();
                    file = file2;
                } catch (IOException e222222222) {
                    Log.e(TAG, "IOException caught while closing FileInputStream", e222222222);
                    file = file2;
                }
            }
        } catch (FileNotFoundException e9) {
            e = e9;
            Log.e(TAG, "initSelloutSms file new File error " + e);
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() == 0) {
                    contentValue = new ContentValues();
                    sEnableSSMS = false;
                    sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                    sEnableSSMS = true;
                    contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                    Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                    contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                    productCode = SystemProperties.get("ril.product_code", "none");
                    productInfo = 0;
                    if ("Not Active".equals(productCode)) {
                    }
                    model = SystemProperties.get("ro.product.model");
                    if (model.length() > 8) {
                        model = model.substring(0, 8);
                    }
                    productCode = model + "BRTEST";
                    productInfo = 1;
                    contentValue.put(PRODUCT_CODE, productCode);
                    contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                    context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                }
                cursor.close();
            }
        } catch (IOException e10) {
            e222222222 = e10;
            Log.e(TAG, "initSelloutSms createNewFile error " + e222222222);
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() == 0) {
                    contentValue = new ContentValues();
                    sEnableSSMS = false;
                    sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                    sEnableSSMS = true;
                    contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                    Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                    contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                    productCode = SystemProperties.get("ril.product_code", "none");
                    productInfo = 0;
                    if ("Not Active".equals(productCode)) {
                    }
                    model = SystemProperties.get("ro.product.model");
                    if (model.length() > 8) {
                        model = model.substring(0, 8);
                    }
                    productCode = model + "BRTEST";
                    productInfo = 1;
                    contentValue.put(PRODUCT_CODE, productCode);
                    contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                    context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
                }
                cursor.close();
            }
        }
        cursor = context.getContentResolver().query(SSMS_CONTENT_URI, new String[]{SSMS_ENABLE}, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 0) {
                contentValue = new ContentValues();
                sEnableSSMS = false;
                sSSMSOperator = CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms");
                if ("ZTO".equals(sSSMSOperator) || "ARO".equals(sSSMSOperator)) {
                    sEnableSSMS = true;
                }
                contentValue.put(SSMS_ENABLE, Boolean.valueOf(sEnableSSMS));
                Log.d(TAG, "initSelloutSms sEnableSSMS=" + sEnableSSMS);
                contentValue.put(SSMS_STATE, Integer.valueOf(ssms_state));
                productCode = SystemProperties.get("ril.product_code", "none");
                productInfo = 0;
                if ("Not Active".equals(productCode) || "none".equals(productCode) || "Unknown".equals(productCode)) {
                    model = SystemProperties.get("ro.product.model");
                    if (model.length() > 8) {
                        model = model.substring(0, 8);
                    }
                    productCode = model + "BRTEST";
                    productInfo = 1;
                } else {
                    if (productCode.contains("TEST")) {
                        productInfo = 1;
                    }
                }
                contentValue.put(PRODUCT_CODE, productCode);
                contentValue.put(PRODUCT_INFO, Integer.valueOf(productInfo));
                context.getContentResolver().insert(SSMS_CONTENT_URI, contentValue);
            }
            cursor.close();
        }
    }

    protected void updateSelloutSmsFile(int SSMS_state) {
        FileNotFoundException e;
        File file;
        IOException e2;
        Throwable th;
        Log.d(TAG, "updateSelloutSmsFile sellout_sms file update - SSMS_state : " + SSMS_state);
        FileOutputStream out = null;
        byte[] buffer = new byte[1];
        try {
            File file2 = new File("/efs/SMS/sellout_sms");
            if (file2 == null) {
                try {
                    Log.d(TAG, "update() NullPointer");
                } catch (FileNotFoundException e3) {
                    e = e3;
                    file = file2;
                    try {
                        Log.e(TAG, "update() file open error " + e);
                        if (out == null) {
                            try {
                                out.close();
                            } catch (IOException e22) {
                                Log.e(TAG, "IOException caught while closing fileOutputStream", e22);
                                return;
                            }
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e222) {
                                Log.e(TAG, "IOException caught while closing fileOutputStream", e222);
                            }
                        }
                        throw th;
                    }
                } catch (IOException e4) {
                    e222 = e4;
                    file = file2;
                    Log.e(TAG, "update() file open error " + e222);
                    if (out == null) {
                        try {
                            out.close();
                        } catch (IOException e2222) {
                            Log.e(TAG, "IOException caught while closing fileOutputStream", e2222);
                            return;
                        }
                    }
                } catch (Throwable th3) {
                    th = th3;
                    file = file2;
                    if (out != null) {
                        out.close();
                    }
                    throw th;
                }
            } else if (file2.exists()) {
                FileOutputStream out2 = new FileOutputStream(file2);
                try {
                    buffer[0] = (byte) SSMS_state;
                    out2.write(buffer);
                    if (out2 != null) {
                        out2.close();
                    }
                    out = out2;
                } catch (FileNotFoundException e5) {
                    e = e5;
                    out = out2;
                    file = file2;
                    Log.e(TAG, "update() file open error " + e);
                    if (out == null) {
                        out.close();
                    }
                } catch (IOException e6) {
                    e2222 = e6;
                    out = out2;
                    file = file2;
                    Log.e(TAG, "update() file open error " + e2222);
                    if (out == null) {
                        out.close();
                    }
                } catch (Throwable th4) {
                    th = th4;
                    out = out2;
                    file = file2;
                    if (out != null) {
                        out.close();
                    }
                    throw th;
                }
            } else {
                Log.d(TAG, "update() File not exists");
            }
            if (out != null) {
                try {
                    out.close();
                    file = file2;
                    return;
                } catch (IOException e22222) {
                    Log.e(TAG, "IOException caught while closing fileOutputStream", e22222);
                    file = file2;
                    return;
                }
            }
        } catch (FileNotFoundException e7) {
            e = e7;
            Log.e(TAG, "update() file open error " + e);
            if (out == null) {
                out.close();
            }
        } catch (IOException e8) {
            e22222 = e8;
            Log.e(TAG, "update() file open error " + e22222);
            if (out == null) {
                out.close();
            }
        }
    }

    protected void sendRawPduSat(byte[] smsc, byte[] pdu, String format, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "re-routing to ImsSMSDispatcher");
        if (this.mImsSMSDispatcher != null) {
            this.mImsSMSDispatcher.sendRawPduSat(smsc, pdu, format, sentIntent, deliveryIntent);
        } else {
            Rlog.e(TAG, "mImsSMSDispatcher is" + this.mImsSMSDispatcher);
        }
    }

    public static boolean getEnableMultiSim() {
        if ("GG".equals("DGG") || "CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
            return true;
        }
        return false;
    }
}
