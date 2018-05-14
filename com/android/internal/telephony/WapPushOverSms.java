package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import com.android.internal.telephony.IWapPushManager.Stub;
import com.google.android.mms.pdu.DeliveryInd;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.ReadOrigInd;

public class WapPushOverSms implements ServiceConnection {
    private static final boolean DBG = true;
    private static final String LOCATION_SELECTION = "m_type=? AND ct_l =?";
    private static final String TAG = "WAP PUSH";
    private static final String THREAD_ID_SELECTION = "m_id=? AND m_type=?";
    private final Context mContext;
    private String mPushOrigAddr;
    private boolean mPushSafeNoti = false;
    private String mWapPushAddress;
    private volatile IWapPushManager mWapPushManager;
    private String mWapPushTimeStamp;

    public void onServiceConnected(ComponentName name, IBinder service) {
        this.mWapPushManager = Stub.asInterface(service);
        Rlog.v(TAG, "wappush manager connected to " + hashCode());
    }

    public void onServiceDisconnected(ComponentName name) {
        this.mWapPushManager = null;
        Rlog.v(TAG, "wappush manager disconnected.");
    }

    public WapPushOverSms(Context context) {
        this.mContext = context;
        Intent intent = new Intent(IWapPushManager.class.getName());
        ComponentName comp = intent.resolveSystemService(context.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !context.bindService(intent, this, 1)) {
            Rlog.e(TAG, "bindService() for wappush manager failed");
        } else {
            Rlog.v(TAG, "bindService() for wappush manager succeeded");
        }
    }

    void dispose() {
        if (this.mWapPushManager != null) {
            Rlog.v(TAG, "dispose: unbind wappush manager");
            this.mContext.unbindService(this);
            return;
        }
        Rlog.e(TAG, "dispose: not bound to a wappush manager");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public int dispatchWapPdu(byte[] r55, android.content.BroadcastReceiver r56, com.android.internal.telephony.InboundSmsHandler r57) {
        /*
        r54 = this;
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;
        r8.<init>();
        r9 = "Rx: ";
        r8 = r8.append(r9);
        r9 = com.android.internal.telephony.uicc.IccUtils.bytesToHexString(r55);
        r8 = r8.append(r9);
        r8 = r8.toString();
        android.telephony.Rlog.d(r7, r8);
        if (r55 != 0) goto L_0x0027;
    L_0x001e:
        r7 = "WAP PUSH";
        r8 = "Received PDU is null.";
        android.telephony.Rlog.w(r7, r8);
        r7 = 2;
    L_0x0026:
        return r7;
    L_0x0027:
        r35 = 0;
        r36 = r35 + 1;
        r7 = r55[r35];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x05c6 }
        r0 = r7 & 255;
        r50 = r0;
        r35 = r36 + 1;
        r7 = r55[r36];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r7 & 255;
        r43 = r0;
        r7 = r57.getPhone();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r44 = r7.getPhoneId();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 6;
        r0 = r43;
        if (r0 == r7) goto L_0x00e5;
    L_0x0046:
        r7 = 7;
        r0 = r43;
        if (r0 == r7) goto L_0x00e5;
    L_0x004b:
        r0 = r54;
        r7 = r0.mContext;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = r7.getResources();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = 17694836; // 0x10e0074 float:2.6081606E-38 double:8.7424106E-317;
        r35 = r7.getInteger(r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = -1;
        r0 = r35;
        if (r0 == r7) goto L_0x00c8;
    L_0x005f:
        r36 = r35 + 1;
        r7 = r55[r35];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x05c6 }
        r0 = r7 & 255;
        r50 = r0;
        r35 = r36 + 1;
        r7 = r55[r36];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r7 & 255;
        r43 = r0;
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "index = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r35;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = " PDU Type = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r43;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = " transactionID = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r50;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.d(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 6;
        r0 = r43;
        if (r0 == r7) goto L_0x00e5;
    L_0x00a6:
        r7 = 7;
        r0 = r43;
        if (r0 == r7) goto L_0x00e5;
    L_0x00ab:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "Received non-PUSH WAP PDU. Type = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r43;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x00c8:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "Received non-PUSH WAP PDU. Type = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r43;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x00e5:
        r42 = new com.android.internal.telephony.WspTypeDecoder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r42;
        r1 = r55;
        r0.<init>(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r42;
        r1 = r35;
        r7 = r0.decodeUintvarInteger(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 != 0) goto L_0x0102;
    L_0x00f8:
        r7 = "WAP PUSH";
        r8 = "Received PDU. Header Length error.";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 2;
        goto L_0x0026;
    L_0x0102:
        r8 = r42.getValue32();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r12 = (int) r8;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = r42.getDecodedDataLength();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r35 = r35 + r7;
        r32 = r35;
        r0 = r42;
        r1 = r35;
        r7 = r0.decodeContentType(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 != 0) goto L_0x0123;
    L_0x0119:
        r7 = "WAP PUSH";
        r8 = "Received PDU. Header Content-Type error.";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 2;
        goto L_0x0026;
    L_0x0123:
        r40 = r42.getValueString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r22 = r42.getValue32();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r37 = r35;
        r8 = r42.getValue32();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = (int) r8;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r34 = r0;
        if (r40 != 0) goto L_0x01ae;
    L_0x0136:
        switch(r34) {
            case 46: goto L_0x018a;
            case 48: goto L_0x018d;
            case 50: goto L_0x0190;
            case 54: goto L_0x01a2;
            case 62: goto L_0x0193;
            case 66: goto L_0x01a5;
            case 67: goto L_0x01a8;
            case 68: goto L_0x019c;
            case 74: goto L_0x0156;
            case 75: goto L_0x0187;
            case 78: goto L_0x019f;
            case 206: goto L_0x019f;
            case 778: goto L_0x01ab;
            case 784: goto L_0x0199;
            case 786: goto L_0x0196;
            default: goto L_0x0139;
        };	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x0139:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "Received PDU. Unsupported Content-Type = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r22;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x0156:
        r40 = "application/vnd.oma.drm.rights+xml";
    L_0x0158:
        r30 = android.sec.enterprise.EnterpriseDeviceManager.getInstance();	 Catch:{ NullPointerException -> 0x030a }
        r6 = r30.getPhoneRestrictionPolicy();	 Catch:{ NullPointerException -> 0x030a }
        r27 = r30.getDeviceInventory();	 Catch:{ NullPointerException -> 0x030a }
        r7 = "gsm.operator.isroaming";
        r8 = 0;
        r7 = android.os.SystemProperties.getBoolean(r7, r8);	 Catch:{ NullPointerException -> 0x030a }
        if (r7 == 0) goto L_0x0177;
    L_0x016d:
        r7 = r30.getRoamingPolicy();	 Catch:{ NullPointerException -> 0x030a }
        r7 = r7.isRoamingPushEnabled();	 Catch:{ NullPointerException -> 0x030a }
        if (r7 == 0) goto L_0x017d;
    L_0x0177:
        r7 = r6.isWapPushAllowed();	 Catch:{ NullPointerException -> 0x030a }
        if (r7 != 0) goto L_0x028c;
    L_0x017d:
        r7 = "WAP PUSH";
        r8 = " MDM RoamingPush or WapPush Disabled ";
        android.telephony.Rlog.d(r7, r8);	 Catch:{ NullPointerException -> 0x030a }
        r7 = 1;
        goto L_0x0026;
    L_0x0187:
        r40 = "application/vnd.oma.drm.rights+wbxml";
        goto L_0x0158;
    L_0x018a:
        r40 = "application/vnd.wap.sic";
        goto L_0x0158;
    L_0x018d:
        r40 = "application/vnd.wap.slc";
        goto L_0x0158;
    L_0x0190:
        r40 = "application/vnd.wap.coc";
        goto L_0x0158;
    L_0x0193:
        r40 = "application/vnd.wap.mms-message";
        goto L_0x0158;
    L_0x0196:
        r40 = "application/vnd.omaloc-supl-init";
        goto L_0x0158;
    L_0x0199:
        r40 = "application/vnd.docomo.pf";
        goto L_0x0158;
    L_0x019c:
        r40 = "application/vnd.syncml.notification";
        goto L_0x0158;
    L_0x019f:
        r40 = "application/vnd.syncml.ds.notification";
        goto L_0x0158;
    L_0x01a2:
        r40 = "application/vnd.wap.connectivity-wbxml";
        goto L_0x0158;
    L_0x01a5:
        r40 = "application/vnd.syncml.dm+wbxml";
        goto L_0x0158;
    L_0x01a8:
        r40 = "application/vnd.syncml.dm+xml";
        goto L_0x0158;
    L_0x01ab:
        r40 = "application/vnd.wap.emn+wbxml";
        goto L_0x0158;
    L_0x01ae:
        r7 = "application/vnd.oma.drm.rights+xml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01bb;
    L_0x01b8:
        r34 = 74;
        goto L_0x0158;
    L_0x01bb:
        r7 = "application/vnd.oma.drm.rights+wbxml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01c8;
    L_0x01c5:
        r34 = 75;
        goto L_0x0158;
    L_0x01c8:
        r7 = "application/vnd.wap.sic";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01d5;
    L_0x01d2:
        r34 = 46;
        goto L_0x0158;
    L_0x01d5:
        r7 = "application/vnd.wap.slc";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01e3;
    L_0x01df:
        r34 = 48;
        goto L_0x0158;
    L_0x01e3:
        r7 = "application/vnd.wap.coc";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01f1;
    L_0x01ed:
        r34 = 50;
        goto L_0x0158;
    L_0x01f1:
        r7 = "application/vnd.wap.mms-message";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x01ff;
    L_0x01fb:
        r34 = 62;
        goto L_0x0158;
    L_0x01ff:
        r7 = "application/vnd.omaloc-supl-init";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x020d;
    L_0x0209:
        r34 = 786; // 0x312 float:1.101E-42 double:3.883E-321;
        goto L_0x0158;
    L_0x020d:
        r7 = "application/vnd.docomo.pf";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x021b;
    L_0x0217:
        r34 = 784; // 0x310 float:1.099E-42 double:3.873E-321;
        goto L_0x0158;
    L_0x021b:
        r7 = "application/vnd.syncml.notification";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0229;
    L_0x0225:
        r34 = 68;
        goto L_0x0158;
    L_0x0229:
        r7 = "application/vnd.syncml.ds.notification";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0237;
    L_0x0233:
        r34 = 78;
        goto L_0x0158;
    L_0x0237:
        r7 = "application/vnd.wap.connectivity-wbxml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0245;
    L_0x0241:
        r34 = 54;
        goto L_0x0158;
    L_0x0245:
        r7 = "application/vnd.syncml.dm+wbxml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0253;
    L_0x024f:
        r34 = 66;
        goto L_0x0158;
    L_0x0253:
        r7 = "application/vnd.syncml.dm+xml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0261;
    L_0x025d:
        r34 = 67;
        goto L_0x0158;
    L_0x0261:
        r7 = "application/vnd.wap.emn+wbxml";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x026f;
    L_0x026b:
        r34 = 778; // 0x30a float:1.09E-42 double:3.844E-321;
        goto L_0x0158;
    L_0x026f:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "Received PDU. Unknown Content-Type = ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r40;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x028c:
        r7 = 62;
        r0 = r34;
        if (r7 != r0) goto L_0x02c8;
    L_0x0292:
        r7 = 1;
        r7 = r6.getEmergencyCallOnly(r7);	 Catch:{ NullPointerException -> 0x030a }
        if (r7 != 0) goto L_0x029f;
    L_0x0299:
        r7 = r6.isIncomingMmsAllowed();	 Catch:{ NullPointerException -> 0x030a }
        if (r7 != 0) goto L_0x02a9;
    L_0x029f:
        r7 = "WAP PUSH";
        r8 = "emergency call only or incoming MMS not allowed";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ NullPointerException -> 0x030a }
        r7 = 1;
        goto L_0x0026;
    L_0x02a9:
        r7 = r6.isBlockMmsWithStorageEnabled();	 Catch:{ NullPointerException -> 0x030a }
        if (r7 == 0) goto L_0x02fd;
    L_0x02af:
        r7 = "WAP PUSH";
        r8 = "blocking mms with storage";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ NullPointerException -> 0x030a }
        r7 = 0;
        r0 = r54;
        r9 = r0.mWapPushAddress;	 Catch:{ NullPointerException -> 0x030a }
        r10 = -1;
        r0 = r54;
        r11 = r0.mWapPushTimeStamp;	 Catch:{ NullPointerException -> 0x030a }
        r8 = r55;
        r6.storeBlockedSmsMms(r7, r8, r9, r10, r11);	 Catch:{ NullPointerException -> 0x030a }
        r7 = 1;
        goto L_0x0026;
    L_0x02c8:
        r53 = new java.lang.StringBuilder;	 Catch:{ NullPointerException -> 0x030a }
        r53.<init>();	 Catch:{ NullPointerException -> 0x030a }
        r33 = 0;
    L_0x02cf:
        r0 = r55;
        r7 = r0.length;	 Catch:{ NullPointerException -> 0x030a }
        r0 = r33;
        if (r0 >= r7) goto L_0x02e1;
    L_0x02d6:
        r7 = r55[r33];	 Catch:{ NullPointerException -> 0x030a }
        r7 = (char) r7;	 Catch:{ NullPointerException -> 0x030a }
        r0 = r53;
        r0.append(r7);	 Catch:{ NullPointerException -> 0x030a }
        r33 = r33 + 1;
        goto L_0x02cf;
    L_0x02e1:
        r7 = r53.toString();	 Catch:{ NullPointerException -> 0x030a }
        r7 = r7.length();	 Catch:{ NullPointerException -> 0x030a }
        if (r7 == 0) goto L_0x02fd;
    L_0x02eb:
        r0 = r54;
        r7 = r0.mWapPushAddress;	 Catch:{ NullPointerException -> 0x030a }
        r0 = r54;
        r8 = r0.mWapPushTimeStamp;	 Catch:{ NullPointerException -> 0x030a }
        r9 = r53.toString();	 Catch:{ NullPointerException -> 0x030a }
        r10 = 1;
        r0 = r27;
        r0.storeSMS(r7, r8, r9, r10);	 Catch:{ NullPointerException -> 0x030a }
    L_0x02fd:
        r28 = 0;
        switch(r34) {
            case 46: goto L_0x037c;
            case 50: goto L_0x0344;
            case 54: goto L_0x037c;
            case 62: goto L_0x0358;
            case 66: goto L_0x037c;
            case 67: goto L_0x037c;
            case 68: goto L_0x036c;
            case 78: goto L_0x037f;
            case 206: goto L_0x037f;
            default: goto L_0x0302;
        };
    L_0x0302:
        r7 = 1;
        r0 = r28;
        if (r0 != r7) goto L_0x0382;
    L_0x0307:
        r7 = -1;
        goto L_0x0026;
    L_0x030a:
        r41 = move-exception;
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "MDM failed to get policy - NullPointerException but this isn't issue";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r41;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.e(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        goto L_0x02fd;
    L_0x0326:
        r19 = move-exception;
    L_0x0327:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;
        r8.<init>();
        r9 = "ignoring dispatchWapPdu() array index exception: ";
        r8 = r8.append(r9);
        r0 = r19;
        r8 = r8.append(r0);
        r8 = r8.toString();
        android.telephony.Rlog.e(r7, r8);
        r7 = 2;
        goto L_0x0026;
    L_0x0344:
        r7 = r54;
        r8 = r55;
        r9 = r50;
        r10 = r43;
        r11 = r32;
        r13 = r56;
        r14 = r57;
        r7.dispatchWapPdu_PushCO(r8, r9, r10, r11, r12, r13, r14);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r28 = 1;
        goto L_0x0302;
    L_0x0358:
        r7 = r54;
        r8 = r55;
        r9 = r50;
        r10 = r43;
        r11 = r32;
        r13 = r56;
        r14 = r57;
        r7.dispatchWapPdu_MMS(r8, r9, r10, r11, r12, r13, r14);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r28 = 1;
        goto L_0x0302;
    L_0x036c:
        r0 = r54;
        r1 = r55;
        r2 = r34;
        r3 = r56;
        r4 = r57;
        r0.dispatchWapPdu_DMNoti(r1, r2, r3, r4);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r28 = 1;
        goto L_0x0302;
    L_0x037c:
        r28 = 0;
        goto L_0x0302;
    L_0x037f:
        r28 = 0;
        goto L_0x0302;
    L_0x0382:
        r7 = r42.getDecodedDataLength();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r35 = r35 + r7;
        r0 = new byte[r12];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r31 = r0;
        r7 = 0;
        r0 = r31;
        r8 = r0.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r55;
        r1 = r32;
        r2 = r31;
        java.lang.System.arraycopy(r0, r1, r2, r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r40 == 0) goto L_0x045e;
    L_0x039b:
        r7 = "application/vnd.wap.coc";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x045e;
    L_0x03a5:
        r38 = r55;
    L_0x03a7:
        r7 = android.telephony.SmsManager.getDefault();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = r7.getAutoPersisting();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x03c8;
    L_0x03b1:
        r47 = android.telephony.SubscriptionManager.getSubId(r44);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r47 == 0) goto L_0x0478;
    L_0x03b7:
        r0 = r47;
        r7 = r0.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 <= 0) goto L_0x0478;
    L_0x03bc:
        r7 = 0;
        r48 = r47[r7];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x03bf:
        r0 = r54;
        r1 = r48;
        r3 = r38;
        r0.writeInboxMessage(r1, r3);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x03c8:
        r7 = r35 + r12;
        r7 = r7 + -1;
        r0 = r42;
        r1 = r35;
        r7 = r0.seekXWapApplicationId(r1, r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0514;
    L_0x03d6:
        r8 = r42.getValue32();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = (int) r8;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r35 = r0;
        r0 = r42;
        r1 = r35;
        r0.decodeXWapApplicationId(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r51 = r42.getValueString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r39 = 1;
        r26 = 33;
        if (r51 != 0) goto L_0x03fb;
    L_0x03ee:
        r8 = r42.getValue32();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = (int) r8;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r51 = java.lang.Integer.toString(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r39 = 0;
        r26 = 4;
    L_0x03fb:
        if (r40 != 0) goto L_0x047e;
    L_0x03fd:
        r24 = java.lang.Long.toString(r22);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x0401:
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "appid found: ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r51;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = ":";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r24;
        r8 = r8.append(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.v(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "application/vnd.omaloc-supl-init";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0495;
    L_0x0431:
        r7 = "CHM";
        r8 = "ro.csc.sales_code";
        r8 = android.os.SystemProperties.get(r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = r7.equals(r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 != 0) goto L_0x0495;
    L_0x043f:
        if (r39 == 0) goto L_0x0481;
    L_0x0441:
        r20 = new java.lang.String;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "x-oma-application:ulp.ua";
        r0 = r20;
        r0.<init>(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r20;
        r1 = r51;
        r7 = r0.equals(r1);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 != 0) goto L_0x0495;
    L_0x0454:
        r7 = "WAP PUSH";
        r8 = " InvalidApplicationID-ASCII";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x045e:
        r25 = r32 + r12;
        r0 = r55;
        r7 = r0.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = r7 - r25;
        r0 = new byte[r7];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r38 = r0;
        r7 = 0;
        r0 = r38;
        r8 = r0.length;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r55;
        r1 = r25;
        r2 = r38;
        java.lang.System.arraycopy(r0, r1, r2, r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        goto L_0x03a7;
    L_0x0478:
        r48 = android.telephony.SmsManager.getDefaultSmsSubId();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        goto L_0x03bf;
    L_0x047e:
        r24 = r40;
        goto L_0x0401;
    L_0x0481:
        r7 = r37 + r26;
        r7 = r7 + 1;
        r7 = r55[r7];	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = -112; // 0xffffffffffffff90 float:NaN double:NaN;
        if (r7 == r8) goto L_0x0495;
    L_0x048b:
        r7 = "WAP PUSH";
        r8 = " InvalidApplicationID-HEX";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 1;
        goto L_0x0026;
    L_0x0495:
        r46 = 1;
        r0 = r54;
        r0 = r0.mWapPushManager;	 Catch:{ RemoteException -> 0x050c }
        r52 = r0;
        if (r52 != 0) goto L_0x04ab;
    L_0x049f:
        r7 = "WAP PUSH";
        r8 = "wap push manager not found!";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ RemoteException -> 0x050c }
    L_0x04a6:
        if (r46 != 0) goto L_0x0514;
    L_0x04a8:
        r7 = 1;
        goto L_0x0026;
    L_0x04ab:
        r14 = new android.content.Intent;	 Catch:{ RemoteException -> 0x050c }
        r14.<init>();	 Catch:{ RemoteException -> 0x050c }
        r7 = "transactionId";
        r0 = r50;
        r14.putExtra(r7, r0);	 Catch:{ RemoteException -> 0x050c }
        r7 = "pduType";
        r0 = r43;
        r14.putExtra(r7, r0);	 Catch:{ RemoteException -> 0x050c }
        r7 = "header";
        r0 = r31;
        r14.putExtra(r7, r0);	 Catch:{ RemoteException -> 0x050c }
        r7 = "data";
        r0 = r38;
        r14.putExtra(r7, r0);	 Catch:{ RemoteException -> 0x050c }
        r7 = "contentTypeParameters";
        r8 = r42.getContentParameters();	 Catch:{ RemoteException -> 0x050c }
        r14.putExtra(r7, r8);	 Catch:{ RemoteException -> 0x050c }
        r0 = r44;
        android.telephony.SubscriptionManager.putPhoneIdAndSubIdExtra(r14, r0);	 Catch:{ RemoteException -> 0x050c }
        r0 = r52;
        r1 = r51;
        r2 = r24;
        r45 = r0.processMessage(r1, r2, r14);	 Catch:{ RemoteException -> 0x050c }
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ RemoteException -> 0x050c }
        r8.<init>();	 Catch:{ RemoteException -> 0x050c }
        r9 = "procRet:";
        r8 = r8.append(r9);	 Catch:{ RemoteException -> 0x050c }
        r0 = r45;
        r8 = r8.append(r0);	 Catch:{ RemoteException -> 0x050c }
        r8 = r8.toString();	 Catch:{ RemoteException -> 0x050c }
        android.telephony.Rlog.v(r7, r8);	 Catch:{ RemoteException -> 0x050c }
        r7 = r45 & 1;
        if (r7 <= 0) goto L_0x04a6;
    L_0x0502:
        r7 = 32768; // 0x8000 float:4.5918E-41 double:1.61895E-319;
        r7 = r7 & r45;
        if (r7 != 0) goto L_0x04a6;
    L_0x0509:
        r46 = 0;
        goto L_0x04a6;
    L_0x050c:
        r29 = move-exception;
        r7 = "WAP PUSH";
        r8 = "remote func failed...";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x0514:
        r7 = "WAP PUSH";
        r8 = "fall back to existing handler";
        android.telephony.Rlog.v(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r40 != 0) goto L_0x0527;
    L_0x051d:
        r7 = "WAP PUSH";
        r8 = "Header Content-Type error.";
        android.telephony.Rlog.w(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = 2;
        goto L_0x0026;
    L_0x0527:
        r7 = "application/vnd.wap.mms-message";
        r0 = r40;
        r7 = r0.equals(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x05c0;
    L_0x0531:
        r15 = "android.permission.RECEIVE_MMS";
        r16 = 18;
    L_0x0535:
        r14 = new android.content.Intent;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "android.provider.Telephony.WAP_PUSH_DELIVER";
        r14.<init>(r7);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r40;
        r14.setType(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "transactionId";
        r0 = r50;
        r14.putExtra(r7, r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "pduType";
        r0 = r43;
        r14.putExtra(r7, r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "header";
        r0 = r31;
        r14.putExtra(r7, r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "data";
        r0 = r38;
        r14.putExtra(r7, r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "contentTypeParameters";
        r8 = r42.getContentParameters();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r14.putExtra(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r54;
        r7 = r0.mPushOrigAddr;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r7 == 0) goto L_0x0575;
    L_0x056c:
        r7 = "origaddr";
        r0 = r54;
        r8 = r0.mPushOrigAddr;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r14.putExtra(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x0575:
        r0 = r44;
        android.telephony.SubscriptionManager.putPhoneIdAndSubIdExtra(r14, r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r0 = r54;
        r7 = r0.mContext;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = 1;
        r21 = com.android.internal.telephony.SmsApplication.getDefaultMmsApplication(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        if (r21 == 0) goto L_0x05b4;
    L_0x0585:
        r0 = r21;
        r14.setComponent(r0);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = "WAP PUSH";
        r8 = new java.lang.StringBuilder;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8.<init>();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = "Delivering MMS to: ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = r21.getPackageName();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = " ";
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r9 = r21.getClassName();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.append(r9);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r8 = r8.toString();	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        android.telephony.Rlog.v(r7, r8);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
    L_0x05b4:
        r18 = android.os.UserHandle.OWNER;	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r13 = r57;
        r17 = r56;
        r13.dispatchIntent(r14, r15, r16, r17, r18);	 Catch:{ ArrayIndexOutOfBoundsException -> 0x0326 }
        r7 = -1;
        goto L_0x0026;
    L_0x05c0:
        r15 = "android.permission.RECEIVE_WAP_PUSH";
        r16 = 19;
        goto L_0x0535;
    L_0x05c6:
        r19 = move-exception;
        r35 = r36;
        goto L_0x0327;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.WapPushOverSms.dispatchWapPdu(byte[], android.content.BroadcastReceiver, com.android.internal.telephony.InboundSmsHandler):int");
    }

    private void writeInboxMessage(long subId, byte[] pushData) {
        GenericPdu pdu = new PduParser(pushData).parse();
        if (pdu == null) {
            Rlog.e(TAG, "Invalid PUSH PDU");
        }
        PduPersister persister = PduPersister.getPduPersister(this.mContext);
        int type = pdu.getMessageType();
        switch (type) {
            case 130:
                NotificationInd nInd = (NotificationInd) pdu;
                Bundle configs = SmsManager.getSmsManagerForSubscriber(subId).getCarrierConfigValues();
                if (configs != null && configs.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID, false)) {
                    byte[] contentLocation = nInd.getContentLocation();
                    if ((byte) 61 == contentLocation[contentLocation.length - 1]) {
                        byte[] transactionId = nInd.getTransactionId();
                        byte[] contentLocationWithId = new byte[(contentLocation.length + transactionId.length)];
                        System.arraycopy(contentLocation, 0, contentLocationWithId, 0, contentLocation.length);
                        System.arraycopy(transactionId, 0, contentLocationWithId, contentLocation.length, transactionId.length);
                        nInd.setContentLocation(contentLocationWithId);
                    }
                }
                if (isDuplicateNotification(this.mContext, nInd)) {
                    Rlog.d(TAG, "Skip storing duplicate MMS WAP push notification ind: " + new String(nInd.getContentLocation()));
                    return;
                }
                if (persister.persist(pdu, Inbox.CONTENT_URI, true, true, null) == null) {
                    Rlog.e(TAG, "Failed to save MMS WAP push notification ind");
                    return;
                }
                return;
            case 134:
            case 136:
                long threadId = getDeliveryOrReadReportThreadId(this.mContext, pdu);
                if (threadId == -1) {
                    Rlog.e(TAG, "Failed to find delivery or read report's thread id");
                    return;
                }
                Uri uri = persister.persist(pdu, Inbox.CONTENT_URI, true, true, null);
                if (uri == null) {
                    Rlog.e(TAG, "Failed to persist delivery or read report");
                    return;
                }
                ContentValues values = new ContentValues(1);
                values.put("thread_id", Long.valueOf(threadId));
                if (SqliteWrapper.update(this.mContext, this.mContext.getContentResolver(), uri, values, null, null) != 1) {
                    Rlog.e(TAG, "Failed to update delivery or read report thread id");
                    return;
                }
                return;
            default:
                try {
                    Log.e(TAG, "Received unrecognized WAP Push PDU.");
                    return;
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to save MMS WAP push data: type=" + type, e);
                    return;
                } catch (Throwable e2) {
                    Log.e(TAG, "Unexpected RuntimeException in persisting MMS WAP push data", e2);
                    return;
                }
        }
    }

    private static long getDeliveryOrReadReportThreadId(Context context, GenericPdu pdu) {
        String messageId;
        if (pdu instanceof DeliveryInd) {
            messageId = new String(((DeliveryInd) pdu).getMessageId());
        } else if (pdu instanceof ReadOrigInd) {
            messageId = new String(((ReadOrigInd) pdu).getMessageId());
        } else {
            Rlog.e(TAG, "WAP Push data is neither delivery or read report type: " + pdu.getClass().getCanonicalName());
            return -1;
        }
        Cursor cursor = null;
        try {
            cursor = SqliteWrapper.query(context, context.getContentResolver(), Mms.CONTENT_URI, new String[]{"thread_id"}, THREAD_ID_SELECTION, new String[]{DatabaseUtils.sqlEscapeString(messageId), Integer.toString(128)}, null);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) {
                    cursor.close();
                }
                return -1;
            }
            long j = cursor.getLong(0);
            if (cursor == null) {
                return j;
            }
            cursor.close();
            return j;
        } catch (SQLiteException e) {
            Rlog.e(TAG, "Failed to query delivery or read report thread id", e);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static boolean isDuplicateNotification(Context context, NotificationInd nInd) {
        if (nInd.getContentLocation() != null) {
            String[] selectionArgs = new String[]{new String(nInd.getContentLocation())};
            Cursor cursor = null;
            try {
                cursor = SqliteWrapper.query(context, context.getContentResolver(), Mms.CONTENT_URI, new String[]{"_id"}, LOCATION_SELECTION, new String[]{Integer.toString(130), new String(rawLocation)}, null);
                if (cursor != null && cursor.getCount() > 0) {
                    if (cursor != null) {
                        cursor.close();
                    }
                    return true;
                } else if (cursor != null) {
                    cursor.close();
                }
            } catch (SQLiteException e) {
                Rlog.e(TAG, "failed to query existing notification ind", e);
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return false;
    }

    public void dispatchWapPushSafeNoti(boolean SafeNoti) {
        this.mPushSafeNoti = SafeNoti;
        Rlog.v(TAG, "dispatchWapPushSafeNoti SafeNoti = " + this.mPushSafeNoti);
    }

    public void dispatchWapPushAddress(String OrigAddr) {
        if (OrigAddr != null) {
            this.mPushOrigAddr = OrigAddr;
        } else {
            this.mPushOrigAddr = null;
        }
    }

    private void dispatchWapPdu_PushCO(byte[] pdu, int transactionId, int pduType, int headerStartIndex, int headerLength, BroadcastReceiver receiver, InboundSmsHandler handler) {
        byte[] header = new byte[headerLength];
        System.arraycopy(pdu, headerStartIndex, header, 0, header.length);
        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType("application/vnd.wap.coc");
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("header", header);
        intent.putExtra("data", pdu);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, handler.getPhone().getPhoneId());
        if (this.mPushOrigAddr != null) {
            intent.putExtra("origaddr", this.mPushOrigAddr);
        }
        handler.dispatchIntent(intent, "android.permission.RECEIVE_WAP_PUSH", 19, receiver, UserHandle.OWNER);
    }

    private void dispatchWapPdu_MMS(byte[] pdu, int transactionId, int pduType, int headerStartIndex, int headerLength, BroadcastReceiver receiver, InboundSmsHandler handler) {
        byte[] header = new byte[headerLength];
        System.arraycopy(pdu, headerStartIndex, header, 0, header.length);
        int dataIndex = headerStartIndex + headerLength;
        byte[] data = new byte[(pdu.length - dataIndex)];
        System.arraycopy(pdu, dataIndex, data, 0, data.length);
        Intent intent = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
        intent.setType("application/vnd.wap.mms-message");
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("header", header);
        intent.putExtra("data", data);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, handler.getPhone().getPhoneId());
        if (this.mPushSafeNoti) {
            intent.putExtra("safeNoti", this.mPushSafeNoti);
            Rlog.d(TAG, "putExtra safeNoti");
        }
        ComponentName componentName = SmsApplication.getDefaultMmsApplication(this.mContext, true);
        if (componentName != null) {
            intent.setComponent(componentName);
            Rlog.v(TAG, "Delivering MMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
        }
        handler.dispatchIntent(intent, "android.permission.RECEIVE_MMS", 18, receiver, UserHandle.OWNER);
    }

    private void dispatchWapPdu_DMNoti(byte[] pdu, int binaryContentType, BroadcastReceiver receiver, InboundSmsHandler handler) {
        Intent intent = new Intent(Intents.WAP_PUSH_DM_NOTI_RECEIVED_ACTION);
        intent.addFlags(32);
        intent.putExtra("pdus", pdu);
        intent.putExtra("pushtype", binaryContentType);
        Rlog.d(TAG, "android.provider.Telephony.WAP_PUSH_DM_NOTI_RECEIVED is sent");
        handler.dispatchIntent(intent, "android.permission.RECEIVE_WAP_PUSH", 19, receiver, UserHandle.OWNER);
    }

    private void dispatchWapPdu_PushMsg(byte[] pdu, int binaryContentType, BroadcastReceiver receiver, InboundSmsHandler handler) {
        Rlog.e(TAG, "dispatchWapPdu_PushMsg : binaryContentType = " + binaryContentType);
        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.putExtra("pdus", pdu);
        intent.putExtra("pushtype", binaryContentType);
        if (this.mPushOrigAddr != null) {
            intent.putExtra("origaddr", this.mPushOrigAddr);
        }
        handler.dispatchIntent(intent, "android.permission.RECEIVE_WAP_PUSH", 19, receiver, UserHandle.OWNER);
    }

    private void dispatchWapPdu_DSNoti(byte[] pdu, int binaryContentType, BroadcastReceiver receiver, InboundSmsHandler handler) {
        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType(WspTypeDecoder.CONTENT_MIME_TYPE_B_PUSH_DS_SYNCML_NOTI);
        intent.putExtra("data", pdu);
        intent.putExtra("ds_message", pdu);
        intent.putExtra("pushtype", binaryContentType);
        Rlog.i(TAG, "ds noti intent is sent");
        handler.dispatchIntent(intent, "android.permission.RECEIVE_WAP_PUSH", 19, receiver, UserHandle.OWNER);
    }

    public void setWpaPushAddressTimeStamp(String wapPushAddress, long wapPushTimeStamp) {
        this.mWapPushAddress = wapPushAddress;
        this.mWapPushTimeStamp = Long.toString(wapPushTimeStamp);
    }
}
