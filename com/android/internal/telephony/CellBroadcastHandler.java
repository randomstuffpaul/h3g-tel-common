package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;

public class CellBroadcastHandler extends WakeLockStateMachine {
    private CellBroadcastHandler(Context context, PhoneBase phone) {
        this("CellBroadcastHandler", context, phone);
    }

    protected CellBroadcastHandler(String debugTag, Context context, PhoneBase phone) {
        super(debugTag, context, phone);
    }

    public static CellBroadcastHandler makeCellBroadcastHandler(Context context, PhoneBase phone) {
        CellBroadcastHandler handler = new CellBroadcastHandler(context, phone);
        handler.start();
        return handler;
    }

    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsCbMessage) {
            handleBroadcastSms((SmsCbMessage) message.obj);
            return true;
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }

    protected void handleLocationInfo(AsyncResult ar) {
        log("handleLocationInfo in CellBroadcastHandler. It will be used in GsmCellBroadcastHandler.");
    }

    protected void handleOperatorInfo(AsyncResult ar) {
        log("handleOperatorInfo in CellBroadcastHandler. It will be used in GsmCellBroadcastHandler.");
    }

    protected void handleBroadcastSms(SmsCbMessage message) {
        Intent intent;
        String receiverPermission;
        int appOp;
        if (message.isEmergencyMessage()) {
            log("Dispatching emergency SMS CB");
            intent = new Intent(Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            receiverPermission = "android.permission.RECEIVE_EMERGENCY_BROADCAST";
            appOp = 17;
        } else {
            log("Dispatching SMS CB");
            intent = new Intent(Intents.SMS_CB_RECEIVED_ACTION);
            receiverPermission = "android.permission.RECEIVE_SMS";
            appOp = 16;
        }
        intent.putExtra("message", message);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
        this.mContext.sendOrderedBroadcast(intent, receiverPermission, appOp, this.mReceiver, getHandler(), -1, null, null);
    }
}
