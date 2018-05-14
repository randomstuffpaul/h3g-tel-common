package com.carrieriq.iqagent.client.metrics.hw;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class HW0E extends Metric {
    public static final int ID = Metric.idFromString("HW0E");
    public byte ucBatteryEvent;

    public HW0E() {
        super(ID);
    }

    public HW0E(byte event) {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
