package com.carrieriq.iqagent.client.metrics.gs;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class GS03 extends Metric {
    public static final int ID = Metric.idFromString("GS03");
    public int dwCallId;
    public int dwErrCode;
    public short wTermCode;

    public GS03() {
        super(ID);
    }

    public GS03(int callId, int errCode, short termCode) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
