package com.android.internal.telephony.uicc;

import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;

public final class SIMFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "SIMFileHandler";

    public SIMFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    protected String getEFPath(int efid) {
        if (("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) && getPhoneId() == 0) {
            int isCsim = Integer.parseInt(SystemProperties.get("ril.IsCSIM", "0"));
            if (efid == IccConstants.EF_SMS) {
                if (!TelephonyManager.isSelectTelecomDF()) {
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
            case 20258:
            case IccConstants.EF_SKT_IRM /*20309*/:
                return "3F007FFF5F3D";
            case IccConstants.EF_PBR /*20272*/:
                return "3F007F105F3A";
            case 28418:
                return "3F007F43";
            case IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS /*28433*/:
            case IccConstants.EF_CFF_CPHS /*28435*/:
            case IccConstants.EF_SPN_CPHS /*28436*/:
            case IccConstants.EF_CSP_CPHS /*28437*/:
            case IccConstants.EF_INFO_CPHS /*28438*/:
            case IccConstants.EF_MAILBOX_CPHS /*28439*/:
            case IccConstants.EF_SPN_SHORT_CPHS /*28440*/:
                return "3F007F20";
            case IccConstants.EF_SST /*28472*/:
            case IccConstants.EF_GID1 /*28478*/:
            case IccConstants.EF_SPN /*28486*/:
            case IccConstants.EF_AD /*28589*/:
            case IccConstants.EF_PNN /*28613*/:
            case IccConstants.EF_OPL /*28614*/:
            case IccConstants.EF_MBDN /*28615*/:
            case IccConstants.EF_EXT6 /*28616*/:
            case IccConstants.EF_MBI /*28617*/:
            case IccConstants.EF_MWIS /*28618*/:
            case IccConstants.EF_CFIS /*28619*/:
            case IccConstants.EF_SPDI /*28621*/:
                return "3F007F20";
            case IccConstants.EF_SMS /*28476*/:
                return TelephonyManager.isSelectTelecomDF() ? "3F007F25" : "3F007F10";
            case IccConstants.EF_SMSP /*28482*/:
            case IccConstants.EF_PSI /*28645*/:
                return "3F007F10";
            default:
                String path = getCommonIccEFPath(efid);
                if (path == null) {
                    Rlog.e(LOG_TAG, "Error: EF Path being returned in null");
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
