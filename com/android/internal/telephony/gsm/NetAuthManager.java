package com.android.internal.telephony.gsm;

import android.os.Handler;
import com.android.internal.telephony.PhoneBase;

public class NetAuthManager extends Handler {
    public NetAuthManager(PhoneBase phone) {
    }

    public void dispose() {
    }

    public String getNamId() {
        return "";
    }

    public String getNamPwd() {
        return "";
    }

    public void checkApnInfo() {
    }

    public boolean getNamState() {
        return true;
    }
}
