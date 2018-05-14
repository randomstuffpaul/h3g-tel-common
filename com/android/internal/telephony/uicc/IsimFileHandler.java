package com.android.internal.telephony.uicc;

import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;

public final class IsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "IsimFH";

    public IsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    protected String getEFPath(int efid) {
        if (("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) && getPhoneId() == 0) {
            int isCsim = Integer.parseInt(SystemProperties.get("ril.IsCSIM", "0"));
            if (efid == IccConstants.EF_SMS) {
                if (TelephonyManager.isSelectTelecomDF()) {
                    return "3F007F10";
                }
                if (isCsim == 1) {
                    return "3F007FFF";
                }
                return "3F007F25";
            } else if (efid == IccConstants.EF_CSIM_IMSIM) {
                return "3F007FFF";
            } else {
                if (efid == 20256 || efid == IccConstants.EF_MSPL || efid == 20258) {
                    return "3F007F105F3C";
                }
            }
        }
        switch (efid) {
            case 28418:
            case IccConstants.EF_DOMAIN /*28419*/:
            case IccConstants.EF_IMPU /*28420*/:
            case IccConstants.EF_IST /*28423*/:
            case IccConstants.EF_PCSCF /*28425*/:
            case IccConstants.EF_GBABP /*28629*/:
                return "3F007FFF";
            default:
                return getCommonIccEFPath(efid);
        }
    }

    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
