package com.android.internal.telephony.cat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.ThreadPolicy.Builder;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.cat.NetworkConnectivityListener.State;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;

public class CatBIPManager extends Handler {
    private static final int ADMIN_PDN_EXTENSION_WAIT = 30000;
    static final int BIP_CONTINUE_ADMIN_PDN = 5;
    static final int BIP_DATA_STATE_CHANGED = 4;
    static final int BIP_REQUEST_SETUP_DATA = 1;
    static final int BIP_UICC_SERVER_RESTART_DONE = 3;
    static final int BIP_UICC_SERVER_STARTED = 2;
    static final int MAX_BIP_CHANNELS = 7;
    private static final int WAKE_LOCK_TIMEOUT = 10000;
    static boolean[] channelIds;
    static CatService mCatServicehandle = null;
    int activeClientConnections = 0;
    private ConnectivityManager connMgr;
    ArrayList<CatBIPConnection> connection_list = new ArrayList();
    CatBIPResponseMessage crnt_resp = null;
    int currentChannel = -1;
    String feature = Phone.FEATURE_ENABLE_BIP;
    private NetworkConnectivityListener mConnectivityListener;
    private Context mContext;
    PhoneBase mPhone;
    private BroadcastReceiver mReceiver = new C00451();
    private int mSlotId;
    boolean monitorChannelStatusEvent = false;
    boolean monitorDataDownloadEvent = false;
    private NetworkInfo nwInfo = null;
    ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
    boolean resp_pending = false;

    class C00451 extends BroadcastReceiver {
        C00451() {
        }

        public void onReceive(Context context, Intent intent) {
            CatLog.m0d((Object) this, ">>>>>>>>>> BROADCAST EVENT FROM CAT BIP MANAGER <<<<<<<<<<");
            if (CatBIPManager.this.mConnectivityListener.isListening()) {
                NetworkInfo nwInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                if (nwInfo == null) {
                    CatLog.m0d((Object) this, "there is no network info");
                    return;
                } else if ((CatBIPManager.this.feature != Phone.FEATURE_ENABLE_BIP || nwInfo.getType() == 23) && (CatBIPManager.this.feature != Phone.FEATURE_ENABLE_HIPRI || nwInfo.getType() == 5)) {
                    boolean noConnectivity = intent.getBooleanExtra("noConnectivity", false);
                    if (noConnectivity) {
                        CatBIPManager.this.mConnectivityListener.setState(State.NOT_CONNECTED);
                    } else {
                        CatBIPManager.this.mConnectivityListener.setState(State.CONNECTED);
                    }
                    CatBIPManager.this.mConnectivityListener.setNetworkInfo((NetworkInfo) intent.getParcelableExtra("networkInfo"));
                    CatBIPManager.this.mConnectivityListener.setOtherNetworkInfo((NetworkInfo) intent.getParcelableExtra("otherNetwork"));
                    CatBIPManager.this.mConnectivityListener.setReason(intent.getStringExtra("reason"));
                    CatBIPManager.this.mConnectivityListener.setFailover(intent.getBooleanExtra("isFailover", false));
                    CatLog.m0d((Object) this, "onReceive(): mNetworkInfo=" + CatBIPManager.this.mConnectivityListener.getNetworkInfo() + " mOtherNetworkInfo = " + (CatBIPManager.this.mConnectivityListener.getOtherNetworkInfo() == null ? "[none]" : CatBIPManager.this.mConnectivityListener.getOtherNetworkInfo() + " noConn=" + noConnectivity) + " mState=" + CatBIPManager.this.mConnectivityListener.getState().toString());
                    CatBIPManager.this.mConnectivityListener.notifyHandler();
                    return;
                } else {
                    CatLog.m0d((Object) this, "It is not BIP type");
                    return;
                }
            }
            CatLog.m0d((Object) this, "intent receiver called with not listening : " + CatBIPManager.this.mConnectivityListener.getState().toString() + " and " + intent);
        }
    }

    class C00484 implements Runnable {
        C00484() {
        }

        public void run() {
            CatBIPManager.this.continueProcessingOpenChannel(CatBIPManager.this.nwInfo);
        }
    }

    static /* synthetic */ class C00495 {
        static final /* synthetic */ int[] $SwitchMap$android$net$NetworkInfo$State = new int[NetworkInfo.State.values().length];
        static final /* synthetic */ int[] f4xca33cf42 = new int[CommandType.values().length];

        static {
            try {
                $SwitchMap$android$net$NetworkInfo$State[NetworkInfo.State.DISCONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$android$net$NetworkInfo$State[NetworkInfo.State.CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$android$net$NetworkInfo$State[NetworkInfo.State.CONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f4xca33cf42[CommandType.OPEN_CHANNEL.ordinal()] = 1;
            } catch (NoSuchFieldError e4) {
            }
            try {
                f4xca33cf42[CommandType.CLOSE_CHANNEL.ordinal()] = 2;
            } catch (NoSuchFieldError e5) {
            }
            try {
                f4xca33cf42[CommandType.SEND_DATA.ordinal()] = 3;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    public CatBIPManager(Context context, PhoneBase phone, CatService handle, int slotId) {
        CatLog.m0d((Object) this, "Inside CatBIPManager");
        this.mPhone = phone;
        this.mContext = context;
        mCatServicehandle = handle;
        this.mSlotId = slotId;
        channelIds = new boolean[7];
        this.connMgr = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        this.mConnectivityListener = new NetworkConnectivityListener();
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        disableBipApn();
    }

    public void registerPhone(PhoneBase phone) {
        CatLog.m0d((Object) this, "CatBIPManager phone reloaded!");
        this.mPhone = phone;
    }

    private void openChannelAsRemoteClient(CatBIPClientConnection bipcon) {
        Message msg_resp = new Message();
        CatLog.m0d((Object) this, "openChannelAsRemoteClient()");
        if (bipcon.ConnectionMode.isBackgroundMode || bipcon.ConnectionMode.isOnDemand) {
            CatLog.m0d((Object) this, "openChannelAsRemoteClient():sending TR connection mode is either backgrnd or ondemand");
            this.crnt_resp.resCode = bipcon.mBuffsizeModified ? ResultCode.PRFRMD_WITH_MODIFICATION : ResultCode.OK;
            this.crnt_resp.hasAdditionalInfo = false;
            this.crnt_resp.data = new OpenChannelResponseData(bipcon);
            CatService catService = mCatServicehandle;
            CatService catService2 = mCatServicehandle;
            msg_resp = catService.obtainMessage(109);
            msg_resp.obj = this.crnt_resp;
            msg_resp.sendToTarget();
            if (bipcon.ConnectionMode.isOnDemand) {
                return;
            }
        }
        if (bipcon.ConnectionMode.isBackgroundMode || !bipcon.ConnectionMode.isOnDemand) {
            int result;
            CatLog.m0d((Object) this, "openChannelAsRemoteClient():requesting PDN connection connection mode :bkgrnd or immediate");
            bipcon.isLinkEstablished = false;
            if ((this.mPhone instanceof GSMPhone) && ((GSMPhone) this.mPhone).getCurrentGprsState() == 0) {
                CatLog.m0d((Object) this, "mPhone instanceof GSMPhone");
                result = requestDataConnection(bipcon);
                CatLog.m0d((Object) this, "requestDataConnection() returns " + result);
            } else {
                result = 3;
                CatLog.m0d((Object) this, "getCurrentGprsState is not STATE_IN_SERVICE");
            }
            switch (result) {
                case -1:
                case 2:
                case 3:
                    CatLog.m0d((Object) this, "sending Failure TR");
                    this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                    this.crnt_resp.hasAdditionalInfo = true;
                    this.crnt_resp.AdditionalInfo = 0;
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    msg_resp = catService.obtainMessage(109);
                    msg_resp.obj = this.crnt_resp;
                    msg_resp.sendToTarget();
                    freeChannel(bipcon);
                    displayConnectionStatus();
                    return;
                case 0:
                    CatLog.m0d((Object) this, "APN_ALREADY_ACTIVE");
                    try {
                        byte[] addrBytes = bipcon.destination.getAddress();
                        if (addrBytes.length != 16) {
                            boolean routeExists;
                            int addr = ((((addrBytes[3] & 255) << 24) | ((addrBytes[2] & 255) << 16)) | ((addrBytes[1] & 255) << 8)) | (addrBytes[0] & 255);
                            ConnectivityManager connectivityManager;
                            ConnectivityManager connectivityManager2;
                            if (this.feature == Phone.FEATURE_ENABLE_BIP) {
                                connectivityManager = this.connMgr;
                                connectivityManager2 = this.connMgr;
                                routeExists = connectivityManager.requestRouteToHost(23, addr);
                            } else {
                                connectivityManager = this.connMgr;
                                connectivityManager2 = this.connMgr;
                                routeExists = connectivityManager.requestRouteToHost(5, addr);
                            }
                            if (routeExists) {
                                CatLog.m0d((Object) this, "RouteExists = " + routeExists);
                                bipcon.setupStreams();
                                bipcon.receiver.start();
                                bipcon.isfirstTime = false;
                                this.crnt_resp.resCode = bipcon.mBuffsizeModified ? ResultCode.PRFRMD_WITH_MODIFICATION : ResultCode.OK;
                                this.crnt_resp.hasAdditionalInfo = false;
                            } else {
                                CatLog.m0d((Object) this, " connMgr.requestRouteToHost returned false");
                            }
                            sendMessageDelayed(obtainMessage(5), 30000);
                            this.mConnectivityListener.registerHandler(this, 4);
                            CatLog.m0d((Object) this, "registering handler with ConnectivityListener ");
                            this.mConnectivityListener.startListening();
                            CatLog.m0d((Object) this, "mConnectivityListener.startListening() called ");
                            CatLog.m0d((Object) this, "wakelock for OPEN CHANNEL");
                            mCatServicehandle.mWakeLock.acquire(10000);
                            break;
                        }
                        CatLog.m0d((Object) this, "Exception occurred while Setting up streams");
                        this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = 0;
                        catService = mCatServicehandle;
                        catService2 = mCatServicehandle;
                        msg_resp = catService.obtainMessage(109);
                        msg_resp.obj = this.crnt_resp;
                        msg_resp.sendToTarget();
                        channelIds[bipcon.channelId - 1] = false;
                        if (this.connection_list.remove(bipcon)) {
                            CatLog.m0d((Object) this, "Removed connection  Successfully!!");
                        }
                        CatLog.m0d((Object) this, "StopListening() & unregisterHandler()");
                        this.mConnectivityListener.stopListening();
                        this.mConnectivityListener.unregisterHandler(this);
                        CatLog.m0d((Object) this, "Time to return");
                        return;
                    } catch (Exception e) {
                        CatLog.m0d((Object) this, "Exception occurred while Setting up streams");
                        this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = 0;
                        catService = mCatServicehandle;
                        catService2 = mCatServicehandle;
                        msg_resp = catService.obtainMessage(109);
                        msg_resp.obj = this.crnt_resp;
                        msg_resp.sendToTarget();
                        channelIds[bipcon.channelId - 1] = false;
                        if (this.connection_list.remove(bipcon)) {
                            CatLog.m0d((Object) this, "Removed connection  Successfully!!");
                        }
                        CatLog.m0d((Object) this, "StopListening() & unregisterHandler()");
                        this.mConnectivityListener.stopListening();
                        this.mConnectivityListener.unregisterHandler(this);
                        CatLog.m0d((Object) this, "Time to return");
                        return;
                    }
                case 1:
                    CatLog.m0d((Object) this, " APN_REQUEST_STARTED , wait till we hear from NetworkListener, returning ");
                    return;
            }
        }
        if (bipcon.ConnectionMode.isBackgroundMode) {
            CatLog.m0d((Object) this, "Backgound mode sending channel status to Cat Service");
            sendChannelStatusEvent(bipcon);
        } else if (!bipcon.ConnectionMode.isOnDemand) {
            CatLog.m0d((Object) this, "Immediate mode Sending TR to Cat Service");
            catService = mCatServicehandle;
            catService2 = mCatServicehandle;
            msg_resp = catService.obtainMessage(109);
            this.crnt_resp.data = new OpenChannelResponseData(bipcon);
            msg_resp.obj = this.crnt_resp;
            msg_resp.sendToTarget();
        }
        displayConnectionStatus();
    }

    private void freeChannel(CatBIPConnection bipcon) {
        if (bipcon == null) {
            CatLog.m0d((Object) this, "Nothing to Free, No channels Open");
            return;
        }
        CatLog.m0d((Object) this, "Trying to freeChannel() chanelid " + bipcon.channelId);
        bipcon.terminateStreams();
        CatLog.m0d((Object) this, "removing channel id and connection from the list");
        channelIds[bipcon.channelId - 1] = false;
        if (this.connection_list.remove(bipcon)) {
            CatLog.m0d((Object) this, "Removed connection  Successfully!!");
        }
    }

    public void handleOpenChannel(OpenChannelParams params) {
        CatLog.m0d((Object) this, "handleOpenChannel");
        this.crnt_resp = new CatBIPResponseMessage();
        this.crnt_resp.mCmdDet = params.mCmdDet;
        this.crnt_resp.resCode = ResultCode.OK;
        this.crnt_resp.hasAdditionalInfo = false;
        CatService catService;
        CatService catService2;
        Message msg;
        if (!channelsAvailable()) {
            CatLog.m0d((Object) this, "Bearer type not supported");
            this.crnt_resp.resCode = ResultCode.BEYOND_TERMINAL_CAPABILITY;
            this.crnt_resp.hasAdditionalInfo = false;
            catService = mCatServicehandle;
            catService2 = mCatServicehandle;
            msg = catService.obtainMessage(109);
            msg.obj = this.crnt_resp;
            msg.sendToTarget();
        } else if (checkPortInUse(params.mTransportLevel.portNumber)) {
            this.crnt_resp.resCode = ResultCode.BIP_ERROR;
            this.crnt_resp.hasAdditionalInfo = true;
            this.crnt_resp.AdditionalInfo = 16;
            catService = mCatServicehandle;
            catService2 = mCatServicehandle;
            msg = catService.obtainMessage(109);
            msg.obj = this.crnt_resp;
            msg.sendToTarget();
        } else {
            displayConnectionStatus();
            CatBIPConnection bipcon;
            if (params.mTransportLevel.isServer()) {
                CatLog.m0d((Object) this, "handleOpenChannel: UICC in SERVER mode");
                bipcon = new CatBIPServerConnection(params.mBufferSize, params.mTransportLevel, this);
                CatLog.m0d((Object) this, "handleOpenChannel: Starting Thread");
                bipcon.channelId = assignChannelId();
                this.connection_list.add(bipcon);
                ((CatBIPServerConnection) bipcon).listener = new Thread(new CatBIPServerListenThread(bipcon, this));
                ((CatBIPServerConnection) bipcon).listener.start();
                CatLog.m0d((Object) this, "handleOpenChannel: Started Thread");
                msg = Message.obtain(this, 2);
                msg.obj = bipcon;
                CatLog.m0d((Object) this, "handleOpenChannel: Msg Obtained");
                sendMessage(msg);
                displayConnectionStatus();
                return;
            }
            Context context = this.mContext;
            Context context2 = this.mContext;
            CatLog.m0d((Object) this, "networkType : " + ((TelephonyManager) context.getSystemService("phone")).getNetworkType());
            bipcon = new CatBIPClientConnection(params.mBearerDesc, params.mBufferSize, params.mTransportLevel, this, params.mNetworkAccessName, params.mBearerMode, params.mDataDestinationAddress, params.mUsernameTextMessage.text, params.mPasswordTextMessage.text);
            CatLog.m0d((Object) this, "Change the StrictMode for BIP client Mode");
            StrictMode.setThreadPolicy(new Builder().detectNetwork().penaltyDropBox().build());
            bipcon.channelId = assignChannelId();
            this.connection_list.add(bipcon);
            CatLog.m0d((Object) this, "Channel Assigned  = " + bipcon.channelId);
            this.currentChannel = bipcon.channelId;
            final CatBIPConnection b = bipcon;
            new Thread(new Runnable() {
                public void run() {
                    CatBIPManager.this.openChannelAsRemoteClient((CatBIPClientConnection) b);
                }
            }).start();
            displayConnectionStatus();
        }
    }

    public void handleCloseChannel(CloseChannelParams params) {
        CatLog.m0d((Object) this, "handleCloseChannel");
        int cid = params.mChannelId & 7;
        this.crnt_resp = new CatBIPResponseMessage();
        CatService catService = mCatServicehandle;
        CatService catService2 = mCatServicehandle;
        Message termResp = catService.obtainMessage(109);
        CatLog.m0d((Object) this, "handleCloseChannel: Requested Chanel ID = " + cid);
        this.crnt_resp.mCmdDet = params.mCmdDet;
        this.crnt_resp.resCode = ResultCode.BIP_ERROR;
        this.crnt_resp.hasAdditionalInfo = true;
        displayConnectionStatus();
        if (params.mChannelId < 33 || params.mChannelId > 39) {
            CatLog.m0d((Object) this, "handleCloseChannel: Invalid Channel Id! BIP's cid =" + cid + " params.mChannelId" + params.mChannelId);
            this.crnt_resp.AdditionalInfo = 3;
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
            return;
        }
        CatBIPConnection b = getBIPConnection(cid);
        if (b == null) {
            CatLog.m0d((Object) this, "handleCloseChannel: No Channel Available! BIP cid =" + cid + " params.mChannelId =" + params.mChannelId);
            this.crnt_resp.AdditionalInfo = 3;
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
        } else if (b.uiccTerminalIface.isServer()) {
            CatLog.m0d((Object) this, "handleCloseChannel: UICC in SERVER Mode");
            closeServerConnection((CatBIPServerConnection) b, params);
        } else {
            CatLog.m0d((Object) this, "handleCloseChannel: UICC in CLIENT Mode");
            closeClientConnection((CatBIPClientConnection) b);
            CatLog.m0d((Object) this, "Be back to old StrictMode");
            StrictMode.setThreadPolicy(this.oldPolicy);
        }
    }

    public void handleSendData(SendDataParams params) {
        CatLog.m0d((Object) this, "handleSendData");
        int cid = params.mChannelId & 7;
        CatService catService = mCatServicehandle;
        CatService catService2 = mCatServicehandle;
        Message termResp = catService.obtainMessage(109);
        this.crnt_resp = new CatBIPResponseMessage();
        this.crnt_resp.mCmdDet = params.mCmdDet;
        this.crnt_resp.resCode = ResultCode.BIP_ERROR;
        this.crnt_resp.hasAdditionalInfo = true;
        displayConnectionStatus();
        CatBIPConnection b = getBIPConnection(cid);
        if (b == null || params.mChannelId < 33 || params.mChannelId > 39) {
            CatLog.m0d((Object) this, "handleSendData: No Channel available : " + cid);
            this.crnt_resp.AdditionalInfo = 3;
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
        } else if (b.uiccTerminalIface.isServer()) {
            CatLog.m0d((Object) this, "handleSendData: UICC in SERVER mode");
            sendDataServerMode((CatBIPServerConnection) b, params.mChannelData, params.mSendImmediate);
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
            CatLog.m0d((Object) this, "handleSendData: Sending Send Data Terminal Response to mCatService handle");
        } else {
            CatLog.m0d((Object) this, "handleSendData: UICC in CLIENT mode");
            final CatBIPConnection bipcon = b;
            final byte[] channelData = (byte[]) params.mChannelData.clone();
            final boolean sendImmediate = params.mSendImmediate;
            new Thread(new Runnable() {
                public void run() {
                    CatBIPManager.this.sendDataClientMode((CatBIPClientConnection) bipcon, channelData, sendImmediate);
                }
            }).start();
        }
    }

    public void handleReceiveData(ReceiveDataParams params) {
        CatLog.m0d((Object) this, "handleReceiveData");
        int cid = params.mChannelId & 7;
        this.crnt_resp = new CatBIPResponseMessage();
        CatService catService = mCatServicehandle;
        CatService catService2 = mCatServicehandle;
        Message termResp = catService.obtainMessage(109);
        this.crnt_resp.mCmdDet = params.mCmdDet;
        this.crnt_resp.resCode = ResultCode.BIP_ERROR;
        this.crnt_resp.hasAdditionalInfo = true;
        CatLog.m0d((Object) this, "handleReceiveData: Created partial Receive Data Terminal Response");
        if (params.mChannelId < 33 || params.mChannelId > 39) {
            CatLog.m0d((Object) this, "handleReceiveData: Invalid Channel ID");
            this.crnt_resp.AdditionalInfo = 3;
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
            return;
        }
        CatBIPConnection b = getBIPConnection(cid);
        if (b == null) {
            CatLog.m0d((Object) this, "handleReceiveData: No Channel available");
            this.crnt_resp.AdditionalInfo = 1;
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
        } else if (b.uiccTerminalIface.isServer()) {
            CatLog.m0d((Object) this, "handleReceiveData: BIP Connection Found. UICC in SERVER mode");
            CatBIPServerConnection server = (CatBIPServerConnection) b;
            try {
                if (server.socket == null || !server.socket.isConnected() || server.socket.isClosed()) {
                    CatLog.m0d((Object) this, "handleReceiveData: socket is not available");
                    this.crnt_resp.AdditionalInfo = 7;
                    termResp.obj = this.crnt_resp;
                    termResp.sendToTarget();
                    return;
                }
                receiveDataServerMode(server, params.mChannelDataLength);
                termResp.obj = this.crnt_resp;
                termResp.sendToTarget();
                CatLog.m0d((Object) this, "handleReceiveData: Sending Receive Data Terminal Response to mCatService handle");
            } catch (NullPointerException e) {
                CatLog.m0d((Object) this, "handleReceiveData: NullPointerException");
                this.crnt_resp.AdditionalInfo = 7;
                termResp.obj = this.crnt_resp;
                termResp.sendToTarget();
            }
        } else {
            CatLog.m0d((Object) this, "Receiving Data in Client mode");
            receiveDataClientMode((CatBIPClientConnection) b, params.mChannelDataLength);
            termResp.obj = this.crnt_resp;
            termResp.sendToTarget();
            CatLog.m0d((Object) this, "handleReceiveData: Sending Receive Data Terminal Response to mCatService handle");
        }
    }

    public void getChannelStatus(CommandParams cmdParams) {
        this.crnt_resp = new CatBIPResponseMessage();
        this.crnt_resp.mCmdDet = cmdParams.mCmdDet;
        this.crnt_resp.resCode = ResultCode.OK;
        this.crnt_resp.hasAdditionalInfo = false;
        CatLog.m0d((Object) this, "getChannelStatus");
        Iterator i = this.connection_list.iterator();
        this.crnt_resp.data = new GetChannelStatusResponse(i, channelIds);
        CatLog.m0d((Object) this, "Filled crnt_resp.data");
        CatService catService = mCatServicehandle;
        CatService catService2 = mCatServicehandle;
        Message msg = catService.obtainMessage(109);
        msg.obj = this.crnt_resp;
        msg.sendToTarget();
    }

    public void handleMessage(Message msg) {
        CatLog.m0d((Object) this, "handling Message : " + msg.what);
        CatBIPServerConnection bipserver;
        CatService catService;
        CatService catService2;
        Message termResp;
        Iterator i;
        CatBIPConnection bipCon;
        CatBIPClientConnection bipclient;
        switch (msg.what) {
            case 2:
                CatLog.m0d((Object) this, "handleMessage: BIP_UICC_SERVER_STARTED");
                bipserver = msg.obj;
                if (bipserver.listener.isAlive()) {
                    CatLog.m0d((Object) this, "handleMessage: BIP Server socket opened in LISTEN state");
                    bipserver.linkState = (byte) 1;
                    bipserver.linkStateCause = (byte) 0;
                    this.crnt_resp.resCode = bipserver.mBuffsizeModified ? ResultCode.PRFRMD_WITH_MODIFICATION : ResultCode.OK;
                    if (bipserver.linkStateCause == (byte) 0) {
                        this.crnt_resp.hasAdditionalInfo = false;
                    } else {
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = bipserver.linkStateCause;
                    }
                    this.crnt_resp.data = new OpenChannelResponseData(bipserver);
                    CatLog.m0d((Object) this, "handleMessage: Filled Open Channel Terminal Response params");
                } else if (bipserver.socket.isClosed()) {
                    CatLog.m0d((Object) this, "handleMessage: BIP Server socket closed");
                    bipserver.linkState = (byte) 0;
                    bipserver.linkStateCause = (byte) 0;
                    this.crnt_resp.resCode = ResultCode.BIP_ERROR;
                    this.crnt_resp.hasAdditionalInfo = true;
                    this.crnt_resp.AdditionalInfo = 0;
                    this.crnt_resp.data = new OpenChannelResponseData(bipserver);
                    CatLog.m0d((Object) this, "handleMessage: Filled Open Channel Terminal Response params");
                }
                catService = mCatServicehandle;
                catService2 = mCatServicehandle;
                termResp = catService.obtainMessage(109);
                termResp.obj = this.crnt_resp;
                termResp.sendToTarget();
                CatLog.m0d((Object) this, "handleMessage: Sending OPEN CHANNEL Terminal Response to mCatService handle");
                return;
            case 3:
                CatLog.m0d((Object) this, "handleMessage: BIP_UICC_SERVER_RESTART_DONE");
                bipserver = (CatBIPServerConnection) msg.obj;
                if (bipserver.listener.isAlive()) {
                    CatLog.m0d((Object) this, "handleMessage: BIP Server socket opened in LISTEN State");
                    bipserver.linkState = (byte) 1;
                    bipserver.linkStateCause = (byte) 0;
                    this.crnt_resp.resCode = ResultCode.OK;
                    if (bipserver.linkStateCause == (byte) 0) {
                        this.crnt_resp.hasAdditionalInfo = false;
                    } else {
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = bipserver.linkStateCause;
                    }
                    CatLog.m0d((Object) this, "handleMessage: Filled Terminal Response params");
                } else if (bipserver.socket.isClosed()) {
                    CatLog.m0d((Object) this, "handleMessage: BIP Server socket closed");
                    bipserver.linkState = (byte) 0;
                    bipserver.linkStateCause = (byte) 0;
                    this.crnt_resp.resCode = ResultCode.BIP_ERROR;
                    this.crnt_resp.hasAdditionalInfo = true;
                    this.crnt_resp.AdditionalInfo = bipserver.linkStateCause;
                    CatLog.m0d((Object) this, "handleMessage: Filled Terminal Response params");
                }
                catService = mCatServicehandle;
                catService2 = mCatServicehandle;
                termResp = catService.obtainMessage(109);
                termResp.obj = this.crnt_resp;
                termResp.sendToTarget();
                CatLog.m0d((Object) this, "handleMessage: BIP_UICC_SERVER_RESTART_DONE: Sending Terminal Response to mCatService handle");
                return;
            case 4:
                CatLog.m0d((Object) this, "BIP_DATA_STATE_CHANGED");
                this.nwInfo = this.mConnectivityListener.getNetworkInfo();
                if (this.nwInfo == null) {
                    CatLog.m0d((Object) this, "No BIP cmd is being processed.");
                    return;
                }
                NetworkInfo.State connectionState = this.nwInfo.getState();
                CatLog.m0d((Object) this, "nwInfo.getType()  = " + this.nwInfo.getType() + " ConnectionState = " + connectionState);
                if ((this.feature != Phone.FEATURE_ENABLE_BIP || this.nwInfo.getType() == 23) && (this.feature != Phone.FEATURE_ENABLE_HIPRI || this.nwInfo.getType() == 5)) {
                    if (connectionState == NetworkInfo.State.DISCONNECTED && this.monitorChannelStatusEvent) {
                        i = this.connection_list.iterator();
                        while (i.hasNext()) {
                            bipCon = (CatBIPConnection) i.next();
                            if (!bipCon.uiccTerminalIface.isServer()) {
                                bipclient = (CatBIPClientConnection) bipCon;
                                if (bipclient.uiccTerminalIface.isRemoteClient()) {
                                    bipclient.isLinkEstablished = false;
                                    bipclient.linkStateCause = (byte) 5;
                                    sendChannelStatusEvent(bipclient);
                                }
                            }
                        }
                    }
                    catService = mCatServicehandle;
                    if (CatService.mBIPCurrntCmd == null) {
                        CatLog.m0d((Object) this, "No BIP cmd is being processed, May not have been unregistered from NWConnectivityListener  ");
                        return;
                    }
                    catService = mCatServicehandle;
                    CommandType cmd = CatService.mBIPCurrntCmd.getCommandType();
                    CatLog.m0d((Object) this, "Still processing " + cmd);
                    switch (C00495.f4xca33cf42[cmd.ordinal()]) {
                        case 1:
                            new Thread(new C00484()).start();
                            return;
                        case 2:
                            continueProcessingCloseChannel(this.nwInfo);
                            return;
                        default:
                            return;
                    }
                }
                CatLog.m0d((Object) this, "Network :nwInfo.getType() = " + this.nwInfo.getType() + " is not TYPE_MOBILE_BIP");
                return;
            case 5:
                int result;
                boolean bActiveClient = false;
                i = this.connection_list.iterator();
                while (i.hasNext()) {
                    bipCon = (CatBIPConnection) i.next();
                    if (!bipCon.uiccTerminalIface.isServer()) {
                        bipclient = (CatBIPClientConnection) bipCon;
                        if (bipclient.uiccTerminalIface.isRemoteClient() && bipclient.isLinkEstablished) {
                            CatLog.m0d((Object) this, "handleMessage: BIP_CONTINUE_ADMIN_PDN: Active client - " + bipCon.channelId);
                            bActiveClient = true;
                            if (bActiveClient) {
                                result = this.connMgr.startUsingNetworkFeature(0, this.feature);
                                CatLog.m0d((Object) this, "handleMessage: BIP_CONTINUE_ADMIN_PDN: Continue connection, result - " + result);
                                if (1 != result || result == 0) {
                                    sendMessageDelayed(obtainMessage(5), 30000);
                                    return;
                                }
                                return;
                            }
                            return;
                        }
                    }
                }
                if (bActiveClient) {
                    result = this.connMgr.startUsingNetworkFeature(0, this.feature);
                    CatLog.m0d((Object) this, "handleMessage: BIP_CONTINUE_ADMIN_PDN: Continue connection, result - " + result);
                    if (1 != result) {
                    }
                    sendMessageDelayed(obtainMessage(5), 30000);
                    return;
                }
                return;
            default:
                CatLog.m0d((Object) this, "handleMessage: default");
                return;
        }
    }

    private CatBIPConnection getBIPConnection(int cid) {
        CatLog.m0d((Object) this, "CatBIPConnection : get ID");
        Iterator i = this.connection_list.iterator();
        while (i.hasNext()) {
            CatBIPConnection b = (CatBIPConnection) i.next();
            if (b.channelId == cid) {
                CatLog.m0d((Object) this, "CatBIPConnection : found ID = " + cid);
                return b;
            }
        }
        CatLog.m0d((Object) this, "CatBIPConnection : null ID");
        return null;
    }

    private int assignChannelId() {
        int i = 0;
        while (i < channelIds.length) {
            if (channelIds[i]) {
                i++;
            } else {
                channelIds[i] = true;
                return i + 1;
            }
        }
        return -1;
    }

    private void receiveDataServerMode(CatBIPServerConnection b, byte ChannelDataLength) {
        CatLog.m0d((Object) this, "receiveDataServerMode");
        int arrayLength = ChannelDataLength & 255;
        byte[] availableData = null;
        byte[] temp = null;
        int bytesInRxBuffer = 0;
        int dataLength = 0;
        this.crnt_resp.resCode = ResultCode.PRFRMD_WITH_MISSING_INFO;
        this.crnt_resp.hasAdditionalInfo = false;
        if (b.byteArrayWriter == null) {
            CatLog.m0d((Object) this, "receiveDataServerMode : byteArrayWriter is null");
            return;
        }
        if (b.byteArrayWriter.size() != 0) {
            availableData = b.byteArrayWriter.toByteArray();
        }
        if (availableData != null) {
            if (availableData.length < arrayLength) {
                dataLength = availableData.length;
                temp = availableData;
            } else {
                this.crnt_resp.resCode = ResultCode.OK;
                int bytesAvailable = availableData.length - b.lastReadPosition;
                CatLog.m0d((Object) this, "bytesAvailable = " + bytesAvailable + "  availableData.length = " + availableData.length + "  lastReadPosition = " + b.lastReadPosition);
                if (arrayLength >= bytesAvailable) {
                    arrayLength = bytesAvailable;
                }
                CatLog.m0d((Object) this, "length = " + arrayLength);
                temp = new byte[arrayLength];
                System.arraycopy(availableData, b.lastReadPosition, temp, 0, arrayLength);
                b.lastReadPosition += arrayLength;
                CatLog.m0d((Object) this, "lastReadPosition = " + b.lastReadPosition);
                bytesInRxBuffer = b.byteArrayWriter.size() - b.lastReadPosition;
                dataLength = temp.length;
                if (b.lastReadPosition >= availableData.length) {
                    CatLog.m0d((Object) this, "reset rxbuf buffer");
                    bytesInRxBuffer = 0;
                    b.lastReadPosition = 0;
                    b.byteArrayWriter.reset();
                }
            }
        }
        this.crnt_resp.data = new ReceiveDataResponse(temp, dataLength, bytesInRxBuffer);
        CatLog.m0d((Object) this, "receiveDataServerMode: Filling Receive Data Terminal Response");
    }

    private void sendDataServerMode(CatBIPServerConnection b, byte[] ChannelData, boolean SendImmediate) {
        CatLog.m0d((Object) this, "sendDataServerMode");
        try {
            Socket s = b.socket;
            if (!s.isConnected() || s.isClosed()) {
                this.crnt_resp.resCode = ResultCode.BIP_ERROR;
                this.crnt_resp.hasAdditionalInfo = true;
                this.crnt_resp.data = new SendDataResponse(0);
                this.crnt_resp.AdditionalInfo = 2;
                CatLog.m0d((Object) this, "sendDataServerMode: Socket Closed/Not Connected");
                return;
            }
            if (SendImmediate) {
                CatLog.m0d((Object) this, "sendDataServerMode: send immediate");
                b.storeSendData.write(ChannelData, 0, ChannelData.length);
                byte[] immediateData = b.storeSendData.toByteArray();
                b.writer.write(immediateData, 0, immediateData.length);
                b.writer.flush();
                CatLog.m0d((Object) this, "sendDataServerMode: Wrote all data to socket " + immediateData.length);
                b.storeSendData.reset();
                CatLog.m0d((Object) this, "sendDataServerMode: Resetting the Buffer");
            } else {
                CatLog.m0d((Object) this, "sendDataServerMode: Store Mode");
                b.storeSendData.write(ChannelData, 0, ChannelData.length);
                CatLog.m0d((Object) this, "sendDataServerMode: Size of Tx buffer=" + b.storeSendData.size());
            }
            this.crnt_resp.resCode = ResultCode.OK;
            this.crnt_resp.hasAdditionalInfo = false;
            this.crnt_resp.data = new SendDataResponse(255);
            CatLog.m0d((Object) this, "sendDataServerMode: Filling SEND DATA Terminal Response");
        } catch (IOException e) {
            this.crnt_resp.resCode = ResultCode.BIP_ERROR;
            this.crnt_resp.hasAdditionalInfo = true;
            this.crnt_resp.data = new SendDataResponse(0);
            this.crnt_resp.AdditionalInfo = 0;
            CatLog.m0d((Object) this, "sendDataServerMode: Java IO Exception: Filling SEND DATA Terminal Response with BIP_ERROR");
        }
    }

    private void receiveDataClientMode(CatBIPClientConnection bipcon, int requestedLength) {
        int bytesRemaining = 0;
        int dataLength = 0;
        this.crnt_resp.resCode = ResultCode.PRFRMD_WITH_MISSING_INFO;
        this.crnt_resp.hasAdditionalInfo = false;
        if ((requestedLength & 255) > 237) {
            CatLog.m0d((Object) this, "receiveDataClientMode: requestedLength is " + requestedLength);
            requestedLength = 237;
        }
        byte[] data = bipcon.getRxData(requestedLength & 255);
        if (data == null) {
            CatLog.m0d((Object) this, "receiveDataClientMode: RxData is null");
        } else if (data.length < (requestedLength & 255)) {
            dataLength = data.length;
            CatLog.m0d((Object) this, "receiveDataClientMode: RxData is shorter than requested length");
        } else {
            this.crnt_resp.resCode = ResultCode.OK;
            bytesRemaining = bipcon.rxBuf.size() - bipcon.lastReadPosition;
            dataLength = data.length;
        }
        this.crnt_resp.data = new ReceiveDataResponse(data, dataLength, bytesRemaining);
    }

    private boolean isbearerTypeSupported(int bearerType) {
        switch (bearerType) {
            case 2:
            case 3:
                return true;
            default:
                CatLog.m0d((Object) this, "Unsupported bearer type: " + bearerType);
                return false;
        }
    }

    private int requestDataConnection(CatBIPClientConnection con) {
        if (con.NetworkAccessname == null) {
            disableBipApn();
            String numeric = TelephonyManager.getDefault().getSimOperator(SubscriptionManager.getSubId(this.mSlotId)[0]);
            Cursor cursor = null;
            Uri TELEPHONY_NO_UPDATE_URI = Uri.parse("content://telephony/carriers/no_update");
            this.feature = Phone.FEATURE_ENABLE_HIPRI;
            try {
                cursor = this.mContext.getContentResolver().query(TELEPHONY_NO_UPDATE_URI, null, "numeric = '" + numeric + "' AND type = 'bip'", null, null);
                if (cursor.getCount() > 0) {
                    this.feature = Phone.FEATURE_ENABLE_BIP;
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (SQLiteException e) {
                CatLog.m0d((Object) this, "Exception caught during check apn : " + e);
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
            CatLog.m0d((Object) this, "con.NetworkAccessname is null, feature is " + this.feature);
        } else {
            CatLog.m0d((Object) this, "defaultApnName = " + null);
            if (null == null || !con.NetworkAccessname.equals(null)) {
                setBipApn(con);
                if ("LGT".equals("") && "3".equals(SystemProperties.get("ril.simtype", ""))) {
                    this.feature = Phone.FEATURE_ENABLE_HIPRI;
                } else {
                    this.feature = Phone.FEATURE_ENABLE_BIP;
                }
            } else {
                CatLog.m0d((Object) this, "con.NetworkAccessname is same as default APN");
                this.feature = Phone.FEATURE_ENABLE_HIPRI;
            }
        }
        int result = this.connMgr.startUsingNetworkFeature(0, this.feature);
        CatLog.m0d((Object) this, "result of startUsingNetworkFeature(" + this.feature + ") " + result);
        if (1 == result) {
            this.mConnectivityListener.registerHandler(this, 4);
            CatLog.m0d((Object) this, "registering handler with ConnectivityListener ");
            this.mConnectivityListener.startListening();
            CatLog.m0d((Object) this, "mConnectivityListener.startListening() called ");
            CatLog.m0d((Object) this, "wakelock for OPEN CHANNEL");
            mCatServicehandle.mWakeLock.acquire(10000);
        }
        this.resp_pending = true;
        return result;
    }

    private void closeClientConnection(CatBIPClientConnection bipcon) {
        boolean linkdrop_status = checkLinkDrop();
        freeChannel(bipcon);
        displayConnectionStatus();
        this.crnt_resp.resCode = ResultCode.OK;
        this.crnt_resp.hasAdditionalInfo = false;
        this.crnt_resp.data = null;
        CatLog.m0d((Object) this, "stopUsingNetworkFeature()");
        this.connMgr.stopUsingNetworkFeature(0, this.feature);
        if (this.feature == Phone.FEATURE_ENABLE_HIPRI || linkdrop_status) {
            CatService catService = mCatServicehandle;
            CatService catService2 = mCatServicehandle;
            Message msg_resp = catService.obtainMessage(109);
            msg_resp.obj = this.crnt_resp;
            msg_resp.sendToTarget();
            CatLog.m0d((Object) this, "Sent close Channel TR: FEATURE_ENABLE_HIPRI");
            if (this.connection_list.isEmpty()) {
                CatLog.m0d((Object) this, "Unregistering...");
                this.mConnectivityListener.stopListening();
                this.mConnectivityListener.unregisterHandler(this);
            }
            this.feature = Phone.FEATURE_ENABLE_BIP;
        }
    }

    private void closeServerConnection(CatBIPServerConnection server, CloseChannelParams params) {
        CatLog.m0d((Object) this, "closeServerConnection");
        CatLog.m0d((Object) this, "handleCloseChannel: BIP Server connection found! ID : " + server.channelId);
        switch (params.mCloseChannelMode.value()) {
            case 0:
                CatService catService = mCatServicehandle;
                CatService catService2 = mCatServicehandle;
                Message termResp = catService.obtainMessage(109);
                CatLog.m0d((Object) this, "handleCloseChannel: remove connection; TCP in CLOSED state!");
                freeChannel(server);
                CatLog.m0d((Object) this, "handleCloseChannel: Channel Mode is 00!");
                this.crnt_resp.resCode = ResultCode.OK;
                this.crnt_resp.hasAdditionalInfo = false;
                termResp.obj = this.crnt_resp;
                termResp.sendToTarget();
                CatLog.m0d((Object) this, "handleCloseChannel: Sending Close Channel Terminal Response to mCatService handle");
                return;
            case 1:
                freeChannel(server);
                server.listener = null;
                CatLog.m0d((Object) this, "handleCloseChannel: put TCP in LISTEN State!");
                server.listener = new Thread(new CatBIPServerListenThread(server, this));
                server.listener.start();
                Message msg = Message.obtain(this, 3);
                msg.obj = server;
                sendMessage(msg);
                CatLog.m0d((Object) this, "handleCloseChannel: Channel Mode is 01!");
                return;
            default:
                return;
        }
    }

    private void sendDataClientMode(CatBIPClientConnection con, byte[] channelData, boolean sendDataImmediate) {
        CatService catService;
        CatService catService2;
        Message msg;
        if (con.ConnectionMode.isOnDemand && con.isfirstTime) {
            int result;
            CatLog.m0d((Object) this, "ConnectionMode.isOnDemand && con.isfirstTime = true");
            if (((GSMPhone) this.mPhone).getCurrentGprsState() == 0) {
                result = requestDataConnection(con);
                CatLog.m0d((Object) this, "requestDataConnection() returns " + result);
            } else {
                result = 3;
                CatLog.m0d((Object) this, "getCurrentGprsState is not STATE_IN_SERVICE");
            }
            switch (result) {
                case 0:
                    CatLog.m0d((Object) this, "APN_ALREADY_ACTIVE");
                    try {
                        byte[] addrBytes = con.destination.getAddress();
                        if (addrBytes.length != 16) {
                            boolean routeExists;
                            int addr = ((((addrBytes[3] & 255) << 24) | ((addrBytes[2] & 255) << 16)) | ((addrBytes[1] & 255) << 8)) | (addrBytes[0] & 255);
                            ConnectivityManager connectivityManager;
                            ConnectivityManager connectivityManager2;
                            if (this.feature == Phone.FEATURE_ENABLE_BIP) {
                                connectivityManager = this.connMgr;
                                connectivityManager2 = this.connMgr;
                                routeExists = connectivityManager.requestRouteToHost(23, addr);
                            } else {
                                connectivityManager = this.connMgr;
                                connectivityManager2 = this.connMgr;
                                routeExists = connectivityManager.requestRouteToHost(5, addr);
                            }
                            if (!routeExists) {
                                CatLog.m0d((Object) this, "connMgr.requestRouteToHost returned false");
                                break;
                            }
                            CatLog.m0d((Object) this, "connMgr.requestRouteToHost returned true");
                            con.setupStreams();
                            con.receiver.start();
                            con.isfirstTime = false;
                            break;
                        }
                        Message msg_resp = new Message();
                        CatLog.m0d((Object) this, "Exception occurred while Setting up streams");
                        this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = 0;
                        catService = mCatServicehandle;
                        catService2 = mCatServicehandle;
                        msg_resp = catService.obtainMessage(109);
                        msg_resp.obj = this.crnt_resp;
                        msg_resp.sendToTarget();
                        CatLog.m0d((Object) this, "StopListening() & unregisterHandler()");
                        this.mConnectivityListener.stopListening();
                        this.mConnectivityListener.unregisterHandler(this);
                        CatLog.m0d((Object) this, "Time to return");
                        return;
                    } catch (Exception e) {
                        CatLog.m0d((Object) this, "Exception occurred while Setting up streams");
                        this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = 0;
                        this.crnt_resp.data = new SendDataResponse(0);
                        catService = mCatServicehandle;
                        catService2 = mCatServicehandle;
                        msg = catService.obtainMessage(109);
                        msg.obj = this.crnt_resp;
                        msg.sendToTarget();
                        if (e.getMessage().compareTo("TIMEOUT") != 0) {
                            freeChannel(con);
                        } else {
                            channelIds[con.channelId - 1] = false;
                            if (this.connection_list.remove(con)) {
                                CatLog.m0d((Object) this, "Removed connection  Successfully!!");
                            }
                        }
                        CatLog.m0d((Object) this, "StopListening() & Unregister Handle");
                        this.mConnectivityListener.stopListening();
                        this.mConnectivityListener.unregisterHandler(this);
                        CatLog.m0d((Object) this, "Time to return");
                        return;
                    }
                case 1:
                    CatLog.m0d((Object) this, "APN_REQUEST_STARTED , wait till we hear from NetworkListner");
                    break;
                case 2:
                case 3:
                    CatLog.m0d((Object) this, "sending Failure TR");
                    this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                    this.crnt_resp.hasAdditionalInfo = true;
                    this.crnt_resp.AdditionalInfo = 0;
                    this.crnt_resp.data = new SendDataResponse(0);
                    con.isLinkEstablished = false;
                    con.linkStateCause = (byte) 0;
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    msg = catService.obtainMessage(109);
                    msg.obj = this.crnt_resp;
                    msg.sendToTarget();
                    freeChannel(con);
                    return;
            }
        }
        if (!con.isfirstTime) {
            CatLog.m0d((Object) this, "con.isfirstTime = false");
            this.crnt_resp.resCode = ResultCode.BIP_ERROR;
            this.crnt_resp.hasAdditionalInfo = true;
            this.crnt_resp.data = new SendDataResponse(con.bufferSize);
            if (con.uiccTerminalIface.isTCPRemoteClient()) {
                Socket s = con.socket;
                if (!s.isConnected() || s.isClosed()) {
                    CatLog.m0d((Object) this, "TCP Remote Client Socket is Closed or Not Connected");
                    this.crnt_resp.AdditionalInfo = 2;
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    msg = catService.obtainMessage(109);
                    msg.obj = this.crnt_resp;
                    msg.sendToTarget();
                    freeChannel(con);
                    displayConnectionStatus();
                    return;
                }
                CatLog.m0d((Object) this, "TCP Remote Client Socket is neither Closed nor Not Connected");
            }
            if (con.uiccTerminalIface.isUDPRemoteClient()) {
                DatagramSocket s2 = con.socket;
                if (!s2.isConnected() || s2.isClosed()) {
                    CatLog.m0d((Object) this, "UDP Remote Client, Socket is Closed or Not Connected");
                    this.crnt_resp.AdditionalInfo = 2;
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    msg = catService.obtainMessage(109);
                    msg.obj = this.crnt_resp;
                    msg.sendToTarget();
                    return;
                }
                CatLog.m0d((Object) this, "UDP Remote Client, Socket is neither Closed nor Not Connected");
            }
            try {
                CatLog.m0d((Object) this, "storing bytes: " + IccUtils.bytesToHexString(channelData));
                CatLog.m0d((Object) this, "txIndex : " + con.txIndex + " channeldatalength =  " + channelData.length);
                con.txBuf.write(channelData, 0, channelData.length);
                con.txIndex += channelData.length;
            } catch (NullPointerException e2) {
                CatLog.m0d((Object) this, "Nul pointer Exception while writing to tx buf ");
            } catch (IndexOutOfBoundsException e3) {
                CatLog.m0d((Object) this, "IndexOutOfBounds Exception while writing to tx buf ");
            }
            int emptySpace;
            if (!sendDataImmediate) {
                CatLog.m0d((Object) this, "Data Stored. Send Response");
                this.crnt_resp.resCode = ResultCode.OK;
                this.crnt_resp.hasAdditionalInfo = false;
                emptySpace = con.bufferSize - con.txIndex;
                this.crnt_resp.data = new SendDataResponse(emptySpace);
            } else if (con.uiccTerminalIface.isUDPRemoteClient()) {
                try {
                    con.socket.send(new DatagramPacket(con.txBuf.toByteArray(), 0, con.txBuf.size(), con.destination, con.uiccTerminalIface.portNumber));
                    CatLog.m0d((Object) this, "Packet Ready sent");
                    this.crnt_resp.resCode = ResultCode.OK;
                    this.crnt_resp.hasAdditionalInfo = false;
                    con.txBuf.reset();
                    con.txIndex = 0;
                    emptySpace = con.bufferSize - con.txBuf.size();
                    this.crnt_resp.data = new SendDataResponse(emptySpace);
                } catch (IOException i) {
                    CatLog.m0d((Object) this, "IOException while sending UDP packet : " + i.getMessage());
                    this.crnt_resp.AdditionalInfo = 0;
                    con.txBuf.reset();
                    con.txIndex = 0;
                }
            } else if (con.uiccTerminalIface.isTCPRemoteClient()) {
                try {
                    con.out.write(con.txBuf.toByteArray(), 0, con.txBuf.size());
                    CatLog.m0d((Object) this, "Data written to TCP sockt " + IccUtils.bytesToHexString(con.txBuf.toByteArray()));
                    this.crnt_resp.resCode = ResultCode.OK;
                    this.crnt_resp.hasAdditionalInfo = false;
                    con.txBuf.reset();
                    con.txIndex = 0;
                    emptySpace = con.bufferSize - con.txBuf.size();
                    this.crnt_resp.data = new SendDataResponse(emptySpace);
                } catch (Exception e4) {
                    CatLog.m0d((Object) this, "Exception while sending to TCP packet : " + e4.getMessage());
                    this.crnt_resp.AdditionalInfo = 0;
                    con.txBuf.reset();
                    con.txIndex = 0;
                }
            }
            catService = mCatServicehandle;
            catService2 = mCatServicehandle;
            msg = catService.obtainMessage(109);
            msg.obj = this.crnt_resp;
            msg.sendToTarget();
        }
    }

    public void sendChannelStatusEvent(CatBIPConnection bipcon) {
        if (this.monitorChannelStatusEvent) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(ComprehensionTlvTag.CHANNEL_STATUS.value() | 128);
            buf.write(2);
            byte s = (byte) (bipcon.channelId & 7);
            if (bipcon.uiccTerminalIface.isServer()) {
                s = (byte) ((((CatBIPServerConnection) bipcon).linkState << 6) | s);
            } else if (((CatBIPClientConnection) bipcon).isLinkEstablished) {
                s = (byte) (s | 128);
            }
            buf.write(s);
            buf.write(bipcon.linkStateCause);
            CatEnvelopeMessage env = new CatEnvelopeMessage(10, 130, 129, buf.toByteArray());
            CatService catService = mCatServicehandle;
            CatService catService2 = mCatServicehandle;
            Message msg = catService.obtainMessage(106);
            CatLog.m2d("CatBIPManager", "sendChannelStatusEvent: Send EVENT_DOWNLOAD_CHANNEL_STATUS Envelope Message to mCatService handle");
            msg.obj = env;
            msg.sendToTarget();
            return;
        }
        CatLog.m2d("CatBIPManager", "sendChannelStatusEvent: not set");
    }

    public void sendDataAvailableEvent(CatBIPConnection bipcon) {
        if (this.monitorDataDownloadEvent || "LGT".equals("")) {
            int dataLength;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(ComprehensionTlvTag.CHANNEL_STATUS.value() | 128);
            buf.write(2);
            byte s = (byte) (bipcon.channelId & 7);
            if (bipcon.uiccTerminalIface.isServer()) {
                s = (byte) ((((CatBIPServerConnection) bipcon).linkState << 6) | s);
            } else if (((CatBIPClientConnection) bipcon).isLinkEstablished) {
                s = (byte) (s | 128);
            }
            buf.write(s);
            buf.write(0);
            buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value() | 128);
            buf.write(1);
            if (bipcon.uiccTerminalIface.isServer()) {
                dataLength = 255;
            } else {
                int bytesAvailable = ((CatBIPClientConnection) bipcon).rxBuf.size();
                dataLength = bytesAvailable > 255 ? 255 : bytesAvailable;
            }
            buf.write(dataLength);
            CatEnvelopeMessage env = new CatEnvelopeMessage(9, 130, 129, buf.toByteArray());
            CatService catService = mCatServicehandle;
            CatService catService2 = mCatServicehandle;
            Message msg = catService.obtainMessage(106);
            CatLog.m2d("CatBIPManager", "sendDataAvailableEvent: Send EVENT_DOWNLOAD_DATA_AVAILABLE Envelope Message to mCatService handle");
            msg.obj = env;
            msg.sendToTarget();
            return;
        }
        CatLog.m2d("CatBIPManager", "sendDataAvailableEvent: not set");
    }

    private void displayConnectionStatus() {
        String s = " ";
        CatLog.m0d((Object) this, "Displaying ConnectionStatus");
        for (int i = 0; i < channelIds.length; i++) {
            s = s + "Channel id" + Integer.toString(i + 1) + " assigned ? - " + Boolean.toString(channelIds[i]) + "\n";
        }
        CatLog.m0d((Object) this, s);
        CatLog.m0d((Object) this, "Total number of connections " + this.connection_list.size());
        Iterator i2 = this.connection_list.iterator();
        while (i2.hasNext()) {
            CatBIPConnection b = (CatBIPConnection) i2.next();
            CatLog.m0d((Object) this, "ChannelID: " + b.channelId + " iface(protcl , port) =  " + b.uiccTerminalIface.transportProtocol + ", " + b.uiccTerminalIface.portNumber);
        }
    }

    private boolean channelsAvailable() {
        for (int i = 0; i < 7; i++) {
            if (!channelIds[i]) {
                return true;
            }
        }
        return false;
    }

    private boolean checkLinkDrop() {
        Iterator i = this.connection_list.iterator();
        while (i.hasNext()) {
            if (((CatBIPConnection) i.next()).linkStateCause == (byte) 5) {
                CatLog.m0d((Object) this, "link drop occured");
                return true;
            }
        }
        return false;
    }

    private boolean checkPortInUse(int port) {
        CatLog.m0d((Object) this, "checkPortInUse");
        Iterator i = this.connection_list.iterator();
        while (i.hasNext()) {
            if (((CatBIPConnection) i.next()).uiccTerminalIface.portNumber == port) {
                CatLog.m0d((Object) this, "Port " + port + " in use. Cannot connect");
                return true;
            }
        }
        CatLog.m0d((Object) this, "Port " + port + " not in use. ");
        return false;
    }

    private void continueProcessingCloseChannel(NetworkInfo nwInfo) {
        CatLog.m0d((Object) this, "continueProcessingCloseChannel(nwinfo):");
        switch (C00495.$SwitchMap$android$net$NetworkInfo$State[nwInfo.getState().ordinal()]) {
            case 1:
                this.crnt_resp.resCode = ResultCode.OK;
                this.crnt_resp.hasAdditionalInfo = false;
                this.crnt_resp.data = null;
                CatService catService = mCatServicehandle;
                CatService catService2 = mCatServicehandle;
                Message msg_resp = catService.obtainMessage(109);
                msg_resp.obj = this.crnt_resp;
                msg_resp.sendToTarget();
                CatLog.m0d((Object) this, "Sent close Channel TR:");
                if (this.connection_list.isEmpty()) {
                    CatLog.m0d((Object) this, "Unregistering...");
                    this.mConnectivityListener.stopListening();
                    this.mConnectivityListener.unregisterHandler(this);
                    return;
                }
                return;
            default:
                return;
        }
    }

    private void continueProcessingOpenChannel(NetworkInfo nwInfo) {
        CatBIPClientConnection bipcon = (CatBIPClientConnection) getBIPConnection(this.currentChannel);
        CatLog.m0d((Object) this, "continueProcessingOpenChannel() " + nwInfo.getState());
        CatService catService;
        CatService catService2;
        Message termResp;
        switch (C00495.$SwitchMap$android$net$NetworkInfo$State[nwInfo.getState().ordinal()]) {
            case 1:
                CatLog.m0d((Object) this, "Sending Failure TR...");
                this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                this.crnt_resp.hasAdditionalInfo = true;
                this.crnt_resp.AdditionalInfo = 0;
                if (this.resp_pending) {
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    termResp = catService.obtainMessage(109);
                    termResp.obj = this.crnt_resp;
                    termResp.sendToTarget();
                    this.resp_pending = false;
                }
                freeChannel(bipcon);
                displayConnectionStatus();
                if (this.connection_list.isEmpty()) {
                    this.mConnectivityListener.stopListening();
                    this.mConnectivityListener.unregisterHandler(this);
                    return;
                }
                return;
            case 2:
                if (bipcon == null) {
                    CatLog.m0d((Object) this, "bipcon is null");
                    return;
                }
                boolean routeExists;
                byte[] addrBytes = bipcon.destination.getAddress();
                ConnectivityManager connectivityManager;
                ConnectivityManager connectivityManager2;
                if (addrBytes.length == 16) {
                    try {
                        InetAddress inetAddress = InetAddress.getByAddress(addrBytes);
                        if (this.feature == Phone.FEATURE_ENABLE_BIP) {
                            connectivityManager = this.connMgr;
                            connectivityManager2 = this.connMgr;
                            routeExists = connectivityManager.requestRouteToHostAddress(23, inetAddress);
                        } else {
                            connectivityManager = this.connMgr;
                            connectivityManager2 = this.connMgr;
                            routeExists = connectivityManager.requestRouteToHostAddress(5, inetAddress);
                        }
                    } catch (UnknownHostException e) {
                        Message msg_resp = new Message();
                        CatLog.m0d((Object) this, "Exception occurred while Setting up streams");
                        this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                        this.crnt_resp.hasAdditionalInfo = true;
                        this.crnt_resp.AdditionalInfo = 0;
                        catService = mCatServicehandle;
                        catService2 = mCatServicehandle;
                        msg_resp = catService.obtainMessage(109);
                        msg_resp.obj = this.crnt_resp;
                        msg_resp.sendToTarget();
                        channelIds[bipcon.channelId - 1] = false;
                        if (this.connection_list.remove(bipcon)) {
                            CatLog.m0d((Object) this, "Removed connection Successfully!!");
                        }
                        CatLog.m0d((Object) this, "StopListening() & unregisterHandler()");
                        this.mConnectivityListener.stopListening();
                        this.mConnectivityListener.unregisterHandler(this);
                        CatLog.m0d((Object) this, "Time to return");
                        return;
                    }
                }
                int addr = ((((addrBytes[3] & 255) << 24) | ((addrBytes[2] & 255) << 16)) | ((addrBytes[1] & 255) << 8)) | (addrBytes[0] & 255);
                if (this.feature == Phone.FEATURE_ENABLE_BIP) {
                    connectivityManager = this.connMgr;
                    connectivityManager2 = this.connMgr;
                    routeExists = connectivityManager.requestRouteToHost(23, addr);
                } else {
                    connectivityManager = this.connMgr;
                    connectivityManager2 = this.connMgr;
                    routeExists = connectivityManager.requestRouteToHost(5, addr);
                }
                if (routeExists) {
                    try {
                        CatLog.m0d((Object) this, "connMgr.requestRouteToHost returned true");
                        bipcon.setupStreams();
                        bipcon.receiver.start();
                        bipcon.isfirstTime = false;
                        this.crnt_resp.resCode = bipcon.mBuffsizeModified ? ResultCode.PRFRMD_WITH_MODIFICATION : ResultCode.OK;
                        this.crnt_resp.hasAdditionalInfo = false;
                    } catch (Exception e2) {
                        CatLog.m0d((Object) this, "HandleMessage: Exception occurred while Setting up streams");
                        if (this.resp_pending) {
                            this.crnt_resp.resCode = ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS;
                            this.crnt_resp.hasAdditionalInfo = true;
                            this.crnt_resp.AdditionalInfo = 0;
                            catService = mCatServicehandle;
                            catService2 = mCatServicehandle;
                            termResp = catService.obtainMessage(109);
                            termResp.obj = this.crnt_resp;
                            termResp.sendToTarget();
                            if (e2.getMessage().compareTo("TIMEOUT") != 0) {
                                freeChannel(bipcon);
                            } else {
                                channelIds[bipcon.channelId - 1] = false;
                                if (this.connection_list.remove(bipcon)) {
                                    CatLog.m0d((Object) this, "Removed connection  Successfully!!");
                                }
                            }
                            CatLog.m0d((Object) this, "Stoplistening(),  Unregistring handler");
                            this.mConnectivityListener.stopListening();
                            this.mConnectivityListener.unregisterHandler(this);
                            CatLog.m0d((Object) this, "Time to return");
                            this.resp_pending = false;
                            return;
                        }
                        return;
                    }
                }
                CatLog.m0d((Object) this, "connMgr.requestRouteToHost returned false");
                sendMessageDelayed(obtainMessage(5), 30000);
                if (this.resp_pending) {
                    catService = mCatServicehandle;
                    catService2 = mCatServicehandle;
                    termResp = catService.obtainMessage(109);
                    this.crnt_resp.data = new OpenChannelResponseData(bipcon);
                    termResp.obj = this.crnt_resp;
                    termResp.sendToTarget();
                    this.resp_pending = false;
                    return;
                }
                return;
            case 3:
                CatLog.m0d((Object) this, "Still Connecting...");
                return;
            default:
                return;
        }
    }

    private void setBipApn(CatBIPClientConnection bipcon) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext());
        if (pref == null) {
            CatLog.m0d((Object) this, "setBipApn : there is no default preferences");
            return;
        }
        Editor editor = pref.edit();
        CatLog.m0d((Object) this, "setBipApn : set values");
        editor.putBoolean("bip.pref.enable", true);
        editor.putString("bip.pref.apn", bipcon.NetworkAccessname);
        editor.putString("bip.pref.user", bipcon.userName);
        editor.putString("bip.pref.passwd", bipcon.passwd);
        editor.commit();
    }

    private void disableBipApn() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext());
        if (pref == null) {
            CatLog.m0d((Object) this, "disableBipApn : there is no default preferences");
            return;
        }
        Editor editor = pref.edit();
        CatLog.m0d((Object) this, "disableBipApn");
        editor.putBoolean("bip.pref.enable", false);
    }

    public void sendBipOtaFailIntent() {
        this.mContext.sendBroadcast(new Intent("com.sec.android.sktota.usim.FAIL"));
    }
}
