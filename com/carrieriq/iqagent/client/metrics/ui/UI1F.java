package com.carrieriq.iqagent.client.metrics.ui;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class UI1F extends Metric {
    public static final int ID = Metric.idFromString("UI1F");
    public int dwDuration;
    public int dwRunAppID;

    public UI1F() {
        super(ID);
    }

    public UI1F(int appInstanceId, int duration) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
