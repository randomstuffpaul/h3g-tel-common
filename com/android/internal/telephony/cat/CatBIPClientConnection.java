package com.android.internal.telephony.cat;

import com.android.internal.telephony.ServiceStateTracker;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/* compiled from: CatBIPConnection */
class CatBIPClientConnection extends CatBIPConnection {
    BearerMode ConnectionMode;
    String NetworkAccessname;
    BearerDescription bDesc;
    InetAddress destination;
    DataInputStream in = null;
    boolean isLinkEstablished;
    boolean isfirstTime;
    int lastReadPosition;
    DataOutputStream out = null;
    String passwd;
    Thread receiver = null;
    ByteArrayOutputStream rxBuf;
    byte[] rxBytes;
    Object socket = null;
    ByteArrayOutputStream txBuf;
    int txIndex;
    String userName;

    public CatBIPClientConnection(BearerDescription bearerDesc, int buffSize, TransportLevel iface, CatBIPManager catBIPManager, String apn, BearerMode mode, DataDestinationAddress dest, String userName, String passwd) {
        super(buffSize, iface, catBIPManager);
        this.bDesc = bearerDesc;
        this.NetworkAccessname = apn;
        this.userName = userName;
        this.passwd = passwd;
        this.ConnectionMode = mode;
        this.isLinkEstablished = false;
        this.txIndex = 0;
        this.isfirstTime = true;
        if (dest != null) {
            try {
                this.destination = InetAddress.getByAddress(dest.address);
            } catch (UnknownHostException e) {
                CatLog.m0d((Object) this, "UnknownHostException while getting the Data destination IP Address");
                this.destination = null;
            }
        }
    }

    public void setupStreams() throws SocketTimeoutException, IOException {
        SocketTimeoutException ste;
        IOException io;
        Socket s = null;
        try {
            CatLog.m0d((Object) this, "Opening socket with" + this.destination.toString() + " at " + this.uiccTerminalIface.portNumber);
            byte[] tmp = this.destination.getAddress();
            for (byte b : tmp) {
                CatLog.m0d((Object) this, " " + b);
            }
            if (this.uiccTerminalIface.isTCPRemoteClient()) {
                CatLog.m0d((Object) this, "Opening TCP socket");
                Socket s2 = new Socket();
                try {
                    s2.connect(new InetSocketAddress(this.destination, this.uiccTerminalIface.portNumber), ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS);
                    this.socket = s2;
                    CatLog.m0d((Object) this, "Opening input stream");
                    this.in = new DataInputStream(s2.getInputStream());
                    CatLog.m0d((Object) this, "Opening output stream");
                    this.out = new DataOutputStream(s2.getOutputStream());
                    this.receiver = new Thread(new tcpRxThread(this));
                    CatLog.m0d((Object) this, "Created receiver thread");
                    s = s2;
                } catch (SocketTimeoutException e) {
                    ste = e;
                    s = s2;
                    this.isLinkEstablished = false;
                    this.isfirstTime = true;
                    CatLog.m0d((Object) this, "Socket Timeout Exception Socket is not connected\nlinkEstablished =" + this.isLinkEstablished);
                    CatLog.m0d((Object) this, ste.getMessage());
                    throw new SocketTimeoutException("TIMEOUT");
                } catch (IOException e2) {
                    io = e2;
                    s = s2;
                    this.isLinkEstablished = false;
                    this.isfirstTime = true;
                    CatLog.m0d((Object) this, "IO Exception while creating socket streams\nlinkEstablished =" + this.isLinkEstablished);
                    CatLog.m0d((Object) this, io.getMessage());
                    if (s != null) {
                        try {
                            s.close();
                        } catch (IOException e3) {
                        }
                    }
                    throw io;
                }
            }
            CatLog.m0d((Object) this, "Opening UDP socket");
            DatagramSocket ds = new DatagramSocket();
            ds.connect(this.destination, this.uiccTerminalIface.portNumber);
            this.socket = ds;
            this.receiver = new Thread(new udpRxThread(this));
            CatLog.m0d((Object) this, "Created receiver thread");
            this.isLinkEstablished = true;
            CatLog.m0d((Object) this, "Successfully setup streams!");
            this.txBuf = new ByteArrayOutputStream(this.bufferSize);
            this.rxBuf = new ByteArrayOutputStream(this.bufferSize);
            this.txIndex = 0;
            this.lastReadPosition = 0;
            CatLog.m0d((Object) this, "txBuff & rxBuff created!");
            this.isfirstTime = false;
        } catch (SocketTimeoutException e4) {
            ste = e4;
            this.isLinkEstablished = false;
            this.isfirstTime = true;
            CatLog.m0d((Object) this, "Socket Timeout Exception Socket is not connected\nlinkEstablished =" + this.isLinkEstablished);
            CatLog.m0d((Object) this, ste.getMessage());
            throw new SocketTimeoutException("TIMEOUT");
        } catch (IOException e5) {
            io = e5;
            this.isLinkEstablished = false;
            this.isfirstTime = true;
            CatLog.m0d((Object) this, "IO Exception while creating socket streams\nlinkEstablished =" + this.isLinkEstablished);
            CatLog.m0d((Object) this, io.getMessage());
            if (s != null) {
                s.close();
            }
            throw io;
        }
    }

    public void terminateStreams() {
        CatLog.m0d((Object) this, "Closing the streams  for channel ID = " + this.channelId);
        try {
            if (this.uiccTerminalIface.isTCPRemoteClient()) {
                if (this.receiver != null) {
                    this.receiver.interrupt();
                }
                CatLog.m0d((Object) this, "Closing input stream");
                if (this.in != null) {
                    this.in.close();
                }
                CatLog.m0d((Object) this, "Closing output stream");
                if (this.out != null) {
                    this.out.close();
                }
                Socket s = this.socket;
                CatLog.m0d((Object) this, "Closing socket");
                if (s != null) {
                    s.close();
                }
                this.isfirstTime = true;
            } else {
                CatLog.m0d((Object) this, "closing UDP socket");
                this.receiver.interrupt();
                DatagramSocket ds = this.socket;
                if (ds != null) {
                    ds.close();
                }
                this.isfirstTime = true;
            }
            CatLog.m0d((Object) this, "Closed Streams Successfully");
        } catch (IOException io) {
            CatLog.m0d((Object) this, "IO Exception while terminating the streams: " + io.getMessage());
        } catch (Exception e) {
            CatLog.m0d((Object) this, "A generic Exception while terminating the streams: " + e.getMessage());
        }
    }

    public byte[] getRxData(int length) {
        if (this.rxBuf.size() == 0) {
            return null;
        }
        CatLog.m0d((Object) this, "rxbuf.size != null!!");
        if (this.rxBytes == null) {
            this.rxBytes = this.rxBuf.toByteArray();
        }
        int bytesAvailable = this.rxBytes.length - this.lastReadPosition;
        CatLog.m0d((Object) this, "bytesAvailable = " + bytesAvailable + "  rxBytes.length = " + this.rxBytes.length + "  lastReadPosition = " + this.lastReadPosition);
        if (length > bytesAvailable) {
            length = bytesAvailable;
        }
        CatLog.m0d((Object) this, "length = " + length);
        byte[] tmp = new byte[length];
        System.arraycopy(this.rxBytes, this.lastReadPosition, tmp, 0, length);
        this.lastReadPosition += length;
        CatLog.m0d((Object) this, "lastReadPosition = " + this.lastReadPosition);
        if (this.lastReadPosition >= this.rxBytes.length) {
            this.rxBytes = null;
            CatLog.m0d((Object) this, "reset buffer rxbuf");
            this.lastReadPosition = 0;
            this.rxBuf.reset();
        }
        CatLog.m0d((Object) this, "return now!!!");
        return tmp;
    }
}
