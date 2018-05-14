package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG02 extends Metric {
    public static final int ID = Metric.idFromString("MG02");
    public int dwErrCode;
    public int dwSmsId;
    public short wResultCode;

    public MG02() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
