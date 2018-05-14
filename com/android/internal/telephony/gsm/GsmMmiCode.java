package com.android.internal.telephony.gsm;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.BidiFormatter;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.gsm.SsData.RequestType;
import com.android.internal.telephony.gsm.SsData.ServiceType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.sec.android.app.CscFeature;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GsmMmiCode extends Handler implements MmiCode {
    static final String ACTION_ACTIVATE = "*";
    static final String ACTION_DEACTIVATE = "#";
    static final String ACTION_ERASURE = "##";
    static final String ACTION_INTERROGATE = "*#";
    static final String ACTION_REGISTER = "**";
    static final String BearerSvcNotProvisoned = "10";
    static final String CallBarred = "14";
    static final String DataMissing = "35";
    static final char END_OF_USSD_COMMAND = '#';
    static final int EVENT_GET_CLIR_COMPLETE = 2;
    static final int EVENT_QUERY_CF_COMPLETE = 3;
    static final int EVENT_QUERY_COMPLETE = 5;
    static final int EVENT_SET_CFF_COMPLETE = 6;
    static final int EVENT_SET_COMPLETE = 1;
    static final int EVENT_USSD_CANCEL_COMPLETE = 7;
    static final int EVENT_USSD_COMPLETE = 4;
    static final String GLOBALDEV_CS = "+19085594899";
    static final String IllegalSSOperation = "16";
    static final String LOG_TAG = "GsmMmiCode";
    static final int MATCH_GROUP_ACTION = 2;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    static final int MATCH_GROUP_GLOBALDEV_DIALNUM = 5;
    static final int MATCH_GROUP_GLOBALDEV_DIALPREFIX = 4;
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final int MAX_LENGTH_SHORT_CODE = 2;
    static final String MCC_CROATIA = "219";
    static final String MCC_SERBIA = "220";
    static final String NegativePWCheck = "38";
    static final String NumOfPWAttempsViolation = "43";
    static final String PwRegFailure = "37";
    static final String SC_BAIC = "35";
    static final String SC_BAICr = "351";
    static final String SC_BAOC = "33";
    static final String SC_BAOIC = "331";
    static final String SC_BAOICxH = "332";
    static final String SC_BA_ALL = "330";
    static final String SC_BA_MO = "333";
    static final String SC_BA_MT = "353";
    static final String SC_CFB = "67";
    static final String SC_CFNR = "62";
    static final String SC_CFNRy = "61";
    static final String SC_CFU = "21";
    static final String SC_CF_All = "002";
    static final String SC_CF_All_Conditional = "004";
    static final String SC_CLIP = "30";
    static final String SC_CLIR = "31";
    static final String SC_CNAP = "300";
    static final String SC_COLP = "76";
    static final String SC_COLR = "77";
    static final String SC_GLOBALDEV_CLIR_INVK = "67";
    static final String SC_GLOBALDEV_CLIR_SUPP = "82";
    static final String SC_GLOBALDEV_CS = "611";
    static final String SC_GLOBALDEV_VM = "86";
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static final String SC_PWD = "03";
    static final String SC_WAIT = "43";
    static final String SSErrStatus = "17";
    static final String SSIncompatibility = "20";
    static final String SSNotAvailable = "18";
    static final String SSSubscriptionViolation = "19";
    static final String SysFailure = "34";
    static final String TelesrviceNotProvisoned = "11";
    static final String UnexpectedDataValue = "36";
    static final String UnknownSubscriber = "1";
    static final String absentSubscriber = "27";
    static final String deflectionToServedSubscriber = "123";
    static final String facilityNotSupported = "21";
    static final String illegalSubscriber = "9";
    static final String invalidDeflectedToNumber = "125";
    static final String longTermDenial = "30";
    static final String maxNumberOfMPTY_ParticipantsExceeded = "126";
    static final String positionMethodFailure = "54";
    static final String rejectedByNetwork = "122";
    static final String rejectedByUser = "121";
    static final String resourcesNotAvailable = "127";
    static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    static Pattern sPatternSuppServiceGlobalDev = Pattern.compile("((\\*)(\\d{2})(\\+{0,1})(\\d{0,}))");
    private static String[] sTwoDigitNumberPattern = null;
    static final String shortTermDenial = "29";
    static final String specialServiceCode = "124";
    static final String unknownAlphabet = "71";
    static final String ussd_Busy = "72";
    private String dialString = null;
    private boolean isSsInfo = false;
    Error lastErr = Error.INVALID_RESPONSE;
    String mAction;
    Context mContext;
    String mDialingNumber;
    IccRecords mIccRecords;
    private boolean mIsCallFwdReg;
    private boolean mIsPendingUSSD;
    private boolean mIsUssdRequest;
    CharSequence mMessage;
    GSMPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    State mState = State.PENDING;
    UiccCardApplication mUiccApplication;
    private CharSequence ussdCode = null;

    static /* synthetic */ class C01051 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$CommandException$Error = new int[Error.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType = new int[RequestType.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType = new int[ServiceType.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CFU.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CF_BUSY.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CF_NO_REPLY.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CF_NOT_REACHABLE.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CF_ALL.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CF_ALL_CONDITIONAL.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CLIP.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_CLIR.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_WAIT.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_BAOC.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_BAOIC.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_BAOIC_EXC_HOME.ordinal()] = 12;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_BAIC.ordinal()] = 13;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_BAIC_ROAMING.ordinal()] = 14;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_ALL_BARRING.ordinal()] = 15;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_OUTGOING_BARRING.ordinal()] = 16;
            } catch (NoSuchFieldError e16) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[ServiceType.SS_INCOMING_BARRING.ordinal()] = 17;
            } catch (NoSuchFieldError e17) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[RequestType.SS_ACTIVATION.ordinal()] = 1;
            } catch (NoSuchFieldError e18) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[RequestType.SS_DEACTIVATION.ordinal()] = 2;
            } catch (NoSuchFieldError e19) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[RequestType.SS_REGISTRATION.ordinal()] = 3;
            } catch (NoSuchFieldError e20) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[RequestType.SS_ERASURE.ordinal()] = 4;
            } catch (NoSuchFieldError e21) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[RequestType.SS_INTERROGATION.ordinal()] = 5;
            } catch (NoSuchFieldError e22) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[Error.NOT_SUBCRIBED_USER.ordinal()] = 1;
            } catch (NoSuchFieldError e23) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[Error.PASSWORD_INCORRECT.ordinal()] = 2;
            } catch (NoSuchFieldError e24) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[Error.SMS_DSAC_FAILURE.ordinal()] = 3;
            } catch (NoSuchFieldError e25) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[Error.REQUEST_NOT_SUPPORTED.ordinal()] = 4;
            } catch (NoSuchFieldError e26) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[Error.OP_NOT_ALLOWED_BEFORE_REG_NW.ordinal()] = 5;
            } catch (NoSuchFieldError e27) {
            }
        }
    }

    static GsmMmiCode newFromDialString(String dialString, GSMPhone phone, UiccCardApplication app) {
        Matcher m = sPatternSuppService.matcher(dialString);
        GsmMmiCode ret;
        if (m.matches()) {
            ret = new GsmMmiCode(phone, app);
            ret.mPoundString = makeEmptyNull(m.group(1));
            ret.mAction = makeEmptyNull(m.group(2));
            ret.mSc = makeEmptyNull(m.group(3));
            ret.mSia = makeEmptyNull(m.group(5));
            ret.mSib = makeEmptyNull(m.group(7));
            ret.mSic = makeEmptyNull(m.group(9));
            ret.mPwd = makeEmptyNull(m.group(11));
            ret.mDialingNumber = makeEmptyNull(m.group(12));
            if (ret.mDialingNumber == null || !ret.mDialingNumber.endsWith(ACTION_DEACTIVATE) || !dialString.endsWith(ACTION_DEACTIVATE)) {
                return ret;
            }
            ret = new GsmMmiCode(phone, app);
            ret.mPoundString = dialString;
            return ret;
        } else if (dialString.endsWith(ACTION_DEACTIVATE)) {
            ret = new GsmMmiCode(phone, app);
            ret.mPoundString = dialString;
            return ret;
        } else if (isCroatiaShortCode(dialString)) {
            return null;
        } else {
            if (isSerbiaShortCode(dialString)) {
                return null;
            }
            if (isTwoDigitShortCode(phone.getContext(), dialString)) {
                return null;
            }
            if (!isShortCode(dialString, phone)) {
                return null;
            }
            ret = new GsmMmiCode(phone, app);
            ret.mDialingNumber = dialString;
            return ret;
        }
    }

    static GsmMmiCode newNetworkInitiatedUssd(String ussdMessage, boolean isUssdRequest, GSMPhone phone, UiccCardApplication app) {
        GsmMmiCode ret = new GsmMmiCode(phone, app);
        ret.mMessage = ussdMessage;
        ret.mIsUssdRequest = isUssdRequest;
        if (isUssdRequest) {
            ret.mIsPendingUSSD = true;
            ret.mState = State.PENDING;
        } else {
            ret.mState = State.COMPLETE;
        }
        return ret;
    }

    static GsmMmiCode newFromUssdUserInput(String ussdMessge, GSMPhone phone, UiccCardApplication app) {
        GsmMmiCode ret = new GsmMmiCode(phone, app);
        ret.mMessage = ussdMessge;
        ret.mState = State.PENDING;
        ret.mIsPendingUSSD = true;
        return ret;
    }

    private static String makeEmptyNull(String s) {
        if (s == null || s.length() != 0) {
            return s;
        }
        return null;
    }

    private static boolean isEmptyOrNull(CharSequence s) {
        return s == null || s.length() == 0;
    }

    private static int scToCallForwardReason(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call forward sc");
        } else if (sc.equals(SC_CF_All)) {
            return 4;
        } else {
            if (sc.equals("21")) {
                return 0;
            }
            if (sc.equals("67")) {
                return 1;
            }
            if (sc.equals(SC_CFNR)) {
                return 3;
            }
            if (sc.equals(SC_CFNRy)) {
                return 2;
            }
            if (sc.equals(SC_CF_All_Conditional)) {
                return 5;
            }
            throw new RuntimeException("invalid call forward sc");
        }
    }

    private static int siToServiceClass(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        switch (Integer.parseInt(si, 10)) {
            case 10:
                return 13;
            case 11:
                return 1;
            case 12:
                return 12;
            case 13:
                return 4;
            case 16:
                return 8;
            case 19:
                return 5;
            case 20:
                return 48;
            case 21:
                return 160;
            case 22:
                return 80;
            case 24:
                return 16;
            case 25:
                return 32;
            case 26:
                return 17;
            case 99:
                return 64;
            default:
                throw new RuntimeException("unsupported MMI service code " + si);
        }
    }

    private static int siToTime(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        return Integer.parseInt(si, 10);
    }

    static boolean isServiceCodeCallForwarding(String sc) {
        return sc != null && (sc.equals("21") || sc.equals("67") || sc.equals(SC_CFNRy) || sc.equals(SC_CFNR) || sc.equals(SC_CF_All) || sc.equals(SC_CF_All_Conditional));
    }

    static boolean isServiceCodeCallBarring(String sc) {
        boolean z = true;
        String mcc = "000";
        String numeric = SystemProperties.get("gsm.operator.numeric");
        if (numeric != null && numeric.length() > 4) {
            mcc = numeric.substring(0, 3);
        }
        Rlog.d(LOG_TAG, "isServiceCodeCallBarring  mcc :" + mcc + ", sc :" + sc);
        if (sc != null && sc.equals(SC_BA_MO) && "520".equals(mcc)) {
            Rlog.d(LOG_TAG, "isServiceCodeCallBarring return false : Indonesia SEA operator takes *#333# as USSD");
            return false;
        } else if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableCallBarringConnectToUssd")) {
            if (sc == null || !(sc.equals(SC_BAOC) || sc.equals(SC_BAOIC) || sc.equals(SC_BAOICxH) || sc.equals("35") || sc.equals(SC_BAICr))) {
                z = false;
            }
            return z;
        } else {
            Resources resource = Resources.getSystem();
            if (sc == null) {
                return false;
            }
            String[] barringMMI = resource.getStringArray(17236020);
            if (barringMMI == null) {
                return false;
            }
            for (String match : barringMMI) {
                if (sc.equals(match)) {
                    return true;
                }
            }
            return false;
        }
    }

    static String scToBarringFacility(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call barring sc");
        } else if (sc.equals(SC_BAOC)) {
            return CommandsInterface.CB_FACILITY_BAOC;
        } else {
            if (sc.equals(SC_BAOIC)) {
                return CommandsInterface.CB_FACILITY_BAOIC;
            }
            if (sc.equals(SC_BAOICxH)) {
                return CommandsInterface.CB_FACILITY_BAOICxH;
            }
            if (sc.equals("35")) {
                return CommandsInterface.CB_FACILITY_BAIC;
            }
            if (sc.equals(SC_BAICr)) {
                return CommandsInterface.CB_FACILITY_BAICr;
            }
            if (sc.equals(SC_BA_ALL)) {
                return CommandsInterface.CB_FACILITY_BA_ALL;
            }
            if (sc.equals(SC_BA_MO)) {
                return CommandsInterface.CB_FACILITY_BA_MO;
            }
            if (sc.equals(SC_BA_MT)) {
                return CommandsInterface.CB_FACILITY_BA_MT;
            }
            throw new RuntimeException("invalid call barring sc");
        }
    }

    GsmMmiCode(GSMPhone phone, UiccCardApplication app) {
        super(phone.getHandler().getLooper());
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mUiccApplication = app;
        if (app != null) {
            this.mIccRecords = app.getIccRecords();
        }
    }

    public State getState() {
        return this.mState;
    }

    public CharSequence getMessage() {
        return this.mMessage;
    }

    public Phone getPhone() {
        return this.mPhone;
    }

    public void cancel() {
        if (this.mState != State.COMPLETE && this.mState != State.FAILED) {
            this.mState = State.CANCELLED;
            if (this.mIsPendingUSSD) {
                this.mPhone.mCi.cancelPendingUssd(obtainMessage(7, this));
            } else {
                this.mPhone.onMMIDone(this);
            }
        }
    }

    public boolean isCancelable() {
        if (isSsCode()) {
        }
        return this.mIsPendingUSSD;
    }

    boolean isMMI() {
        return this.mPoundString != null;
    }

    boolean isShortCode() {
        return this.mPoundString == null && this.mDialingNumber != null && this.mDialingNumber.length() <= 2;
    }

    public boolean isSsCode() {
        if (this.mSc == null) {
            return false;
        }
        if (this.mSc.equals("30") || this.mSc.equals(SC_CLIR) || this.mSc.equals("43") || isServiceCodeCallForwarding(this.mSc) || isServiceCodeCallBarring(this.mSc)) {
            return true;
        }
        return false;
    }

    private boolean isCfIconUpdateRequired() {
        boolean isCfUnconditionalVoice;
        int serviceClass = siToServiceClass(this.mSib);
        if (this.mSc == null || (!(this.mSc.equals("21") || this.mSc.equals(SC_CF_All)) || ((serviceClass & 1) == 0 && serviceClass != 0))) {
            isCfUnconditionalVoice = false;
        } else {
            isCfUnconditionalVoice = true;
        }
        Rlog.d(LOG_TAG, "isCfUnconditionalVoice :" + isCfUnconditionalVoice + ",isInterrogate() :" + isInterrogate());
        if (!isCfUnconditionalVoice || isInterrogate()) {
            return false;
        }
        return true;
    }

    private boolean getCfIconStatus() {
        int cfAction = -1;
        if (isActivate()) {
            cfAction = 1;
        } else if (isDeactivate()) {
            cfAction = 0;
        } else if (isRegister()) {
            cfAction = 3;
        } else if (isErasure()) {
            cfAction = 4;
        }
        Rlog.d(LOG_TAG, "getCfIconStatus cfAction :" + cfAction);
        if (cfAction == 1 || cfAction == 3) {
            return true;
        }
        return false;
    }

    private static boolean isTwoDigitShortCode(Context context, String dialString) {
        Rlog.d(LOG_TAG, "isTwoDigitShortCode");
        if (dialString == null || dialString.length() > 2) {
            return false;
        }
        if (sTwoDigitNumberPattern == null) {
            sTwoDigitNumberPattern = context.getResources().getStringArray(17236006);
        }
        for (String dialnumber : sTwoDigitNumberPattern) {
            Rlog.d(LOG_TAG, "Two Digit Number Pattern " + dialnumber);
            if (dialString.equals(dialnumber)) {
                Rlog.d(LOG_TAG, "Two Digit Number Pattern -true");
                return true;
            }
        }
        Rlog.d(LOG_TAG, "Two Digit Number Pattern -false");
        return false;
    }

    private static boolean isShortCode(String dialString, GSMPhone phone) {
        if (dialString == null || dialString.length() == 0 || PhoneNumberUtils.isLocalEmergencyNumber(phone.getContext(), dialString)) {
            return false;
        }
        return isShortCodeUSSD(dialString, phone);
    }

    private static boolean isShortCodeUSSD(String dialString, GSMPhone phone) {
        if (dialString != null && dialString.length() <= 2) {
            if (phone.isInCall()) {
                return true;
            }
            if (dialString.length() <= 2 && dialString.charAt(dialString.length() - 1) == END_OF_USSD_COMMAND) {
                return true;
            }
            if (dialString.equals("0") || dialString.equals("00") || (dialString.length() <= 2 && (dialString.charAt(0) == '*' || dialString.charAt(0) == END_OF_USSD_COMMAND))) {
                return false;
            }
            if (dialString.length() <= 2 && dialString.charAt(0) != '1') {
                return true;
            }
        }
        return false;
    }

    boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    boolean isTemporaryModeCLIR() {
        return this.mSc != null && this.mSc.equals(SC_CLIR) && this.mDialingNumber != null && (isActivate() || isDeactivate());
    }

    int getCLIRMode() {
        if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
            if (isActivate()) {
                return 2;
            }
            if (isDeactivate()) {
                return 1;
            }
        }
        return 0;
    }

    public boolean isRequiredToRouteCS() {
        return this.mSc != null && ((isInterrogate() && ((this.mSc.equals("21") || this.mSc.equals(SC_CFNRy) || this.mSc.equals(SC_CFNR) || this.mSc.equals("67") || this.mSc.equals("43") || this.mSc.equals("30") || this.mSc.equals(SC_CLIR)) && this.mSia == null && this.mSib == null && this.mSic == null)) || ((isErasure() && this.mSc.equals(SC_CF_All_Conditional) && this.mSia == null && this.mSib == null && this.mSic == null) || ((isRegister() && ((this.mSc.equals(SC_CF_All_Conditional) || this.mSc.equals(SC_CF_All)) && this.mSia != null && this.mSib == null && this.mSic == null)) || (this.mSc.equals(SC_CFNRy) && this.mSia != null && this.mSib == null && this.mSic != null))));
    }

    boolean isActivate() {
        return this.mAction != null && this.mAction.equals("*");
    }

    boolean isDeactivate() {
        return this.mAction != null && this.mAction.equals(ACTION_DEACTIVATE);
    }

    boolean isInterrogate() {
        return this.mAction != null && this.mAction.equals(ACTION_INTERROGATE);
    }

    boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    boolean isErasure() {
        return this.mAction != null && this.mAction.equals(ACTION_ERASURE);
    }

    public boolean isPendingUSSD() {
        return this.mIsPendingUSSD;
    }

    public boolean isUssdRequest() {
        return this.mIsUssdRequest;
    }

    void processCode() {
        try {
            if (isShortCode()) {
                Rlog.d(LOG_TAG, "isShortCode");
                sendUssd(this.mDialingNumber);
            } else if (this.mDialingNumber != null) {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            } else {
                if (this.mPoundString != null && this.mPhone.isImsRegistered() && isRequiredToRouteCS()) {
                }
                if (this.mSc != null && this.mSc.equals("30")) {
                    Rlog.d(LOG_TAG, "is CLIP");
                    if (isInterrogate()) {
                        this.mPhone.mCi.queryCLIP(obtainMessage(5, this));
                        return;
                    }
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                } else if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
                    Rlog.d(LOG_TAG, "is CLIR");
                    if (isActivate()) {
                        this.mPhone.mCi.setCLIR(1, obtainMessage(1, this));
                    } else if (isDeactivate()) {
                        this.mPhone.mCi.setCLIR(2, obtainMessage(1, this));
                    } else if (isInterrogate()) {
                        this.mPhone.mCi.getCLIR(obtainMessage(2, this));
                    } else {
                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                    }
                } else if (isServiceCodeCallForwarding(this.mSc)) {
                    Rlog.d(LOG_TAG, "is CF");
                    String dialingNumber = this.mSia;
                    serviceClass = siToServiceClass(this.mSib);
                    int reason = scToCallForwardReason(this.mSc);
                    int time = siToTime(this.mSic);
                    if (isInterrogate()) {
                        this.mPhone.mCi.queryCallForwardStatus(reason, serviceClass, dialingNumber, obtainMessage(3, this));
                        return;
                    }
                    int cfAction;
                    if (isActivate()) {
                        if (isEmptyOrNull(dialingNumber)) {
                            cfAction = 1;
                            this.mIsCallFwdReg = false;
                        } else {
                            cfAction = 3;
                            this.mIsCallFwdReg = true;
                        }
                    } else if (isDeactivate()) {
                        cfAction = 0;
                    } else if (isRegister()) {
                        cfAction = 3;
                    } else if (isErasure()) {
                        cfAction = 4;
                    } else {
                        throw new RuntimeException("invalid action");
                    }
                    int enableDesiredIfCfu = -1;
                    if (reason == 0 || reason == 4) {
                        enableDesiredIfCfu = (cfAction == 1 || cfAction == 3) ? 1 : 0;
                    }
                    Rlog.d(LOG_TAG, "is CF setCallForward");
                    this.mPhone.mCi.setCallForward(cfAction, reason, serviceClass, dialingNumber, time, obtainMessage(6, serviceClass, enableDesiredIfCfu, this));
                } else if (isServiceCodeCallBarring(this.mSc)) {
                    String password = this.mSia;
                    serviceClass = siToServiceClass(this.mSib);
                    facility = scToBarringFacility(this.mSc);
                    if (isInterrogate()) {
                        this.mPhone.mCi.queryFacilityLock(facility, password, serviceClass, obtainMessage(5, this));
                    } else if (isActivate() || isDeactivate()) {
                        this.mPhone.mCi.setFacilityLock(facility, isActivate(), password, serviceClass, obtainMessage(1, this));
                    } else {
                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                    }
                } else if (this.mSc != null && this.mSc.equals(SC_PWD)) {
                    String oldPwd = this.mSib;
                    String newPwd = this.mSic;
                    if (isActivate() || isRegister()) {
                        this.mAction = ACTION_REGISTER;
                        if (this.mSia == null) {
                            facility = CommandsInterface.CB_FACILITY_BA_ALL;
                        } else {
                            facility = scToBarringFacility(this.mSia);
                        }
                        this.mPhone.mCi.changeBarringPassword(facility, oldPwd, newPwd, this.mPwd, obtainMessage(1, this));
                        return;
                    }
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                } else if (this.mSc != null && this.mSc.equals("43")) {
                    serviceClass = siToServiceClass(this.mSia);
                    if (isActivate() || isDeactivate()) {
                        this.mPhone.mCi.setCallWaiting(isActivate(), serviceClass, obtainMessage(1, this));
                    } else if (isInterrogate()) {
                        this.mPhone.mCi.queryCallWaiting(serviceClass, obtainMessage(5, this));
                    } else {
                        throw new RuntimeException("Invalid or Unsupported MMI Code");
                    }
                } else if (isPinPukCommand()) {
                    String oldPinOrPuk = this.mSia;
                    String newPinOrPuk = this.mSib;
                    int pinLen = newPinOrPuk.length();
                    int oldPinLen = oldPinOrPuk.length();
                    if (isRegister()) {
                        if (!newPinOrPuk.equals(this.mSic)) {
                            handlePasswordError(17039511);
                            return;
                        } else if (oldPinLen < 4 || oldPinLen > 8 || pinLen < 4 || pinLen > 8) {
                            handlePasswordError(17039512);
                            return;
                        } else if (this.mSc.equals(SC_PIN) && this.mUiccApplication != null && this.mUiccApplication.getState() == AppState.APPSTATE_PUK) {
                            handlePasswordError(17039514);
                            return;
                        } else if (this.mSc.equals(SC_PIN) && this.mUiccApplication != null && !this.mUiccApplication.getIccLockEnabled()) {
                            handlePasswordError(17039516);
                            return;
                        } else if (this.mUiccApplication != null) {
                            Rlog.d(LOG_TAG, "process mmi service code using UiccApp sc=" + this.mSc);
                            if (this.mSc.equals(SC_PIN)) {
                                this.mUiccApplication.changeIccLockPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PIN2)) {
                                this.mUiccApplication.changeIccFdnPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PUK)) {
                                this.mUiccApplication.supplyPuk(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                                return;
                            } else if (this.mSc.equals(SC_PUK2)) {
                                this.mUiccApplication.supplyPuk2(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                                return;
                            } else {
                                throw new RuntimeException("uicc unsupported service code=" + this.mSc);
                            }
                        } else {
                            throw new RuntimeException("No application mUiccApplicaiton is null");
                        }
                    }
                    throw new RuntimeException("Ivalid register/action=" + this.mAction);
                } else if (this.mPoundString != null) {
                    sendUssd(this.mPoundString);
                } else {
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            }
        } catch (RuntimeException e) {
            this.mState = State.FAILED;
            this.mMessage = this.mContext.getText(17039500);
            this.mPhone.onMMIDone(this);
        }
    }

    private void handlePasswordError(int res) {
        this.mState = State.FAILED;
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        sb.append(this.mContext.getText(res));
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    void onUssdFinished(String ussdMessage, boolean isUssdRequest) {
        if (this.mState == State.PENDING) {
            if (ussdMessage == null) {
                this.mMessage = this.mContext.getText(17039508);
            } else {
                this.mMessage = ussdMessage;
            }
            this.mIsUssdRequest = isUssdRequest;
            if (!isUssdRequest) {
                this.mState = State.COMPLETE;
            }
            this.mPhone.onMMIDone(this);
        }
    }

    void onUssdFinishedError() {
        if (this.mState == State.PENDING) {
            this.mState = State.FAILED;
            this.mMessage = this.mContext.getText(17039500);
            this.mPhone.onMMIDone(this);
        }
    }

    void sendUssd(String ussdMessage) {
        this.mIsPendingUSSD = true;
        this.mPhone.mCi.sendUSSD(ussdMessage, obtainMessage(4, this));
    }

    public void handleMessage(Message msg) {
        boolean cffEnabled = false;
        AsyncResult ar = (AsyncResult) msg.obj;
        if (!(ar == null || ar.exception == null || !(ar.exception instanceof CommandException))) {
            this.lastErr = ((CommandException) ar.exception).getCommandError();
            if (!("ILO".equals(SystemProperties.get("ro.csc.sales_code")) || "PCL".equals(SystemProperties.get("ro.csc.sales_code")) || "CEL".equals(SystemProperties.get("ro.csc.sales_code")) || "PTR".equals(SystemProperties.get("ro.csc.sales_code")) || "MIR".equals(SystemProperties.get("ro.csc.sales_code")))) {
                this.mPhone.mMmiInitBySTK = false;
            }
        }
        switch (msg.what) {
            case 1:
                onSetComplete(msg, (AsyncResult) msg.obj);
                return;
            case 2:
                onGetClirComplete((AsyncResult) msg.obj);
                return;
            case 3:
                onQueryCfComplete((AsyncResult) msg.obj);
                return;
            case 4:
                ar = (AsyncResult) msg.obj;
                if (isSsCode()) {
                }
                if (ar.exception != null) {
                    this.mState = State.FAILED;
                    this.mMessage = getErrorMessage(ar);
                    this.mPhone.onMMIDone(this);
                    return;
                }
                return;
            case 5:
                onQueryComplete((AsyncResult) msg.obj);
                return;
            case 6:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null && msg.arg2 != -1) {
                    if (msg.arg2 == 1) {
                        cffEnabled = true;
                    }
                    if (this.mIccRecords != null) {
                        if ((msg.arg1 & 1) != 0) {
                            this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, this.mDialingNumber);
                        }
                        if ((msg.arg1 & 16) != 0) {
                            this.mIccRecords.setVideoCallForwardingFlag(1, cffEnabled, this.mDialingNumber);
                        }
                        if (msg.arg1 == 0) {
                            this.mIccRecords.setCallForwardingFlag(1, Boolean.valueOf(cffEnabled), Boolean.valueOf(cffEnabled));
                        }
                    }
                }
                onSetComplete(msg, ar);
                return;
            case 7:
                this.mPhone.onMMIDone(this);
                return;
            default:
                return;
        }
    }

    private CharSequence getErrorMessage(AsyncResult ar) {
        if (!(ar.exception instanceof CommandException) || ((CommandException) ar.exception).getCommandError() != Error.FDN_CHECK_FAILURE) {
            return this.mContext.getText(17039500);
        }
        Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
        return this.mContext.getText(17039501);
    }

    private CharSequence getScString() {
        if (this.mSc != null) {
            if (isServiceCodeCallBarring(this.mSc)) {
                return this.mContext.getText(17039525);
            }
            if (isServiceCodeCallForwarding(this.mSc)) {
                return this.mContext.getText(17039523);
            }
            if (this.mSc.equals("30")) {
                return this.mContext.getText(17039519);
            }
            if (this.mSc.equals(SC_CLIR)) {
                return this.mContext.getText(17039520);
            }
            if (this.mSc.equals(SC_PWD)) {
                return this.mContext.getText(17039526);
            }
            if (this.mSc.equals("43")) {
                return this.mContext.getText(17039524);
            }
            if (isPinPukCommand()) {
                if (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PUK)) {
                    return this.mContext.getText(17039527);
                }
                return this.mContext.getText(17041475);
            }
        }
        return "";
    }

    private void onSetComplete(Message msg, AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = State.FAILED;
            if (ar.exception instanceof CommandException) {
                Error err = ((CommandException) ar.exception).getCommandError();
                if (err == Error.PASSWORD_INCORRECT) {
                    if (isPinPukCommand()) {
                        if (this.mSc.equals(SC_PUK)) {
                            sb.append(this.mContext.getText(17039510));
                        } else if (this.mSc.equals(SC_PUK2)) {
                            sb.append(this.mContext.getText(17041477));
                        } else if (this.mSc.equals(SC_PIN2)) {
                            sb.append(this.mContext.getText(17041476));
                        } else {
                            sb.append(this.mContext.getText(17039509));
                        }
                        int attemptsRemaining = msg.arg1;
                        if (attemptsRemaining <= 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: PUK locked, cancel as lock screen will handle this");
                            this.mState = State.CANCELLED;
                        } else if (attemptsRemaining > 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: attemptsRemaining=" + attemptsRemaining);
                            sb.append(this.mContext.getResources().getQuantityString(18087936, attemptsRemaining, new Object[]{Integer.valueOf(attemptsRemaining)}));
                        }
                    } else if (isServiceCodeCallForwarding(this.mSc)) {
                        sb.append(this.mContext.getText(17039500));
                        sb.append("\n");
                        sb.append(forwardingTypeToString(this.mSc));
                        sb.append(" ");
                        sb.append(serviceClassString(siToServiceClass(this.mSib)));
                    } else if (isServiceCodeCallBarring(this.mSc)) {
                        sb.append(this.mContext.getText(17039500));
                        sb.append("\n");
                        sb.append(barringTypeToString(this.mSc));
                        sb.append(" ");
                        sb.append(serviceClassString(siToServiceClass(this.mSib)));
                    } else if (this.mSc.equals("43")) {
                        sb.append(this.mContext.getText(17041442));
                        sb.append("\n");
                        sb.append(serviceClassString(siToServiceClass(this.mSib)));
                    } else {
                        sb.append("\n");
                        sb.append(this.mContext.getText(17039507));
                    }
                } else if (err == Error.SIM_PUK2) {
                    sb.append(this.mContext.getText(17039509));
                    sb.append("\n");
                    sb.append(this.mContext.getText(17039515));
                } else if (err == Error.REQUEST_NOT_SUPPORTED) {
                    if (this.mSc.equals(SC_PIN)) {
                        sb.append(this.mContext.getText(17039516));
                    }
                } else if (err == Error.FDN_CHECK_FAILURE) {
                    Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                    sb.append(this.mContext.getText(17039501));
                } else if (isServiceCodeCallForwarding(this.mSc)) {
                    sb.append(this.mContext.getText(17039500));
                    sb.append("\n");
                    sb.append(forwardingTypeToString(this.mSc));
                    sb.append(" ");
                    sb.append(serviceClassString(siToServiceClass(this.mSib)));
                } else if (isServiceCodeCallBarring(this.mSc)) {
                    sb.append(this.mContext.getText(17039500));
                    sb.append("\n");
                    sb.append(barringTypeToString(this.mSc));
                    sb.append(" ");
                    sb.append(serviceClassString(siToServiceClass(this.mSib)));
                } else if (!this.mSc.equals("43")) {
                    sb.append(this.mContext.getText(17039500));
                } else if (err == Error.NOT_SUBCRIBED_USER) {
                    sb.replace(0, sb.length(), this.mContext.getString(17041513));
                } else {
                    sb.append(this.mContext.getText(17041442));
                    sb.append("\n");
                    sb.append(serviceClassString(siToServiceClass(this.mSib)));
                }
            } else {
                sb.append(this.mContext.getText(17039500));
            }
        } else if (isActivate()) {
            this.mState = State.COMPLETE;
            if (isServiceCodeCallForwarding(this.mSc)) {
                sb.append(forwardingTypeToString(this.mSc));
                sb.append("\n");
            } else if (isServiceCodeCallBarring(this.mSc)) {
                sb.append(barringTypeToString(this.mSc));
                sb.append("\n");
            }
            if (this.mIsCallFwdReg) {
                sb.append(this.mContext.getText(17039505));
            } else {
                sb.append(this.mContext.getText(17039502));
            }
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(1);
            }
        } else if (isDeactivate()) {
            this.mState = State.COMPLETE;
            if (isServiceCodeCallForwarding(this.mSc)) {
                sb.append(forwardingTypeToString(this.mSc));
                sb.append("\n");
            } else if (isServiceCodeCallBarring(this.mSc)) {
                sb.append(barringTypeToString(this.mSc));
                sb.append("\n");
            }
            sb.append(this.mContext.getText(17039504));
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(2);
            }
        } else if (isRegister()) {
            this.mState = State.COMPLETE;
            if (isServiceCodeCallForwarding(this.mSc)) {
                sb.append(forwardingTypeToString(this.mSc));
                sb.append("\n");
            }
            sb.append(this.mContext.getText(17039505));
        } else if (isErasure()) {
            this.mState = State.COMPLETE;
            if (isServiceCodeCallForwarding(this.mSc)) {
                sb.append(forwardingTypeToString(this.mSc));
                sb.append("\n");
            }
            sb.append(this.mContext.getText(17039506));
        } else {
            this.mState = State.FAILED;
            sb.append(this.mContext.getText(17039500));
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onGetClirComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception == null) {
            int[] clirArgs = (int[]) ar.result;
            switch (clirArgs[1]) {
                case 0:
                    sb.append(this.mContext.getText(17039538));
                    this.mState = State.COMPLETE;
                    break;
                case 1:
                    sb.append(this.mContext.getText(17039539));
                    this.mState = State.COMPLETE;
                    break;
                case 2:
                    sb.append(this.mContext.getText(17039500));
                    this.mState = State.FAILED;
                    break;
                case 3:
                    switch (clirArgs[0]) {
                        case 1:
                            sb.append(this.mContext.getText(17039534));
                            break;
                        case 2:
                            sb.append(this.mContext.getText(17039535));
                            break;
                        default:
                            sb.append(this.mContext.getText(17039534));
                            break;
                    }
                    this.mState = State.COMPLETE;
                    break;
                case 4:
                    switch (clirArgs[0]) {
                        case 1:
                            sb.append(this.mContext.getText(17039536));
                            break;
                        case 2:
                            sb.append(this.mContext.getText(17039537));
                            break;
                        default:
                            sb.append(this.mContext.getText(17039537));
                            break;
                    }
                    this.mState = State.COMPLETE;
                    break;
                default:
                    break;
            }
        }
        this.mState = State.FAILED;
        sb.append(getErrorMessage(ar));
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence serviceClassToCFString(int serviceClass) {
        switch (serviceClass) {
            case 1:
                return this.mContext.getText(17039549);
            case 2:
                return this.mContext.getText(17039550);
            case 4:
                return this.mContext.getText(17039551);
            case 8:
                return this.mContext.getText(17039552);
            case 16:
                return this.mContext.getText(17039554);
            case 32:
                return this.mContext.getText(17039553);
            case 64:
                return this.mContext.getText(17039555);
            case 128:
                return this.mContext.getText(17039556);
            default:
                return null;
        }
    }

    private CharSequence makeCFQueryResultMessage(CallForwardInfo info, int serviceClassMask) {
        boolean needTimeTemplate;
        CharSequence template;
        String[] sources = new String[]{"{0}", "{1}", "{2}"};
        CharSequence[] destinations = new CharSequence[3];
        if (info.reason == 2) {
            needTimeTemplate = true;
        } else {
            needTimeTemplate = false;
        }
        if (info.status == 1) {
            if (needTimeTemplate) {
                template = this.mContext.getText(17039573);
            } else {
                template = this.mContext.getText(17039572);
            }
        } else if (info.status == 0 && isEmptyOrNull(info.number)) {
            template = this.mContext.getText(17039571);
        } else if (needTimeTemplate) {
            template = this.mContext.getText(17039575);
        } else {
            template = this.mContext.getText(17039574);
        }
        destinations[0] = serviceClassToCFString(info.serviceClass & serviceClassMask);
        destinations[1] = formatLtr(PhoneNumberUtils.stringFromStringAndTOA(info.number, info.toa));
        destinations[2] = Integer.toString(info.timeSeconds);
        if (info.reason == 0) {
            boolean cffEnabled = info.status == 1;
            if (this.mIccRecords != null) {
                if ((info.serviceClass & serviceClassMask) == 1) {
                    this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, info.number);
                }
                if ((info.serviceClass & serviceClassMask) == 16) {
                    this.mIccRecords.setVideoCallForwardingFlag(1, cffEnabled, info.number);
                }
            }
        }
        return TextUtils.replace(template, sources, destinations);
    }

    private String formatLtr(String str) {
        return str == null ? str : BidiFormatter.getInstance().unicodeWrap(str, TextDirectionHeuristics.LTR, true);
    }

    private void onQueryCfComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = State.FAILED;
            if (((CommandException) ar.exception).getCommandError() == Error.FDN_CHECK_FAILURE) {
                Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                sb.append(this.mContext.getText(17039501));
            } else {
                sb.append(this.mContext.getText(17041442));
                sb.append("\n");
                sb.append(forwardingTypeToString(this.mSc));
                sb.append(" ");
                sb.append(serviceClassString(siToServiceClass(this.mSib)));
            }
        } else {
            CallForwardInfo[] infos = (CallForwardInfo[]) ar.result;
            if (infos.length == 0) {
                sb.append(forwardingTypeToString(this.mSc));
                sb.append(this.mContext.getText(17039504));
                if (this.mIccRecords != null) {
                    this.mIccRecords.setCallForwardingFlag(1, Boolean.valueOf(false), Boolean.valueOf(false));
                }
            } else {
                SpannableStringBuilder tb = new SpannableStringBuilder();
                tb.append(forwardingTypeToString(this.mSc));
                tb.append("\n");
                CallForwardInfo fi_voice = null;
                CallForwardInfo fi_video = null;
                for (int serviceClassMask = 1; serviceClassMask <= 128; serviceClassMask <<= 1) {
                    int s = infos.length;
                    for (int i = 0; i < s; i++) {
                        if ((infos[i].serviceClass & serviceClassMask) != 0) {
                            tb.append(makeCFQueryResultMessage(infos[i], serviceClassMask));
                            tb.append("\n");
                            if (serviceClassMask == 1) {
                                fi_voice = infos[i];
                            }
                            if (serviceClassMask == 16) {
                                fi_video = infos[i];
                            }
                        }
                    }
                }
                sb.append(tb);
                if (this.mSc.equals("21")) {
                    if (fi_voice == null && this.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, false, null);
                    }
                    if (fi_video == null && this.mIccRecords != null) {
                        this.mIccRecords.setVideoCallForwardingFlag(1, false, null);
                    }
                }
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = State.FAILED;
            sb.append(getErrorMessage(ar));
            sb.append("\n");
            if (this.mSc != null) {
                if (this.mSc.equals("43")) {
                    sb.append(serviceClassString(siToServiceClass(this.mSib)));
                } else if (isServiceCodeCallBarring(this.mSc)) {
                    sb.append(barringTypeToString(this.mSc));
                    sb.append(" ");
                    sb.append(serviceClassString(siToServiceClass(this.mSib)));
                }
            }
            if (ar.exception instanceof CommandException) {
                Error err = ((CommandException) ar.exception).getCommandError();
                if (this.mSc != null && this.mSc.equals("43") && err == Error.NOT_SUBCRIBED_USER) {
                    sb.replace(0, sb.length(), this.mContext.getString(17041513));
                }
            }
        } else {
            int[] ints = (int[]) ar.result;
            if (ints.length == 0) {
                sb.append(this.mContext.getText(17039500));
            } else if (ints[0] == 0) {
                if (isServiceCodeCallBarring(this.mSc)) {
                    sb.append(barringTypeToString(this.mSc));
                    sb.append("\n");
                }
                if (this.mSc == null || !this.mSc.equals("30")) {
                    sb.append(this.mContext.getText(17039504));
                } else {
                    sb.append(this.mContext.getText(17041428));
                }
            } else if (this.mSc != null && this.mSc.equals("43")) {
                sb.append(createQueryCallWaitingResultMessage(ints[1]));
            } else if (isServiceCodeCallBarring(this.mSc)) {
                sb.append(createQueryCallBarringResultMessage(ints[0]));
            } else if (ints[0] == 1) {
                if (this.mSc != null && isServiceCodeCallBarring(this.mSc)) {
                    sb.append(barringTypeToString(this.mSc));
                    sb.append(" ");
                }
                if (this.mSc == null || !this.mSc.equals("30")) {
                    sb.append(this.mContext.getText(17039502));
                } else {
                    sb.append(this.mContext.getText(17041427));
                }
            } else {
                sb.append(this.mContext.getText(17039500));
            }
            this.mState = State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence createQueryCallWaitingResultMessage(int serviceClass) {
        StringBuilder sb = new StringBuilder(this.mContext.getText(17039503));
        for (int classMask = 1; classMask <= 128; classMask <<= 1) {
            if ((classMask & serviceClass) != 0) {
                sb.append("\n");
                sb.append(serviceClassToCFString(classMask & serviceClass));
            }
        }
        return sb;
    }

    private CharSequence createQueryCallBarringResultMessage(int serviceClass) {
        StringBuilder sb = new StringBuilder(barringTypeToString(this.mSc));
        sb.append(" ");
        sb.append(this.mContext.getText(17039503));
        for (int classMask = 1; classMask <= 128; classMask <<= 1) {
            if ((classMask & serviceClass) != 0) {
                sb.append("\n");
                sb.append(serviceClassToCFString(classMask & serviceClass));
            }
        }
        return sb;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("GsmMmiCode {");
        sb.append("State=" + getState());
        if (this.mAction != null) {
            sb.append(" action=" + this.mAction);
        }
        if (this.mSc != null) {
            sb.append(" sc=" + this.mSc);
        }
        if (this.mSia != null) {
            sb.append(" sia=" + this.mSia);
        }
        if (this.mSib != null) {
            sb.append(" sib=" + this.mSib);
        }
        if (this.mSic != null) {
            sb.append(" sic=" + this.mSic);
        }
        if (Debug.isProductShip() != 1) {
            if (this.mPoundString != null) {
                sb.append(" poundString=" + this.mPoundString);
            }
            if (this.mDialingNumber != null) {
                sb.append(" dialingNumber=" + this.mDialingNumber);
            }
            if (this.mPwd != null) {
                sb.append(" pwd=" + this.mPwd);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    static boolean isMMICodeSupport(String dialString, GsmMmiCode mmiCode) {
        if (dialString.length() >= 3 && ((dialString.startsWith(ACTION_DEACTIVATE) || dialString.startsWith("*")) && dialString.endsWith(ACTION_DEACTIVATE))) {
            String mcc = "000";
            String numeric = SystemProperties.get("gsm.operator.numeric");
            if (numeric != null && numeric.length() > 4) {
                Rlog.d(LOG_TAG, "numeric = " + numeric);
                mcc = numeric.substring(0, 3);
            }
            Rlog.d(LOG_TAG, " salesCode = ");
            if (!"KTT".equals("") || !"450".equals(mcc)) {
                return true;
            }
            int start_ptr;
            if (dialString.startsWith(ACTION_DEACTIVATE)) {
                if (dialString.charAt(1) == END_OF_USSD_COMMAND) {
                    start_ptr = 2;
                } else {
                    start_ptr = 1;
                }
                if (isKTMMICodeSupport(dialString, start_ptr)) {
                    return true;
                }
                return false;
            } else if (!dialString.startsWith("*")) {
                return false;
            } else {
                if (dialString.charAt(1) == '*' || dialString.charAt(1) == END_OF_USSD_COMMAND) {
                    start_ptr = 2;
                } else {
                    start_ptr = 1;
                }
                if (isKTMMICodeSupport(dialString, start_ptr)) {
                    return true;
                }
                return false;
            }
        } else if (dialString.length() == 2 && dialString.charAt(0) == '7' && dialString.charAt(1) >= '0' && dialString.charAt(1) <= '9') {
            return true;
        } else {
            if (dialString.equals(UnexpectedDataValue) || dialString.equals("7")) {
                return true;
            }
            if ((!dialString.startsWith("#31#") && !dialString.startsWith("*31#")) || dialString.length() < 5) {
                return false;
            }
            Rlog.d("gsmmmi", "isMMICodeSupport 4");
            return true;
        }
    }

    static boolean isKTMMICodeSupport(String dialString, int start_ptr) {
        int sc_start_ptr = 0;
        String TempSC = "";
        String[] KTMMICode = new String[]{"75", "750", "751", "752", "753", "754", "66", "30", SC_CLIR, SC_COLP, SC_COLR, "21", "67", SC_CFNRy, SC_CFNR, SC_CF_All, SC_CF_All_Conditional, "43", "361", "362", "363", "360", SC_BAOC, SC_BAOIC, SC_BAOICxH, "35", SC_BAICr, SC_BA_ALL, SC_BA_MO, SC_BA_MT, "96", PwRegFailure, "214", SC_CNAP, "591", "592", "593", "594", "88", SC_PWD};
        while (dialString.charAt(start_ptr + sc_start_ptr) != '*' && dialString.charAt(start_ptr + sc_start_ptr) != END_OF_USSD_COMMAND) {
            TempSC = TempSC + dialString.charAt(start_ptr + sc_start_ptr);
            sc_start_ptr++;
        }
        for (String equals : KTMMICode) {
            if (equals.equals(TempSC)) {
                return true;
            }
        }
        return false;
    }

    boolean isGlobalDevMmi() {
        return this.mSc != null && (this.mSc.equals(SC_GLOBALDEV_VM) || this.mSc.equals(SC_GLOBALDEV_CS));
    }

    private CharSequence getErrorCode(AsyncResult ar) {
        CharSequence errCode = "";
        Rlog.d(LOG_TAG, "getErrorCode()");
        if (ar.exception instanceof CommandException) {
            switch (C01051.$SwitchMap$com$android$internal$telephony$CommandException$Error[((CommandException) ar.exception).getCommandError().ordinal()]) {
                case 1:
                    errCode = SSErrStatus;
                    break;
                case 2:
                    errCode = NegativePWCheck;
                    break;
                case 3:
                    errCode = "13";
                    break;
                case 4:
                    errCode = UnexpectedDataValue;
                    break;
                case 5:
                    errCode = BearerSvcNotProvisoned;
                    break;
                default:
                    errCode = "0";
                    break;
            }
        }
        Rlog.d(LOG_TAG, "getErrorCode( return " + errCode + " )");
        return errCode;
    }

    private int ErrcodeToString(String errorcode) {
        Rlog.d(LOG_TAG, "ErrcodeToString errorcode - " + errorcode);
        if (errorcode.equals(PwRegFailure)) {
            return 17041480;
        }
        if (errorcode.equals(NegativePWCheck)) {
            return 17041481;
        }
        if (errorcode.equals("43")) {
            return 17041482;
        }
        if (errorcode.equals(UnknownSubscriber)) {
            return 17041483;
        }
        if (errorcode.equals(BearerSvcNotProvisoned)) {
            return 17041484;
        }
        if (errorcode.equals(TelesrviceNotProvisoned)) {
            return 17041485;
        }
        if (errorcode.equals(CallBarred)) {
            return 17041486;
        }
        if (errorcode.equals(IllegalSSOperation)) {
            return 17041487;
        }
        if (errorcode.equals(SSErrStatus)) {
            return 17041488;
        }
        if (errorcode.equals(SSNotAvailable)) {
            return 17041489;
        }
        if (errorcode.equals(SSSubscriptionViolation)) {
            return 17041490;
        }
        if (errorcode.equals(SSIncompatibility)) {
            return 17041491;
        }
        if (errorcode.equals(SysFailure)) {
            return 17041492;
        }
        if (errorcode.equals("35")) {
            return 17041493;
        }
        if (errorcode.equals(UnexpectedDataValue)) {
            return 17041494;
        }
        if (errorcode.equals(illegalSubscriber)) {
            return 17041495;
        }
        if (errorcode.equals("21")) {
            return 17041496;
        }
        if (errorcode.equals(absentSubscriber)) {
            return 17041497;
        }
        if (errorcode.equals(shortTermDenial)) {
            return 17041498;
        }
        if (errorcode.equals("30")) {
            return 17041499;
        }
        if (errorcode.equals(positionMethodFailure)) {
            return 17041500;
        }
        if (errorcode.equals(unknownAlphabet)) {
            return 17041501;
        }
        if (errorcode.equals(ussd_Busy)) {
            return 17041502;
        }
        if (errorcode.equals(rejectedByUser)) {
            return 17041503;
        }
        if (errorcode.equals(rejectedByNetwork)) {
            return 17041504;
        }
        if (errorcode.equals(deflectionToServedSubscriber)) {
            return 17041505;
        }
        if (errorcode.equals(specialServiceCode)) {
            return 17041506;
        }
        if (errorcode.equals(invalidDeflectedToNumber)) {
            return 17041507;
        }
        if (errorcode.equals(maxNumberOfMPTY_ParticipantsExceeded)) {
            return 17041508;
        }
        if (errorcode.equals(resourcesNotAvailable)) {
            return 17041509;
        }
        return 17041510;
    }

    private CharSequence serviceModeToCFString(int reason) {
        switch (reason) {
            case 0:
                return this.mContext.getText(17041443);
            case 1:
                return this.mContext.getText(17041425);
            case 2:
                return this.mContext.getText(17041444);
            case 3:
                return this.mContext.getText(17041445);
            case 4:
                return this.mContext.getText(17041446);
            case 5:
                return this.mContext.getText(17041447);
            default:
                return null;
        }
    }

    private CharSequence serviceClassString(int serviceClass) {
        switch (serviceClass) {
            case 1:
                return this.mContext.getText(17041448);
            case 2:
                return this.mContext.getText(17041449);
            case 4:
                return this.mContext.getText(17041450);
            case 5:
                return this.mContext.getText(17041460);
            case 8:
                return this.mContext.getText(17041451);
            case 12:
                return this.mContext.getText(17041459);
            case 13:
                return this.mContext.getText(17041458);
            case 16:
                return this.mContext.getText(17041452);
            case 17:
                return this.mContext.getText(17041462);
            case 32:
                return this.mContext.getText(17041453);
            case 48:
                return this.mContext.getText(17041461);
            case 64:
                return this.mContext.getText(17041454);
            case CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM /*96*/:
                return this.mContext.getText(17041457);
            case 128:
                return this.mContext.getText(17041455);
            case 160:
                return this.mContext.getText(17041456);
            default:
                return this.mContext.getText(17041463);
        }
    }

    private CharSequence forwardingTypeToString(String cfType) {
        if (this.mSc.equals(SC_CF_All)) {
            return this.mContext.getText(17041446);
        }
        if (this.mSc.equals("21")) {
            return this.mContext.getText(17041443);
        }
        if (this.mSc.equals("67")) {
            return this.mContext.getText(17041425);
        }
        if (this.mSc.equals(SC_CFNR)) {
            return this.mContext.getText(17041445);
        }
        if (this.mSc.equals(SC_CFNRy)) {
            return this.mContext.getText(17041444);
        }
        if (this.mSc.equals(SC_CF_All_Conditional)) {
            return this.mContext.getText(17041447);
        }
        return " ";
    }

    private CharSequence barringTypeToString(String cbType) {
        String salescode = SystemProperties.get("ro.csc.sales_code");
        if (this.mSc.equals(SC_BAOC)) {
            return this.mContext.getText(17041464);
        }
        if (this.mSc.equals(SC_BAOIC)) {
            return this.mContext.getText(17041465);
        }
        if (this.mSc.equals(SC_BAOICxH)) {
            return this.mContext.getText(17041466);
        }
        if (this.mSc.equals("35")) {
            return this.mContext.getText(17041467);
        }
        if (this.mSc.equals(SC_BAICr)) {
            return this.mContext.getText(17041468);
        }
        if (this.mSc.equals(SC_BA_ALL)) {
            if (salescode == null || (!"CHN".equals(salescode) && !"CHM".equals(salescode) && !"CHC".equals(salescode) && !"CHU".equals(salescode))) {
                return this.mContext.getText(17041469);
            }
            return this.mContext.getText(17039525);
        } else if (this.mSc.equals(SC_BA_MO)) {
            if (salescode == null || (!"CHN".equals(salescode) && !"CHM".equals(salescode) && !"CHC".equals(salescode) && !"CHU".equals(salescode))) {
                return this.mContext.getText(17041470);
            }
            return this.mContext.getText(17041464);
        } else if (!this.mSc.equals(SC_BA_MT)) {
            return " ";
        } else {
            if (salescode == null || (!"CHN".equals(salescode) && !"CHM".equals(salescode) && !"CHC".equals(salescode) && !"CHU".equals(salescode))) {
                return this.mContext.getText(17041471);
            }
            return this.mContext.getText(17041467);
        }
    }

    public CharSequence getUssdCode() {
        return this.ussdCode;
    }

    public String getDialString() {
        return this.dialString;
    }

    void sendEncodedUssd(byte[] ussdMessage, int length, int dcsCode) {
        this.mIsPendingUSSD = true;
        this.mPhone.mCi.sendEncodedUSSD(ussdMessage, length, dcsCode, obtainMessage(4, this));
    }

    private static boolean isCroatiaShortCode(String dialString) {
        String mcc = "000";
        String numeric = SystemProperties.get("gsm.operator.numeric");
        if (numeric == null || numeric.length() <= 4) {
            Rlog.e(LOG_TAG, "isCroatiaShortCode Returning false");
            return false;
        }
        mcc = numeric.substring(0, 3);
        Rlog.d(LOG_TAG, "isCroatiaShortCode mcc" + mcc);
        if (mcc.equals(MCC_CROATIA) && (dialString.equals("92") || dialString.equals("93") || dialString.equals("94") || dialString.equals("95") || dialString.equals("96"))) {
            Rlog.e(LOG_TAG, "isCroatiaShortCode Returning true");
            return true;
        }
        Rlog.d(LOG_TAG, "isCroatiaShortCode Returning false");
        return false;
    }

    private static boolean isSerbiaShortCode(String dialString) {
        String mcc = "000";
        String numeric = SystemProperties.get("gsm.operator.numeric");
        if (numeric == null || numeric.length() <= 4) {
            Rlog.e(LOG_TAG, "isSerbiaShortCode Returning false");
            return false;
        }
        mcc = numeric.substring(0, 3);
        Rlog.d(LOG_TAG, "isSerbiaShortCode mcc" + mcc);
        if (mcc.equals(MCC_SERBIA) && (dialString.equals("92") || dialString.equals("93") || dialString.equals("94") || dialString.equals("95") || dialString.equals("96"))) {
            Rlog.e(LOG_TAG, "isSerbiaShortCode Returning true");
            return true;
        }
        Rlog.d(LOG_TAG, "isSerbiaShortCode Returning false");
        return false;
    }

    public boolean IsUssdConnectToCall() {
        String scUssd2Call = CscFeature.getInstance().getString("CscFeature_RIL_UssdConnectToCall");
        Rlog.d(LOG_TAG, "IsUssdConnect2Call : (" + scUssd2Call + "), serviceCode = (" + this.mSc + ")");
        if (scUssd2Call == null || TextUtils.isEmpty(scUssd2Call) || this.mSc == null || TextUtils.isEmpty(this.mSc) || this.mDialingNumber == null || TextUtils.isEmpty(this.mDialingNumber) || !scUssd2Call.contains(this.mSc)) {
            return false;
        }
        return true;
    }

    void processSsData(Message msg, AsyncResult data) {
        this.isSsInfo = true;
        try {
            parseSsData(msg, data.result);
        } catch (ClassCastException ex) {
            Rlog.e(LOG_TAG, "Exception in parsing SS Data : " + ex);
        }
    }

    void parseSsData(Message msg, SsData ssData) {
        boolean cffEnabled = false;
        CommandException ex = CommandException.fromRilErrno(ssData.mResult);
        this.mSc = getScStringFromScType(ssData.mServiceType);
        this.mAction = getActionStringFromReqType(ssData.mRequestType);
        Rlog.d(LOG_TAG, "parseSsData sc = " + this.mSc + ", action = " + this.mAction + ", ex = " + ex + "RequestType = " + ssData.mRequestType);
        if (!(ex == null || "ILO".equals(SystemProperties.get("ro.csc.sales_code")) || "PCL".equals(SystemProperties.get("ro.csc.sales_code")) || "CEL".equals(SystemProperties.get("ro.csc.sales_code")) || "PTR".equals(SystemProperties.get("ro.csc.sales_code")) || "MIR".equals(SystemProperties.get("ro.csc.sales_code")))) {
            this.mPhone.mMmiInitBySTK = false;
        }
        switch (C01051.$SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[ssData.mRequestType.ordinal()]) {
            case 1:
            case 2:
            case 3:
            case 4:
                if (ssData.mResult == 0 && ssData.mServiceType.isTypeUnConditional()) {
                    if ((ssData.mRequestType == RequestType.SS_ACTIVATION || ssData.mRequestType == RequestType.SS_REGISTRATION) && isServiceClassVoiceorNone(ssData.mServiceClass)) {
                        cffEnabled = true;
                    }
                    IccRecords r = (IccRecords) this.mPhone.mIccRecords.get();
                    if (r != null) {
                        r.setVoiceCallForwardingFlag(1, cffEnabled, this.mDialingNumber);
                    } else {
                        Rlog.d(LOG_TAG, "setVoiceCallForwardingFlag aborted. sim records is null.");
                    }
                }
                onSetComplete(msg, new AsyncResult(null, ssData.mCfInfo, ex));
                return;
            case 5:
                if (ssData.mServiceType.isTypeClir()) {
                    onGetClirComplete(new AsyncResult(null, ssData.mSsInfo, ex));
                    return;
                } else if (ssData.mServiceType.isTypeCF()) {
                    onQueryCfComplete(new AsyncResult(null, ssData.mCfInfo, ex));
                    return;
                } else {
                    onQueryComplete(new AsyncResult(null, ssData.mSsInfo, ex));
                    return;
                }
            default:
                return;
        }
    }

    private String getScStringFromScType(ServiceType sType) {
        switch (C01051.$SwitchMap$com$android$internal$telephony$gsm$SsData$ServiceType[sType.ordinal()]) {
            case 1:
                return "21";
            case 2:
                return "67";
            case 3:
                return SC_CFNRy;
            case 4:
                return SC_CFNR;
            case 5:
                return SC_CF_All;
            case 6:
                return SC_CF_All_Conditional;
            case 7:
                return "30";
            case 8:
                return SC_CLIR;
            case 9:
                return "43";
            case 10:
                return SC_BAOC;
            case 11:
                return SC_BAOIC;
            case 12:
                return SC_BAOICxH;
            case 13:
                return "35";
            case 14:
                return SC_BAICr;
            case 15:
                return SC_BA_ALL;
            case 16:
                return SC_BA_MO;
            case 17:
                return SC_BA_MT;
            default:
                return "";
        }
    }

    private String getActionStringFromReqType(RequestType rType) {
        switch (C01051.$SwitchMap$com$android$internal$telephony$gsm$SsData$RequestType[rType.ordinal()]) {
            case 1:
                return "*";
            case 2:
                return ACTION_DEACTIVATE;
            case 3:
                return ACTION_REGISTER;
            case 4:
                return ACTION_ERASURE;
            case 5:
                return ACTION_INTERROGATE;
            default:
                return "";
        }
    }

    private boolean isServiceClassVoiceorNone(int serviceClass) {
        return (serviceClass & 1) != 0 || serviceClass == 0;
    }

    public boolean isSsInfo() {
        return this.isSsInfo;
    }
}
