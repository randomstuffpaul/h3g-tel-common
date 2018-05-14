package com.android.internal.telephony.uicc;

import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;

public final class CsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "CsimFH";

    public CsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
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
                if (isCsim == 1) {
                    return "3F007FFF";
                }
                return "3F007F25";
            } else if (efid == 20256 || efid == IccConstants.EF_MSPL || efid == 20258) {
                return "3F007F105F3C";
            }
        }
        switch (efid) {
            case IccConstants.EF_CSIM_IMSIM /*28450*/:
            case IccConstants.EF_CSIM_CDMAHOME /*28456*/:
            case IccConstants.EF_RUIMID /*28465*/:
            case IccConstants.EF_CST /*28466*/:
            case 28474:
            case IccConstants.EF_FDN /*28475*/:
            case IccConstants.EF_SMS /*28476*/:
            case IccConstants.EF_MSISDN /*28480*/:
            case 28481:
            case IccConstants.EF_CSIM_MDN /*28484*/:
            case IccConstants.EF_CSIM_EPRL /*28506*/:
            case IccConstants.EF_CSIM_EUIMID /*28532*/:
                return "3F007FFF";
            default:
                String path = getCommonIccEFPath(efid);
                if (path == null) {
                    return "3F007F105F3A";
                }
                return path;
        }
    }

    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
