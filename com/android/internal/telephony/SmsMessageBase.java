package com.android.internal.telephony;

import android.util.secutil.Log;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.util.Arrays;
import java.util.StringTokenizer;

public abstract class SmsMessageBase {
    private static final int DELIMITER_ETX = 3;
    private static final int DELIMITER_GS = 29;
    private static final String LOG_TAG = "SMS";
    private static final char[] connectText = new char[]{'연', '결', ' ', '하', '시', '겠', '습', '니', '까', '?'};
    private static final char[] dataText = new char[]{'[', 'L', 'G', ' ', 'U', '+', ' ', '무', '선', '인', '터', '넷', ']'};
    private static final char[] lguText = new char[]{'[', 'L', 'G', ' ', 'U', '+', ' ', '안', '내', ']'};
    private static final char[] pagingText = new char[]{'[', '호', '출', '메', '시', '지', ']'};
    private static final char[] thirdPartyText = new char[]{'[', '외', '부', '사', '업', '자', ' ', '연', '결', ']'};
    private static final char[] voiceMailText = new char[]{'새', '로', '운', ' ', '음', '성', '메', '일', '이', ' ', '도', '착', '했', '습', '니', '다', '.', '통', '화', '키', '를', ' ', '누', '르', '면', ' ', '자', '동', '연', '결', '됩', '니', '다', '.'};
    private static final char[] webText = new char[]{'[', '웹', '서', '핑', ' ', '연', '결', ']'};
    protected byte[] bearerData = null;
    protected String callbackNumber;
    protected String linkUrl = null;
    protected int mBodyOffset;
    protected String mEmailBody;
    protected String mEmailFrom;
    protected int mIndexOnIcc = -1;
    protected boolean mIsEmail;
    protected boolean mIsMwi;
    protected boolean mIsfourBytesUnicode;
    protected String mMessageBody;
    public int mMessageRef;
    protected int mMti;
    protected boolean mMwiDontStore;
    protected boolean mMwiSense;
    protected SmsAddress mOriginatingAddress;
    protected byte[] mPdu;
    protected String mPseudoSubject;
    protected String mScAddress;
    protected long mScTimeMillis;
    protected String mSharedAppID = null;
    protected String mSharedCmd = null;
    protected String mSharedPayLoad = null;
    protected int mStatusOnIcc = -1;
    protected int mTeleserviceId;
    protected byte[] mUserData;
    protected SmsHeader mUserDataHeader;
    protected byte[] mlastByte;
    protected SmsAddress recipientAddress;
    protected SmsAddress replyAddress;
    protected int sessionId;
    protected int sessionSeq;
    protected int sessionSeqEos;

    public static abstract class DeliverPduBase {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "DeliverPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }
    }

    public static abstract class SubmitPduBase {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }
    }

    public abstract MessageClass getMessageClass();

    public abstract int getMessageIdentifier();

    public abstract int getMessagePriority();

    public abstract int getProtocolIdentifier();

    public abstract int getStatus();

    public abstract boolean isCphsMwiMessage();

    public abstract boolean isMWIClearMessage();

    public abstract boolean isMWISetMessage();

    public abstract boolean isMwiDontStore();

    public abstract boolean isReplace();

    public abstract boolean isReplyPathPresent();

    public abstract boolean isStatusReportMessage();

    public String getServiceCenterAddress() {
        return this.mScAddress;
    }

    public String getOriginatingAddress() {
        if (this.mOriginatingAddress == null) {
            return null;
        }
        return this.mOriginatingAddress.getAddressString();
    }

    public String getDisplayOriginatingAddress() {
        if (this.mIsEmail) {
            return this.mEmailFrom;
        }
        return getOriginatingAddress();
    }

    public String getMessageBody() {
        return this.mMessageBody;
    }

    public void replaceMessageBody(String messasgeBody) {
        this.mMessageBody = messasgeBody;
    }

    public boolean getIsFourBytesUnicode() {
        return this.mIsfourBytesUnicode;
    }

    public int getBodyOffset() {
        return this.mBodyOffset;
    }

    public byte[] getLastByte() {
        return this.mlastByte;
    }

    public String getDisplayMessageBody() {
        if (this.mIsEmail) {
            return this.mEmailBody;
        }
        return getMessageBody();
    }

    public String getPseudoSubject() {
        return this.mPseudoSubject == null ? "" : this.mPseudoSubject;
    }

    public long getTimestampMillis() {
        return this.mScTimeMillis;
    }

    public boolean isEmail() {
        return this.mIsEmail;
    }

    public String getEmailBody() {
        return this.mEmailBody;
    }

    public String getEmailFrom() {
        return this.mEmailFrom;
    }

    public byte[] getUserData() {
        return this.mUserData;
    }

    public SmsHeader getUserDataHeader() {
        return this.mUserDataHeader;
    }

    public byte[] getPdu() {
        return this.mPdu;
    }

    public int getStatusOnIcc() {
        return this.mStatusOnIcc;
    }

    public int getIndexOnIcc() {
        return this.mIndexOnIcc;
    }

    protected void parseMessageBody() {
        if (this.mOriginatingAddress != null && this.mOriginatingAddress.couldBeEmailGateway()) {
            extractEmailAddressFromMessageBody();
        }
    }

    protected void extractEmailAddressFromMessageBody() {
        String[] parts = this.mMessageBody.split("( /)|( )", 2);
        if (parts.length >= 2) {
            this.mEmailFrom = parts[0];
            int len = this.mEmailFrom.length();
            int firstAt = this.mEmailFrom.indexOf(64);
            int lastAt = this.mEmailFrom.lastIndexOf(64);
            int firstDot = this.mEmailFrom.indexOf(46, lastAt + 1);
            int lastDot = this.mEmailFrom.lastIndexOf(46);
            if (firstAt > 0 && firstAt == lastAt && lastAt + 1 < firstDot && firstDot <= lastDot && lastDot < len - 1) {
                this.mEmailBody = parts[1];
                this.mIsEmail = true;
            }
        }
    }

    public String getReplyAddress() {
        if (this.replyAddress == null) {
            return null;
        }
        return this.replyAddress.getAddressString();
    }

    public String getOriginalOriginatingAddress() {
        if (this.mIsEmail) {
            return this.mEmailFrom;
        }
        return getOriginatingAddress();
    }

    public String getlinkUrl() {
        return this.linkUrl;
    }

    public String getSharedAppId() {
        return this.mSharedAppID;
    }

    public String getSharedCmd() {
        return this.mSharedCmd;
    }

    public String getSharedPayLoad() {
        return this.mSharedPayLoad;
    }

    public int getTeleserviceId() {
        return this.mTeleserviceId;
    }

    public String getCallbackNumber() {
        return this.callbackNumber;
    }

    public byte[] getBearerData() {
        return this.bearerData;
    }

    public String getDisplayDestinationAddress() {
        if (this.recipientAddress == null) {
            return null;
        }
        return this.recipientAddress.getAddressString();
    }

    public int getMessageType() {
        return this.mMti;
    }

    public int getCDMAMessageType() {
        return 0;
    }

    public int getSessionId() {
        return this.sessionId;
    }

    public int getSessionSeq() {
        return this.sessionSeq;
    }

    public int getSessionSeqEos() {
        return this.sessionSeqEos;
    }

    protected void parseSpecificTid(int tid) {
        switch (tid) {
            case 4097:
                if (this.mMessageBody == null || this.mMessageBody.length() == 0) {
                    this.mMessageBody = String.valueOf(pagingText);
                    return;
                } else {
                    this.mMessageBody = String.valueOf(pagingText) + "\n" + this.mMessageBody;
                    return;
                }
            case 4099:
            case SmsEnvelope.TELESERVICE_MWI /*262144*/:
                this.mMessageBody = String.valueOf(voiceMailText);
                return;
            case SmsEnvelope.TELESERVICE_LGT_ETC_SHARE_49162 /*49162*/:
                parseLGTSharingNoti();
                return;
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49166 /*49166*/:
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49167 /*49167*/:
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49168 /*49168*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_THIRD_49763 /*49763*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_LGT_49765 /*49765*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_CP_49767 /*49767*/:
                parseLGTWebNWapNoti(tid);
                return;
            default:
                return;
        }
    }

    private void parseLGTWebNWapNoti(int tid) {
        String destBody = "";
        int gs = this.mMessageBody.indexOf(29);
        if (gs != -1) {
            destBody = this.mMessageBody.substring(0, gs);
            int etx = this.mMessageBody.indexOf(3);
            if (etx == -1) {
                etx = this.mMessageBody.length();
            }
            if (etx == -1 || gs > etx) {
                Log.secE(LOG_TAG, "parseLGTWapUrlNoti parsing error...  DELIMITER_ETX");
            } else {
                this.linkUrl = this.mMessageBody.substring(gs, etx).trim();
            }
        } else {
            destBody = this.mMessageBody;
            Log.secE(LOG_TAG, "parseLGTWapUrlNoti parsing error...  DELIMITER_GS");
        }
        switch (tid) {
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49166 /*49166*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_THIRD_49763 /*49763*/:
                this.mMessageBody = String.valueOf(thirdPartyText) + "\n" + destBody + "\n" + String.valueOf(connectText);
                return;
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49167 /*49167*/:
                this.mMessageBody = String.valueOf(dataText) + "\n" + destBody;
                return;
            case SmsEnvelope.TELESERVICE_LGT_WAP_URL_NOTI_49168 /*49168*/:
                this.mMessageBody = String.valueOf(lguText) + "\n" + destBody;
                return;
            case SmsEnvelope.TELESERVICE_LGT_WEB_LGT_49765 /*49765*/:
            case SmsEnvelope.TELESERVICE_LGT_WEB_CP_49767 /*49767*/:
                this.mMessageBody = String.valueOf(webText) + "\n" + destBody + "\n" + String.valueOf(connectText);
                return;
            default:
                return;
        }
    }

    private void parseLGTSharingNoti() {
        String destBody = "";
        StringTokenizer tokenizer = new StringTokenizer(this.mMessageBody, String.valueOf('\u001d'));
        int i = 0;
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (i == 0) {
                destBody = token;
            } else if (i == 1) {
                this.mSharedAppID = token;
            } else if (i == 2) {
                this.mSharedCmd = token;
            } else if (i == 3) {
                this.mSharedPayLoad = token;
                int index = this.mSharedPayLoad.lastIndexOf(String.valueOf('\u0003'));
                if (index != -1) {
                    this.mSharedPayLoad = this.mSharedPayLoad.substring(0, index);
                }
            }
            i++;
        }
        this.mMessageBody = destBody;
    }
}
