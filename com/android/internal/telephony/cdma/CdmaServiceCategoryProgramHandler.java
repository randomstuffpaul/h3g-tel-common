package com.android.internal.telephony.cdma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.WakeLockStateMachine;
import java.util.ArrayList;

public final class CdmaServiceCategoryProgramHandler extends WakeLockStateMachine {
    final CommandsInterface mCi;
    private final BroadcastReceiver mScpResultsReceiver = new C00781();

    class C00781 extends BroadcastReceiver {
        C00781() {
        }

        public void onReceive(Context context, Intent intent) {
            sendScpResults();
            CdmaServiceCategoryProgramHandler.this.log("mScpResultsReceiver finished");
            CdmaServiceCategoryProgramHandler.this.sendMessage(2);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private void sendScpResults() {
            /*
            r13 = this;
            r11 = 0;
            r7 = r13.getResultCode();
            r10 = -1;
            if (r7 == r10) goto L_0x0024;
        L_0x0008:
            r10 = 1;
            if (r7 == r10) goto L_0x0024;
        L_0x000b:
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;
            r11 = new java.lang.StringBuilder;
            r11.<init>();
            r12 = "SCP results error: result code = ";
            r11 = r11.append(r12);
            r11 = r11.append(r7);
            r11 = r11.toString();
            r10.loge(r11);
        L_0x0023:
            return;
        L_0x0024:
            r6 = r13.getResultExtras(r11);
            if (r6 != 0) goto L_0x0032;
        L_0x002a:
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;
            r11 = "SCP results error: missing extras";
            r10.loge(r11);
            goto L_0x0023;
        L_0x0032:
            r10 = "sender";
            r9 = r6.getString(r10);
            if (r9 != 0) goto L_0x0042;
        L_0x003a:
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;
            r11 = "SCP results error: missing sender extra.";
            r10.loge(r11);
            goto L_0x0023;
        L_0x0042:
            r10 = "results";
            r8 = r6.getParcelableArrayList(r10);
            if (r8 != 0) goto L_0x0052;
        L_0x004a:
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;
            r11 = "SCP results error: missing results extra.";
            r10.loge(r11);
            goto L_0x0023;
        L_0x0052:
            r0 = new com.android.internal.telephony.cdma.sms.BearerData;
            r0.<init>();
            r10 = 2;
            r0.messageType = r10;
            r10 = com.android.internal.telephony.cdma.SmsMessage.getNextMessageId();
            r0.messageId = r10;
            r0.serviceCategoryProgramResults = r8;
            r5 = com.android.internal.telephony.cdma.sms.BearerData.encode(r0);
            r1 = new java.io.ByteArrayOutputStream;
            r10 = 100;
            r1.<init>(r10);
            r3 = new java.io.DataOutputStream;
            r3.<init>(r1);
            r10 = 4102; // 0x1006 float:5.748E-42 double:2.0267E-320;
            r3.writeInt(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r3.writeInt(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r3.writeInt(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = android.telephony.PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(r9);	 Catch:{ IOException -> 0x00d2 }
            r2 = com.android.internal.telephony.cdma.sms.CdmaSmsAddress.parse(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.digitMode;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.numberMode;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.ton;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.numberPlan;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.numberOfDigits;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r2.origBytes;	 Catch:{ IOException -> 0x00d2 }
            r11 = 0;
            r12 = r2.origBytes;	 Catch:{ IOException -> 0x00d2 }
            r12 = r12.length;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10, r11, r12);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = r5.length;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r10);	 Catch:{ IOException -> 0x00d2 }
            r10 = 0;
            r11 = r5.length;	 Catch:{ IOException -> 0x00d2 }
            r3.write(r5, r10, r11);	 Catch:{ IOException -> 0x00d2 }
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;	 Catch:{ IOException -> 0x00d2 }
            r10 = r10.mCi;	 Catch:{ IOException -> 0x00d2 }
            r11 = r1.toByteArray();	 Catch:{ IOException -> 0x00d2 }
            r12 = 0;
            r10.sendCdmaSms(r11, r12);	 Catch:{ IOException -> 0x00d2 }
            r3.close();	 Catch:{ IOException -> 0x00cf }
            goto L_0x0023;
        L_0x00cf:
            r10 = move-exception;
            goto L_0x0023;
        L_0x00d2:
            r4 = move-exception;
            r10 = com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.this;	 Catch:{ all -> 0x00e2 }
            r11 = "exception creating SCP results PDU";
            r10.loge(r11, r4);	 Catch:{ all -> 0x00e2 }
            r3.close();	 Catch:{ IOException -> 0x00df }
            goto L_0x0023;
        L_0x00df:
            r10 = move-exception;
            goto L_0x0023;
        L_0x00e2:
            r10 = move-exception;
            r3.close();	 Catch:{ IOException -> 0x00e7 }
        L_0x00e6:
            throw r10;
        L_0x00e7:
            r11 = move-exception;
            goto L_0x00e6;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaServiceCategoryProgramHandler.1.sendScpResults():void");
        }
    }

    CdmaServiceCategoryProgramHandler(Context context, CommandsInterface commandsInterface) {
        super("CdmaServiceCategoryProgramHandler", context, null);
        this.mContext = context;
        this.mCi = commandsInterface;
    }

    static CdmaServiceCategoryProgramHandler makeScpHandler(Context context, CommandsInterface commandsInterface) {
        CdmaServiceCategoryProgramHandler handler = new CdmaServiceCategoryProgramHandler(context, commandsInterface);
        handler.start();
        return handler;
    }

    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsMessage) {
            return handleServiceCategoryProgramData((SmsMessage) message.obj);
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }

    private boolean handleServiceCategoryProgramData(SmsMessage sms) {
        ArrayList<CdmaSmsCbProgramData> programDataList = sms.getSmsCbProgramData();
        if (programDataList == null) {
            loge("handleServiceCategoryProgramData: program data list is null!");
            return false;
        }
        Intent intent = new Intent(Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION);
        intent.putExtra("sender", sms.getOriginatingAddress());
        intent.putParcelableArrayListExtra("program_data", programDataList);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS", 16, this.mScpResultsReceiver, getHandler(), -1, null, null);
        return true;
    }
}
