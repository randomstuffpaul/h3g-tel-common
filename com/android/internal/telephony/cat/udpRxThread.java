package com.android.internal.telephony.cat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/* compiled from: CatBIPConnection */
class udpRxThread implements Runnable {
    CatBIPClientConnection conn;
    volatile boolean stopRequestUDP = false;

    public void run() {
        while (!this.stopRequestUDP) {
            try {
                if (Thread.interrupted()) {
                    this.stopRequestUDP = true;
                }
                if (this.conn.rxBuf.size() != 0 || CatService.isBIPCmdBeingProcessed()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        CatLog.m0d((Object) this, "Interrupt Received!");
                        this.stopRequestUDP = true;
                    }
                } else {
                    DatagramSocket ds = this.conn.socket;
                    int size = this.conn.bufferSize;
                    CatLog.m0d((Object) this, "Maximum UDP Buffer Size that can be received " + size);
                    DatagramPacket dp = new DatagramPacket(new byte[size], size);
                    ds.receive(dp);
                    CatLog.m0d((Object) this, "Length of UDP data received from network " + dp.getLength());
                    this.conn.rxBuf.write(dp.getData(), 0, dp.getLength());
                    CatLog.m0d((Object) this, "Size of rxBuf : " + this.conn.rxBuf.size());
                    this.conn.mCatBIPManager.sendDataAvailableEvent(this.conn);
                }
            } catch (IOException e2) {
                CatLog.m0d((Object) this, "IOException : " + e2.getMessage());
                Thread.yield();
                if (Thread.interrupted()) {
                    this.stopRequestUDP = true;
                }
            } catch (Exception ee) {
                CatLog.m0d((Object) this, "Exception : " + ee.getMessage());
                Thread.yield();
                if (Thread.interrupted()) {
                    this.stopRequestUDP = true;
                }
            }
        }
    }

    public udpRxThread(CatBIPClientConnection c) {
        this.conn = c;
    }
}
