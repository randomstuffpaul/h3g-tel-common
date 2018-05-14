package com.android.internal.telephony;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.CellBroadcasts;
import android.provider.Telephony.Sms;
import android.telephony.LmsTokenCTC;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Pair;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class LmsAssemblyTrackerCTC extends Handler {
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final int DEST_PORT_FLAG_3GPP = 131072;
    private static final int DEST_PORT_FLAG_3GPP2 = 262144;
    private static final int DEST_PORT_FLAG_NO_PORT = 65536;
    private static final int EVENT_FIRST_DISPLAY_TIMEOUT = 1;
    private static final int EVENT_MAXIMAL_CONNECTION_TIMEOUT = 2;
    private static final String MAXIMAL_CONNECTION_TIME_ALARM_ACTION = "com.android.internal.telephony.CTC_LMS_CONNECTION_ALARM";
    private static final Uri RAW_URI = Uri.withAppendedPath(Sms.CONTENT_URI, "raw");
    private static final String TAG = "LmsAssemblyTrackerCTC";
    private final BroadcastReceiver mAlarmReceiver = new C00171();
    private final Context mContext;
    private final InboundSmsHandler mInboundSmsHandler;
    private boolean mIsAlarmReceiverActive = false;
    private final Map<LmsTokenCTC, LmsTokenCTC> mQueuedLmsTokens = new HashMap();

    class C00171 extends BroadcastReceiver {
        C00171() {
        }

        public void onReceive(Context context, Intent intent) {
            LmsAssemblyTrackerCTC.this.sendEmptyMessage(2);
        }
    }

    LmsAssemblyTrackerCTC(Context context, InboundSmsHandler smshandler) {
        this.mContext = context;
        this.mInboundSmsHandler = smshandler;
        updateMaximalConnectionTimeAlarm();
    }

    private static long getFirstDisplayTimeoutDuration(int msgCount) {
        long defaultValue = (long) (((msgCount - 1) * 20) * 1000);
        if (DEBUG) {
            return getSystemPropertyAsLong("debug.lms_assemble_timeout_1st", defaultValue);
        }
        return defaultValue;
    }

    private static long getMaximalTimeoutDuration() {
        if (DEBUG) {
            return getSystemPropertyAsLong("debug.lms_assemble_timeout_max", 43200000);
        }
        return 43200000;
    }

    private static long getSystemPropertyAsLong(String prop, long defaultValue) {
        try {
            defaultValue = Long.valueOf(System.getProperty(prop, "")).longValue();
        } catch (NumberFormatException e) {
        }
        return defaultValue;
    }

    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case 1:
                    handleFirstDisplayTimeout((LmsTokenCTC) msg.obj);
                    return;
                case 2:
                    handleMaximalConnectionTimeout();
                    return;
                default:
                    return;
            }
        } catch (RuntimeException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
        Log.e(TAG, ex.getMessage(), ex);
    }

    private void handleFirstDisplayTimeout(LmsTokenCTC lmsToken) {
        logd("handleFirstDisplayTimeout, lmsToken=%s", lmsToken);
        cancelFirstDisplayTimeout(lmsToken);
        dispatchIncompleteLms(lmsToken, 1);
        updateMaximalConnectionTimeAlarm();
    }

    private void handleMaximalConnectionTimeout() {
        logd("handleMaximalConnectionTimeout", new Object[0]);
        logd("handleMaximalConnectionTimeout: timed out lms: %s", findStoredMessagePartsOlderThan(System.currentTimeMillis() - getMaximalTimeoutDuration()));
        for (LmsTokenCTC lmsToken : timedOutLms) {
            dispatchIncompleteLms(lmsToken, 2);
            deleteStoredMessageParts(lmsToken);
        }
        updateMaximalConnectionTimeAlarm();
    }

    public void scheduleFirstDisplayTimeout(LmsTokenCTC lmsToken) {
        logd("scheduleFirstDisplayTimeout, lmsToken=%s", lmsToken);
        LmsTokenCTC queuedToken = getOrCreateLmsToken(lmsToken);
        sendMessageDelayed(obtainMessage(1, queuedToken), getFirstDisplayTimeoutDuration(lmsToken.msgCount));
    }

    public void cancelFirstDisplayTimeout(LmsTokenCTC lmsToken) {
        logd("cancelFirstDisplayTimeout, lmsToken=%s", lmsToken);
        LmsTokenCTC queuedToken = getQueuedLmsToken(lmsToken);
        if (queuedToken != null) {
            removeMessages(1, queuedToken);
            removeQueuedLmsToken(queuedToken);
        }
    }

    public boolean hasScheduledFirstDisplayTimeout(LmsTokenCTC lmsToken) {
        LmsTokenCTC queuedToken = getQueuedLmsToken(lmsToken);
        if (queuedToken == null || !hasMessages(1, queuedToken)) {
            return false;
        }
        return true;
    }

    public boolean hasScheduledFirstDisplayTimeout() {
        return hasMessages(1);
    }

    public void updateMaximalConnectionTimeAlarm() {
        logd("updateMaximalConnectionTimeAlarm", new Object[0]);
        Cursor c = queryStoredMessageParts(new String[]{"date"}, null, null, "date ASC");
        if (c == null) {
            Log.w(TAG, "updateMaximalConnectionTimeAlarm: cursor is NULL");
            return;
        }
        Long triggerAtTime = null;
        try {
            if (c.moveToFirst()) {
                triggerAtTime = Long.valueOf(c.getLong(0) + getMaximalTimeoutDuration());
            }
            c.close();
            PendingIntent operation = PendingIntent.getBroadcast(this.mContext, 0, new Intent(MAXIMAL_CONNECTION_TIME_ALARM_ACTION), 268435456);
            AlarmManager am = (AlarmManager) this.mContext.getSystemService("alarm");
            if (triggerAtTime != null) {
                logd("updateMaximalConnectionTimeAlarm: set alarm at %s", new Date(triggerAtTime.longValue()));
                registerAlarmReceiver();
                am.set(1, triggerAtTime.longValue(), operation);
                return;
            }
            logd("updateMaximalConnectionTimeAlarm: no alarm needed", new Object[0]);
            unregisterAlarmReceiver();
            am.cancel(operation);
        } catch (Throwable th) {
            c.close();
        }
    }

    private void registerAlarmReceiver() {
        if (!this.mIsAlarmReceiverActive) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(MAXIMAL_CONNECTION_TIME_ALARM_ACTION);
            this.mContext.registerReceiver(this.mAlarmReceiver, filter);
            this.mIsAlarmReceiverActive = true;
        }
    }

    private void unregisterAlarmReceiver() {
        if (this.mIsAlarmReceiverActive) {
            this.mContext.unregisterReceiver(this.mAlarmReceiver);
            this.mIsAlarmReceiverActive = false;
        }
    }

    public boolean isMaximalConnectionTimeAlarmActive() {
        return this.mIsAlarmReceiverActive;
    }

    private void dispatchIncompleteLms(LmsTokenCTC lmsToken, int lmsStatus) {
        logd("dispatchIncompleteLms, lmsToken=%s, lmsStatus=%d", lmsToken, Integer.valueOf(lmsStatus));
        Cursor cursor = queryStoredMessageParts(SMSDispatcher.RAW_PROJECTION, lmsToken, null);
        if (cursor == null) {
            Rlog.d(TAG, "dispatchIncompleteLms: cursor is NULL");
            return;
        }
        LmsPartCollector collector = new LmsPartCollector(lmsToken.msgCount, false);
        try {
            collector.addAllFromCursor(cursor, cursor.getColumnIndexOrThrow("sequence"), cursor.getColumnIndexOrThrow("pdu"), cursor.getColumnIndexOrThrow("destination_port"));
            if (collector.isEmpty()) {
                Rlog.d(TAG, "dispatchIncompleteLms: no parts stored");
                return;
            }
            int destPort;
            if (SmsMessage.FORMAT_3GPP2.equals(lmsToken.format)) {
                destPort = DEST_PORT_FLAG_NO_PORT | 262144;
            } else {
                destPort = DEST_PORT_FLAG_NO_PORT | DEST_PORT_FLAG_3GPP;
            }
            if (collector.getDestPort() != destPort) {
                Rlog.d(TAG, "dispatchIncompleteLms: ignoring partial dispatch request for port-addressed LMS");
            } else {
                this.mInboundSmsHandler.dispatchPdusCTC(collector.getPdusSequence(), lmsToken, lmsStatus, null);
            }
        } finally {
            cursor.close();
        }
    }

    private Set<LmsTokenCTC> findStoredMessagePartsOlderThan(long maxTime) {
        Set<LmsTokenCTC> result = new LinkedHashSet();
        Cursor cursor = queryStoredMessageParts(new String[]{"address", "reference_number", "count", CellBroadcasts.MESSAGE_FORMAT}, "date <= ?", new String[]{Long.toString(maxTime)}, "date ASC");
        if (cursor == null) {
            Log.w(TAG, "findStoredMessagePartsOlderThan, cursor is NULL");
        } else {
            while (cursor.moveToNext()) {
                try {
                    result.add(new LmsTokenCTC(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getString(3)));
                } finally {
                    cursor.close();
                }
            }
        }
        return result;
    }

    private Cursor queryStoredMessageParts(String[] projection, String where, String[] whereArgs, String sortOrder) {
        return this.mContext.getContentResolver().query(RAW_URI, projection, where, whereArgs, sortOrder);
    }

    private Cursor queryStoredMessageParts(String[] projection, LmsTokenCTC lmsToken, String sortOrder) {
        Pair<String, String[]> selection = dbSelectionForLmsToken(lmsToken);
        return this.mContext.getContentResolver().query(RAW_URI, projection, (String) selection.first, (String[]) selection.second, sortOrder);
    }

    private void deleteStoredMessageParts(LmsTokenCTC lmsToken) {
        Pair<String, String[]> selection = dbSelectionForLmsToken(lmsToken);
        this.mContext.getContentResolver().delete(RAW_URI, (String) selection.first, (String[]) selection.second);
    }

    private static Pair<String, String[]> dbSelectionForLmsToken(LmsTokenCTC lmsToken) {
        return Pair.create("address = ? AND reference_number = ?", new String[]{lmsToken.address, Integer.toString(lmsToken.refNumber)});
    }

    private LmsTokenCTC getQueuedLmsToken(LmsTokenCTC lmsTokenCopy) {
        return (LmsTokenCTC) this.mQueuedLmsTokens.get(lmsTokenCopy);
    }

    private LmsTokenCTC getOrCreateLmsToken(LmsTokenCTC lmsTokenCopy) {
        LmsTokenCTC result = getQueuedLmsToken(lmsTokenCopy);
        if (result != null) {
            return result;
        }
        result = new LmsTokenCTC(lmsTokenCopy);
        this.mQueuedLmsTokens.put(result, result);
        return result;
    }

    private void removeQueuedLmsToken(LmsTokenCTC lmsTokenCopy) {
        this.mQueuedLmsTokens.remove(lmsTokenCopy);
    }

    private static void logd(String msg, Object... args) {
        if (DEBUG) {
            Rlog.d(TAG, String.format(msg, args));
        }
    }
}
