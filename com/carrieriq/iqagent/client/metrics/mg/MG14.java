package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG14 extends Metric {
    public static final int ID = Metric.idFromString("MG14");
    public int dwErrorCode;
    public String szLocationUrl;
    public String szMmsTransId;
    public byte ucRetryCount;
    public byte ucState;
    public short wResultCode;

    public MG14() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
