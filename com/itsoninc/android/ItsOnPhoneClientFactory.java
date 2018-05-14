package com.itsoninc.android;

import android.content.Context;
import com.android.internal.telephony.RIL;

public class ItsOnPhoneClientFactory {
    static RIL mRil;

    public static void configure(RIL ril) {
        mRil = ril;
    }

    public static ItsOnPhoneClient get(Context context) {
        if (mRil == null) {
            return null;
        }
        return new ItsOnPhoneClient(context, mRil);
    }
}
