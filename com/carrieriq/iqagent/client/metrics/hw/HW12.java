package com.carrieriq.iqagent.client.metrics.hw;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class HW12 extends Metric {
    public static final int ID = Metric.idFromString("HW12");
    public byte ucCause;
    public byte ucProcessor;

    public HW12() {
        super(ID);
    }

    public HW12(byte cause, byte processor) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
