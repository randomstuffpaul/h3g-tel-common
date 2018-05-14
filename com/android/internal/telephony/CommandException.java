package com.android.internal.telephony;

import android.telephony.Rlog;
import com.android.internal.telephony.cdma.CallFailCause;

public class CommandException extends RuntimeException {
    private Error mError;

    public enum Error {
        INVALID_RESPONSE,
        RADIO_NOT_AVAILABLE,
        GENERIC_FAILURE,
        PASSWORD_INCORRECT,
        SIM_PIN2,
        SIM_PUK2,
        REQUEST_NOT_SUPPORTED,
        OP_NOT_ALLOWED_DURING_VOICE_CALL,
        OP_NOT_ALLOWED_BEFORE_REG_NW,
        SMS_FAIL_RETRY,
        SIM_ABSENT,
        SUBSCRIPTION_NOT_AVAILABLE,
        MODE_NOT_SUPPORTED,
        FDN_CHECK_FAILURE,
        SMS_DSAC_FAILURE,
        NOT_SUBCRIBED_USER,
        ILLEGAL_SIM_OR_ME,
        OPER_NOT_ALLOWED,
        MEMORY_ERROR,
        INVALID_INDEX,
        TEXT_STR_TOO_LONG,
        DIAL_STR_TOO_LONG,
        INVALID_CHARACTERS_IN_TEXT_STR,
        INVALID_CHARACTERS_IN_DIAL_STR,
        MISSING_RESOURCE,
        NO_SUCH_ELEMENT,
        SUBSCRIPTION_NOT_SUPPORTED,
        MAC_ADDRESS_FAIL,
        SMS_FAIL_DAN_RETRY
    }

    public CommandException(Error e) {
        super(e.toString());
        this.mError = e;
    }

    public static CommandException fromRilErrno(int ril_errno) {
        switch (ril_errno) {
            case -1:
                return new CommandException(Error.INVALID_RESPONSE);
            case 0:
                return null;
            case 1:
                return new CommandException(Error.RADIO_NOT_AVAILABLE);
            case 2:
                return new CommandException(Error.GENERIC_FAILURE);
            case 3:
                return new CommandException(Error.PASSWORD_INCORRECT);
            case 4:
                return new CommandException(Error.SIM_PIN2);
            case 5:
                return new CommandException(Error.SIM_PUK2);
            case 6:
                return new CommandException(Error.REQUEST_NOT_SUPPORTED);
            case 8:
                return new CommandException(Error.OP_NOT_ALLOWED_DURING_VOICE_CALL);
            case 9:
                return new CommandException(Error.OP_NOT_ALLOWED_BEFORE_REG_NW);
            case 10:
                return new CommandException(Error.SMS_FAIL_RETRY);
            case 11:
                return new CommandException(Error.SIM_ABSENT);
            case 12:
                return new CommandException(Error.SUBSCRIPTION_NOT_AVAILABLE);
            case 13:
                return new CommandException(Error.MODE_NOT_SUPPORTED);
            case 14:
                return new CommandException(Error.FDN_CHECK_FAILURE);
            case 15:
                return new CommandException(Error.ILLEGAL_SIM_OR_ME);
            case 16:
                return new CommandException(Error.MISSING_RESOURCE);
            case 17:
                return new CommandException(Error.NO_SUCH_ELEMENT);
            case 26:
                return new CommandException(Error.SUBSCRIPTION_NOT_SUPPORTED);
            case 32:
                return new CommandException(Error.MAC_ADDRESS_FAIL);
            case WspTypeDecoder.WSP_HEADER_CACHE_CONTROL2 /*61*/:
                return new CommandException(Error.SMS_DSAC_FAILURE);
            case 62:
                return new CommandException(Error.NOT_SUBCRIBED_USER);
            case 63:
                return new CommandException(Error.SMS_FAIL_DAN_RETRY);
            case 1000:
                return new CommandException(Error.OPER_NOT_ALLOWED);
            case CallFailCause.CDMA_DROP /*1001*/:
                return new CommandException(Error.MEMORY_ERROR);
            case CallFailCause.CDMA_INTERCEPT /*1002*/:
                return new CommandException(Error.INVALID_INDEX);
            case CallFailCause.CDMA_REORDER /*1003*/:
                return new CommandException(Error.TEXT_STR_TOO_LONG);
            case CallFailCause.CDMA_SO_REJECT /*1004*/:
                return new CommandException(Error.DIAL_STR_TOO_LONG);
            case CallFailCause.CDMA_RETRY_ORDER /*1005*/:
                return new CommandException(Error.INVALID_CHARACTERS_IN_TEXT_STR);
            case CallFailCause.CDMA_ACCESS_FAILURE /*1006*/:
                return new CommandException(Error.INVALID_CHARACTERS_IN_DIAL_STR);
            default:
                Rlog.e("GSM", "Unrecognized RIL errno " + ril_errno);
                return new CommandException(Error.INVALID_RESPONSE);
        }
    }

    public Error getCommandError() {
        return this.mError;
    }

    public int toApplicationError() {
        return -1000 - this.mError.ordinal();
    }
}
