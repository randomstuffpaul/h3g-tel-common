package com.android.internal.telephony.cat;

public interface AppInterface {
    public static final String BROWSER_HOMEPAGE = "homepage";
    public static final String CARD_STATUS = "card_status";
    public static final String CAT_CMD_ACTION = "android.intent.action.stk.command";
    public static final String CAT_CMD_ACTION2 = "android.intent.action.stk2.command";
    public static final String CAT_CMD_ACTIVATE = "android.intent.action.stk.activate";
    public static final String CAT_CMD_BROWSER_HOMEPAGE = "android.intent.action.STK_BROWSER_HOMEPAGE";
    public static final String CAT_CMD_BROWSER_HOMEPAGE2 = "android.intent.action.STK_BROWSER_HOMEPAGE2";
    public static final String CAT_CMD_GET_BROWSER_HOMEPAGE = "android.intent.action.STK_BROWSER_GET_HOMEPAGE";
    public static final String CAT_CMD_GET_BROWSER_HOMEPAGE2 = "android.intent.action.STK_BROWSER_GET_HOMEPAGE2";
    public static final String CAT_EXTRA_CAT_CMD = "STK CMD";
    public static final String CAT_EXTRA_SIM_ID = "simId";
    public static final String CAT_EXTRA_SIM_SLOT = "simSlot";
    public static final String CAT_ICC_STATUS_CHANGE = "android.intent.action.stk.icc_status_change";
    public static final String CAT_IDLE_SCREEN_ACTION = "android.intent.action.stk.idle_screen";
    public static final String CAT_REMOVE_ACTION2 = "android.intent.action.stk2.remove";
    public static final String CAT_SESSION_END_ACTION = "android.intent.action.stk.session_end";
    public static final String CAT_SESSION_END_ACTION2 = "android.intent.action.stk2.session_end";
    public static final String CHECK_SCREEN_IDLE_ACTION = "android.intent.action.stk.check_screen_idle";
    public static final String REFRESH_RESULT = "refresh_result";
    public static final String START_MAIN_ACTIVITY = "android.intent.action.stk.start_main_activity";
    public static final String START_MAIN_ACTIVITY2 = "android.intent.action.stk.start_main_activity2";
    public static final String START_MAIN_ACTIVITY_1 = "android.intent.action.stk2.start_main_activity";
    public static final String STK_PERMISSION = "android.permission.RECEIVE_STK_COMMANDS";
    public static final String UTK_CMD_ACTION = "android.intent.action.utk.command";
    public static final String UTK_CMD_ACTIVATE = "android.intent.action.utk.activate";
    public static final String UTK_SESSION_END_ACTION = "android.intent.action.utk.session_end";
    public static final String UTK_START_MAIN_ACTIVITY = "android.intent.action.utk.start_main_activity";

    public enum CommandType {
        DISPLAY_TEXT(33),
        GET_INKEY(34),
        GET_INPUT(35),
        LAUNCH_BROWSER(21),
        PLAY_TONE(32),
        REFRESH(1),
        SELECT_ITEM(36),
        SEND_SS(17),
        SEND_USSD(18),
        SEND_SMS(19),
        SEND_DTMF(20),
        SET_UP_EVENT_LIST(5),
        SET_UP_IDLE_MODE_TEXT(40),
        SET_UP_MENU(37),
        SET_UP_CALL(16),
        PROVIDE_LOCAL_INFORMATION(38),
        MORE_TIME(2),
        POLL_INTERVAL(3),
        POLLING_OFF(4),
        TIMER_MANAGEMENT(39),
        PERFORM_CARD_APDU(48),
        POWER_ON_CARD(49),
        POWER_OFF_CARD(50),
        GET_READER_STATUS(51),
        RUN_AT_COMMAND(52),
        LANGUAGE_NOTIFICATION(53),
        OPEN_CHANNEL(64),
        CLOSE_CHANNEL(65),
        RECEIVE_DATA(66),
        SEND_DATA(67),
        GET_CHANNEL_STATUS(68),
        ACTIVATE(112);
        
        private int mValue;

        private CommandType(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }

        public static CommandType fromInt(int value) {
            for (CommandType e : values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }

    boolean isAirplaneMode();

    void onCmdResponse(CatResponseMessage catResponseMessage);

    void onEventDownload(CatEnvelopeMessage catEnvelopeMessage);

    void sendEnvelopeToTriggerBip();

    void sendEnvelopeToTriggerBipforOTA(boolean z);

    void sentTerminalResponseForSetupMenu(boolean z);

    void setEventListChannelStatus(boolean z);

    void setEventListDataAvailable(boolean z);
}
