package com.android.internal.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.Rlog;

public final class LegacyIms {
    private static final boolean DEBUG = true;
    private static final String IMS_PS_DOMAIN = "persist.radio.domain.ps";
    private static final String LOG_TAG = "LegacyIms";
    public static final int NET_TYPE_BLUETOOTH = 3;
    public static final int NET_TYPE_ETHERNET = 4;
    public static final int NET_TYPE_MAX = 5;
    public static final int NET_TYPE_MOBILE = 0;
    public static final int NET_TYPE_WIFI = 1;
    public static final int NET_TYPE_WIMAX = 2;
    public static final int RIL_EPDG_STATUS_NOT_REGISTERED = 0;
    public static final int RIL_EPDG_STATUS_REGISTERED = 1;
    public static final int RIL_IMS_STATUS_LIMITED_MODE_REG = 2;
    public static final int RIL_IMS_STATUS_NOT_REGISTERED = 0;
    public static final int RIL_IMS_STATUS_NOT_REGISTERED_E911 = 3;
    public static final int RIL_IMS_STATUS_REGISTERED = 1;
    public static final int RIL_IMS_STATUS_REGISTERED_E911 = 4;
    public static final int RIL_IMS_TYPE_PSVT = 8;
    public static final int RIL_IMS_TYPE_RCS = 4;
    public static final int RIL_IMS_TYPE_SMSIP = 2;
    public static final int RIL_IMS_TYPE_VOLTE = 1;
    public static final Uri mFormatUri = Uri.parse("content://com.example.HiddenMenuContentProvider/IMSSETTINGSData");
    protected CommandsInterface mCi;
    protected final Context mContext;
    private int mECMPStatus = 0;
    private int mEpdgState = 0;
    private int[] mFeature = new int[5];
    private int mNetworkType = -1;
    protected int mPhoneId;
    private boolean mPrevVolteRegi = false;
    private int[] mRegState = new int[5];
    protected ContentResolver mResolver;

    public LegacyIms(Context context, CommandsInterface ci, int phoneId) {
        this.mContext = context;
        this.mCi = ci;
        this.mPhoneId = phoneId;
        this.mResolver = this.mContext.getContentResolver();
    }

    public int convertNetworkType(int connectivityType) {
        switch (connectivityType) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 6:
                return 2;
            case 7:
                return 3;
            case 9:
                return 4;
            default:
                Rlog.e(LOG_TAG, "invalid network type " + connectivityType);
                return 0;
        }
    }

    public void setRegisteredNetworkType(int networkType) {
        this.mNetworkType = networkType;
    }

    public int getRegisteredNetworkType() {
        return this.mNetworkType;
    }

    public void setRegiState(int newState, int netType) {
        this.mRegState[netType] = newState;
    }

    public int getRegiState(int netType) {
        return this.mRegState[netType];
    }

    public void setFeatureMask(int feature, int netType) {
        this.mFeature[netType] = feature;
    }

    public int getFeatureMask(int netType) {
        return this.mFeature[netType];
    }

    public void setEcmpStatus(int ecmpStatus, int regIndex) {
        this.mECMPStatus = ecmpStatus;
        Intent intent = new Intent("android.intent.action.ECMP_STATE_CHANGED");
        intent.addFlags(536870912);
        intent.putExtra("ECMP_STATE", ecmpStatus);
        if ("LGT".equals("") || "SKT".equals("") || "KTT".equals("")) {
            intent.putExtra("REG_STATE", this.mRegState[regIndex]);
        }
        this.mContext.sendBroadcast(intent);
    }

    public int getEcmpStatus() {
        return this.mECMPStatus;
    }

    public void setEpdgState(int epdgState) {
        this.mEpdgState = epdgState;
    }

    public int getEpdgState() {
        return this.mEpdgState;
    }

    public boolean isImsRegistered() {
        for (int i = 0; i < 5; i++) {
            if (this.mRegState[i] == 1) {
                return true;
            }
        }
        return false;
    }

    public boolean isVolteRegistered() {
        int i = 0;
        while (i < 5) {
            if ((this.mFeature[i] & 1) == 1 && this.mRegState[i] == 1) {
                this.mPrevVolteRegi = true;
                return true;
            }
            i++;
        }
        return false;
    }

    public void clearVolteRegistered() {
        this.mPrevVolteRegi = false;
    }

    public boolean isWfcRegistered() {
        return this.mRegState[1] == 1;
    }

    public String getDcnAddress() {
        String address;
        Cursor c = null;
        try {
            c = this.mResolver.query(mFormatUri, new String[]{"mDcnNumber"}, null, null, null);
            if (c.getCount() > 0) {
                c.moveToFirst();
                Rlog.d(LOG_TAG, "Domain Change Address : " + c.getString(0));
                address = c.getString(0);
                if (c != null) {
                    c.close();
                }
            } else {
                Rlog.e(LOG_TAG, "Cursor < 1");
                address = "4437501000";
                if (c != null) {
                    c.close();
                }
            }
        } catch (Exception e) {
            address = "4437501000";
            if (c != null) {
                c.close();
            }
        } catch (Throwable th) {
            if (c != null) {
                c.close();
            }
        }
        return address;
    }

    public boolean dcnAvailability() {
        Rlog.d(LOG_TAG, "dcnAvailability: " + this.mPrevVolteRegi);
        return false;
    }

    public void setLegacyImsRegistration(int[] responseArray) {
        int networkType = responseArray[2];
        int regIndex = convertNetworkType(networkType);
        setRegiState(responseArray[0], regIndex);
        setFeatureMask(responseArray[1], regIndex);
        setEcmpStatus(responseArray[3], regIndex);
        setEpdgState(responseArray[4]);
        if (getRegiState(regIndex) == 0 || getRegiState(regIndex) == 3) {
            if (!isImsRegistered()) {
                setRegisteredNetworkType(-1);
            }
        } else if (getRegiState(regIndex) == 1) {
            if (networkType == 0 && isWfcRegistered()) {
                Rlog.d(LOG_TAG, "WFC is already registered. Keep current NetworkType");
            } else {
                setRegisteredNetworkType(networkType);
            }
        }
        Rlog.d(LOG_TAG, "NetworkType: " + networkTypeToString(networkType) + ", RegIndex: " + regIndex + ", RegiState: " + imsStatusToString(getRegiState(regIndex)) + ", FeatureMask: [" + featureMaskToString(getFeatureMask(regIndex)) + "]" + ", isIMSRegistered: " + isImsRegistered() + ", isVolteRegistered: " + isVolteRegistered() + ", EpdgState: " + getEpdgState() + ", EcmpStatus: " + getEcmpStatus());
    }

    public static String networkTypeToString(int networkType) {
        switch (networkType) {
            case 0:
                return "MOBILE";
            case 1:
                return "WIFI";
            case 6:
                return "WIMAX";
            case 7:
                return "BLUETOOTH";
            case 9:
                return "ETHERNET";
            default:
                Rlog.e(LOG_TAG, "invalid network type: " + networkType);
                return "Unexpected";
        }
    }

    public static String imsStatusToString(int imsStatus) {
        switch (imsStatus) {
            case 0:
                return "NOT_REGISTERED";
            case 1:
                return "REGISTERED";
            case 2:
                return "LIMITED_MODE_REG";
            case 3:
                return "NOT_REGISTERED_E911";
            case 4:
                return "REGISTERED_E911";
            default:
                Rlog.e(LOG_TAG, "invalid ims status: " + imsStatus);
                return "Unexpected";
        }
    }

    public static String featureMaskToString(int featureMask) {
        String rtString = "";
        if ((featureMask & 1) == 1) {
            rtString = rtString + "VOLTE";
        }
        if ((featureMask & 2) == 2) {
            if (!rtString.isEmpty()) {
                rtString = rtString + ", ";
            }
            rtString = rtString + "SMSIP";
        }
        if ((featureMask & 4) == 4) {
            if (!rtString.isEmpty()) {
                rtString = rtString + ", ";
            }
            rtString = rtString + "RCS";
        }
        if ((featureMask & 8) != 8) {
            return rtString;
        }
        if (!rtString.isEmpty()) {
            rtString = rtString + ", ";
        }
        return rtString + "PSVT";
    }
}
