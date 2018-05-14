package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG01 extends Metric {
    public static final int ID = Metric.idFromString("MG01");
    public int dwSmsId;
    public String szRecipient;
    public String szSMSC;
    public short wNumFrags;
    public short wSize;

    public MG01() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
