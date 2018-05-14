package com.carrieriq.iqagent.client;

public class IQClient {
    public boolean checkWAPPush(byte[] b) {
        return false;
    }

    public boolean checkSMS(String s) {
        return false;
    }

    public int submitMetric(Metric metric) {
        return -1;
    }

    public int submitMetric(int metricID, long timestamp, byte[] payloadBytes, int payloadOffs, int payloadLen) {
        return -1;
    }
}
