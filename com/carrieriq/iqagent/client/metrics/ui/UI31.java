package com.carrieriq.iqagent.client.metrics.ui;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class UI31 extends Metric {
    public static final byte EXIT_APP_INIT_ABNORMAL = (byte) 1;
    public static final byte EXIT_NORMAL = (byte) 0;
    public static final byte EXIT_SYS_INIT_ABNORMAL = (byte) 2;
    public static final byte EXIT_UNKNOWN = (byte) 3;
    public static final int ID = Metric.idFromString("UI31");
    public int dwRunAppID;
    public byte ucExitType;

    public UI31() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
