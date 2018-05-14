package com.carrieriq.iqagent.client.metrics.mg;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class MG11 extends Metric {
    public static final int ID = Metric.idFromString("MG11");
    public int dwContentType;
    public int dwSize;
    public short shMmsVersion;
    public String szMmsTransId;
    public String szRecipient;
    public String szRelayURL;
    public String szSender;

    public MG11() {
        super(ID);
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
