package com.android.internal.telephony.cat;

public enum ComprehensionTlvTag {
    COMMAND_DETAILS(1),
    DEVICE_IDENTITIES(2),
    RESULT(3),
    DURATION(4),
    ALPHA_ID(5),
    ADDRESS(6),
    SUBADDRESS(8),
    SS_STRING(9),
    USSD_STRING(10),
    SMS_TPDU(11),
    SMS_TPDU_CDMA(72),
    TEXT_STRING(13),
    TONE(14),
    ITEM(15),
    ITEM_ID(16),
    RESPONSE_LENGTH(17),
    FILE_LIST(18),
    HELP_REQUEST(21),
    DEFAULT_TEXT(23),
    EVENT_LIST(25),
    ICON_ID(30),
    ITEM_ICON_ID_LIST(31),
    IMMEDIATE_RESPONSE(43),
    DTMF_STRING(44),
    LANGUAGE(45),
    URL(49),
    BROWSER_TERMINATION_CAUSE(52),
    BEARER_DESCRIPTION(53),
    CHANNEL_DATA(54),
    CHANNEL_DATA_LENGTH(55),
    CHANNEL_STATUS(56),
    BUFFER_SIZE(57),
    TRANSPORT_LEVEL(60),
    DATA_DESTINATION_ADDRESS(62),
    NETWORK_ACCESS_NAME(71),
    TEXT_ATTRIBUTE(80),
    ITEM_TEXT_ATTRIBUTE_LIST(81);
    
    private int mValue;

    private ComprehensionTlvTag(int value) {
        this.mValue = value;
    }

    public int value() {
        return this.mValue;
    }

    public static ComprehensionTlvTag fromInt(int value) {
        for (ComprehensionTlvTag e : values()) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
