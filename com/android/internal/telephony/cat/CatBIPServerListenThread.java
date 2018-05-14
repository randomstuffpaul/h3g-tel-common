package com.android.internal.telephony.cat;

import java.net.ServerSocket;
import java.net.Socket;

/* compiled from: CatBIPServerListenTread */
class CatBIPServerListenThread implements Runnable {
    CatBIPServerConnection bipcon = null;
    CatBIPManager mCatBIPManager = null;
    Socket f5s = null;
    ServerSocket serverSocket = null;
    volatile boolean stopRequest = false;

    public void run() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0116 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r7 = this;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Port Number : ";
        r3 = r3.append(r4);
        r4 = r7.bipcon;
        r4 = r4.uiccTerminalIface;
        r4 = r4.portNumber;
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        r3 = new java.net.ServerSocket;	 Catch:{ IOException -> 0x0125 }
        r4 = r7.bipcon;	 Catch:{ IOException -> 0x0125 }
        r4 = r4.uiccTerminalIface;	 Catch:{ IOException -> 0x0125 }
        r4 = r4.portNumber;	 Catch:{ IOException -> 0x0125 }
        r3.<init>(r4);	 Catch:{ IOException -> 0x0125 }
        r7.serverSocket = r3;	 Catch:{ IOException -> 0x0125 }
        r3 = "Server socket created.";	 Catch:{ IOException -> 0x0125 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ IOException -> 0x0125 }
        r3 = r7.bipcon;
        r4 = r7.serverSocket;
        r3.server = r4;
        r1 = 0;
        r3 = r7.bipcon;
        r3 = r3.bufferSize;
        r0 = new byte[r3];
    L_0x003b:
        r3 = r7.stopRequest;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        if (r3 != 0) goto L_0x0161;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
    L_0x003f:
        r3 = r7.serverSocket;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.accept();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r7.f5s = r3;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = "Connection Accepted";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = r7.f5s;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.socket = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = "Sending Channel Status event ";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 2;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.linkState = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 0;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.linkStateCause = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.mCatBIPManager;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.sendChannelStatusEvent(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = new java.io.BufferedInputStream;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r5.socket;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r5.getInputStream();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r6 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r6 = r6.bufferSize;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4.<init>(r5, r6);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.reader = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = new java.io.BufferedOutputStream;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r5.socket;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = r5.getOutputStream();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r6 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r6 = r6.bufferSize;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4.<init>(r5, r6);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.writer = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = new java.io.ByteArrayOutputStream;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4.<init>(r5);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.byteArrayWriter = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = new java.io.ByteArrayOutputStream;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r5 = 255; // 0xff float:3.57E-43 double:1.26E-321;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4.<init>(r5);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.storeSendData = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 0;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.lastReadPosition = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = "Reading data from input stream...";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
    L_0x00b4:
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.reader;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r1 = r3.read(r0);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = -1;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        if (r1 == r3) goto L_0x0141;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
    L_0x00bf:
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.byteArrayWriter;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 0;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.write(r0, r4, r1);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.<init>();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = "bytesRead=[";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r1);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = "], sendDataAvailable Event";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.toString();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.mCatBIPManager;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.sendDataAvailableEvent(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = "Reading data from input stream...";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        goto L_0x00b4;
    L_0x00f2:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.<init>();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = "Exception while handling connection: ";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = r2.getMessage();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.toString();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.f5s;
        if (r3 == 0) goto L_0x0116;
    L_0x0111:
        r3 = r7.f5s;
        r3.close();
    L_0x0116:
        r3 = r7.serverSocket;	 Catch:{ Exception -> 0x0203 }
        if (r3 == 0) goto L_0x011f;	 Catch:{ Exception -> 0x0203 }
    L_0x011a:
        r3 = r7.serverSocket;	 Catch:{ Exception -> 0x0203 }
        r3.close();	 Catch:{ Exception -> 0x0203 }
    L_0x011f:
        r3 = "Server thread stopped.";
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
    L_0x0124:
        return;
    L_0x0125:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "IOException while creating server socket: ";
        r3 = r3.append(r4);
        r4 = r2.getMessage();
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x0124;
    L_0x0141:
        r3 = "Input stream end reached.";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.f5s;	 Catch:{ Exception -> 0x0179 }
        r3.close();	 Catch:{ Exception -> 0x0179 }
    L_0x014b:
        r3 = 0;
        r7.f5s = r3;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 1;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.linkState = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r7.bipcon;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = 0;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.linkStateCause = r4;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = java.lang.Thread.interrupted();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        if (r3 == 0) goto L_0x003b;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
    L_0x015e:
        r3 = 1;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r7.stopRequest = r3;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
    L_0x0161:
        r3 = r7.f5s;	 Catch:{ Exception -> 0x01ae }
        if (r3 == 0) goto L_0x016a;	 Catch:{ Exception -> 0x01ae }
    L_0x0165:
        r3 = r7.f5s;	 Catch:{ Exception -> 0x01ae }
        r3.close();	 Catch:{ Exception -> 0x01ae }
    L_0x016a:
        r3 = r7.serverSocket;	 Catch:{ Exception -> 0x01ca }
        if (r3 == 0) goto L_0x0173;	 Catch:{ Exception -> 0x01ca }
    L_0x016e:
        r3 = r7.serverSocket;	 Catch:{ Exception -> 0x01ca }
        r3.close();	 Catch:{ Exception -> 0x01ca }
    L_0x0173:
        r3 = "Server thread stopped.";
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x0124;
    L_0x0179:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3.<init>();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = "Exception while closing connection socket: ";	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r4 = r2.getMessage();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        r3 = r3.toString();	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);	 Catch:{ Exception -> 0x00f2, all -> 0x0195 }
        goto L_0x014b;
    L_0x0195:
        r3 = move-exception;
        r4 = r7.f5s;	 Catch:{ Exception -> 0x0220 }
        if (r4 == 0) goto L_0x019f;	 Catch:{ Exception -> 0x0220 }
    L_0x019a:
        r4 = r7.f5s;	 Catch:{ Exception -> 0x0220 }
        r4.close();	 Catch:{ Exception -> 0x0220 }
    L_0x019f:
        r4 = r7.serverSocket;	 Catch:{ Exception -> 0x023d }
        if (r4 == 0) goto L_0x01a8;	 Catch:{ Exception -> 0x023d }
    L_0x01a3:
        r4 = r7.serverSocket;	 Catch:{ Exception -> 0x023d }
        r4.close();	 Catch:{ Exception -> 0x023d }
    L_0x01a8:
        r4 = "Server thread stopped.";
        com.android.internal.telephony.cat.CatLog.m0d(r7, r4);
        throw r3;
    L_0x01ae:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Exception while closing connection socket: ";
        r3 = r3.append(r4);
        r4 = r2.getMessage();
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x016a;
    L_0x01ca:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Exception while closing server socket: ";
        r3 = r3.append(r4);
        r4 = r2.getLocalizedMessage();
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x0173;
    L_0x01e6:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Exception while closing connection socket: ";
        r3 = r3.append(r4);
        r4 = r2.getMessage();
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x0116;
    L_0x0203:
        r2 = move-exception;
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "Exception while closing server socket: ";
        r3 = r3.append(r4);
        r4 = r2.getLocalizedMessage();
        r3 = r3.append(r4);
        r3 = r3.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r3);
        goto L_0x011f;
    L_0x0220:
        r2 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "Exception while closing connection socket: ";
        r4 = r4.append(r5);
        r5 = r2.getMessage();
        r4 = r4.append(r5);
        r4 = r4.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r4);
        goto L_0x019f;
    L_0x023d:
        r2 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "Exception while closing server socket: ";
        r4 = r4.append(r5);
        r5 = r2.getLocalizedMessage();
        r4 = r4.append(r5);
        r4 = r4.toString();
        com.android.internal.telephony.cat.CatLog.m0d(r7, r4);
        goto L_0x01a8;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cat.CatBIPServerListenThread.run():void");
    }

    public CatBIPServerListenThread(CatBIPConnection con, CatBIPManager bipManager) {
        this.bipcon = (CatBIPServerConnection) con;
        this.mCatBIPManager = bipManager;
    }
}
