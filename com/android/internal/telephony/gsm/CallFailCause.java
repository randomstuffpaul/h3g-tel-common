package com.android.internal.telephony.gsm;

public interface CallFailCause {
    public static final int ACM_LIMIT_EXCEEDED = 68;
    public static final int BEARER_CAPABILITY_NOT_AUTHORIZED = 57;
    public static final int BEARER_NOT_AVAIL = 58;
    public static final int CALL_BARRED = 240;
    public static final int CAUSE_ACCESS_INFO_DISCARDED = 3043;
    public static final int CAUSE_ACM_LIMIT_EXCEEDED = 3068;
    public static final int CAUSE_BEARER_NOT_ALLOWED = 3057;
    public static final int CAUSE_BEARER_NOT_AVAIL = 3058;
    public static final int CAUSE_BEARER_NOT_IMPLEMENTED = 3065;
    public static final int CAUSE_CALL_REJECTED = 3021;
    public static final int CAUSE_CHANNEL_NOT_AVAIL = 3044;
    public static final int CAUSE_CHANNEL_UNACCEPTABLE = 3006;
    public static final int CAUSE_DESTINATION_OUT_OF_ORDER = 3027;
    public static final int CAUSE_FACILITY_NOT_IMPLEMENTED = 3069;
    public static final int CAUSE_FACILITY_NOT_SUBSCRIBED = 3050;
    public static final int CAUSE_FACILITY_REJECTED = 3029;
    public static final int CAUSE_IE_NON_EXIST_OR_NOT_IMPL = 3099;
    public static final int CAUSE_INCOMING_CALLS_BARRED_IN_CUG = 3055;
    public static final int CAUSE_INCOMPATIBLE_DESTINATION = 3088;
    public static final int CAUSE_INTERWORKING = 3127;
    public static final int CAUSE_INVALID_IE_CONTENTS = 3100;
    public static final int CAUSE_INVALID_NUMBER_FORMAT = 3028;
    public static final int CAUSE_INVALID_TI_VALUE = 3081;
    public static final int CAUSE_INVALID_TRANSIT_NETWORK = 3091;
    public static final int CAUSE_MANDATORY_IE_ERROR = 3096;
    public static final int CAUSE_MSG_NOT_COMP_STATE = 3098;
    public static final int CAUSE_MSG_NOT_COMP_WITH_CALL_STATE = 3101;
    public static final int CAUSE_MSG_TYPE_NON_EXIST_OR_NOT_IMPL = 3097;
    public static final int CAUSE_NETWORK_OUT_OF_ORDER = 3038;
    public static final int CAUSE_NON_SELECTED_USER_CLEARING = 3026;
    public static final int CAUSE_NORMAL_CLEARING = 3016;
    public static final int CAUSE_NORMAL_UNSPECIFIED = 3031;
    public static final int CAUSE_NO_CIRCUIT_AVAIL = 3034;
    public static final int CAUSE_NO_ROUTE_TO_DESTINATION = 3003;
    public static final int CAUSE_NO_USER_RESPONDING = 3018;
    public static final int CAUSE_NUMBER_CHANGED = 3022;
    public static final int CAUSE_OFFSET = 3000;
    public static final int CAUSE_ONLY_RESTRICTED_DIGITAL = 3070;
    public static final int CAUSE_OP_DETERMINED_BARRING = 3008;
    public static final int CAUSE_PRE_EMPTION = 3025;
    public static final int CAUSE_PROTOCOL_ERROR_UNSPECIFIED = 3111;
    public static final int CAUSE_QOS_NOT_AVAIL = 3049;
    public static final int CAUSE_RECOVERY_ON_TIMER_EXPIRY = 3102;
    public static final int CAUSE_RESOURCES_UNAVAILABLE = 3047;
    public static final int CAUSE_SEMANTICAL_INCORRECT_MSG = 3095;
    public static final int CAUSE_SERVICE_NOT_AVAILABLE = 3063;
    public static final int CAUSE_SERVICE_NOT_IMPLEMENTED = 3079;
    public static final int CAUSE_STATUS_ENQUIRY = 3030;
    public static final int CAUSE_SWITCHING_CONGESTION = 3042;
    public static final int CAUSE_TEMPORARY_FAILURE = 3041;
    public static final int CAUSE_UNASSIGNED_NUMBER = 3001;
    public static final int CAUSE_USER_ALERTING_NO_ANSWER = 3019;
    public static final int CAUSE_USER_BUSY = 3017;
    public static final int CAUSE_USER_NOT_IN_CUG = 3087;
    public static final int CHANNEL_NOT_AVAIL = 44;
    public static final int ERROR_UNSPECIFIED = 65535;
    public static final int FDN_BLOCKED = 241;
    public static final int KTF_FAIL_CAUSE_114 = 114;
    public static final int KTF_FAIL_CAUSE_115 = 115;
    public static final int KTF_FAIL_CAUSE_116 = 116;
    public static final int KTF_FAIL_CAUSE_20 = 20;
    public static final int NETWORK_OUT_OF_ORDER = 38;
    public static final int NORMAL_CLEARING = 16;
    public static final int NORMAL_UNSPECIFIED = 31;
    public static final int NO_CIRCUIT_AVAIL = 34;
    public static final int NO_USER_RESPONDING = 18;
    public static final int NUMBER_CHANGED = 22;
    public static final int QOS_NOT_AVAIL = 49;
    public static final int STATUS_ENQUIRY = 30;
    public static final int SWITCHING_CONGESTION = 42;
    public static final int TEMPORARY_FAILURE = 41;
    public static final int UNASSIGNED_NUMBER = 2;
    public static final int UNOBTAINABLE_NUMBER = 1;
    public static final int USER_ALERTING_NO_ANSWER = 19;
    public static final int USER_BUSY = 17;
    public static final int WIFI_OUT_OF_FOOTPRINT = 1100;
}
