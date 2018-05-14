package com.android.internal.telephony.gsm;

import com.android.internal.telephony.CallForwardInfo;

public class SsData {
    public CallForwardInfo[] mCfInfo;
    public RequestType mRequestType;
    public int mResult;
    public int mServiceClass;
    public ServiceType mServiceType;
    public int[] mSsInfo;
    public TeleserviceType mTeleserviceType;

    public enum RequestType {
        SS_ACTIVATION,
        SS_DEACTIVATION,
        SS_INTERROGATION,
        SS_REGISTRATION,
        SS_ERASURE;

        public boolean isTypeInterrogation() {
            return this == SS_INTERROGATION;
        }
    }

    public enum ServiceType {
        SS_CFU,
        SS_CF_BUSY,
        SS_CF_NO_REPLY,
        SS_CF_NOT_REACHABLE,
        SS_CF_ALL,
        SS_CF_ALL_CONDITIONAL,
        SS_CLIP,
        SS_CLIR,
        SS_COLP,
        SS_COLR,
        SS_WAIT,
        SS_BAOC,
        SS_BAOIC,
        SS_BAOIC_EXC_HOME,
        SS_BAIC,
        SS_BAIC_ROAMING,
        SS_ALL_BARRING,
        SS_OUTGOING_BARRING,
        SS_INCOMING_BARRING;

        public boolean isTypeCF() {
            return this == SS_CFU || this == SS_CF_BUSY || this == SS_CF_NO_REPLY || this == SS_CF_NOT_REACHABLE || this == SS_CF_ALL || this == SS_CF_ALL_CONDITIONAL;
        }

        public boolean isTypeUnConditional() {
            return this == SS_CFU || this == SS_CF_ALL;
        }

        public boolean isTypeCW() {
            return this == SS_WAIT;
        }

        public boolean isTypeClip() {
            return this == SS_CLIP;
        }

        public boolean isTypeClir() {
            return this == SS_CLIR;
        }

        public boolean isTypeBarring() {
            return this == SS_BAOC || this == SS_BAOIC || this == SS_BAOIC_EXC_HOME || this == SS_BAIC || this == SS_BAIC_ROAMING || this == SS_ALL_BARRING || this == SS_OUTGOING_BARRING || this == SS_INCOMING_BARRING;
        }
    }

    public enum TeleserviceType {
        SS_ALL_TELE_AND_BEARER_SERVICES,
        SS_ALL_TELESEVICES,
        SS_TELEPHONY,
        SS_ALL_DATA_TELESERVICES,
        SS_SMS_SERVICES,
        SS_ALL_TELESERVICES_EXCEPT_SMS
    }

    public ServiceType ServiceTypeFromRILInt(int type) {
        switch (type) {
            case 0:
                return ServiceType.SS_CFU;
            case 1:
                return ServiceType.SS_CF_BUSY;
            case 2:
                return ServiceType.SS_CF_NO_REPLY;
            case 3:
                return ServiceType.SS_CF_NOT_REACHABLE;
            case 4:
                return ServiceType.SS_CF_ALL;
            case 5:
                return ServiceType.SS_CF_ALL_CONDITIONAL;
            case 6:
                return ServiceType.SS_CLIP;
            case 7:
                return ServiceType.SS_CLIR;
            case 8:
                return ServiceType.SS_COLP;
            case 9:
                return ServiceType.SS_COLR;
            case 10:
                return ServiceType.SS_WAIT;
            case 11:
                return ServiceType.SS_BAOC;
            case 12:
                return ServiceType.SS_BAOIC;
            case 13:
                return ServiceType.SS_BAOIC_EXC_HOME;
            case 14:
                return ServiceType.SS_BAIC;
            case 15:
                return ServiceType.SS_BAIC_ROAMING;
            case 16:
                return ServiceType.SS_ALL_BARRING;
            case 17:
                return ServiceType.SS_OUTGOING_BARRING;
            case 18:
                return ServiceType.SS_INCOMING_BARRING;
            default:
                throw new RuntimeException("Unrecognized SS ServiceType " + type);
        }
    }

    public RequestType RequestTypeFromRILInt(int type) {
        switch (type) {
            case 0:
                return RequestType.SS_ACTIVATION;
            case 1:
                return RequestType.SS_DEACTIVATION;
            case 2:
                return RequestType.SS_INTERROGATION;
            case 3:
                return RequestType.SS_REGISTRATION;
            case 4:
                return RequestType.SS_ERASURE;
            default:
                throw new RuntimeException("Unrecognized SS RequestType " + type);
        }
    }

    public TeleserviceType TeleserviceTypeFromRILInt(int type) {
        switch (type) {
            case 0:
                return TeleserviceType.SS_ALL_TELE_AND_BEARER_SERVICES;
            case 1:
                return TeleserviceType.SS_ALL_TELESEVICES;
            case 2:
                return TeleserviceType.SS_TELEPHONY;
            case 3:
                return TeleserviceType.SS_ALL_DATA_TELESERVICES;
            case 4:
                return TeleserviceType.SS_SMS_SERVICES;
            case 5:
                return TeleserviceType.SS_ALL_TELESERVICES_EXCEPT_SMS;
            default:
                throw new RuntimeException("Unrecognized SS TeleserviceType " + type);
        }
    }

    public String toString() {
        return "[SsData] ServiceType: " + this.mServiceType + " RequestType: " + this.mRequestType + " TeleserviceType: " + this.mTeleserviceType + " ServiceClass: " + this.mServiceClass + " Result: " + this.mResult + " Is Service Type CF: " + this.mServiceType.isTypeCF();
    }
}
