package com.android.internal.telephony.cat;

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.provider.Telephony.Sms.Intents;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SSReleaseCompleteNotification;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.google.android.mms.pdu.CharacterSets;
import com.samsung.android.telephony.MultiSimManager;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CatService extends Handler implements AppInterface {
    private static final boolean DBG = false;
    private static final int DEV_ID_DISPLAY = 2;
    private static final int DEV_ID_EARPIECE = 3;
    private static final int DEV_ID_KEYPAD = 1;
    private static final int DEV_ID_NETWORK = 131;
    private static final int DEV_ID_TERMINAL = 130;
    private static final int DEV_ID_UICC = 129;
    static final int EVENT_RIL_CONNECTED = 111;
    static final int EVENT_SEND_CALL_SCREEN_INTENT = 110;
    static final int EVENT_USSD_COMPLETE = 100;
    protected static final int MSG_ID_ALPHA_NOTIFY = 9;
    static final int MSG_ID_BIP_TERMINAL_RESPONSE = 109;
    static final int MSG_ID_CALL_CONTROL_RESULT = 105;
    protected static final int MSG_ID_CALL_SETUP = 4;
    static final int MSG_ID_CALL_STATUS = 24;
    static final int MSG_ID_EVENT = 106;
    protected static final int MSG_ID_EVENT_NOTIFY = 3;
    protected static final int MSG_ID_ICC_CHANGED = 8;
    private static final int MSG_ID_ICC_RECORDS_LOADED = 20;
    private static final int MSG_ID_ICC_REFRESH = 30;
    static final int MSG_ID_PHONE_DISCONNECT = 107;
    protected static final int MSG_ID_PROACTIVE_COMMAND = 2;
    static final int MSG_ID_REFRESH = 5;
    static final int MSG_ID_RELEASE_COMPLETE_MESSAGE = 101;
    static final int MSG_ID_RESPONSE = 6;
    static final int MSG_ID_RIL_MSG_DECODED = 10;
    static final int MSG_ID_SEND_DTMF_PAUSE = 108;
    static final int MSG_ID_SEND_DTMF_RESULT = 103;
    static final int MSG_ID_SEND_SMS_RESULT = 104;
    protected static final int MSG_ID_SESSION_END = 1;
    static final int MSG_ID_SIM_READY = 7;
    static final int MSG_ID_STK_ALPHA_ID = 112;
    static final int MSG_ID_TIMEOUT = 102;
    private static final int NOT_IN_USE = 0;
    static final String SIMNUM = "simnum";
    static final int SIM_FILE_UPDATE = 0;
    static final int SIM_INIT = 1;
    static final int SIM_NUM1 = 1;
    static final int SIM_NUM2 = 2;
    static final int SIM_RESET = 2;
    static final int SIM_RESET_FOR_SAP = 3;
    static final int SIM_SLOT1 = 0;
    static final int SIM_SLOT2 = 1;
    private static final int STK2_NOTIFICATION_ID = 444;
    static final String STK_DEFAULT = "Default Message";
    private static final int STK_NOTIFICATION_ID = 333;
    static final String STK_REFRESH = "default refresh...";
    static final String STK_RESET = "default reset...";
    static final String STK_SENDING = "null alphaId, default sending...";
    public static final int TYPE_STK = 0;
    public static final int TYPE_STK2 = 1;
    public static final int TYPE_UTK = 2;
    private static final int WAITING_ACTIVATE_RESULT = 6;
    private static final int WAITING_ACTIVATE_RESULT_TIME = 10000;
    private static final int WAITING_RELEASE_COMPLETE = 1;
    private static final int WAITING_RELEASE_COMPLETE_TIME = 30000;
    static final int WAITING_SEND_DTMF = 5;
    static final int WAITING_SEND_DTMF_TIME = 3500;
    private static final int WAITING_SETUP_CALL = 4;
    private static final int WAITING_SETUP_CALL_CONNECTED_RESULT = 8;
    private static final int WAITING_SETUP_CALL_CONNECTED_TIME = 2000;
    private static final int WAITING_SETUP_CALL_DISCONNECT_RESULT = 7;
    private static final int WAITING_SETUP_CALL_DISCONNECT_TIME = 2000;
    private static final int WAITING_SETUP_CALL_HOLD_RESULT = 3;
    private static final int WAITING_SETUP_CALL_HOLD_RESULT_TIME = 5000;
    private static final int WAITING_SETUP_CALL_TIME = 10000;
    private static final int WAITING_SMS_RESULT = 2;
    private static final int WAITING_SMS_RESULT_TIME = 60000;
    private static final int WAKE_LOCK_TIMEOUT = 65000;
    private static boolean is_stk_icon_label_update = false;
    static CommandParams mBIPCurrntCmd = null;
    private static IccRecords[] mIccRecords = new IccRecords[TelephonyManager.getDefault().getPhoneCount()];
    private static IccSmsInterfaceManager[] mIccSms = new IccSmsInterfaceManager[TelephonyManager.getDefault().getPhoneCount()];
    private static UiccCardApplication[] mUiccApplication = new UiccCardApplication[TelephonyManager.getDefault().getPhoneCount()];
    private static CatService[] sInstance = new CatService[TelephonyManager.getDefault().getPhoneCount()];
    private static final Object sInstanceLock = new Object();
    String alpha_id_display = null;
    private boolean bBIPSuccess = false;
    private boolean blockProactiveCommandDisplayText = false;
    String[] disabledCmdList = null;
    String disabledProactiveCmd;
    private boolean isSetupMenuByUser = false;
    private boolean isTerminalResponseForSEUPMENU = false;
    private boolean isUsingBackUpCmd = false;
    private String lastParams = "";
    private int mCallControlResultCode = 0;
    private int mCallType = 0;
    private CardState mCardState = CardState.CARDSTATE_ABSENT;
    private CatBIPManager mCatBIPMgr = null;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private CatCmdMessage mCurrntCmd = null;
    private DtmfString mDtmfString = null;
    private Handler mGetUiccVerHandler = new Handler();
    private Runnable mGetUiccVerRunnable = new C00521();
    private HandlerThread mHandlerThread;
    private CatCmdMessage mMenuCmd = null;
    private RilMessageDecoder mMsgDecoder = null;
    private NotificationManager mNotificationManager = null;
    private PhoneBase mPhone;
    private boolean mSendTerminalResponseExpectedByCallSetup = false;
    private boolean mSetupCallDisc = false;
    private int mSlotId;
    private boolean mStkAppInstalled = false;
    private int mTimeoutDest = 0;
    private UiccController mUiccController;
    WakeLock mWakeLock;
    private boolean stkRefreshReset = false;

    class C00521 implements Runnable {
        C00521() {
        }

        public void run() {
            CatService.mIccRecords[CatService.this.mSlotId].refreshUiccVer();
        }
    }

    static /* synthetic */ class C00532 {
        static final /* synthetic */ int[] f7xca33cf42 = new int[CommandType.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$cat$CallType = new int[CallType.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$cat$ResultCode = new int[ResultCode.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$cat$CallType[CallType.CALL_TYPE_MO_VOICE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$CallType[CallType.CALL_TYPE_MO_SMS.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$CallType[CallType.CALL_TYPE_MO_SS.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.HELP_INFO_REQUIRED.ordinal()] = 1;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.OK.ordinal()] = 2;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_WITH_PARTIAL_COMPREHENSION.ordinal()] = 3;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_WITH_MISSING_INFO.ordinal()] = 4;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_WITH_ADDITIONAL_EFS_READ.ordinal()] = 5;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_ICON_NOT_DISPLAYED.ordinal()] = 6;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_MODIFIED_BY_NAA.ordinal()] = 7;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_LIMITED_SERVICE.ordinal()] = 8;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_WITH_MODIFICATION.ordinal()] = 9;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_NAA_NOT_ACTIVE.ordinal()] = 10;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.PRFRMD_TONE_NOT_PLAYED.ordinal()] = 11;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS.ordinal()] = 12;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.BACKWARD_MOVE_BY_USER.ordinal()] = 13;
            } catch (NoSuchFieldError e16) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.USER_NOT_ACCEPT.ordinal()] = 14;
            } catch (NoSuchFieldError e17) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.NO_RESPONSE_FROM_USER.ordinal()] = 15;
            } catch (NoSuchFieldError e18) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.UICC_SESSION_TERM_BY_USER.ordinal()] = 16;
            } catch (NoSuchFieldError e19) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.BEYOND_TERMINAL_CAPABILITY.ordinal()] = 17;
            } catch (NoSuchFieldError e20) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$cat$ResultCode[ResultCode.LAUNCH_BROWSER_ERROR.ordinal()] = 18;
            } catch (NoSuchFieldError e21) {
            }
            try {
                f7xca33cf42[CommandType.SET_UP_MENU.ordinal()] = 1;
            } catch (NoSuchFieldError e22) {
            }
            try {
                f7xca33cf42[CommandType.DISPLAY_TEXT.ordinal()] = 2;
            } catch (NoSuchFieldError e23) {
            }
            try {
                f7xca33cf42[CommandType.REFRESH.ordinal()] = 3;
            } catch (NoSuchFieldError e24) {
            }
            try {
                f7xca33cf42[CommandType.SET_UP_IDLE_MODE_TEXT.ordinal()] = 4;
            } catch (NoSuchFieldError e25) {
            }
            try {
                f7xca33cf42[CommandType.PROVIDE_LOCAL_INFORMATION.ordinal()] = 5;
            } catch (NoSuchFieldError e26) {
            }
            try {
                f7xca33cf42[CommandType.LAUNCH_BROWSER.ordinal()] = 6;
            } catch (NoSuchFieldError e27) {
            }
            try {
                f7xca33cf42[CommandType.SELECT_ITEM.ordinal()] = 7;
            } catch (NoSuchFieldError e28) {
            }
            try {
                f7xca33cf42[CommandType.GET_INPUT.ordinal()] = 8;
            } catch (NoSuchFieldError e29) {
            }
            try {
                f7xca33cf42[CommandType.GET_INKEY.ordinal()] = 9;
            } catch (NoSuchFieldError e30) {
            }
            try {
                f7xca33cf42[CommandType.SEND_DTMF.ordinal()] = 10;
            } catch (NoSuchFieldError e31) {
            }
            try {
                f7xca33cf42[CommandType.SEND_SMS.ordinal()] = 11;
            } catch (NoSuchFieldError e32) {
            }
            try {
                f7xca33cf42[CommandType.SEND_SS.ordinal()] = 12;
            } catch (NoSuchFieldError e33) {
            }
            try {
                f7xca33cf42[CommandType.SEND_USSD.ordinal()] = 13;
            } catch (NoSuchFieldError e34) {
            }
            try {
                f7xca33cf42[CommandType.PLAY_TONE.ordinal()] = 14;
            } catch (NoSuchFieldError e35) {
            }
            try {
                f7xca33cf42[CommandType.SET_UP_CALL.ordinal()] = 15;
            } catch (NoSuchFieldError e36) {
            }
            try {
                f7xca33cf42[CommandType.OPEN_CHANNEL.ordinal()] = 16;
            } catch (NoSuchFieldError e37) {
            }
            try {
                f7xca33cf42[CommandType.CLOSE_CHANNEL.ordinal()] = 17;
            } catch (NoSuchFieldError e38) {
            }
            try {
                f7xca33cf42[CommandType.RECEIVE_DATA.ordinal()] = 18;
            } catch (NoSuchFieldError e39) {
            }
            try {
                f7xca33cf42[CommandType.SEND_DATA.ordinal()] = 19;
            } catch (NoSuchFieldError e40) {
            }
            try {
                f7xca33cf42[CommandType.GET_CHANNEL_STATUS.ordinal()] = 20;
            } catch (NoSuchFieldError e41) {
            }
            try {
                f7xca33cf42[CommandType.SET_UP_EVENT_LIST.ordinal()] = 21;
            } catch (NoSuchFieldError e42) {
            }
            try {
                f7xca33cf42[CommandType.LANGUAGE_NOTIFICATION.ordinal()] = 22;
            } catch (NoSuchFieldError e43) {
            }
        }
    }

    private CatService(CommandsInterface ci, UiccCardApplication ca, IccRecords ir, Context context, IccFileHandler fh, UiccCard ic, PhoneBase phone, int slotId) {
        if (ci == null || ca == null || ir == null || context == null || fh == null || ic == null || phone == null) {
            throw new NullPointerException("Service: Input parameters must not be null");
        }
        this.mCmdIf = ci;
        this.mContext = context;
        this.mSlotId = slotId;
        this.mHandlerThread = new HandlerThread("Cat Telephony service" + slotId);
        this.mHandlerThread.start();
        this.mPhone = phone;
        this.mMsgDecoder = RilMessageDecoder.getInstance(this, fh, slotId);
        if (this.mMsgDecoder == null) {
            CatLog.m0d((Object) this, "Null RilMessageDecoder instance");
            return;
        }
        this.mMsgDecoder.start();
        this.mCmdIf.setOnCatSessionEnd(this, 1, null);
        this.mCmdIf.setOnCatProactiveCmd(this, 2, null);
        this.mCmdIf.setOnCatEvent(this, 3, null);
        this.mCmdIf.setOnCatCallSetUp(this, 4, null);
        this.mCmdIf.registerForIccRefresh(this, 30, null);
        this.mCmdIf.setOnReleaseCompleteMessageRegistrant(this, 101, null);
        this.mCmdIf.setOnSendDTMFResult(this, 103, null);
        this.mCmdIf.setOnCatSendSmsResult(this, 104, null);
        this.mCmdIf.setOnCatCallControlResult(this, MSG_ID_CALL_CONTROL_RESULT, null);
        this.mCmdIf.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        this.mPhone.registerForDisconnect(this, MSG_ID_PHONE_DISCONNECT, null);
        if ("CTC".equals(SystemProperties.get("ro.csc.sales_code")) && "false".equals(getSystemProperty("ro.ril.stk_qmi_ril", "false")) && "DCGS".equals("DGG")) {
            this.mPhone.SimSlotOnOff(1);
        }
        if (2 == getPackageType(this.mSlotId)) {
            this.mCmdIf.setOnStkCallStatusResult(this, 24, null);
            this.mCmdIf.unSetOnCatCallControlResult(this);
            this.mPhone.unregisterForDisconnect(this);
        }
        mIccRecords[this.mSlotId] = ir;
        mUiccApplication[this.mSlotId] = ca;
        if (this.mPhone instanceof GSMPhone) {
            mIccSms[this.mSlotId] = ((PhoneProxy) PhoneFactory.getPhone(this.mSlotId)).getIccSmsInterfaceManager();
            if (mIccSms[this.mSlotId] == null) {
                throw new NullPointerException("mIccSms should not be null");
            }
        }
        this.isTerminalResponseForSEUPMENU = false;
        this.isSetupMenuByUser = false;
        CatLog.m0d((Object) this, "registerForReady slotid: " + this.mSlotId + "instance : " + this);
        mIccRecords[this.mSlotId].registerForRecordsLoaded(this, 20, null);
        mUiccApplication[this.mSlotId].registerForReady(this, 7, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 8, null);
        createWakelock();
        this.mCatBIPMgr = new CatBIPManager(context, phone, this, this.mSlotId);
        if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
            String stkProp;
            if (2 == getPackageType(this.mSlotId)) {
                stkProp = "gsm.UTK_SETUP_MENU";
            } else {
                stkProp = MultiSimManager.appendSimSlot("gsm.STK_SETUP_MENU", getPackageType(this.mSlotId));
            }
            if (!"".equals(SystemProperties.get(stkProp, ""))) {
                RilMessage rilMsg = loadBackUpProactiveCmd();
                if (rilMsg != null) {
                    this.isUsingBackUpCmd = true;
                    this.mMsgDecoder.sendStartDecodingMessageParams(rilMsg);
                }
            }
        }
        this.mStkAppInstalled = isStkAppInstalled();
        CatLog.m0d((Object) this, "Running CAT service on Slotid: " + this.mSlotId + ". PackageType: " + getPackageType(this.mSlotId) + ". STK app installed:" + this.mStkAppInstalled);
        this.disabledProactiveCmd = CscFeature.getInstance().getString("CscFeature_RIL_DisableSimToolKitCmds");
        this.disabledCmdList = this.disabledProactiveCmd.split(",");
        this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
    }

    public static CatService getInstance(CommandsInterface ci, Context context, UiccCard ic, PhoneBase phone, int slotId) {
        CatService catService = null;
        UiccCardApplication ca = null;
        IccFileHandler fh = null;
        IccRecords ir = null;
        if (ic != null) {
            ca = ic.getApplicationIndex(0);
            if (ca != null) {
                fh = ca.getIccFileHandler();
                ir = ca.getIccRecords();
            }
        }
        if (slotId >= 0 && slotId < TelephonyManager.getDefault().getPhoneCount()) {
            synchronized (sInstanceLock) {
                if (sInstance[slotId] == null) {
                    if (ci == null || ca == null || ir == null || context == null || fh == null || ic == null || phone == null) {
                    } else {
                        sInstance[slotId] = new CatService(ci, ca, ir, context, fh, ic, phone, slotId);
                    }
                } else if (!(ir == null || mIccRecords[slotId] == ir)) {
                    if (mIccRecords[slotId] != null) {
                        mIccRecords[slotId].unregisterForRecordsLoaded(sInstance[slotId]);
                    }
                    if (mUiccApplication[slotId] != null) {
                        mUiccApplication[slotId].unregisterForReady(sInstance[slotId]);
                    }
                    mIccRecords[slotId] = ir;
                    mUiccApplication[slotId] = ca;
                    mIccRecords[slotId].registerForRecordsLoaded(sInstance[slotId], 20, null);
                    mUiccApplication[slotId].registerForReady(sInstance[slotId], 7, null);
                }
                catService = sInstance[slotId];
            }
        }
        return catService;
    }

    public void dispose() {
        synchronized (sInstanceLock) {
            CatLog.m0d((Object) this, "Disposing CatService object");
            mIccRecords[this.mSlotId].unregisterForRecordsLoaded(this);
            broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_ABSENT, null);
            this.mCmdIf.unSetOnCatSessionEnd(this);
            this.mCmdIf.unSetOnCatProactiveCmd(this);
            this.mCmdIf.unSetOnCatEvent(this);
            this.mCmdIf.unSetOnCatCallSetUp(this);
            this.mCmdIf.unregisterForIccRefresh(this);
            this.mCmdIf.unSetOnReleaseCompleteMessageRegistrant(this);
            this.mCmdIf.unSetOnSendDTMFResult(this);
            this.mCmdIf.unSetOnCatSendSmsResult(this);
            this.mCmdIf.unSetOnCatCallControlResult(this);
            this.mCmdIf.unregisterForRilConnected(this);
            this.mPhone.unregisterForDisconnect(this);
            if (this.mUiccController != null) {
                this.mUiccController.unregisterForIccChanged(this);
                this.mUiccController = null;
            }
            if (mUiccApplication[this.mSlotId] != null) {
                mUiccApplication[this.mSlotId].unregisterForReady(this);
            }
            this.mMsgDecoder.dispose();
            this.mMsgDecoder = null;
            this.mHandlerThread.quit();
            this.mHandlerThread = null;
            removeCallbacksAndMessages(null);
            sInstance[this.mSlotId] = null;
        }
    }

    protected void finalize() {
        CatLog.m0d((Object) this, "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg != null) {
            CommandParams cmdParams;
            switch (rilMsg.mId) {
                case 1:
                    handleSessionEnd();
                    return;
                case 2:
                    try {
                        cmdParams = (CommandParams) rilMsg.mData;
                        if (cmdParams == null) {
                            return;
                        }
                        if (rilMsg.mResCode == ResultCode.OK) {
                            handleCommand(cmdParams, true);
                            return;
                        } else {
                            sendTerminalResponse(cmdParams.mCmdDet, rilMsg.mResCode, false, 0, null);
                            return;
                        }
                    } catch (ClassCastException e) {
                        CatLog.m0d((Object) this, "Fail to parse proactive command");
                        CommandDetails cmdDet = new CommandDetails();
                        byte[] rawData = IccUtils.hexStringToBytes((String) rilMsg.mData);
                        int lengthOffset = 0;
                        if (rawData[1] == (byte) -127) {
                            lengthOffset = 1;
                        }
                        cmdDet.commandNumber = rawData[lengthOffset + 4] & 255;
                        cmdDet.typeOfCommand = rawData[lengthOffset + 5] & 255;
                        cmdDet.commandQualifier = rawData[lengthOffset + 6] & 255;
                        sendTerminalResponse(cmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false, 0, null);
                        return;
                    }
                case 3:
                    if (rilMsg.mResCode == ResultCode.OK) {
                        cmdParams = rilMsg.mData;
                        if (cmdParams != null) {
                            handleCommand(cmdParams, false);
                            return;
                        }
                        return;
                    }
                    return;
                case 5:
                    cmdParams = rilMsg.mData;
                    if (cmdParams != null) {
                        handleCommand(cmdParams, false);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    private void handleCommand(CommandParams cmdParams, boolean isProactiveCmd) {
        CatLog.m0d((Object) this, cmdParams.getCommandType().name());
        CatCmdMessage catCmdMessage = new CatCmdMessage(cmdParams);
        Boolean sessionEnd = Boolean.valueOf(false);
        this.mCurrntCmd = catCmdMessage;
        Boolean sendIntent = Boolean.valueOf(true);
        this.blockProactiveCommandDisplayText = false;
        if ("AFR".equals(SystemProperties.get("ro.csc.sales_code")) || "KEN".equals(SystemProperties.get("ro.csc.sales_code"))) {
            if (cmdParams.getCommandType().name() == "DISPLAY_TEXT" && this.lastParams == "SEND_USSD") {
                this.blockProactiveCommandDisplayText = true;
                CatLog.m0d((Object) this, "DSPLAY_TEXT Popup will be blocked");
            }
            this.lastParams = cmdParams.getCommandType().name();
        }
        CharSequence message;
        switch (C00532.f7xca33cf42[cmdParams.getCommandType().ordinal()]) {
            case 1:
                if (!isDisabledCmd("SetupMenu")) {
                    if (this.mContext.getResources().getBoolean(17956933)) {
                        String stkProp;
                        if (2 == getPackageType(this.mSlotId)) {
                            stkProp = "gsm.UTK_SETUP_MENU";
                        } else {
                            stkProp = MultiSimManager.appendSimSlot("gsm.STK_SETUP_MENU", getPackageType(this.mSlotId));
                        }
                        if (!removeMenu(catCmdMessage.getMenu())) {
                            this.mMenuCmd = catCmdMessage;
                            CatLog.m0d((Object) this, "Feature for is_stk_icon_label_update is enabled");
                            String oldSysProp = SystemProperties.get(stkProp, null);
                            String newSysProp = this.mMenuCmd.getMenu().title;
                            if (newSysProp == null || "".equals(newSysProp)) {
                                is_stk_icon_label_update = false;
                            } else if ("".equals(oldSysProp)) {
                                CatLog.m0d((Object) this, "Condition for STK refresh detected enabling the intent to be fired");
                                is_stk_icon_label_update = true;
                            } else if (newSysProp.contains(oldSysProp)) {
                                is_stk_icon_label_update = false;
                            } else {
                                CatLog.m0d((Object) this, "Condition for STK refresh detected enabling the intent to be fired");
                                is_stk_icon_label_update = true;
                            }
                            if (this.mMenuCmd.getMenu().title != null) {
                                int maxNameLen = CscFeature.getInstance().getInteger("CscFeature_RIL_ConfigSTKNameLength", 20);
                                CatLog.m0d((Object) this, "SETUP_MENU property Setting. -AAA" + this.mSlotId);
                                if (this.mMenuCmd.getMenu().title.length() > maxNameLen) {
                                    SystemProperties.set(stkProp, this.mMenuCmd.getMenu().title.substring(0, maxNameLen - 1));
                                } else {
                                    SystemProperties.set(stkProp, this.mMenuCmd.getMenu().title);
                                }
                            } else {
                                CatLog.m0d((Object) this, "SETUP_MENU property Setting. -BBB" + this.mSlotId);
                                if (2 == getPackageType(this.mSlotId)) {
                                    SystemProperties.set(stkProp, "UIM Toolkit");
                                } else {
                                    SystemProperties.set(stkProp, MultiSimManager.appendSimSlot("SIM Toolkit", getPackageType(this.mSlotId)));
                                }
                            }
                            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_FixedStkMenu") || is_stk_icon_label_update || "China".equals(SystemProperties.get("ro.csc.country_code"))) {
                                String pkg;
                                String action = "android.intent.action.STK_TITLE_IS_LOADED";
                                if (2 == getPackageType(this.mSlotId)) {
                                    pkg = "com.sec.android.app.utk";
                                } else {
                                    pkg = MultiSimManager.appendSimSlot("com.android.stk", getPackageType(this.mSlotId));
                                }
                                this.mContext.sendBroadcast(new Intent(action, Uri.fromParts(Intents.EXTRA_PACKAGE_NAME, pkg, null)));
                                break;
                            }
                        }
                        this.mMenuCmd = null;
                        SystemProperties.set(stkProp, "");
                        break;
                    }
                    CatLog.m0d((Object) this, "Voice call is not supported. SET_UP_MENU is discarded.");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                return;
                break;
            case 2:
                if (isDisabledCmd("DisplayText")) {
                    CatLog.m0d((Object) this, "DisplayText not supported");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    return;
                } else if (!catCmdMessage.geTextMessage().responseNeeded) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    break;
                } else if (this.blockProactiveCommandDisplayText) {
                    CatLog.m0d((Object) this, "DISPLAY_TEXT POPUP IS BLOCKED");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                }
                break;
            case 3:
                CatLog.m0d((Object) this, "REFRESH : " + ((DisplayTextParams) cmdParams).mTextMsg.text.toString());
                if (!STK_REFRESH.equals(((DisplayTextParams) cmdParams).mTextMsg.text)) {
                    if (STK_RESET.equals(((DisplayTextParams) cmdParams).mTextMsg.text)) {
                        launchSimRefreshMsgAndCancelNoti(2);
                        break;
                    }
                }
                launchSimRefreshMsgAndCancelNoti(1);
                break;
                break;
            case 4:
                if (!isDisabledCmd("SetupIdleModeText")) {
                    launchIdleText();
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    break;
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                sendIntent = Boolean.valueOf(false);
                break;
            case 5:
                if (isDisabledCmd("ProvideLocalInformation")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                handleProactiveCommandProvideLocalInfo(cmdParams);
                return;
            case 6:
                if (!isDisabledCmd("LaunchBrowser")) {
                    if (((LaunchBrowserParams) cmdParams).mConfirmMsg.text == null || ((LaunchBrowserParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                        message = this.mContext.getText(17040841);
                        ((LaunchBrowserParams) cmdParams).mConfirmMsg.text = message.toString();
                        break;
                    }
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                sendIntent = Boolean.valueOf(false);
                break;
            case 7:
                if (isDisabledCmd("SelectItem")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                break;
            case 8:
                if (isDisabledCmd("GetInput")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                break;
            case 9:
                if (isDisabledCmd("GetInkey")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                break;
            case 10:
                if (isDisabledCmd("SendDtmf")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    return;
                } else if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    if (((DisplayTextParams) cmdParams).mTextMsg.text != null && ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                        ((DisplayTextParams) cmdParams).mTextMsg.text = null;
                        break;
                    }
                } else if (this.mPhone.getServiceState().getState() != 0) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                    return;
                } else if (this.mPhone.getForegroundCall().getState().isAlive()) {
                    handleProactiveCommandSendDTMF(cmdParams);
                    if (STK_DEFAULT.equals(((DisplayTextParams) cmdParams).mTextMsg.text)) {
                        CatLog.m0d((Object) this, "wakelock for Send DTMF");
                        this.mWakeLock.acquire(65000);
                        return;
                    }
                } else {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 7, null);
                    return;
                }
                break;
            case 11:
                if (!isDisabledCmd("SendSms")) {
                    if (this.mPhone.getServiceState().getState() == 0) {
                        handleProactiveCommandSendSMS(cmdParams);
                        if (((DisplayTextParams) cmdParams).mTextMsg != null && ((DisplayTextParams) cmdParams).mTextMsg.text != null && !((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                            if (2 == getPackageType(this.mSlotId) && ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_SENDING)) {
                                message = this.mContext.getText(17040840);
                                ((DisplayTextParams) cmdParams).mTextMsg.text = message.toString();
                                CatLog.m0d((Object) this, "sending sms " + message);
                                break;
                            }
                        }
                        CatLog.m0d((Object) this, "wakelock for SMS");
                        this.mWakeLock.acquire(65000);
                        return;
                    }
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                    return;
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                return;
                break;
            case 12:
                if (isDisabledCmd("SendSs")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    return;
                } else if (this.mPhone.getServiceState().getState() != 0) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                    return;
                } else {
                    handleProactiveCommandSendSS(cmdParams);
                    if (((DisplayTextParams) cmdParams).mTextMsg == null || ((DisplayTextParams) cmdParams).mTextMsg.text == null || ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                        CatLog.m0d((Object) this, "wakelock for SS");
                        this.mWakeLock.acquire(65000);
                        return;
                    }
                }
            case 13:
                if (isDisabledCmd("SendUssd")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    return;
                } else if (this.mPhone.getServiceState().getState() != 0) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                    return;
                } else {
                    handleProactiveCommandSendUSSD(cmdParams);
                    if (((DisplayTextParams) cmdParams).mTextMsg == null || ((DisplayTextParams) cmdParams).mTextMsg.text == null || ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                        CatLog.m0d((Object) this, "wakelock for USSD");
                        this.mWakeLock.acquire(65000);
                        return;
                    }
                }
            case 14:
                if (isDisabledCmd("PlayTone")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                break;
            case 15:
                if (isDisabledCmd("SetupCall")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                }
                CatLog.m0d((Object) this, "setup call");
                if (isInCall()) {
                    CatLog.m0d((Object) this, "phone is in call");
                    if (SetupCallCommandQualifiers.fromInt(cmdParams.mCmdDet.commandQualifier) == SetupCallCommandQualifiers.SET_UP_CALL_BUT_ONLY_IF_NOT_CURRENTLY_BUSY_ON_ANOTHER_CALL) {
                        CatLog.m0d((Object) this, "show Notification - Can't call by Incall");
                        Toast.makeText(this.mContext, this.mContext.getText(17041428).toString(), 1).show();
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 2, null);
                        return;
                    }
                }
                if (((CallSetupParams) cmdParams).mConfirmMsg.text != null && ((CallSetupParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                    message = this.mContext.getText(17040842);
                    ((CallSetupParams) cmdParams).mConfirmMsg.text = message.toString();
                } else if (((CallSetupParams) cmdParams).mConfirmMsg.text == null && 2 != getPackageType(this.mSlotId)) {
                    message = this.mContext.getText(17040842);
                    ((CallSetupParams) cmdParams).mConfirmMsg.text = message.toString();
                }
                if (((CallSetupParams) cmdParams).mCallMsg.text != null && ((CallSetupParams) cmdParams).mCallMsg.text.equals(STK_DEFAULT)) {
                    ((CallSetupParams) cmdParams).mCallMsg.text = null;
                    break;
                }
                break;
            case 16:
                CatLog.m0d((Object) this, "OPEN_CHANNEL");
                if (isDisabledCmd("OpenChannel")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                } else if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    OpenChannelParams OpenChannelcmd = (OpenChannelParams) cmdParams;
                    if (OpenChannelcmd.mTextMessage.text == null || OpenChannelcmd.mTextMessage.text.length() == 0) {
                        CatLog.m0d((Object) this, "cmd " + cmdParams.getCommandType() + " with null alpha id ");
                        this.mCmdIf.handleCallSetupRequestFromSim(true, null);
                        return;
                    }
                } else {
                    Context context = this.mContext;
                    Context context2 = this.mContext;
                    TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
                    CatLog.m0d((Object) this, "tm.getNetworkType() = " + tm.getNetworkType());
                    CommandParams op = (OpenChannelParams) cmdParams;
                    if (tm.getNetworkType() != 0 || op.mTransportLevel.isServer()) {
                        mBIPCurrntCmd = op;
                        displayOpenChannelParams(op);
                        int radioTechnology = this.mPhone.getServiceState().getRilDataRadioTechnology();
                        CatLog.m0d((Object) this, "get current radio techonology = " + radioTechnology);
                        if ((radioTechnology == 1 || radioTechnology == 2) && isInCall()) {
                            CatLog.m0d((Object) this, "send terminal response, TERMINAL_CRNTLY_UNABLE_TO_PROCESS, ME_CURRENTLY_BUSY_ON_CALL");
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 2, new OpenChannelResponseData());
                            return;
                        } else if (op.mTextMessage.text == null || op.mTextMessage.text.length() == 0) {
                            CatLog.m0d((Object) this, "no alphaID or alphaID 0 : no user confirm");
                            this.mCatBIPMgr.handleOpenChannel(op);
                            if (!CscFeature.getInstance().getEnableStatus("CscFeature_RIL_RemoveToastDuringBipOperation") && op.mTextMessage.text == null && !"LGT".equals("")) {
                                Toast.makeText(this.mContext, "open channel for BIP", 1).show();
                                return;
                            }
                            return;
                        }
                    }
                    CatLog.m0d((Object) this, "NETWORK_TYPE_UNKNOWN ");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                    return;
                }
                break;
            case 17:
                if (isDisabledCmd("CloseChannel")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                CommandParams cp = (CloseChannelParams) cmdParams;
                mBIPCurrntCmd = cp;
                if ("LGT".equals("") && !"true".equals(SystemProperties.get("ril.domesticOtaStart"))) {
                    this.mGetUiccVerHandler.postDelayed(this.mGetUiccVerRunnable, 100);
                    this.bBIPSuccess = true;
                }
                if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    CatLog.m0d((Object) this, "Does not send Terminal response ");
                    if (cp.mTextMessage.text == null) {
                        return;
                    }
                }
                displayCloseChannelParams(cp);
                CatLog.m0d((Object) this, "After Displaying Params Close Channel...Calling CatBIPMgr.handleCloseChannel: ");
                if (cp.mTextMessage.text == null) {
                    this.mCatBIPMgr.handleCloseChannel(cp);
                    return;
                }
                break;
            case 18:
                if (isDisabledCmd("ReceiveData")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                } else if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    CatLog.m0d((Object) this, "Does not send Terminal response ");
                    break;
                } else {
                    CommandParams rd = (ReceiveDataParams) cmdParams;
                    mBIPCurrntCmd = rd;
                    displayReceiveDataParams(rd);
                    CatLog.m0d((Object) this, "After Displaying Params RECEIVE_DATA...Calling CatBIPMgr.handleReceiveData: ");
                    this.mCatBIPMgr.handleReceiveData(rd);
                    if (rd.mTextMessage.text == null) {
                        return;
                    }
                }
                break;
            case 19:
                if (isDisabledCmd("SendData")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                } else if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    CatLog.m0d((Object) this, "Does not send Terminal response ");
                    break;
                } else {
                    CommandParams sd = (SendDataParams) cmdParams;
                    mBIPCurrntCmd = sd;
                    displaySendDataParams(sd);
                    CatLog.m0d((Object) this, "After Displaying Params SEND_DATA...Calling CatBIPMgr.handleSendData: ");
                    this.mCatBIPMgr.handleSendData(sd);
                    if (sd.mTextMessage.text == null) {
                        return;
                    }
                }
                break;
            case 20:
                if (isDisabledCmd("GetChannelStatus")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    return;
                }
                mBIPCurrntCmd = (GetChannelDataParams) cmdParams;
                displayChannelStatusParams(cmdParams);
                CatLog.m0d((Object) this, "After Displaying Params GET_CHANNEL_STATUS");
                this.mCatBIPMgr.getChannelStatus(cmdParams);
                return;
            case 21:
                if (!isDisabledCmd("SetupEventList")) {
                    if ("LGT".equals("")) {
                        sendIntent = Boolean.valueOf(false);
                        Bundle args = new Bundle();
                        args.putInt("op", 1);
                        args.putParcelable("cmd message", catCmdMessage);
                        Intent intent = new Intent();
                        intent.setClassName("com.android.stk", "com.android.stk.StkAppService");
                        intent.putExtras(args);
                        this.mContext.startService(intent);
                        break;
                    }
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                sendIntent = Boolean.valueOf(false);
                break;
                break;
            case 22:
                if (isDisabledCmd("LanguageNotification")) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    sendIntent = Boolean.valueOf(false);
                    break;
                }
                break;
            default:
                CatLog.m0d((Object) this, "Unsupported command");
                return;
        }
        if (sendIntent.booleanValue()) {
            this.mCurrntCmd = catCmdMessage;
            broadcastCatCmdIntent(catCmdMessage);
        }
    }

    private void broadcastCatCmdIntent(CatCmdMessage cmdMsg) {
        Intent intent;
        if (2 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.UTK_CMD_ACTION);
        } else if (1 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION2);
        } else {
            intent = new Intent(AppInterface.CAT_CMD_ACTION);
        }
        intent.putExtra(AppInterface.CAT_EXTRA_CAT_CMD, cmdMsg);
        intent.putExtra("SLOT_ID", this.mSlotId);
        CatLog.m0d((Object) this, "Sending CmdMsg: " + cmdMsg + " on slotid:" + this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void handleSessionEnd() {
        Intent intent;
        CatLog.m0d((Object) this, "SESSION END on " + this.mSlotId);
        this.mCurrntCmd = this.mMenuCmd;
        if (2 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.UTK_SESSION_END_ACTION);
        } else if (1 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION2);
        } else {
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
        }
        intent.putExtra("SLOT_ID", this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
        if ("LGT".equals("") && this.bBIPSuccess) {
            CatLog.m0d((Object) this, "broadcasting com.sec.android.lgt.bip.SUCCESS");
            this.mContext.sendBroadcast(new Intent("com.sec.android.lgt.bip.SUCCESS"));
            this.bBIPSuccess = false;
        }
    }

    private void sendTerminalResponse(CommandDetails cmdDet, ResultCode resultCode, boolean includeAdditionalInfo, int additionalInfo, ResponseData resp) {
        int length = 2;
        if (cmdDet != null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Input cmdInput = null;
            if (this.mCurrntCmd != null) {
                cmdInput = this.mCurrntCmd.geInput();
            }
            int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
            if (cmdDet.compRequired) {
                tag |= 128;
            }
            buf.write(tag);
            buf.write(3);
            buf.write(cmdDet.commandNumber);
            buf.write(cmdDet.typeOfCommand);
            buf.write(cmdDet.commandQualifier);
            buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
            buf.write(2);
            buf.write(130);
            buf.write(129);
            if (this.mCurrntCmd != null && resultCode == ResultCode.OK && this.mCurrntCmd.hasTextAttribute()) {
                resultCode = ResultCode.PRFRMD_WITH_PARTIAL_COMPREHENSION;
            }
            if (this.mCurrntCmd != null && resultCode == ResultCode.OK && this.mCurrntCmd.getHasIcon()) {
                resultCode = ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
            }
            tag = ComprehensionTlvTag.RESULT.value();
            if (cmdDet.compRequired) {
                tag |= 128;
            }
            buf.write(tag);
            if (!includeAdditionalInfo) {
                length = 1;
            }
            buf.write(length);
            buf.write(resultCode.value());
            if (includeAdditionalInfo) {
                buf.write(additionalInfo);
            }
            if (resp != null) {
                resp.format(buf);
            } else {
                encodeOptionalTags(cmdDet, resultCode, cmdInput, buf);
            }
            this.mCmdIf.sendTerminalResponse(IccUtils.bytesToHexString(buf.toByteArray()), null);
            this.mCurrntCmd = null;
            mBIPCurrntCmd = null;
        }
    }

    private void sendTerminalResponse(CommandDetails cmdDet, ResultCode resultCode, SSReleaseCompleteNotification data, ResponseData resp) {
        CatLog.m0d((Object) this, " sendTerminalResponse");
        if (cmdDet == null) {
            CatLog.m0d((Object) this, "(cmdDet == null) ");
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
        if (cmdDet.compRequired) {
            tag |= 128;
        }
        buf.write(tag);
        buf.write(3);
        buf.write(cmdDet.commandNumber);
        buf.write(cmdDet.typeOfCommand);
        buf.write(cmdDet.commandQualifier);
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        buf.write(2);
        buf.write(130);
        buf.write(129);
        buf.write(ComprehensionTlvTag.RESULT.value() | 128);
        int length = 0;
        byte[] rawData;
        if (cmdDet.typeOfCommand == 17) {
            CatLog.m0d((Object) this, " making Send SS Terminal Response ");
            if (data.dataLen != 0) {
                length = data.dataLen + 1;
            }
            if (length == 0) {
                length = 1;
            }
            buf.write(length);
            if (data.params == 3) {
                CatLog.m0d((Object) this, " SS Release complete error info ");
                buf.write(52);
            } else {
                if (this.mCurrntCmd != null && resultCode == ResultCode.OK && this.mCurrntCmd.getHasIcon()) {
                    resultCode = ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
                }
                buf.write(resultCode.value());
            }
            rawData = null;
            try {
                rawData = IccUtils.hexStringToBytes(data.data);
            } catch (Exception e) {
                CatLog.m0d((Object) this, "fail make additionalInfo");
            }
            if (rawData != null) {
                buf.write(rawData, 0, data.dataLen);
            }
        } else if (cmdDet.typeOfCommand == 18) {
            CatLog.m0d((Object) this, " making Send USSD Terminal Response ");
            if (data.params == 3) {
                buf.write(2);
                CatLog.m0d((Object) this, " USSD result error ");
                buf.write(55);
                rawData = null;
                try {
                    rawData = IccUtils.hexStringToBytes(data.data);
                } catch (Exception e2) {
                    CatLog.m0d((Object) this, "fail make additionalInfo");
                }
                if (rawData != null) {
                    if (rawData[0] == (byte) 18) {
                        rawData[0] = (byte) 0;
                    }
                    buf.write(rawData, 0, data.dataLen);
                }
            } else {
                buf.write(1);
                if (this.mCurrntCmd != null && resultCode == ResultCode.OK && this.mCurrntCmd.getHasIcon()) {
                    resultCode = ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
                }
                buf.write(resultCode.value());
                if (!("XSE".equals(SystemProperties.get("ro.csc.sales_code")) || "DRC".equals(SystemProperties.get("ro.csc.sales_code")) || "XID".equals(SystemProperties.get("ro.csc.sales_code")))) {
                    buf.write(13);
                    rawData = null;
                    try {
                        rawData = IccUtils.hexStringToBytes(data.data);
                    } catch (Exception e3) {
                        CatLog.m0d((Object) this, "fail make additionalInfo");
                    }
                    if (rawData != null && rawData.length > 1) {
                        if (((rawData[1] + 1) & 255) > 127) {
                            buf.write(129);
                        }
                        buf.write(rawData[1] + 1);
                        if (rawData[0] == (byte) 17) {
                            rawData[0] = (byte) 8;
                        } else if ((rawData[0] & 240) == 0) {
                            CatLog.m0d((Object) this, "CBS DCS for GSM 7bit will be changed to SMS DCS for GSM 7bit!!! ");
                            rawData[0] = (byte) 0;
                        }
                        buf.write(rawData[0] & 15);
                        try {
                            buf.write(rawData, 2, data.dataLen);
                        } catch (ArrayIndexOutOfBoundsException e4) {
                            CatLog.m0d((Object) this, "fail make ussd string");
                        }
                    }
                }
            }
        }
        if (resp != null) {
            resp.format(buf);
        }
        String hexString = IccUtils.bytesToHexString(buf.toByteArray());
        CatLog.m0d((Object) this, "TERMINAL RESPONSE: " + hexString);
        this.mCmdIf.sendTerminalResponse(hexString, null);
        this.mCurrntCmd = null;
    }

    private void encodeOptionalTags(CommandDetails cmdDet, ResultCode resultCode, Input cmdInput, ByteArrayOutputStream buf) {
        CommandType cmdType = CommandType.fromInt(cmdDet.typeOfCommand);
        if (cmdType != null) {
            switch (C00532.f7xca33cf42[cmdType.ordinal()]) {
                case 5:
                    if (cmdDet.commandQualifier == 4 && resultCode.value() == ResultCode.OK.value()) {
                        getPliResponse(buf);
                        return;
                    }
                    return;
                case 9:
                    if (resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value() && cmdInput != null && cmdInput.duration != null) {
                        getInKeyResponse(buf, cmdInput);
                        return;
                    }
                    return;
                default:
                    CatLog.m0d((Object) this, "encodeOptionalTags() Unsupported Cmd details=" + cmdDet);
                    return;
            }
        }
        CatLog.m0d((Object) this, "encodeOptionalTags() bad Cmd details=" + cmdDet);
    }

    private void getInKeyResponse(ByteArrayOutputStream buf, Input cmdInput) {
        buf.write(ComprehensionTlvTag.DURATION.value());
        buf.write(2);
        TimeUnit timeUnit = cmdInput.duration.timeUnit;
        buf.write(TimeUnit.SECOND.value());
        buf.write(cmdInput.duration.timeInterval);
    }

    private void getPliResponse(ByteArrayOutputStream buf) {
        String lang = SystemProperties.get("persist.sys.language");
        if (lang != null) {
            buf.write(ComprehensionTlvTag.LANGUAGE.value());
            ResponseData.writeLength(buf, lang.length());
            buf.write(lang.getBytes(), 0, lang.length());
        }
    }

    private void sendMenuSelection(int menuId, boolean helpRequired) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(211);
        buf.write(0);
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        buf.write(2);
        buf.write(1);
        buf.write(129);
        buf.write(ComprehensionTlvTag.ITEM_ID.value() | 128);
        buf.write(1);
        buf.write(menuId);
        if (helpRequired) {
            buf.write(ComprehensionTlvTag.HELP_REQUEST.value());
            buf.write(0);
        }
        byte[] rawData = buf.toByteArray();
        rawData[1] = (byte) (rawData.length - 2);
        this.mCmdIf.sendEnvelope(IccUtils.bytesToHexString(rawData), null);
    }

    private void eventDownload(int event, int sourceId, int destinationId, byte[] additionalInfo, boolean oneShot) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(214);
        buf.write(0);
        buf.write(ComprehensionTlvTag.EVENT_LIST.value() | 128);
        buf.write(1);
        buf.write(event);
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        buf.write(2);
        buf.write(sourceId);
        buf.write(destinationId);
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }
        byte[] rawData = buf.toByteArray();
        rawData[1] = (byte) (rawData.length - 2);
        this.mCmdIf.sendEnvelope(IccUtils.bytesToHexString(rawData), null);
    }

    public static AppInterface getInstance() {
        SubscriptionController sControl = SubscriptionController.getInstance();
        if (sControl != null) {
            int slotId = sControl.getSlotId(sControl.getDefaultSubId());
        }
        return getInstance(null, null, null, null, 0);
    }

    public static AppInterface getInstance(int slotId) {
        return getInstance(null, null, null, null, slotId);
    }

    public void handleMessage(Message msg) {
        CatLog.m0d((Object) this, "handleMessage[" + msg.what + "]");
        AsyncResult ar;
        switch (msg.what) {
            case 1:
            case 2:
            case 3:
            case 5:
                CatLog.m0d((Object) this, "ril message arrived,slotid:" + this.mSlotId);
                String data = null;
                if (msg.obj != null) {
                    ar = msg.obj;
                    if (!(ar == null || ar.result == null)) {
                        try {
                            data = ar.result;
                        } catch (ClassCastException e) {
                            return;
                        }
                    }
                }
                if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true")) && msg.what == 2 && isSetUpMenu(data)) {
                    saveBackUpProactiveCmd(new RilMessage(msg.what, data));
                }
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
                return;
            case 4:
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
                return;
            case 6:
                handleCmdResponse((CatResponseMessage) msg.obj);
                return;
            case 7:
                CatLog.m0d((Object) this, "SIM ready. Reporting STK service running now...");
                this.mCmdIf.reportStkServiceIsRunning(null);
                return;
            case 8:
                CatLog.m0d((Object) this, "MSG_ID_ICC_CHANGED");
                updateIccAvailability();
                return;
            case 10:
                handleRilMsg((RilMessage) msg.obj);
                return;
            case 20:
                if ("LGT".equals("")) {
                    this.mCmdIf.unSetOnCatSessionEnd(this);
                    this.mCmdIf.unSetOnCatProactiveCmd(this);
                    this.mCmdIf.unSetOnCatEvent(this);
                    this.mCmdIf.setOnCatSessionEnd(this, 1, null);
                    this.mCmdIf.setOnCatProactiveCmd(this, 2, null);
                    this.mCmdIf.setOnCatEvent(this, 3, null);
                    return;
                }
                return;
            case 24:
                CatLog.m0d((Object) this, "handleMsg : MSG_ID_CALL_STATUS");
                if (this.mTimeoutDest == 4) {
                    CatLog.m0d((Object) this, "The Msg ID data:" + msg.what);
                    int[] callstatus = new int[1];
                    if (msg.obj != null) {
                        ar = (AsyncResult) msg.obj;
                        if (!(ar == null || ar.result == null)) {
                            try {
                                callstatus = (int[]) ar.result;
                            } catch (ClassCastException e2) {
                                return;
                            }
                        }
                    }
                    int callstat = callstatus[0];
                    CatLog.m0d((Object) this, "Call status result data" + callstat);
                    if (this.mSendTerminalResponseExpectedByCallSetup && CallStatus.fromInt(callstat) == CallStatus.CALL_STATUS_CONNECTED) {
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
                        this.mSendTerminalResponseExpectedByCallSetup = false;
                        cancelTimeOut();
                    }
                    if (this.mSendTerminalResponseExpectedByCallSetup && CallStatus.fromInt(callstat) == CallStatus.CALL_STATUS_RELEASED) {
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                        this.mSendTerminalResponseExpectedByCallSetup = false;
                        cancelTimeOut();
                        return;
                    }
                    return;
                }
                return;
            case 30:
                CatLog.m0d((Object) this, "MSG_ID_ICC_REFRESH");
                if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_RemoveToastDuringStkRefresh")) {
                    CatLog.m0d((Object) this, "Do not display a toast for SIM Refresh");
                    return;
                } else if (msg.obj != null) {
                    ar = (AsyncResult) msg.obj;
                    if (ar == null || ar.result == null) {
                        CatLog.m0d((Object) this, "Icc REFRESH with exception: " + ar.exception);
                        return;
                    }
                    broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_PRESENT, (IccRefreshResponse) ar.result);
                    try {
                        IccRefreshResponse refreshRsp = ar.result;
                        CatLog.m0d((Object) this, "send refresh");
                        if (refreshRsp.refreshResult == 2) {
                            this.stkRefreshReset = true;
                        }
                        launchSimRefreshMsgAndCancelNoti(refreshRsp.refreshResult);
                        return;
                    } catch (ClassCastException e3) {
                        CatLog.m0d((Object) this, "ClassCastException from SIM_REFRESH");
                        return;
                    }
                } else {
                    CatLog.m0d((Object) this, "IccRefresh Message is null");
                    return;
                }
            case 101:
                CatLog.m0d((Object) this, "handleMsg : MSG_ID_RELEASE_COMPLETE_MESSAGE");
                switch (this.mTimeoutDest) {
                    case 1:
                        cancelTimeOut();
                        SSReleaseCompleteNotification sSReleaseCompleteNotification = null;
                        if (msg.obj != null) {
                            ar = (AsyncResult) msg.obj;
                            if (!(ar == null || ar.result == null)) {
                                try {
                                    sSReleaseCompleteNotification = ar.result;
                                } catch (ClassCastException e4) {
                                    return;
                                }
                            }
                        }
                        if (sSReleaseCompleteNotification == null) {
                            sSReleaseCompleteNotification = new SSReleaseCompleteNotification();
                        }
                        CatLog.m0d((Object) this, "got ReleaseComplete and need it");
                        if (this.mCurrntCmd != null) {
                            CatLog.m0d((Object) this, "mCallType : " + this.mCallType);
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED && (CallType.fromInt(this.mCallType) == CallType.CALL_TYPE_MO_SS || CallType.fromInt(this.mCallType) == CallType.CALL_TYPE_MO_USSD)) {
                                CatLog.m0d((Object) this, "send fail TR ");
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            }
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, sSReleaseCompleteNotification, null);
                            return;
                        }
                        CatLog.m0d((Object) this, "mCurrntCmd = null error handle is needed");
                        return;
                    case 3:
                        CatLog.m0d((Object) this, "mWaitingSetupCallHoldResult = true");
                        cancelTimeOut();
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                        return;
                    default:
                        return;
                }
            case 102:
                CatLog.m0d((Object) this, "MSG_ID_TIMEOUT timeout!!!");
                if (this.mCurrntCmd == null) {
                    this.mTimeoutDest = 0;
                    return;
                }
                switch (this.mTimeoutDest) {
                    case 1:
                        if (this.mCurrntCmd.mCmdDet.typeOfCommand != 17) {
                            if (this.mCurrntCmd.mCmdDet.typeOfCommand == 18) {
                                if (CallControlResult.fromInt(this.mCallControlResultCode) != CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                    sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USSD_RETURN_ERROR, true, 4, null);
                                    break;
                                } else {
                                    sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                    break;
                                }
                            }
                        } else if (CallControlResult.fromInt(this.mCallControlResultCode) != CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.SS_RETURN_ERROR, true, 4, null);
                            break;
                        } else {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                            break;
                        }
                        break;
                    case 2:
                        if (CallControlResult.fromInt(this.mCallControlResultCode) != CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                            break;
                        } else {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                            break;
                        }
                    case 3:
                    case 7:
                        if (this.mCurrntCmd.getCallSettings().address != null) {
                            Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", this.mCurrntCmd.getCallSettings().address, null));
                            if (intent != null) {
                                intent.setFlags(268435456);
                                this.mSendTerminalResponseExpectedByCallSetup = true;
                                if (2 != getPackageType(this.mSlotId)) {
                                    CatLog.m0d((Object) this, "*************call intent");
                                    this.mCmdIf.setSimInitEvent(null);
                                    this.mContext.startActivity(intent);
                                    break;
                                }
                                intent.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, this.mSlotId);
                                intent.putExtra(SIMNUM, 1);
                                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", (Parcelable) ((TelecomManager) this.mContext.getSystemService("telecom")).getCallCapablePhoneAccounts().get(0));
                                if (this.mTimeoutDest == 3) {
                                    CatLog.m0d((Object) this, "startTimeOut(WAITING_SETUP_CALL_CONNECTED_RESULT)");
                                    startTimeOut(8, 2000);
                                } else {
                                    CatLog.m0d((Object) this, "WAITING_SETUP_CALL_DISCONNECT_RESULT startTimeOut(WAITING_SETUP_CALL)");
                                    startTimeOut(4, 10000);
                                }
                                this.mContext.sendBroadcast(new Intent("android.intent.action.SetupCallbyUTK"));
                                CatLog.m0d((Object) this, "*************call intent for CDMA");
                                this.mContext.startActivity(intent);
                                return;
                            }
                            CatLog.m0d((Object) this, "fail to make call intent");
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                            return;
                        }
                        CatLog.m0d((Object) this, "setup call address is null");
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.REQUIRED_VALUES_MISSING, false, 0, null);
                        return;
                    case 4:
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                        break;
                    case 5:
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 7, null);
                        break;
                    case 6:
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                        break;
                    case 8:
                        CatLog.m0d((Object) this, "WAITING_SETUP_CALL_CONNECTED_RESULT");
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
                        cancelTimeOut();
                        break;
                }
                this.mTimeoutDest = 0;
                return;
            case 103:
                CatLog.m0d((Object) this, "MSG_ID_SEND_DTMF_RESULT");
                cancelTimeOut();
                if (msg.obj != null) {
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
                        return;
                    } else if (((CommandException) ar.exception).getCommandError() == Error.GENERIC_FAILURE) {
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 7, null);
                        return;
                    } else {
                        CatLog.m0d((Object) this, "send DTMF Error except GENERIC_FAILURE!!!");
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true, 4, null);
                        return;
                    }
                }
                return;
            case 104:
                CatLog.m0d((Object) this, "handleMsg : MSG_ID_SEND_SMS_RESULT");
                if (this.mTimeoutDest == 2 && this.mCurrntCmd != null) {
                    cancelTimeOut();
                    CatLog.m0d((Object) this, "The Msg ID data:" + msg.what);
                    int[] result = new int[1];
                    if (msg.obj != null) {
                        ar = (AsyncResult) msg.obj;
                        if (!(ar == null || ar.result == null)) {
                            try {
                                result = (int[]) ar.result;
                            } catch (ClassCastException e5) {
                                return;
                            }
                        }
                    }
                    switch (result[0]) {
                        case 0:
                            CatLog.m0d((Object) this, "SMS SEND OK");
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            } else {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
                                return;
                            }
                        case 32790:
                            CatLog.m0d((Object) this, "SMS SEND FAIL - MEMORY NOT AVAILABLE");
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            } else {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                                return;
                            }
                        case 32810:
                            CatLog.m0d((Object) this, "SMS SEND FAIL RETRY");
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            } else {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                                return;
                            }
                        case 32879:
                            CatLog.m0d((Object) this, "NO RP-ACK");
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            } else {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS, true, 0, null);
                                return;
                            }
                        default:
                            CatLog.m0d((Object) this, "SMS SEND GENERIC FAIL");
                            if (CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
                                return;
                            } else {
                                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                                return;
                            }
                    }
                }
                return;
            case MSG_ID_CALL_CONTROL_RESULT /*105*/:
                CatLog.m0d((Object) this, "handleMsg : MSG_ID_CALL_CONTROL_RESULT");
                String callcontrol_result = null;
                if (msg.obj != null) {
                    ar = (AsyncResult) msg.obj;
                    if (!(ar == null || ar.result == null)) {
                        try {
                            callcontrol_result = ar.result;
                        } catch (ClassCastException e6) {
                            return;
                        }
                    }
                }
                CatLog.m0d((Object) this, "Call control result data" + callcontrol_result);
                handleCallControlResultNoti(callcontrol_result);
                return;
            case 106:
                eventDownload(((CatEnvelopeMessage) msg.obj).event, ((CatEnvelopeMessage) msg.obj).sourceID, ((CatEnvelopeMessage) msg.obj).destinationID, ((CatEnvelopeMessage) msg.obj).additionalInfo, true);
                return;
            case MSG_ID_PHONE_DISCONNECT /*107*/:
                CatLog.m0d((Object) this, "MSG_ID_PHONE_DISCONNECT");
                if (this.mSetupCallDisc) {
                    this.mSetupCallDisc = false;
                    SetupCallFromStk(this.mCurrntCmd.getCallSettings().address);
                    return;
                }
                return;
            case MSG_ID_SEND_DTMF_PAUSE /*108*/:
                CatLog.m0d((Object) this, "pause 3 secs");
                processDTMFString();
                return;
            case MSG_ID_BIP_TERMINAL_RESPONSE /*109*/:
                if (msg.obj != null) {
                    CatBIPResponseMessage res = msg.obj;
                    sendTerminalResponse(res.mCmdDet, res.resCode, res.hasAdditionalInfo, res.AdditionalInfo, res.data);
                    return;
                }
                return;
            case 110:
                CatLog.m0d((Object) this, "Send InCallScreen Intent");
                this.mContext.startActivity((Intent) msg.obj);
                return;
            case EVENT_RIL_CONNECTED /*111*/:
                CatLog.m0d((Object) this, "Ril Connected so we send Stk Running Request");
                this.mCmdIf.reportStkServiceIsRunning(null);
                return;
            default:
                throw new AssertionError("Unrecognized CAT command: " + msg.what);
        }
    }

    private void broadcastCardStateAndIccRefreshResp(CardState cardState, IccRefreshResponse iccRefreshState) {
        Intent intent = new Intent(AppInterface.CAT_ICC_STATUS_CHANGE);
        boolean cardPresent = cardState == CardState.CARDSTATE_PRESENT;
        if (iccRefreshState != null) {
            intent.putExtra(AppInterface.REFRESH_RESULT, iccRefreshState.refreshResult);
            CatLog.m0d((Object) this, "Sending IccResult with Result: " + iccRefreshState.refreshResult);
        }
        intent.putExtra(AppInterface.CARD_STATUS, cardPresent);
        CatLog.m0d((Object) this, "Sending Card Status: " + cardState + " " + "cardPresent: " + cardPresent);
        intent.putExtra("SLOT_ID", this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    public synchronized void onCmdResponse(CatResponseMessage resMsg) {
        if (resMsg != null) {
            obtainMessage(6, resMsg).sendToTarget();
        }
    }

    private boolean validateResponse(CatResponseMessage resMsg) {
        if (this.mCurrntCmd != null) {
            return resMsg.mCmdDet.compareTo(this.mCurrntCmd.mCmdDet);
        }
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
            return false;
        } catch (NullPointerException e) {
            CatLog.m0d((Object) this, "Unable to get Menu's items size");
            return true;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleCmdResponse(com.android.internal.telephony.cat.CatResponseMessage r25) {
        /*
        r24 = this;
        r2 = r24.validateResponse(r25);
        if (r2 != 0) goto L_0x0007;
    L_0x0006:
        return;
    L_0x0007:
        r7 = 0;
        r18 = 0;
        r3 = r25.getCmdDetails();
        r2 = r3.typeOfCommand;
        r23 = com.android.internal.telephony.cat.AppInterface.CommandType.fromInt(r2);
        r15 = 0;
        r2 = com.android.internal.telephony.cat.CatService.C00532.$SwitchMap$com$android$internal$telephony$cat$ResultCode;
        r0 = r25;
        r4 = r0.mResCode;
        r4 = r4.ordinal();
        r2 = r2[r4];
        switch(r2) {
            case 1: goto L_0x0025;
            case 2: goto L_0x0027;
            case 3: goto L_0x0027;
            case 4: goto L_0x0027;
            case 5: goto L_0x0027;
            case 6: goto L_0x0027;
            case 7: goto L_0x0027;
            case 8: goto L_0x0027;
            case 9: goto L_0x0027;
            case 10: goto L_0x0027;
            case 11: goto L_0x0027;
            case 12: goto L_0x0027;
            case 13: goto L_0x044c;
            case 14: goto L_0x046c;
            case 15: goto L_0x048b;
            case 16: goto L_0x04c1;
            case 17: goto L_0x04c1;
            case 18: goto L_0x04c1;
            default: goto L_0x0024;
        };
    L_0x0024:
        goto L_0x0006;
    L_0x0025:
        r18 = 1;
    L_0x0027:
        r2 = com.android.internal.telephony.cat.CatService.C00532.f7xca33cf42;
        r4 = r23.ordinal();
        r2 = r2[r4];
        switch(r2) {
            case 1: goto L_0x0049;
            case 2: goto L_0x0032;
            case 3: goto L_0x0032;
            case 4: goto L_0x0032;
            case 5: goto L_0x0032;
            case 6: goto L_0x0032;
            case 7: goto L_0x0087;
            case 8: goto L_0x0091;
            case 9: goto L_0x0091;
            case 10: goto L_0x0032;
            case 11: goto L_0x0032;
            case 12: goto L_0x0032;
            case 13: goto L_0x0032;
            case 14: goto L_0x0032;
            case 15: goto L_0x0142;
            case 16: goto L_0x00c5;
            case 17: goto L_0x0404;
            default: goto L_0x0032;
        };
    L_0x0032:
        r0 = r25;
        r2 = r0.mAdditionalInfo;
        if (r2 != 0) goto L_0x04c4;
    L_0x0038:
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
    L_0x0043:
        r2 = 0;
        r0 = r24;
        r0.mCurrntCmd = r2;
        goto L_0x0006;
    L_0x0049:
        r0 = r24;
        r2 = r0.isTerminalResponseForSEUPMENU;
        if (r2 == 0) goto L_0x006e;
    L_0x004f:
        r0 = r24;
        r2 = r0.isUsingBackUpCmd;
        if (r2 != 0) goto L_0x0060;
    L_0x0055:
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
    L_0x0060:
        r2 = 0;
        r0 = r24;
        r0.isUsingBackUpCmd = r2;
        r2 = 0;
        r0 = r24;
        r0.isTerminalResponseForSEUPMENU = r2;
        r24.handleSessionEnd();
        goto L_0x0006;
    L_0x006e:
        r0 = r25;
        r2 = r0.mResCode;
        r4 = com.android.internal.telephony.cat.ResultCode.HELP_INFO_REQUIRED;
        if (r2 != r4) goto L_0x0084;
    L_0x0076:
        r18 = 1;
    L_0x0078:
        r0 = r25;
        r2 = r0.mUsersMenuSelection;
        r0 = r24;
        r1 = r18;
        r0.sendMenuSelection(r2, r1);
        goto L_0x0006;
    L_0x0084:
        r18 = 0;
        goto L_0x0078;
    L_0x0087:
        r7 = new com.android.internal.telephony.cat.SelectItemResponseData;
        r0 = r25;
        r2 = r0.mUsersMenuSelection;
        r7.<init>(r2);
        goto L_0x0032;
    L_0x0091:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        if (r2 == 0) goto L_0x0032;
    L_0x0097:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r19 = r2.geInput();
        r0 = r19;
        r2 = r0.yesNo;
        if (r2 != 0) goto L_0x00ba;
    L_0x00a5:
        if (r18 != 0) goto L_0x0032;
    L_0x00a7:
        r7 = new com.android.internal.telephony.cat.GetInkeyInputResponseData;
        r0 = r25;
        r2 = r0.mUsersInput;
        r0 = r19;
        r4 = r0.ucs2;
        r0 = r19;
        r5 = r0.packed;
        r7.<init>(r2, r4, r5);
        goto L_0x0032;
    L_0x00ba:
        r7 = new com.android.internal.telephony.cat.GetInkeyInputResponseData;
        r0 = r25;
        r2 = r0.mUsersYesNoSelection;
        r7.<init>(r2);
        goto L_0x0032;
    L_0x00c5:
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 == 0) goto L_0x00f5;
    L_0x00d7:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 == r4) goto L_0x00f5;
    L_0x00e2:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        r2 = 0;
        r0 = r24;
        r0.mCurrntCmd = r2;
        goto L_0x0006;
    L_0x00f5:
        r0 = r25;
        r2 = r0.mUsersConfirm;
        if (r2 != 0) goto L_0x0135;
    L_0x00fb:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "resMsg.mResCode = ";
        r2 = r2.append(r4);
        r0 = r25;
        r4 = r0.mResCode;
        r2 = r2.append(r4);
        r4 = " Openchannel : Sending TR :user did not accept";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = com.android.internal.telephony.cat.ResultCode.USER_NOT_ACCEPT;
        r0 = r25;
        r0.mResCode = r2;
        r7 = new com.android.internal.telephony.cat.OpenChannelResponseData;
        r7.<init>();
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x0135:
        r0 = r24;
        r4 = r0.mCatBIPMgr;
        r2 = mBIPCurrntCmd;
        r2 = (com.android.internal.telephony.cat.OpenChannelParams) r2;
        r4.handleOpenChannel(r2);
        goto L_0x0006;
    L_0x0142:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.getCallSettings();
        r2 = r2.address;
        if (r2 != 0) goto L_0x0168;
    L_0x014e:
        r2 = "setup call address is null";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = com.android.internal.telephony.cat.ResultCode.REQUIRED_VALUES_MISSING;
        r0 = r25;
        r0.mResCode = r2;
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x0168:
        r0 = r25;
        r2 = r0.mUsersConfirm;
        if (r2 != 0) goto L_0x01ac;
    L_0x016e:
        r2 = com.android.internal.telephony.cat.ResultCode.USER_NOT_ACCEPT;
        r0 = r25;
        r0.mResCode = r2;
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 == 0) goto L_0x019f;
    L_0x0186:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 == r4) goto L_0x019f;
    L_0x0191:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        goto L_0x0006;
    L_0x019f:
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x01ac:
        r2 = "setup call in handleCmdResponse";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = r24.isInCall();
        if (r2 != 0) goto L_0x0275;
    L_0x01b9:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 != r4) goto L_0x0244;
    L_0x01c4:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.getCallSettings();
        r2 = r2.address;
        r0 = r24;
        r0.SetupCallFromStk(r2);
        r2 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x022a }
        r2.<init>();	 Catch:{ Exception -> 0x022a }
        r4 = "getState() ";
        r2 = r2.append(r4);	 Catch:{ Exception -> 0x022a }
        r0 = r24;
        r4 = r0.mPhone;	 Catch:{ Exception -> 0x022a }
        r4 = r4.getServiceState();	 Catch:{ Exception -> 0x022a }
        r4 = r4.getState();	 Catch:{ Exception -> 0x022a }
        r2 = r2.append(r4);	 Catch:{ Exception -> 0x022a }
        r2 = r2.toString();	 Catch:{ Exception -> 0x022a }
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);	 Catch:{ Exception -> 0x022a }
        r0 = r24;
        r2 = r0.mPhone;	 Catch:{ Exception -> 0x022a }
        r2 = r2.getServiceState();	 Catch:{ Exception -> 0x022a }
        r2 = r2.getState();	 Catch:{ Exception -> 0x022a }
        r4 = 1;
        if (r2 != r4) goto L_0x0006;
    L_0x0206:
        r0 = r24;
        r2 = r0.mCurrntCmd;	 Catch:{ Exception -> 0x022a }
        r9 = r2.mCmdDet;	 Catch:{ Exception -> 0x022a }
        r10 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;	 Catch:{ Exception -> 0x022a }
        r11 = 0;
        r12 = 0;
        r13 = 0;
        r8 = r24;
        r8.sendTerminalResponse(r9, r10, r11, r12, r13);	 Catch:{ Exception -> 0x022a }
        r20 = new android.content.Intent;	 Catch:{ Exception -> 0x022a }
        r2 = "android.intent.action.SetupCallFail";
        r0 = r20;
        r0.<init>(r2);	 Catch:{ Exception -> 0x022a }
        r0 = r24;
        r2 = r0.mContext;	 Catch:{ Exception -> 0x022a }
        r0 = r20;
        r2.sendBroadcast(r0);	 Catch:{ Exception -> 0x022a }
        goto L_0x0006;
    L_0x022a:
        r16 = move-exception;
        r2 = "exception happened";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r9 = r2.mCmdDet;
        r10 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        r11 = 0;
        r12 = 0;
        r13 = 0;
        r8 = r24;
        r8.sendTerminalResponse(r9, r10, r11, r12, r13);
        goto L_0x0006;
    L_0x0244:
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 != 0) goto L_0x0267;
    L_0x0256:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.getCallSettings();
        r2 = r2.address;
        r0 = r24;
        r0.SetupCallFromStk(r2);
        goto L_0x0006;
    L_0x0267:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        goto L_0x0006;
    L_0x0275:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.mCmdDet;
        r2 = r2.commandQualifier;
        r2 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.fromInt(r2);
        r4 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.SET_UP_CALL_BUT_ONLY_IF_NOT_CURRENTLY_BUSY_ON_ANOTHER_CALL;
        if (r2 != r4) goto L_0x02ba;
    L_0x0285:
        r2 = "show Notification - Can't call by Incall";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r0 = r24;
        r2 = r0.mContext;
        r4 = 17041428; // 0x1040814 float:2.4250367E-38 double:8.419584E-317;
        r21 = r2.getText(r4);
        r15 = r21.toString();
        r0 = r24;
        r2 = r0.mContext;
        r4 = 1;
        r2 = android.widget.Toast.makeText(r2, r15, r4);
        r2.show();
        r2 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        r0 = r25;
        r0.mResCode = r2;
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 1;
        r6 = 2;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x02ba:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.mCmdDet;
        r2 = r2.commandQualifier;
        r2 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.fromInt(r2);
        r4 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.SET_UP_CALL_PUTTING_ALL_OTHER_CALLS_ON_HOLD;
        if (r2 != r4) goto L_0x0321;
    L_0x02ca:
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 == 0) goto L_0x02f5;
    L_0x02dc:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 == r4) goto L_0x02f5;
    L_0x02e7:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        goto L_0x0006;
    L_0x02f5:
        r0 = r24;
        r2 = r0.mPhone;	 Catch:{ CallStateException -> 0x0306 }
        r2.switchHoldingAndActive();	 Catch:{ CallStateException -> 0x0306 }
        r2 = 3;
        r4 = 5000; // 0x1388 float:7.006E-42 double:2.4703E-320;
        r0 = r24;
        r0.startTimeOut(r2, r4);	 Catch:{ CallStateException -> 0x0306 }
        goto L_0x0006;
    L_0x0306:
        r16 = move-exception;
        r2 = "fail to setup call";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        r0 = r25;
        r0.mResCode = r2;
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x0321:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.mCmdDet;
        r2 = r2.commandQualifier;
        r2 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.fromInt(r2);
        r4 = com.android.internal.telephony.cat.SetupCallCommandQualifiers.SET_UP_CALL_DISCONNECTING_ALL_OTHER_CALLS;
        if (r2 != r4) goto L_0x03c8;
    L_0x0331:
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 == 0) goto L_0x035c;
    L_0x0343:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 == r4) goto L_0x035c;
    L_0x034e:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        goto L_0x0006;
    L_0x035c:
        r0 = r24;
        r2 = r0.mPhone;	 Catch:{ CallStateException -> 0x0392 }
        r22 = r2.getRingingCall();	 Catch:{ CallStateException -> 0x0392 }
        r0 = r24;
        r2 = r0.mPhone;	 Catch:{ CallStateException -> 0x0392 }
        r17 = r2.getForegroundCall();	 Catch:{ CallStateException -> 0x0392 }
        r0 = r24;
        r2 = r0.mPhone;	 Catch:{ CallStateException -> 0x0392 }
        r14 = r2.getBackgroundCall();	 Catch:{ CallStateException -> 0x0392 }
        r2 = r22.isIdle();	 Catch:{ CallStateException -> 0x0392 }
        if (r2 != 0) goto L_0x03ad;
    L_0x037a:
        r22.hangup();	 Catch:{ CallStateException -> 0x0392 }
    L_0x037d:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;	 Catch:{ CallStateException -> 0x0392 }
        r4 = getPackageType(r4);	 Catch:{ CallStateException -> 0x0392 }
        if (r2 != r4) goto L_0x03c1;
    L_0x0388:
        r2 = 7;
        r4 = 2000; // 0x7d0 float:2.803E-42 double:9.88E-321;
        r0 = r24;
        r0.startTimeOut(r2, r4);	 Catch:{ CallStateException -> 0x0392 }
        goto L_0x0006;
    L_0x0392:
        r16 = move-exception;
        r2 = "fail to setup call";
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        r0 = r25;
        r0.mResCode = r2;
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x03ad:
        r2 = r17.isIdle();	 Catch:{ CallStateException -> 0x0392 }
        if (r2 != 0) goto L_0x03b7;
    L_0x03b3:
        r17.hangup();	 Catch:{ CallStateException -> 0x0392 }
        goto L_0x037d;
    L_0x03b7:
        r2 = r14.isIdle();	 Catch:{ CallStateException -> 0x0392 }
        if (r2 != 0) goto L_0x037d;
    L_0x03bd:
        r14.hangup();	 Catch:{ CallStateException -> 0x0392 }
        goto L_0x037d;
    L_0x03c1:
        r2 = 1;
        r0 = r24;
        r0.mSetupCallDisc = r2;	 Catch:{ CallStateException -> 0x0392 }
        goto L_0x0006;
    L_0x03c8:
        r2 = "true";
        r4 = "ro.ril.stk_qmi_ril";
        r5 = "true";
        r0 = r24;
        r4 = r0.getSystemProperty(r4, r5);
        r2 = r2.equals(r4);
        if (r2 == 0) goto L_0x03f3;
    L_0x03da:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 == r4) goto L_0x03f3;
    L_0x03e5:
        r0 = r24;
        r2 = r0.mCmdIf;
        r0 = r25;
        r4 = r0.mUsersConfirm;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        goto L_0x0006;
    L_0x03f3:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r2 = r2.getCallSettings();
        r2 = r2.address;
        r0 = r24;
        r0.SetupCallFromStk(r2);
        goto L_0x0006;
    L_0x0404:
        r0 = r25;
        r2 = r0.mUsersConfirm;
        if (r2 != 0) goto L_0x043f;
    L_0x040a:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "resMsg.mResCode = ";
        r2 = r2.append(r4);
        r0 = r25;
        r4 = r0.mResCode;
        r2 = r2.append(r4);
        r4 = " CLOSE_CHANNEL : Sending TR :user did not accept";
        r2 = r2.append(r4);
        r2 = r2.toString();
        r0 = r24;
        com.android.internal.telephony.cat.CatLog.m0d(r0, r2);
        r2 = com.android.internal.telephony.cat.ResultCode.USER_NOT_ACCEPT;
        r0 = r25;
        r0.mResCode = r2;
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 0;
        r6 = 0;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0006;
    L_0x043f:
        r0 = r24;
        r4 = r0.mCatBIPMgr;
        r2 = mBIPCurrntCmd;
        r2 = (com.android.internal.telephony.cat.CloseChannelParams) r2;
        r4.handleCloseChannel(r2);
        goto L_0x0006;
    L_0x044c:
        r2 = 2;
        r0 = r24;
        r4 = r0.mSlotId;
        r4 = getPackageType(r4);
        if (r2 != r4) goto L_0x046c;
    L_0x0457:
        r0 = r24;
        r2 = r0.mTimeoutDest;
        r4 = 4;
        if (r2 != r4) goto L_0x046c;
    L_0x045e:
        r24.cancelTimeOut();
        r2 = 0;
        r0 = r24;
        r0.mSendTerminalResponseExpectedByCallSetup = r2;
        r2 = com.android.internal.telephony.cat.ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS;
        r0 = r25;
        r0.mResCode = r2;
    L_0x046c:
        r2 = com.android.internal.telephony.cat.AppInterface.CommandType.SET_UP_CALL;
        r0 = r23;
        if (r0 == r2) goto L_0x0478;
    L_0x0472:
        r2 = com.android.internal.telephony.cat.AppInterface.CommandType.OPEN_CHANNEL;
        r0 = r23;
        if (r0 != r2) goto L_0x0488;
    L_0x0478:
        r0 = r24;
        r2 = r0.mCmdIf;
        r4 = 0;
        r5 = 0;
        r2.handleCallSetupRequestFromSim(r4, r5);
        r2 = 0;
        r0 = r24;
        r0.mCurrntCmd = r2;
        goto L_0x0006;
    L_0x0488:
        r7 = 0;
        goto L_0x0032;
    L_0x048b:
        r2 = com.android.internal.telephony.cat.CatService.C00532.f7xca33cf42;
        r4 = r3.typeOfCommand;
        r4 = com.android.internal.telephony.cat.AppInterface.CommandType.fromInt(r4);
        r4 = r4.ordinal();
        r2 = r2[r4];
        switch(r2) {
            case 8: goto L_0x049f;
            case 9: goto L_0x049f;
            default: goto L_0x049c;
        };
    L_0x049c:
        r7 = 0;
        goto L_0x0032;
    L_0x049f:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        if (r2 == 0) goto L_0x0032;
    L_0x04a5:
        r0 = r24;
        r2 = r0.mCurrntCmd;
        r19 = r2.geInput();
        r0 = r19;
        r2 = r0.duration;
        if (r2 == 0) goto L_0x04be;
    L_0x04b3:
        r7 = new com.android.internal.telephony.cat.GetInkeyInputResponseData;
        r0 = r19;
        r2 = r0.duration;
        r7.<init>(r2);
        goto L_0x0032;
    L_0x04be:
        r7 = 0;
        goto L_0x0032;
    L_0x04c1:
        r7 = 0;
        goto L_0x0032;
    L_0x04c4:
        r0 = r25;
        r2 = r0.mAdditionalInfo;
        r4 = 1;
        if (r2 != r4) goto L_0x0043;
    L_0x04cb:
        r0 = r25;
        r4 = r0.mResCode;
        r5 = 1;
        r0 = r25;
        r6 = r0.mAdditionalInfoData;
        r2 = r24;
        r2.sendTerminalResponse(r3, r4, r5, r6, r7);
        goto L_0x0043;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.CatService.handleCmdResponse(com.android.internal.telephony.cat.CatResponseMessage):void");
    }

    private boolean isStkAppInstalled() {
        Intent intent;
        if (2 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.UTK_CMD_ACTION);
        } else if (1 == getPackageType(this.mSlotId)) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION2);
        } else {
            intent = new Intent(AppInterface.CAT_CMD_ACTION);
        }
        List<ResolveInfo> broadcastReceivers = this.mContext.getPackageManager().queryBroadcastReceivers(intent, 128);
        if ((broadcastReceivers == null ? 0 : broadcastReceivers.size()) > 0) {
            return true;
        }
        return false;
    }

    public void update(CommandsInterface ci, Context context, UiccCard ic) {
        UiccCardApplication ca = null;
        IccRecords ir = null;
        if (ic != null) {
            ca = ic.getApplicationIndex(0);
            if (ca != null) {
                ir = ca.getIccRecords();
            }
        }
        synchronized (sInstanceLock) {
            if (ir != null) {
                if (mIccRecords[this.mSlotId] != ir) {
                    if (mIccRecords[this.mSlotId] != null) {
                        mIccRecords[this.mSlotId].unregisterForRecordsLoaded(this);
                    }
                    if (mUiccApplication[this.mSlotId] != null) {
                        CatLog.m0d((Object) this, "unregisterForReady slotid: " + this.mSlotId + "instance : " + this);
                        mUiccApplication[this.mSlotId].unregisterForReady(this);
                    }
                    CatLog.m0d((Object) this, "Reinitialize the Service with SIMRecords and UiccCardApplication");
                    mIccRecords[this.mSlotId] = ir;
                    mUiccApplication[this.mSlotId] = ca;
                    mIccRecords[this.mSlotId].registerForRecordsLoaded(this, 20, null);
                    mUiccApplication[this.mSlotId].registerForReady(this, 7, null);
                    CatLog.m0d((Object) this, "registerForReady slotid: " + this.mSlotId + "instance : " + this);
                }
            }
        }
    }

    void updateIccAvailability() {
        if (this.mUiccController != null) {
            CardState newState = CardState.CARDSTATE_ABSENT;
            UiccCard newCard = this.mUiccController.getUiccCard(this.mSlotId);
            if (newCard != null) {
                newState = newCard.getCardState();
            }
            CardState oldState = this.mCardState;
            this.mCardState = newState;
            CatLog.m0d((Object) this, "New Card State = " + newState + " " + "Old Card State = " + oldState);
            if (oldState == CardState.CARDSTATE_PRESENT && newState != CardState.CARDSTATE_PRESENT) {
                broadcastCardStateAndIccRefreshResp(newState, null);
            } else if (oldState != CardState.CARDSTATE_PRESENT && newState == CardState.CARDSTATE_PRESENT) {
                this.mCmdIf.reportStkServiceIsRunning(null);
            } else if (this.stkRefreshReset && newState == CardState.CARDSTATE_PRESENT) {
                this.mCmdIf.reportStkServiceIsRunning(null);
                this.stkRefreshReset = false;
            }
        }
    }

    private boolean isDisabledCmd(String cmd) {
        if (this.disabledCmdList.length <= 0) {
            return false;
        }
        for (Object equals : this.disabledCmdList) {
            if (cmd.equals(equals)) {
                return true;
            }
        }
        return false;
    }

    public boolean registerPhone(PhoneBase phone) {
        CatLog.m0d((Object) this, "CatService phone reloaded!");
        if (2 == getPackageType(this.mSlotId)) {
            this.mPhone = phone;
            this.mCatBIPMgr.registerPhone(this.mPhone);
        } else {
            this.mPhone.unregisterForDisconnect(this);
            this.mPhone = phone;
            this.mPhone.registerForDisconnect(this, MSG_ID_PHONE_DISCONNECT, null);
            this.mCatBIPMgr.registerPhone(this.mPhone);
        }
        return true;
    }

    private void cancelTimeOut() {
        removeMessages(102);
        this.mTimeoutDest = 0;
    }

    void startTimeOut(int timeDest, int duration) {
        cancelTimeOut();
        this.mTimeoutDest = timeDest;
        sendMessageDelayed(obtainMessage(102), (long) duration);
    }

    public synchronized void onEventDownload(CatEnvelopeMessage eventMsg) {
        CatLog.m0d((Object) this, "onEventDownload()");
        if (eventMsg != null) {
            obtainMessage(106, eventMsg).sendToTarget();
        }
    }

    public synchronized void sendEnvelopeToTriggerBip() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(214);
        buf.write(0);
        buf.write(25);
        buf.write(1);
        buf.write(47);
        buf.write(130);
        buf.write(2);
        buf.write(130);
        buf.write(129);
        buf.write(5);
        byte[] rawData = buf.toByteArray();
        rawData[1] = (byte) (rawData.length - 2);
        String hexString = IccUtils.bytesToHexString(rawData);
        CatLog.m0d((Object) this, "sendEnvelopeToTriggerBip cmd: " + hexString);
        this.mCmdIf.sendEnvelope(hexString, null);
    }

    public synchronized void sendEnvelopeToTriggerBipforOTA(boolean unreg) {
        byte[] sms_tpdu = new byte[]{(byte) -28, (byte) 10, (byte) -104, (byte) 51, (byte) 17, (byte) 17, (byte) 17, (byte) 17, Byte.MAX_VALUE, (byte) 22, (byte) 12, (byte) 1, (byte) 9, (byte) 21, (byte) 87, (byte) 50, (byte) 54, (byte) 20, (byte) 2, (byte) 112, (byte) 0, (byte) 0, (byte) 15, (byte) 13, (byte) 0, (byte) 1, (byte) 32, (byte) 32, (byte) -80, (byte) 0, (byte) 6, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(BerTlv.BER_SMS_PP_DATA_DOWNLOAD_TAG);
        buf.write(52);
        buf.write(2);
        buf.write(2);
        buf.write(131);
        buf.write(129);
        buf.write(6);
        buf.write(6);
        buf.write(152);
        buf.write(51);
        buf.write(17);
        buf.write(17);
        buf.write(17);
        buf.write(17);
        buf.write(11);
        buf.write(38);
        buf.write(sms_tpdu, 0, 37);
        if (unreg) {
            buf.write(2);
        } else {
            buf.write(5);
        }
        String hexString = IccUtils.bytesToHexString(buf.toByteArray());
        CatLog.m0d((Object) this, "sendEnvelopeToTriggerBipforOTA cmd: " + hexString);
        this.mCmdIf.sendEnvelope(hexString, null);
    }

    private void handleProactiveCommandSendSS(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "ssString is " + ((SendSSParams) cmdParams).ssString);
        try {
            if (2 != getPackageType(this.mSlotId)) {
                if ("ORO".equals(SystemProperties.get("ro.csc.sales_code")) || "XFA".equals(SystemProperties.get("ro.csc.sales_code")) || "XFM".equals(SystemProperties.get("ro.csc.sales_code"))) {
                    this.mPhone.setmMmiInitBySTK(false);
                } else {
                    this.mPhone.setmMmiInitBySTK(true);
                }
            }
            if ("false".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                this.mCmdIf.setSimInitEvent(null);
                this.mPhone.dial(((SendSSParams) cmdParams).ssString, 0);
                startTimeOut(1, WAITING_RELEASE_COMPLETE_TIME);
            }
        } catch (CallStateException e) {
            CatLog.m0d((Object) this, "fail to send SS");
            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
        }
    }

    private void handleProactiveCommandSendUSSD(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "ussdString is " + IccUtils.bytesToHexString(((SendUSSDParams) cmdParams).ussdString));
        int dcsCode = ((SendUSSDParams) cmdParams).dcsCode;
        if ("45205".equals(SystemProperties.get("gsm.sim.operator.numeric")) && dcsCode + 16 == 0) {
            CatLog.m0d((Object) this, "change DCS F0 to 0F in STK Module");
            dcsCode = 15;
        }
        int ussdLength = ((SendUSSDParams) cmdParams).ussdLength;
        byte[] ussdString = ((SendUSSDParams) cmdParams).ussdString;
        CatLog.m0d((Object) this, "dcsCode : " + dcsCode + ", length : " + ussdLength);
        if (2 != getPackageType(this.mSlotId)) {
            if ("ORO".equals(SystemProperties.get("ro.csc.sales_code")) || "XFA".equals(SystemProperties.get("ro.csc.sales_code")) || "XFM".equals(SystemProperties.get("ro.csc.sales_code")) || "XFC".equals(SystemProperties.get("ro.csc.sales_code")) || "XFE".equals(SystemProperties.get("ro.csc.sales_code")) || "XFV".equals(SystemProperties.get("ro.csc.sales_code")) || "INU".equals(SystemProperties.get("ro.csc.sales_code")) || "INS".equals(SystemProperties.get("ro.csc.sales_code")) || "NPL".equals(SystemProperties.get("ro.csc.sales_code")) || "SLK".equals(SystemProperties.get("ro.csc.sales_code")) || "ETR".equals(SystemProperties.get("ro.csc.sales_code")) || "TML".equals(SystemProperties.get("ro.csc.sales_code")) || "XEC".equals(SystemProperties.get("ro.csc.sales_code")) || "XSE".equals(SystemProperties.get("ro.csc.sales_code")) || "AFR".equals(SystemProperties.get("ro.csc.sales_code")) || "KEN".equals(SystemProperties.get("ro.csc.sales_code")) || CscFeature.getInstance().getEnableStatus("CscFeature_RIL_DisplayStkUssdDialog")) {
                this.mPhone.setmMmiInitBySTK(false);
            } else {
                this.mPhone.setmMmiInitBySTK(true);
            }
        }
        if ("false".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
            this.mCmdIf.setSimInitEvent(null);
            this.mPhone.sendEncodedUssd(ussdString, ussdLength, dcsCode);
            startTimeOut(1, WAITING_RELEASE_COMPLETE_TIME);
        }
    }

    private void handleProactiveCommandSendDTMF(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "DTMF String is " + IccUtils.bytesToHexString(((SendDTMFParams) cmdParams).dtmfString));
        int rawDataLength = ((SendDTMFParams) cmdParams).dtmfString[0];
        byte[] tempDTMFString = new byte[(rawDataLength * 2)];
        int tempDTMFStringLength = 0;
        int i = 0;
        int workingPtr = 0;
        while (i < rawDataLength) {
            byte temp = (byte) (((SendDTMFParams) cmdParams).dtmfString[i + 1] & 15);
            if (temp == (byte) 12) {
                temp = (byte) 112;
            } else if (temp == (byte) 10) {
                temp = (byte) 42;
            } else if (temp == (byte) 11) {
                temp = (byte) 35;
            } else {
                temp = (byte) (temp + 48);
            }
            int workingPtr2 = workingPtr + 1;
            tempDTMFString[workingPtr] = temp;
            tempDTMFStringLength++;
            temp = (byte) ((((SendDTMFParams) cmdParams).dtmfString[i + 1] >> 4) & 15);
            if (temp != (byte) 15) {
                if (temp == (byte) 12) {
                    temp = (byte) 112;
                } else if (temp == (byte) 10) {
                    temp = (byte) 42;
                } else if (temp == (byte) 11) {
                    temp = (byte) 35;
                } else {
                    temp = (byte) (temp + 48);
                }
                workingPtr = workingPtr2 + 1;
                tempDTMFString[workingPtr2] = temp;
                tempDTMFStringLength++;
                workingPtr2 = workingPtr;
            }
            i++;
            workingPtr = workingPtr2;
        }
        CatLog.m0d((Object) this, "wakelock for DTMF");
        this.mWakeLock.acquire(65000);
        this.mDtmfString = new DtmfString(tempDTMFStringLength, tempDTMFString);
        processDTMFString();
    }

    void processDTMFString() {
        CatLog.m0d((Object) this, "dtmfStringLength : " + this.mDtmfString.dtmfStringLength + "    DTMFString : <" + this.mDtmfString.dtfmString + ">");
        while (this.mDtmfString.pointer < this.mDtmfString.dtmfStringLength) {
            if (this.mDtmfString.pointer == this.mDtmfString.dtmfStringLength - 1) {
                sendDtmfLastRequest(this.mDtmfString.dtfmString.charAt(this.mDtmfString.pointer));
                break;
            } else if (this.mDtmfString.dtfmString.charAt(this.mDtmfString.pointer) == 'p') {
                int countP = 0 + 1;
                while (this.mDtmfString.pointer + countP < this.mDtmfString.dtmfStringLength && this.mDtmfString.dtfmString.charAt(this.mDtmfString.pointer + countP) == 'p') {
                    countP++;
                }
                CatLog.m0d((Object) this, "delay time = " + (countP * CallFailCause.CAUSE_OFFSET));
                sendMessageDelayed(obtainMessage(MSG_ID_SEND_DTMF_PAUSE), (long) (countP * CallFailCause.CAUSE_OFFSET));
                r1 = this.mDtmfString;
                r1.pointer += countP;
                return;
            } else {
                sendDtmfRequest(this.mDtmfString.dtfmString.charAt(this.mDtmfString.pointer));
                r1 = this.mDtmfString;
                r1.pointer++;
            }
        }
        startTimeOut(5, this.mDtmfString.dtmfStringLength * WAITING_SEND_DTMF_TIME);
    }

    void sendDtmfRequest(char c) {
        CatLog.m0d((Object) this, "sendDtmfRequest (" + c + ")");
        this.mCmdIf.sendDtmf(c, null);
    }

    void sendDtmfLastRequest(char c) {
        CatLog.m0d((Object) this, "sendDtmfLastRequest (" + c + ")");
        this.mCmdIf.sendDtmf(c, obtainMessage(103));
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleProactiveCommandSendSMS(com.android.internal.telephony.cat.CommandParams r8) {
        /*
        r7 = this;
        r6 = 2;
        r4 = 0;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "The Smscaddress is: ";
        r1 = r0.append(r1);
        r0 = r8;
        r0 = (com.android.internal.telephony.cat.SendSMSParams) r0;
        r0 = r0.SmscAddress;
        r0 = r1.append(r0);
        r0 = r0.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r0);
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "The Sms Pdu is: ";
        r1 = r0.append(r1);
        r0 = r8;
        r0 = (com.android.internal.telephony.cat.SendSMSParams) r0;
        r0 = r0.Pdu;
        r0 = r1.append(r0);
        r0 = r0.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r0);
        r0 = r7.mPhone;
        r0 = r0 instanceof com.android.internal.telephony.gsm.GSMPhone;
        if (r0 != 0) goto L_0x0061;
    L_0x003e:
        r0 = r7.mSlotId;
        r0 = getPackageType(r0);
        if (r6 == r0) goto L_0x0061;
    L_0x0046:
        r0 = "VZW-CDMA";
        r1 = "";
        r0 = r0.equals(r1);
        if (r0 == 0) goto L_0x0050;
    L_0x0050:
        r0 = "false";
        r1 = "ro.ril.stk_qmi_ril";
        r2 = "true";
        r1 = r7.getSystemProperty(r1, r2);
        r0 = r0.equals(r1);
        if (r0 != 0) goto L_0x0079;
    L_0x0060:
        return;
    L_0x0061:
        r0 = "NEW handleProactiveCommandSendSMS set";
        com.android.internal.telephony.cat.CatLog.m0d(r7, r0);
        r1 = mIccSms;
        r2 = r7.mSlotId;
        r0 = r7.mSlotId;
        r0 = com.android.internal.telephony.PhoneFactory.getPhone(r0);
        r0 = (com.android.internal.telephony.PhoneProxy) r0;
        r0 = r0.getIccSmsInterfaceManager();
        r1[r2] = r0;
        goto L_0x0050;
    L_0x0079:
        r0 = r7.mCmdIf;
        r0.setSimInitEvent(r4);
        r0 = mIccSms;
        r1 = r7.mSlotId;
        r0 = r0[r1];
        r1 = r8;
        r1 = (com.android.internal.telephony.cat.SendSMSParams) r1;
        r1 = r1.SmscAddress;
        r1 = com.android.internal.telephony.uicc.IccUtils.hexStringToBytes(r1);
        r2 = r8;
        r2 = (com.android.internal.telephony.cat.SendSMSParams) r2;
        r2 = r2.Pdu;
        r2 = com.android.internal.telephony.uicc.IccUtils.hexStringToBytes(r2);
        r8 = (com.android.internal.telephony.cat.SendSMSParams) r8;
        r3 = r8.Format;
        r5 = r4;
        r0.sendRawPduSat(r1, r2, r3, r4, r5);
        r0 = 60000; // 0xea60 float:8.4078E-41 double:2.9644E-319;
        r7.startTimeOut(r6, r0);
        goto L_0x0060;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.CatService.handleProactiveCommandSendSMS(com.android.internal.telephony.cat.CommandParams):void");
    }

    private void handleProactiveCommandProvideLocalInfo(CommandParams cmdParams) {
        int commandQualifier = cmdParams.mCmdDet.commandQualifier;
        CatLog.m0d((Object) this, "Provide local info command Qualifier : " + commandQualifier);
        switch (commandQualifier) {
            case 0:
            case 1:
            case 2:
            case 5:
                if ("true".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                } else {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                    return;
                }
            case 3:
                Calendar calendar = Calendar.getInstance();
                if (calendar == null) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                    return;
                }
                byte timezone;
                int i;
                String tz = SystemProperties.get("persist.sys.timezone", "");
                if (2 == getPackageType(this.mSlotId)) {
                    boolean daylight = calendar.getTimeZone().inDaylightTime(new Date());
                    timezone = (byte) ((((((daylight ? calendar.getTimeZone().getDSTSavings() : 0) + calendar.getTimeZone().getRawOffset()) / 1000) / 60) / 60) * 4);
                } else if (TextUtils.isEmpty(tz)) {
                    timezone = (byte) -1;
                } else {
                    TimeZone zone = TimeZone.getTimeZone(tz);
                    timezone = getTZOffSetByte((long) (zone.getRawOffset() + zone.getDSTSavings()));
                }
                CatLog.m0d((Object) this, "y : " + calendar.get(1) + " m : " + calendar.get(2) + " d : " + calendar.get(5) + " hh : " + calendar.get(10) + " mm : " + calendar.get(12) + " ss : " + calendar.get(13) + " zone : " + calendar.getTimeZone().getRawOffset() + " AM_PM : " + calendar.get(9));
                int i2 = calendar.get(1);
                int i3 = calendar.get(2);
                int i4 = calendar.get(5);
                if (calendar.get(9) == 0) {
                    i = calendar.get(10);
                } else {
                    i = calendar.get(10) + 12;
                }
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, new ProvideLocalInfoTimeResponseData(i2, i3, i4, i, calendar.get(12), calendar.get(13), timezone));
                return;
            case 4:
                Locale loc = Locale.getDefault();
                if (loc == null) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                    return;
                } else {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, new ProvideLocalInfoLangSetting(loc.getLanguage()));
                    return;
                }
            default:
                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                return;
        }
    }

    private void displayOpenChannelParams(CommandParams cmdParams) {
        BearerDescription bearerDesc = ((OpenChannelParams) cmdParams).mBearerDesc;
        TransportLevel transportLevel = ((OpenChannelParams) cmdParams).mTransportLevel;
        if (bearerDesc != null) {
            CatLog.m0d((Object) this, "The BearerDescription is: ");
            CatLog.m0d((Object) this, "The Bearer Type is:" + ((OpenChannelParams) cmdParams).mBearerDesc.bearerType);
            if (((OpenChannelParams) cmdParams).mBearerDesc.bearerDefault) {
                CatLog.m0d((Object) this, "The Default Network Access Name is used for BEARER_DEFAULT");
            } else {
                CatLog.m0d((Object) this, "The Buffer Size is: " + ((OpenChannelParams) cmdParams).mBufferSize);
                CatLog.m0d((Object) this, "The Network Access Name is: " + ((OpenChannelParams) cmdParams).mNetworkAccessName);
                CatLog.m0d((Object) this, "The Bearer Mode Parameters are :");
                CatLog.m0d((Object) this, "Is On Demand : " + ((OpenChannelParams) cmdParams).mBearerMode.isOnDemand);
                CatLog.m0d((Object) this, "Is Auto Reconnect: " + ((OpenChannelParams) cmdParams).mBearerMode.isAutoReconnect);
                CatLog.m0d((Object) this, "Is Background Mode: " + ((OpenChannelParams) cmdParams).mBearerMode.isBackgroundMode);
                CatLog.m0d((Object) this, "The User Name is: " + ((OpenChannelParams) cmdParams).mUsernameTextMessage.text);
                CatLog.m0d((Object) this, "The User Password is: " + ((OpenChannelParams) cmdParams).mPasswordTextMessage.text);
            }
        } else {
            CatLog.m0d((Object) this, "The BearerDescription is: null");
        }
        if (transportLevel != null) {
            CatLog.m0d((Object) this, "The Transport Level Protocol is: " + ((OpenChannelParams) cmdParams).mTransportLevel.transportProtocol);
            CatLog.m0d((Object) this, "The Port Number is: " + ((OpenChannelParams) cmdParams).mTransportLevel.portNumber);
            if (((OpenChannelParams) cmdParams).mDataDestinationAddress != null) {
                CatLog.m0d((Object) this, "The Data Destination Address Type is: " + ((OpenChannelParams) cmdParams).mDataDestinationAddress.addressType);
                CatLog.m0d((Object) this, "The Data Destination Address is: " + IccUtils.bytesToHexString(((OpenChannelParams) cmdParams).mDataDestinationAddress.address));
            } else {
                CatLog.m0d((Object) this, "The Data Destination Address is: null");
            }
        } else {
            CatLog.m0d((Object) this, "The Transport Level is: null");
        }
        CatLog.m0d((Object) this, "The Text Message is: " + ((OpenChannelParams) cmdParams).mTextMessage.text);
    }

    private void displayCloseChannelParams(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "The Channel ID is: " + ((CloseChannelParams) cmdParams).mChannelId);
        CatLog.m0d((Object) this, "The Close Channel Mode is: " + ((CloseChannelParams) cmdParams).mCloseChannelMode);
        CatLog.m0d((Object) this, "The Text Message is: " + ((CloseChannelParams) cmdParams).mTextMessage.text);
    }

    private void displayReceiveDataParams(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "The Channel ID is: " + (((ReceiveDataParams) cmdParams).mChannelId & 255));
        CatLog.m0d((Object) this, "The Channel Data Length is: " + (((ReceiveDataParams) cmdParams).mChannelDataLength & 255));
        CatLog.m0d((Object) this, "The Text Message is: " + ((ReceiveDataParams) cmdParams).mTextMessage.text);
    }

    private void displaySendDataParams(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "The Channel ID is: " + ((SendDataParams) cmdParams).mChannelId);
        CatLog.m0d((Object) this, "The Channel Data is: " + IccUtils.bytesToHexString(((SendDataParams) cmdParams).mChannelData));
        CatLog.m0d((Object) this, "The Send Immediate is: " + ((SendDataParams) cmdParams).mSendImmediate);
    }

    private void displayChannelStatusParams(CommandParams cmdParams) {
        CatLog.m0d((Object) this, "The Channel ID is: " + ((GetChannelDataParams) cmdParams).mChannelId);
    }

    private void handleCallControlResultNoti(String callcontrol_result) {
        boolean alphaidpresent = false;
        byte[] alpha_id = new byte[64];
        String callControlResult = null;
        byte[] rawData = IccUtils.hexStringToBytes(callcontrol_result);
        this.mCallType = rawData[0];
        this.mCallControlResultCode = rawData[1];
        int callControlResultCode = rawData[1];
        int call_type = rawData[0];
        CatLog.m0d((Object) this, "The call control result by SIM = " + this.mCallControlResultCode);
        try {
            if (rawData[2] == (byte) 1) {
                alphaidpresent = true;
                if (rawData[3] > (byte) 0) {
                    this.alpha_id_display = IccUtils.adnStringFieldToString(rawData, 4, rawData[3]);
                    CatLog.m0d((Object) this, "The call control result by SIM : alpha_id = " + this.alpha_id_display);
                }
            } else {
                alphaidpresent = false;
            }
            CatLog.m0d((Object) this, "The call control result by SIM : alphaidpresent = " + alphaidpresent);
        } catch (IndexOutOfBoundsException e) {
        }
        if (CallControlResult.fromInt(callControlResultCode) == CallControlResult.CALL_CONTROL_ALLOWED_WITH_MOD) {
            StringBuffer sb = new StringBuffer();
            byte length_of_mod;
            byte i;
            int i2;
            String voicecall_ss_modified_address;
            switch (C00532.$SwitchMap$com$android$internal$telephony$cat$CallType[CallType.fromInt(call_type).ordinal()]) {
                case 1:
                    try {
                        if (rawData[70] == (byte) 1) {
                            if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableNotiPopupWhenStkCallControl")) {
                                CatLog.m0d((Object) this, "Brazil Feature - Remove '+' from voicecall_ss_modified_address ");
                            } else {
                                sb.append("+");
                            }
                        }
                        length_of_mod = rawData[72];
                        for (i = (byte) 0; i < length_of_mod; i++) {
                            switch (rawData[i + 73]) {
                                case (byte) 0:
                                case (byte) 1:
                                case (byte) 2:
                                case (byte) 3:
                                case (byte) 4:
                                case (byte) 5:
                                case (byte) 6:
                                case (byte) 7:
                                case (byte) 8:
                                case (byte) 9:
                                    i2 = i + 73;
                                    rawData[i2] = (byte) (rawData[i2] + 48);
                                    sb.append(new String(rawData, i + 73, 1));
                                    break;
                                case (byte) 10:
                                    sb.append(CharacterSets.MIMENAME_ANY_CHARSET);
                                    break;
                                case (byte) 11:
                                    sb.append("#");
                                    break;
                                default:
                                    break;
                            }
                        }
                    } catch (IndexOutOfBoundsException e2) {
                    }
                    voicecall_ss_modified_address = sb.toString();
                    CatLog.m0d((Object) this, "The Modified number by SIM : " + voicecall_ss_modified_address);
                    callControlResult = this.mContext.getText(17041429).toString() + " : " + voicecall_ss_modified_address;
                    break;
                case 2:
                    callControlResult = this.mContext.getText(17041429).toString();
                    break;
                case 3:
                    length_of_mod = rawData[72];
                    for (i = (byte) 0; i < length_of_mod; i++) {
                        switch (rawData[i + 73]) {
                            case (byte) 0:
                            case (byte) 1:
                            case (byte) 2:
                            case (byte) 3:
                            case (byte) 4:
                            case (byte) 5:
                            case (byte) 6:
                            case (byte) 7:
                            case (byte) 8:
                            case (byte) 9:
                                i2 = i + 73;
                                rawData[i2] = (byte) (rawData[i2] + 48);
                                sb.append(new String(rawData, i + 73, 1));
                                break;
                            case (byte) 10:
                                sb.append(CharacterSets.MIMENAME_ANY_CHARSET);
                                break;
                            case (byte) 11:
                                sb.append("#");
                                break;
                            default:
                                break;
                        }
                    }
                    voicecall_ss_modified_address = sb.toString();
                    try {
                        this.mPhone.dial(voicecall_ss_modified_address, 0);
                        callControlResult = this.mContext.getText(17041430).toString() + " : " + voicecall_ss_modified_address;
                        break;
                    } catch (CallStateException e3) {
                        CatLog.m0d((Object) this, "fail to send SS");
                        cancelTimeOut();
                        sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                        return;
                    }
            }
            if (CallType.fromInt(call_type) == CallType.CALL_TYPE_MO_VOICE || CallType.fromInt(call_type) == CallType.CALL_TYPE_MO_SMS) {
                if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableNotiPopupWhenStkCallControl")) {
                    if (!(alphaidpresent && this.alpha_id_display == null)) {
                        if (alphaidpresent) {
                            Toast.makeText(this.mContext, this.alpha_id_display, 1).show();
                        } else {
                            Toast.makeText(this.mContext, callControlResult, 1).show();
                        }
                    }
                } else if (!("CLN".equals(SystemProperties.get("ro.csc.sales_code")) || "KDO".equals(SystemProperties.get("ro.csc.sales_code")) || "TLS".equals(SystemProperties.get("ro.csc.sales_code")) || !alphaidpresent || this.alpha_id_display == null)) {
                    Toast.makeText(this.mContext, this.alpha_id_display, 1).show();
                }
            }
        } else if (CallControlResult.fromInt(callControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
            callControlResult = this.mContext.getText(17041431).toString();
            if (!(alphaidpresent && this.alpha_id_display == null)) {
                if (alphaidpresent) {
                    Toast.makeText(this.mContext, this.alpha_id_display, 1).show();
                    this.alpha_id_display = null;
                } else {
                    Toast.makeText(this.mContext, callControlResult, 1).show();
                }
            }
        } else if (!(alphaidpresent && this.alpha_id_display == null) && alphaidpresent) {
            Toast.makeText(this.mContext, this.alpha_id_display, 1).show();
            this.alpha_id_display = null;
        }
        if (this.mSendTerminalResponseExpectedByCallSetup && CallType.fromInt(call_type) == CallType.CALL_TYPE_MO_VOICE && CallControlResult.fromInt(this.mCallControlResultCode) == CallControlResult.CALL_CONTROL_NOT_ALLOWED) {
            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.USIM_CALL_CONTROL_PERMANENT, true, 1, null);
            this.mSendTerminalResponseExpectedByCallSetup = false;
            cancelTimeOut();
        } else if (this.mSendTerminalResponseExpectedByCallSetup && CallType.fromInt(call_type) == CallType.CALL_TYPE_MO_VOICE) {
            if (this.mCurrntCmd != null) {
                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.OK, false, 0, null);
            } else {
                CatLog.m0d((Object) this, "mCurrntCmd = null error handle is needed");
            }
            this.mSendTerminalResponseExpectedByCallSetup = false;
            cancelTimeOut();
        }
    }

    public void sendSessionEndTerminalResponseForAirplaneMode() {
        if (this.mCurrntCmd != null && this.mCurrntCmd.mCmdDet.typeOfCommand != 37) {
            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.UICC_SESSION_TERM_BY_USER, false, 0, null);
        }
    }

    private boolean isInCall() {
        boolean callState = this.mPhone.getForegroundCall().getState().isAlive() || this.mPhone.getBackgroundCall().getState().isAlive() || this.mPhone.getRingingCall().getState().isAlive();
        CatLog.m0d((Object) this, "Is in call: " + callState);
        return callState;
    }

    private void SetupCallFromStk(String dialNum) {
        CatLog.m0d((Object) this, "SetupCallFromStk()");
        Intent intent;
        if (2 == getPackageType(this.mSlotId)) {
            intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", this.mCurrntCmd.getCallSettings().address, null));
            if (intent == null) {
                CatLog.m0d((Object) this, "fail to make call intent");
                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                return;
            }
            intent.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, this.mSlotId);
            intent.putExtra(SIMNUM, 1);
            intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", (Parcelable) ((TelecomManager) this.mContext.getSystemService("telecom")).getCallCapablePhoneAccounts().get(0));
            intent.setFlags(268435456);
            startTimeOut(4, 10000);
            this.mContext.sendBroadcast(new Intent("android.intent.action.SetupCallbyUTK"));
            CatLog.m0d((Object) this, "*************call intent 1");
            this.mContext.startActivity(intent);
        } else if (this.mPhone.getServiceState().getState() == 0) {
            this.mCmdIf.setSimInitEvent(null);
            intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", dialNum, null));
            if (intent == null) {
                CatLog.m0d((Object) this, "fail to make call intent from stk");
                sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                return;
            }
            intent.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, this.mSlotId);
            intent.setFlags(268435456);
            this.mContext.startActivity(intent);
        } else {
            Toast.makeText(this.mContext, this.mContext.getText(17040252).toString(), 1).show();
        }
        this.mSendTerminalResponseExpectedByCallSetup = true;
        startTimeOut(4, 10000);
    }

    private void createWakelock() {
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, "STKService");
    }

    public synchronized void sentTerminalResponseForSetupMenu(boolean value) {
        this.isTerminalResponseForSEUPMENU = value;
    }

    public synchronized void setEventListChannelStatus(boolean val) {
        this.mCatBIPMgr.monitorChannelStatusEvent = val;
    }

    public synchronized void setEventListDataAvailable(boolean val) {
        this.mCatBIPMgr.monitorDataDownloadEvent = val;
    }

    public boolean isAirplaneMode() {
        CatLog.m0d((Object) this, "mPhone.mCM.getRadioState = " + this.mPhone.mCi.getRadioState());
        return this.mPhone.mCi.getRadioState() == RadioState.RADIO_OFF;
    }

    private void launchIdleText() {
        CatLog.m0d((Object) this, "launchIdleText");
        TextMessage msg = this.mCurrntCmd.geTextMessage();
        int notificationId = STK_NOTIFICATION_ID;
        if (this.mSlotId == 1) {
            notificationId = STK2_NOTIFICATION_ID;
        }
        if (msg.text == null) {
            CatLog.m0d((Object) this, "REMOVE IDLE TEXT ");
            this.mNotificationManager.cancel(notificationId);
        } else if (msg.text.length() == 0) {
            CatLog.m0d((Object) this, "REMOVE IDLE TEXT  due to text length is 0");
            this.mNotificationManager.cancel(notificationId);
        } else {
            PendingIntent pendingIntent = PendingIntent.getService(this.mContext, 0, new Intent(this.mContext, CatService.class), 0);
            Builder notificationBuilder = new Builder(this.mContext);
            String title = SystemProperties.get(MultiSimManager.appendSimSlot("gsm.STK_SETUP_MENU", getPackageType(this.mSlotId)), "");
            if (title != null) {
                notificationBuilder.setContentTitle(title);
            } else {
                notificationBuilder.setContentTitle("");
            }
            notificationBuilder.setSmallIcon(17303704);
            notificationBuilder.setContentIntent(pendingIntent);
            notificationBuilder.setOngoing(true);
            if (!msg.iconSelfExplanatory) {
                notificationBuilder.setContentText(msg.text);
                notificationBuilder.setTicker(msg.text);
            }
            notificationBuilder.setColor(this.mContext.getResources().getColor(17170520));
            notificationBuilder.setVisibility(1);
            this.mNotificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private boolean isSetUpMenu(String data) {
        byte[] rawData = IccUtils.hexStringToBytes(data);
        int lengthOffset = 0;
        if (rawData[1] == (byte) -127) {
            lengthOffset = 1;
        }
        if ((rawData[lengthOffset + 5] & 255) == 37) {
            return true;
        }
        return false;
    }

    private void saveBackUpProactiveCmd(RilMessage rilMsg) {
        Context context = this.mContext;
        SharedPreferences pref = this.mContext.getSharedPreferences("backUpProactiveCmd", 0);
        if (pref == null) {
            CatLog.m0d((Object) this, "save back up SharedPreferences open error");
            return;
        }
        Editor editor = pref.edit();
        editor.clear();
        editor.putInt("id", rilMsg.mId);
        editor.putString("data", (String) rilMsg.mData);
        editor.commit();
    }

    private RilMessage loadBackUpProactiveCmd() {
        Context context = this.mContext;
        SharedPreferences pref = this.mContext.getSharedPreferences("backUpProactiveCmd", 0);
        if (pref != null) {
            return new RilMessage(pref.getInt("id", 0), pref.getString("data", ""));
        }
        CatLog.m0d((Object) this, "load back up SharedPreferences open error");
        return null;
    }

    public static boolean isBIPCmdBeingProcessed() {
        return mBIPCurrntCmd != null;
    }

    private byte getTZOffSetByte(long offSetVal) {
        int i = 1;
        boolean isNegative = offSetVal < 0;
        long tzOffset = offSetVal / 900000;
        if (isNegative) {
            i = -1;
        }
        byte bcdVal = byteToBCD((int) (tzOffset * ((long) i)));
        if (isNegative) {
            bcdVal = (byte) (bcdVal | 8);
            return bcdVal;
        }
        byte b = bcdVal;
        return bcdVal;
    }

    private byte byteToBCD(int value) {
        if (value >= 0 || value <= 99) {
            return (byte) ((value / 10) | ((value % 10) << 4));
        }
        CatLog.m0d((Object) this, "Err: byteToBCD conversion Value is " + value + " Value has to be between 0 and 99");
        return (byte) 0;
    }

    public static int getPackageType(int slot) {
        String mIccType = TelephonyManager.getTelephonyProperty("ril.ICC_TYPE", SubscriptionManager.getSubId(slot)[0], "");
        int mPackageType = slot;
        if (!"DCG".equals("DGG") && !"DCGG".equals("DGG") && !"DCGS".equals("DGG") && !"DCGGS".equals("DGG") && !"CG".equals("DGG")) {
            return mPackageType;
        }
        if (slot == 0) {
            return 2;
        }
        if (slot == 1) {
            return 0;
        }
        return mPackageType;
    }

    private String getSystemProperty(String key, String defValue) {
        return TelephonyManager.getTelephonyProperty(key, SubscriptionController.getInstance().getSubId(this.mSlotId)[0], defValue);
    }

    private void launchSimRefreshMsgAndCancelNoti(int refreshResult) {
        if (!this.mWakeLock.isHeld()) {
            this.mWakeLock.acquire(65000);
        }
        int notificationId = STK_NOTIFICATION_ID;
        if (this.mSlotId == 1) {
            notificationId = STK2_NOTIFICATION_ID;
        }
        this.mNotificationManager.cancel(notificationId);
        if (2 == getPackageType(this.mSlotId)) {
            CatLog.m0d((Object) this, "launchSimRefreshMsgAndCancelNoti: skip refresh toast");
            return;
        }
        String message;
        if (refreshResult == 1 || refreshResult == 0) {
            message = new String(this.mContext.getText(17041473).toString());
        } else if (refreshResult == 2) {
            message = new String(this.mContext.getText(17041474).toString());
        } else {
            return;
        }
        Toast.makeText(this.mContext, message, 1).show();
    }
}
