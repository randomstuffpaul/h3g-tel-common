package com.android.internal.telephony.cdma;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CdmaMmiCode extends Handler implements MmiCode {
    static final String ACTION_REGISTER = "**";
    static final int EVENT_SET_COMPLETE = 1;
    static final String GLOBALDEV_CS = "+19085594899";
    static final String LOG_TAG = "CdmaMmiCode";
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
    static final String SC_GLOBALDEV_CLIR_INVK = "67";
    static final String SC_GLOBALDEV_CLIR_SUPP = "82";
    static final String SC_GLOBALDEV_CS = "611";
    static final String SC_GLOBALDEV_VM = "86";
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    static Pattern sPatternSuppServiceGlobalDev = Pattern.compile("((\\*)(\\d{2})(\\+{0,1})(\\d{0,}))");
    String mAction;
    Context mContext;
    String mDialingNumber;
    CharSequence mMessage;
    CDMAPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    State mState = State.PENDING;
    UiccCardApplication mUiccApplication;

    public static CdmaMmiCode newFromDialString(String dialString, CDMAPhone phone, UiccCardApplication app) {
        Matcher m = sPatternSuppService.matcher(dialString);
        if (!m.matches()) {
            return null;
        }
        CdmaMmiCode ret = new CdmaMmiCode(phone, app);
        ret.mPoundString = makeEmptyNull(m.group(1));
        ret.mAction = makeEmptyNull(m.group(2));
        ret.mSc = makeEmptyNull(m.group(3));
        ret.mSia = makeEmptyNull(m.group(5));
        ret.mSib = makeEmptyNull(m.group(7));
        ret.mSic = makeEmptyNull(m.group(9));
        ret.mPwd = makeEmptyNull(m.group(11));
        ret.mDialingNumber = makeEmptyNull(m.group(12));
        return ret;
    }

    static CdmaMmiCode newFromDialStringForMultimodeCdma(String dialString, CDMAPhone phone, UiccCardApplication app) {
        Matcher m = sPatternSuppServiceGlobalDev.matcher(dialString);
        if (m.matches()) {
            String DialCode = makeEmptyNull(m.group(3));
            String DialPrefix = m.group(4);
            String DialNumber = makeEmptyNull(m.group(5));
            if (dialString.substring(1).equals(SC_GLOBALDEV_CS)) {
                CdmaMmiCode ret = new CdmaMmiCode(phone, app);
                ret.mAction = makeEmptyNull(m.group(2));
                ret.mSc = SC_GLOBALDEV_CS;
                ret.mDialingNumber = convertCustomerServiceNumberForCdmaRoaming(phone.getContext());
                return ret;
            }
        }
        return null;
    }

    static String convertCustomerServiceNumberForCdmaRoaming(Context context) {
        return PhoneNumberUtils.assistedDialFromDialPad(GLOBALDEV_CS.replace("+", PhoneNumberUtils.getCurrentOtaCountryIdd(context)), context);
    }

    boolean isGlobalDevMmi() {
        return this.mSc != null && (this.mSc.equals(SC_GLOBALDEV_VM) || this.mSc.equals(SC_GLOBALDEV_CS));
    }

    private static String makeEmptyNull(String s) {
        if (s == null || s.length() != 0) {
            return s;
        }
        return null;
    }

    CdmaMmiCode(CDMAPhone phone, UiccCardApplication app) {
        super(phone.getHandler().getLooper());
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mUiccApplication = app;
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
            this.mPhone.onMMIDone(this);
        }
    }

    public boolean isCancelable() {
        return false;
    }

    boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    public boolean isUssdRequest() {
        Rlog.w(LOG_TAG, "isUssdRequest is not implemented in CdmaMmiCode");
        return false;
    }

    void processCode() {
        try {
            if (isPinPukCommand()) {
                String oldPinOrPuk = this.mSia;
                String newPinOrPuk = this.mSib;
                int pinLen = newPinOrPuk.length();
                if (!isRegister()) {
                    throw new RuntimeException("Ivalid register/action=" + this.mAction);
                } else if (!newPinOrPuk.equals(this.mSic)) {
                    handlePasswordError(17039511);
                } else if (pinLen < 4 || pinLen > 8) {
                    handlePasswordError(17039512);
                } else if (this.mSc.equals(SC_PIN) && this.mUiccApplication != null && this.mUiccApplication.getState() == AppState.APPSTATE_PUK) {
                    handlePasswordError(17039514);
                } else if (this.mUiccApplication != null) {
                    Rlog.d(LOG_TAG, "process mmi service code using UiccApp sc=" + this.mSc);
                    if (this.mSc.equals(SC_PIN)) {
                        this.mUiccApplication.changeIccLockPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PIN2)) {
                        this.mUiccApplication.changeIccFdnPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PUK)) {
                        this.mUiccApplication.supplyPuk(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                    } else if (this.mSc.equals(SC_PUK2)) {
                        this.mUiccApplication.supplyPuk2(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                    } else {
                        throw new RuntimeException("Unsupported service code=" + this.mSc);
                    }
                } else {
                    throw new RuntimeException("No application mUiccApplicaiton is null");
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

    public void handleMessage(Message msg) {
        if (msg.what == 1) {
            onSetComplete(msg, (AsyncResult) msg.obj);
        } else {
            Rlog.e(LOG_TAG, "Unexpected reply");
        }
    }

    private CharSequence getScString() {
        if (this.mSc == null || !isPinPukCommand()) {
            return "";
        }
        return this.mContext.getText(17039527);
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
                        if (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) {
                            sb.append(this.mContext.getText(17039510));
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
                    } else {
                        sb.append(this.mContext.getText(17039507));
                    }
                } else if (err == Error.SIM_PUK2) {
                    sb.append(this.mContext.getText(17039509));
                    sb.append("\n");
                    sb.append(this.mContext.getText(17039515));
                } else if (err != Error.REQUEST_NOT_SUPPORTED) {
                    sb.append(this.mContext.getText(17039500));
                } else if (this.mSc.equals(SC_PIN)) {
                    sb.append(this.mContext.getText(17039516));
                }
            } else {
                sb.append(this.mContext.getText(17039500));
            }
        } else if (isRegister()) {
            this.mState = State.COMPLETE;
            sb.append(this.mContext.getText(17039505));
        } else {
            this.mState = State.FAILED;
            sb.append(this.mContext.getText(17039500));
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    public CharSequence getUssdCode() {
        return null;
    }

    public String getDialString() {
        return null;
    }
}
