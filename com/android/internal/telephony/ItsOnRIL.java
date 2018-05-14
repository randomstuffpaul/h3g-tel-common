package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SmsMessage;
import android.util.Log;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams;
import com.itsoninc.android.ItsOnPhoneClient;
import com.itsoninc.android.ItsOnPhoneClientFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ItsOnRIL extends RIL {
    static final boolean DEBUG = false;
    protected static final int EVENT_POLL_CALLS_RESULT = 1;
    static final String LOG_TAG = "ItsOnRil";
    public static final int RIL_REQUEST_IMS_SEND_SMS = 99999;
    private Handler mHandler = new ItsOnRilHandler();
    ItsOnPhoneClient mIOPhoneClient;

    class ItsOnRilHandler extends Handler {
        ItsOnRilHandler() {
        }

        public void handleMessage(Message msg) {
            AsyncResult ar = msg.obj;
            switch (msg.what) {
                case 1:
                    for (DriverCall c : ar.result) {
                        if (c.isVoice && ((c.state == State.INCOMING || c.state == State.WAITING) && !ItsOnRIL.this.mIOPhoneClient.authorizeIncomingVoice(c.number))) {
                            ItsOnRIL.this.hangupWaitingOrBackground(obtainMessage());
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    protected void riljLog(String msg) {
    }

    public ItsOnRIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
        riljLog("Creating ril " + preferredNetworkType + " " + cdmaSubscription + " " + instanceId);
        ItsOnPhoneClientFactory.configure(this);
        this.mIOPhoneClient = ItsOnPhoneClientFactory.get(context);
    }

    private static String decodeDtmfSmsAddress(byte[] rawData, int numFields) {
        if (rawData == null) {
            return null;
        }
        StringBuffer strBuf = new StringBuffer(numFields);
        for (int i = 0; i < numFields; i++) {
            int val = rawData[i] & 15;
            if (val >= 1 && val <= 9) {
                strBuf.append(Integer.toString(val, 10));
            } else if (val == 10) {
                strBuf.append('0');
            } else if (val == 11) {
                strBuf.append('*');
            } else if (val != 12) {
                return null;
            } else {
                strBuf.append('#');
            }
        }
        return strBuf.toString();
    }

    private byte[] extractCdmaPdu(Parcel p) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            int i;
            dos.writeInt(p.readInt());
            dos.writeInt(p.readByte());
            dos.writeInt(p.readInt());
            dos.write(p.readInt());
            dos.write(p.readInt());
            dos.write(p.readInt());
            dos.write(p.readInt());
            int address_nbr_of_digits = p.readByte();
            dos.write(address_nbr_of_digits);
            for (i = 0; i < address_nbr_of_digits; i++) {
                dos.writeByte(p.readByte());
            }
            dos.write(p.readInt());
            dos.write(p.readByte());
            int subaddr_nbr_of_digits = p.readByte();
            dos.write(subaddr_nbr_of_digits);
            for (i = 0; i < subaddr_nbr_of_digits; i++) {
                dos.writeByte(p.readByte());
            }
            int bearerDataLength = p.readInt();
            dos.write(bearerDataLength);
            for (i = 0; i < bearerDataLength; i++) {
                dos.writeByte(p.readByte());
            }
        } catch (IOException e) {
            riljLog("Failed to convert SMS PDU");
        }
        return baos.toByteArray();
    }

    private RILRequest findFromList(int serial) {
        RILRequest rILRequest;
        synchronized (this.mRequestList) {
            rILRequest = (RILRequest) this.mRequestList.get(serial);
        }
        return rILRequest;
    }

    protected void send(RILRequest rr) {
        switch (rr.mRequest) {
            case 10:
                boolean authorized;
                if (this.mPhoneType == 2) {
                    authorized = true;
                } else {
                    rr.mParcel.setDataPosition(8);
                    authorized = this.mIOPhoneClient.authorizeOutgoingVoice(rr.mParcel.readString());
                }
                if (!authorized) {
                    riljLog("Could not authorize call");
                    AsyncResult.forMessage(rr.mResult, null, null);
                    rr.mResult.sendToTarget();
                    rr.release();
                    return;
                }
                break;
            case 12:
                this.mIOPhoneClient.incomingCallReject();
                break;
            case 13:
                this.mIOPhoneClient.incomingCallReject();
                break;
            case 25:
            case 26:
                rr.mParcel.setDataPosition(8);
                rr.mParcel.readInt();
                rr.mParcel.readString();
                if (!this.mIOPhoneClient.authorizeOutgoingSMS(IccUtils.hexStringToBytes(rr.mParcel.readString()), rr.mSerial)) {
                    rr.onError(2, null);
                    rr.release();
                    return;
                }
                break;
            case 40:
                if (this.mPhoneType == 2) {
                    this.mIOPhoneClient.acceptCall();
                    break;
                }
                break;
            case 84:
                rr.mParcel.setDataPosition(8);
                if (!this.mIOPhoneClient.flash(rr.mParcel.readString())) {
                    rr.onError(2, null);
                    rr.release();
                    return;
                }
                break;
            case 87:
                rr.mParcel.setDataPosition(8);
                if (!this.mIOPhoneClient.authorizeOutgoingSMS(extractCdmaPdu(rr.mParcel), rr.mSerial)) {
                    rr.onError(2, null);
                    rr.release();
                    return;
                }
                break;
            case RIL_REQUEST_IMS_SEND_SMS /*99999*/:
                if (!this.mIOPhoneClient.authorizeOutgoingSMS(extractAddress(rr, 20), rr.mSerial)) {
                    rr.onError(2, null);
                    rr.release();
                    return;
                }
                break;
        }
        riljLog("Sending " + RIL.requestToString(rr.mRequest));
        super.send(rr);
    }

    private String extractAddress(RILRequest rr, int initialOffset) {
        rr.mParcel.setDataPosition(initialOffset + 28);
        int address_nbr_of_digits = rr.mParcel.readByte();
        byte[] addressBytes = new byte[address_nbr_of_digits];
        for (int i = 0; i < address_nbr_of_digits; i++) {
            addressBytes[i] = rr.mParcel.readByte();
        }
        return decodeDtmfSmsAddress(addressBytes, address_nbr_of_digits);
    }

    protected RILRequest processSolicited(Parcel p) {
        int pos = p.dataPosition();
        int serial = p.readInt();
        int error = p.readInt();
        RILRequest rr = findFromList(serial);
        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: " + serial + " error: " + error);
            p.setDataPosition(pos);
            return super.processSolicited(p);
        }
        riljLog("processSolicited " + RIL.requestToString(rr.mRequest));
        if (error != 0) {
            switch (rr.mRequest) {
                case 25:
                case 87:
                    this.mIOPhoneClient.sendSMSError(rr.mSerial);
                    break;
                default:
                    break;
            }
        }
        switch (rr.mRequest) {
            case 25:
            case 87:
                this.mIOPhoneClient.sendSMSDone(rr.mSerial);
                break;
            case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                ConnectionParams cp = rr.mResult.obj;
                String apn = null;
                String iface = null;
                if (!(cp.getApnContext().getApnSetting().apn == null || cp.getApnContext().getApnSetting().apn.length() == 0)) {
                    apn = cp.getApnContext().getApnSetting().apn;
                }
                try {
                    DataCallResponse result = (DataCallResponse) responseSetupDataCall(p);
                    if (!(result.ifname == null || result.ifname.length() == 0)) {
                        iface = result.ifname;
                    }
                    if (!(apn == null || result.status != 0 || iface == null)) {
                        riljLog("New session apn " + apn + " iface " + iface + " type " + cp.getApnContext().getApnType());
                        this.mIOPhoneClient.onNewDataSession(iface, apn, cp.getApnContext().getApnType());
                        break;
                    }
                } catch (Throwable t) {
                    riljLog("ERROR: fail to parse call state ");
                    riljLog("ERROR: " + t);
                    break;
                }
        }
        p.setDataPosition(pos);
        return super.processSolicited(p);
    }

    protected void processUnsolicited(Parcel p) {
        int pos = p.dataPosition();
        int response = p.readInt();
        riljLog("processUnsolicited " + pos + " " + RIL.responseToString(response));
        switch (response) {
            case CallFailCause.CDMA_REORDER /*1003*/:
                String[] a = new String[2];
                a[1] = p.readString();
                if (!this.mIOPhoneClient.authorizeIncomingSMS(SmsMessage.newFromCMT(a))) {
                    acknowledgeLastIncomingGsmSms(true, 0, null);
                    return;
                }
                break;
            case CallFailCause.CDMA_NOT_EMERGENCY /*1008*/:
                this.mIOPhoneClient.nitzTimeReceived(p.readString(), p.readLong());
                break;
            case 1018:
                if (this.mHandler == null) {
                    getCurrentCalls(this.mHandler.obtainMessage(1));
                    break;
                }
                getCurrentCalls(this.mHandler.obtainMessage(1));
            case 1020:
                SmsMessage sms = SmsMessage.newFromParcel(p);
                sms.mWrappedSmsMessage.mOriginatingAddress.address = new String(sms.mWrappedSmsMessage.mOriginatingAddress.origBytes);
                if (!this.mIOPhoneClient.authorizeIncomingSMS(sms)) {
                    acknowledgeLastIncomingCdmaSms(true, 0, null);
                    return;
                }
                break;
            case 1025:
                if (!this.mIOPhoneClient.callWaiting(((CdmaCallWaitingNotification) responseCdmaCallWaiting(p)).number)) {
                    return;
                }
                break;
        }
        p.setDataPosition(pos);
        super.processUnsolicited(p);
    }

    protected Object responseCallList(Parcel p) {
        riljLog("Processing response call list");
        ArrayList<DriverCall> response = (ArrayList) super.responseCallList(p);
        if (this.mPhoneType == 2) {
            this.mIOPhoneClient.trackCdmaCalls(response);
        } else {
            this.mIOPhoneClient.trackCalls(response);
        }
        return response;
    }
}
