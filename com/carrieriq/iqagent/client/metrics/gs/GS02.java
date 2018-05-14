package com.carrieriq.iqagent.client.metrics.gs;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class GS02 extends Metric {
    public static final int ID = Metric.idFromString("GS02");
    public int dwCallId;
    public byte ucCallState;

    public GS02() {
        super(ID);
    }

    public GS02(int callId, byte callState) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
