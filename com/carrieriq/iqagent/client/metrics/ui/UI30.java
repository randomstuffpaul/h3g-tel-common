package com.carrieriq.iqagent.client.metrics.ui;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class UI30 extends Metric {
    public static final int ID = Metric.idFromString("UI30");
    public int dwInstAppID;
    public int dwRunAppID;

    public UI30() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
