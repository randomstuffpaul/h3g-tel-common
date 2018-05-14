package com.carrieriq.iqagent.client.metrics.gs;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class GS01 extends Metric {
    public static final int ID = Metric.idFromString("GS01");
    public int dwCallId;
    public String szNumber;
    public byte ucCallAttr;
    public byte ucCallState;

    public GS01() {
        super(ID);
    }

    public void setTerminated() {
    }

    public void setOriginated() {
    }

    public void setVoice() {
    }

    public void setVideo() {
    }

    public GS01(int callId, byte callState, String number) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
