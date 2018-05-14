package com.itsoninc.android;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import java.util.List;

public interface ItsOnOemInterface {
    void acceptCall();

    void accountMms(String str);

    boolean authorizeIncomingMms(String str, String str2);

    @Deprecated
    boolean authorizeIncomingSms(String str, SmsType smsType, String str2);

    boolean authorizeIncomingSms(byte[] bArr);

    boolean authorizeIncomingVoice(String str);

    boolean authorizeOutgoingMms(String str, String str2);

    boolean authorizeOutgoingMms(List<String> list, String str);

    @Deprecated
    boolean authorizeOutgoingSms(String str, int i);

    boolean authorizeOutgoingSms(byte[] bArr, int i);

    boolean authorizeOutgoingVoice(String str);

    boolean callWaiting(String str);

    void cleanupMms(String str);

    void destroy();

    boolean dial(String str);

    boolean flash(String str);

    void initFramework(Context context);

    void initTelephony(Context context);

    boolean isDataAllowed(long j, String str);

    void nitzTimeReceived(String str, long j);

    void onForegroundActivitiesChanged(int i, int i2, boolean z);

    void onImportanceChanged(int i, int i2, int i3);

    void onNewDataSession(String str, String str2, String str3);

    void onProcessDied(int i, int i2);

    void processCDMACallList(List<DeviceCall> list);

    void processCallList(List<DeviceCall> list);

    void registerActivityMapping(String str, int i);

    void registerDownloadMapping(String str);

    void registerMediaMapping(String str);

    void rejectCall();

    @Deprecated
    void setContext(Context context);

    void setDataConnectionHandler(Handler handler, Message message);

    void setEmergencyMode(boolean z);

    void setFrameworkInterface(ItsOnFrameworkInterface itsOnFrameworkInterface);

    void smsDone(int i);

    void smsError(int i);
}
