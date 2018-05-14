package com.android.internal.telephony.cat;

import java.io.IOException;

/* compiled from: CatBIPConnection */
class tcpRxThread implements Runnable {
    CatBIPClientConnection conn;
    volatile boolean stopRequestTCP = false;
    volatile boolean terminatedByException = false;

    public void run() {
        while (!this.stopRequestTCP) {
            try {
                if (Thread.interrupted()) {
                    this.stopRequestTCP = true;
                }
                if (this.conn.rxBuf.size() != 0 || CatService.isBIPCmdBeingProcessed()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        CatLog.m0d((Object) this, "Interrupt Received!");
                        this.stopRequestTCP = true;
                    }
                } else {
                    int size = this.conn.bufferSize;
                    CatLog.m0d((Object) this, "Maximum Packet Size negotiated by UICC " + size);
                    byte[] dataReceived = new byte[size];
                    int oneByte = this.conn.in.read(dataReceived);
                    CatLog.m0d((Object) this, "Length of data = " + oneByte);
                    if (oneByte != -1) {
                        this.conn.rxBuf.write(dataReceived, 0, oneByte);
                        this.conn.mCatBIPManager.sendDataAvailableEvent(this.conn);
                        CatLog.m0d((Object) this, "Read Data!!");
                    } else {
                        this.conn.isLinkEstablished = false;
                        if (this.conn.uiccTerminalIface.isServer()) {
                            this.conn.linkStateCause = (byte) 5;
                            this.conn.mCatBIPManager.sendChannelStatusEvent(this.conn);
                        }
                        this.stopRequestTCP = true;
                        CatLog.m0d((Object) this, "Connection terminated by BIP Server");
                    }
                }
            } catch (IOException e2) {
                CatLog.m0d((Object) this, "IOException : " + e2.getMessage());
                this.terminatedByException = true;
                Thread.yield();
                if (Thread.interrupted()) {
                    this.stopRequestTCP = true;
                }
            } catch (Exception ee) {
                CatLog.m0d((Object) this, "Exception : " + ee.getMessage());
                this.terminatedByException = true;
                Thread.yield();
                if (Thread.interrupted()) {
                    this.stopRequestTCP = true;
                }
            }
        }
    }

    public tcpRxThread(CatBIPClientConnection c) {
        this.conn = c;
    }
}
