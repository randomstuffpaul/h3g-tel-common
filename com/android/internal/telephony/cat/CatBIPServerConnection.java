package com.android.internal.telephony.cat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/* compiled from: CatBIPConnection */
class CatBIPServerConnection extends CatBIPConnection {
    ByteArrayOutputStream byteArrayWriter = null;
    int lastReadPosition = 0;
    byte linkState = (byte) 0;
    Thread listener = null;
    BufferedInputStream reader = null;
    ServerSocket server = null;
    Socket socket = null;
    ByteArrayOutputStream storeSendData = null;
    BufferedOutputStream writer = null;

    public CatBIPServerConnection(int buffSize, TransportLevel iface, CatBIPManager catBIPManager) {
        super(buffSize, iface, catBIPManager);
    }

    public void terminateStreams() {
        CatLog.m0d((Object) this, "Closing the streams  for channel ID = " + this.channelId);
        try {
            if (this.listener != null) {
                this.listener.interrupt();
            }
            if (this.server != null) {
                this.server.close();
            }
            if (this.storeSendData != null) {
                this.storeSendData.close();
            }
            if (this.byteArrayWriter != null) {
                this.byteArrayWriter.close();
            }
            if (this.reader != null) {
                this.reader.close();
            }
            if (this.writer != null) {
                this.writer.close();
            }
            if (this.socket != null) {
                this.socket.close();
            }
            CatLog.m0d((Object) this, "handleCloseChannel: Closed socket and all streams!");
        } catch (IOException ioe) {
            CatLog.m0d((Object) this, "handleCloseChannel; IOException: " + ioe.getMessage());
        }
    }
}
