package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG15 extends Metric {
    public static final int ID = Metric.idFromString("MG15");
    public int dwContentType;
    public int dwErrorCode;
    public String szLocationUrl;
    public String szMmsMsgId;
    public String szMmsTransId;
    public short wMmsVersion;
    public short wResultCode;

    public MG15() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
