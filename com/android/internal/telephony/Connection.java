package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telecom.Connection.VideoProvider;
import android.telephony.Rlog;
import com.android.internal.telephony.Call.State;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class Connection {
    public static final int AUDIO_QUALITY_HIGH_DEFINITION = 2;
    public static final int AUDIO_QUALITY_STANDARD = 1;
    private static String LOG_TAG = "Connection";
    protected CallDetails callDetails = null;
    private CallModify callModifyRequest = null;
    int id = -1;
    protected String mAddress;
    private int mAudioQuality;
    protected String mCnapName;
    protected int mCnapNamePresentation = 1;
    protected long mConnectTime;
    protected long mConnectTimeReal;
    protected String mConvertedNumber;
    protected long mCreateTime;
    protected String mDialString;
    protected long mDuration;
    protected long mHoldingStartTime;
    protected boolean mIsIncoming;
    public Set<Listener> mListeners = new CopyOnWriteArraySet();
    private boolean mLocalVideoCapable;
    protected boolean mNumberConverted = false;
    protected int mNumberPresentation = 1;
    protected Connection mOrigConnection;
    protected String mOriginalAddress;
    private List<PostDialListener> mPostDialListeners = new ArrayList();
    private boolean mRemoteVideoCapable;
    Object mUserData;
    private VideoProvider mVideoProvider;
    private int mVideoState;
    protected CallDetails oldCallDetails = null;
    protected int sipError = 0;

    public interface Listener {
        void onAudioQualityChanged(int i);

        void onLocalVideoCapabilityChanged(boolean z);

        void onRemoteVideoCapabilityChanged(boolean z);

        void onVideoProviderChanged(VideoProvider videoProvider);

        void onVideoStateChanged(int i);
    }

    public static abstract class ListenerBase implements Listener {
        public void onVideoStateChanged(int videoState) {
        }

        public void onLocalVideoCapabilityChanged(boolean capable) {
        }

        public void onRemoteVideoCapabilityChanged(boolean capable) {
        }

        public void onVideoProviderChanged(VideoProvider videoProvider) {
        }

        public void onAudioQualityChanged(int audioQuality) {
        }
    }

    public interface PostDialListener {
        void onPostDialWait();
    }

    public enum PostDialState {
        NOT_STARTED,
        STARTED,
        WAIT,
        WILD,
        COMPLETE,
        CANCELLED,
        PAUSE
    }

    public abstract void cancelPostDial();

    public abstract Call getCall();

    public abstract int getDisconnectCause();

    public abstract long getDisconnectTime();

    public abstract long getHoldDurationMillis();

    public abstract int getNumberPresentation();

    public abstract PostDialState getPostDialState();

    public abstract int getPreciseDisconnectCause();

    public abstract String getRemainingPostDialString();

    public abstract UUSInfo getUUSInfo();

    public abstract void hangup() throws CallStateException;

    public abstract boolean isMultiparty();

    public abstract void proceedAfterWaitChar();

    public abstract void proceedAfterWildChar(String str);

    public abstract void separate() throws CallStateException;

    public String getAddress() {
        return this.mAddress;
    }

    public String getCnapName() {
        return this.mCnapName;
    }

    public String getOrigDialString() {
        return null;
    }

    public int getCnapNamePresentation() {
        return this.mCnapNamePresentation;
    }

    public long getCreateTime() {
        return this.mCreateTime;
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public long getConnectTimeReal() {
        return this.mConnectTimeReal;
    }

    public long getDurationMillis() {
        if (this.mConnectTimeReal == 0) {
            return 0;
        }
        if (this.mDuration == 0) {
            return SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        return this.mDuration;
    }

    public long getHoldingStartTime() {
        return this.mHoldingStartTime;
    }

    public boolean isIncoming() {
        return this.mIsIncoming;
    }

    public State getState() {
        Call c = getCall();
        if (c == null) {
            return State.IDLE;
        }
        return c.getState();
    }

    public boolean isAlive() {
        return getState().isAlive();
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public Object getUserData() {
        return this.mUserData;
    }

    public void setUserData(Object userdata) {
        this.mUserData = userdata;
    }

    public void clearUserData() {
        this.mUserData = null;
    }

    public final void addPostDialListener(PostDialListener listener) {
        if (!this.mPostDialListeners.contains(listener)) {
            this.mPostDialListeners.add(listener);
        }
    }

    protected final void clearPostDialListeners() {
        this.mPostDialListeners.clear();
    }

    protected final void notifyPostDialListeners() {
        if (getPostDialState() == PostDialState.WAIT) {
            Iterator i$ = new ArrayList(this.mPostDialListeners).iterator();
            while (i$.hasNext()) {
                ((PostDialListener) i$.next()).onPostDialWait();
            }
        }
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public void migrateFrom(Connection c) {
        if (c != null) {
            this.mListeners = c.mListeners;
            this.mAddress = c.getAddress();
            this.mNumberPresentation = c.getNumberPresentation();
            this.mDialString = c.getOrigDialString();
            this.mCnapName = c.getCnapName();
            this.mCnapNamePresentation = c.getCnapNamePresentation();
            this.mIsIncoming = c.isIncoming();
            this.mCreateTime = c.getCreateTime();
            this.mConnectTime = c.getConnectTime();
            this.mConnectTimeReal = c.getConnectTimeReal();
            this.mHoldingStartTime = c.getHoldingStartTime();
            this.mOrigConnection = c.getOrigConnection();
        }
    }

    public final void addListener(Listener listener) {
        this.mListeners.add(listener);
    }

    public final void removeListener(Listener listener) {
        this.mListeners.remove(listener);
    }

    public int getVideoState() {
        return this.mVideoState;
    }

    public boolean isLocalVideoCapable() {
        return this.mLocalVideoCapable;
    }

    public boolean isRemoteVideoCapable() {
        return this.mRemoteVideoCapable;
    }

    public VideoProvider getVideoProvider() {
        return this.mVideoProvider;
    }

    public int getAudioQuality() {
        return this.mAudioQuality;
    }

    public void setVideoState(int videoState) {
        this.mVideoState = videoState;
        for (Listener l : this.mListeners) {
            l.onVideoStateChanged(this.mVideoState);
        }
    }

    public void setLocalVideoCapable(boolean capable) {
        this.mLocalVideoCapable = capable;
        for (Listener l : this.mListeners) {
            l.onLocalVideoCapabilityChanged(this.mLocalVideoCapable);
        }
    }

    public void setRemoteVideoCapable(boolean capable) {
        this.mRemoteVideoCapable = capable;
        for (Listener l : this.mListeners) {
            l.onRemoteVideoCapabilityChanged(this.mRemoteVideoCapable);
        }
    }

    public void setAudioQuality(int audioQuality) {
        this.mAudioQuality = audioQuality;
        for (Listener l : this.mListeners) {
            l.onAudioQualityChanged(this.mAudioQuality);
        }
    }

    public void setVideoProvider(VideoProvider videoProvider) {
        this.mVideoProvider = videoProvider;
        for (Listener l : this.mListeners) {
            l.onVideoProviderChanged(this.mVideoProvider);
        }
    }

    public void setConverted(String oriNumber) {
        this.mNumberConverted = true;
        this.mConvertedNumber = this.mAddress;
        this.mAddress = oriNumber;
        this.mDialString = oriNumber;
    }

    public String toString() {
        StringBuilder str = new StringBuilder(128);
        if (Rlog.isLoggable(LOG_TAG, 3)) {
            str.append("addr: " + getAddress()).append(" pres.: " + getNumberPresentation()).append(" dial: " + getOrigDialString()).append(" postdial: " + getRemainingPostDialString()).append(" cnap name: " + getCnapName()).append("(" + getCnapNamePresentation() + ")");
        }
        str.append(" incoming: " + isIncoming()).append(" state: " + getState()).append(" post dial state: " + getPostDialState());
        str.append(" isCdmaCwActive: " + isCdmaCwActive()).append(" isCdmaCwHolding: " + isCdmaCwHolding());
        return str.toString();
    }

    public String getCdnipNumber() {
        return null;
    }

    public int getCWToneSignal() {
        return 0;
    }

    public String getOriginalAddress() {
        return this.mOriginalAddress;
    }

    public void setOriginalAddress(String originalAddress) {
        this.mOriginalAddress = originalAddress;
    }

    public boolean isCdmaCwActive() {
        return false;
    }

    public boolean isCdmaCwHolding() {
        return false;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int value) {
        this.id = value;
    }

    public CallDetails getCallDetails() {
        return this.callDetails;
    }

    public boolean setCallDetails(CallDetails details) {
        if (this.callDetails == details) {
            return false;
        }
        boolean changed;
        if (this.callDetails != null) {
            changed = this.callDetails.isChanged(details);
        } else {
            changed = details != null;
        }
        this.oldCallDetails = this.callDetails;
        this.callDetails = details;
        return changed;
    }

    public CallDetails getOldCallDetails() {
        return this.oldCallDetails;
    }

    public void resetOldCallDetails() {
        this.oldCallDetails = null;
    }

    public String getRadioTech() {
        if (this.callDetails != null) {
            return this.callDetails.getExtraValue("radiotech");
        }
        return null;
    }

    protected int getIndex() {
        return -1;
    }

    protected CallTracker getOwner() {
        return null;
    }

    public int getConnectionType() {
        if (this.callDetails != null) {
            return this.callDetails.call_type;
        }
        return -1;
    }

    private boolean validateCanModifyConnectionType(Message msg, int newCallType) {
        boolean isCb;
        boolean isMP;
        boolean ret;
        Call call = getCall();
        if (call == null || call.getCallDetails() == null || call.getCallDetails().call_domain != 2) {
            isCb = false;
        } else {
            isCb = true;
        }
        if (call == null || !call.isMultiparty()) {
            isMP = false;
        } else {
            isMP = true;
        }
        if (!isCb || isMP || getIndex() < 0) {
            ret = false;
        } else {
            ret = true;
        }
        if (!(ret || msg == null)) {
            AsyncResult ar;
            String s = "";
            if (!isCb) {
                s = s + "Call is not CallBase. ";
            }
            if (isMP) {
                s = s + "Call is Multiparty. ";
            }
            if (getIndex() < 0) {
                s = s + "Index is not yet assigned. ";
            }
            if (newCallType != getCallDetails().call_type) {
                ar = AsyncResult.forMessage(msg, null, null);
            } else {
                ar = AsyncResult.forMessage(msg, null, new Exception("Unable to change: " + s));
            }
            msg.obj = ar;
            msg.sendToTarget();
        }
        return ret;
    }

    public void changeConnectionType(Message msg, int newCallType, Map<String, String> newExtras) throws CallStateException {
        if (validateCanModifyConnectionType(msg, newCallType)) {
            getOwner().modifyCallInitiate(msg, new CallModify(new CallDetails(newCallType, getCallDetails().call_domain, CallDetails.getExtrasFromMap(newExtras)), getIndex() + 1));
        }
    }

    public boolean onReceivedModifyCall(CallModify callModify) {
        Rlog.d(LOG_TAG, "onReceivedCallModify(" + callModify + ")");
        boolean ret = validateCanModifyConnectionType(null, callModify.call_details.call_type);
        this.callModifyRequest = callModify;
        Rlog.d(LOG_TAG, "onReceivedCallModify() " + ret);
        return ret;
    }

    public int getProposedConnectionType() throws CallStateException {
        int ret = getConnectionType();
        if (this.callModifyRequest == null) {
            return ret;
        }
        if (this.callModifyRequest.call_details != null) {
            return this.callModifyRequest.call_details.call_type;
        }
        Rlog.d(LOG_TAG, "Received callModifyRequest without call details");
        return ret;
    }

    public void acceptConnectionTypeChange(Map<String, String> newExtras) throws CallStateException {
        Rlog.d(LOG_TAG, "Confirming call type change request: " + this.callModifyRequest);
        if (this.callModifyRequest != null) {
            this.callModifyRequest.call_details.setExtrasFromMap(newExtras);
            getOwner().modifyCallConfirm(null, this.callModifyRequest);
            this.callModifyRequest = null;
        }
    }

    public void rejectConnectionTypeChange() throws CallStateException {
        String salesCode = SystemProperties.get("ro.csc.sales_code", "none");
        if (this.callModifyRequest != null) {
            CallModify callModify = new CallModify();
            callModify.call_index = getIndex() + 1;
            callModify.call_details = new CallDetails(this.callDetails);
            if ("ATT".equals(salesCode) || "TMB".equals(salesCode)) {
                callModify.call_details.call_type = 10;
            }
            Rlog.d(LOG_TAG, "Rejecting Change request: " + this.callModifyRequest + " keep as " + callModify);
            if (getOwner() != null) {
                getOwner().modifyCallConfirm(null, callModify);
            }
            this.callModifyRequest = null;
        }
    }

    public int getSipErrorCode() {
        return this.sipError;
    }
}
