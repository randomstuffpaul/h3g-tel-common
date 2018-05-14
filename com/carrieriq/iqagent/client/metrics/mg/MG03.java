package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG03 extends Metric {
    public static final int ID = Metric.idFromString("MG03");
    public int dwSmsId;
    public String szOriginator;
    public String szSMSC;
    public short wNumFrags;
    public short wSize;

    public MG03() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
