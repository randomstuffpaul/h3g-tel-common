package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG12 extends Metric {
    public static final int ID = Metric.idFromString("MG12");
    public int dwErrorCode;
    public String szMmsMsgId;
    public String szMmsTransId;
    public short wMmsVersion;
    public short wResultCode;

    public MG12() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
