package com.carrieriq.iqagent.client;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class Metric {
    public static final long CURRENT_TIME = -1;
    public int metricID;
    public long timestamp;

    public static int idFromString(String _id) {
        return -1;
    }

    public static void idToBytes(int _id, byte[] array, int offset) {
    }

    public static String idToString(int _id) {
        return null;
    }

    public Metric(int _metricID) {
    }

    public Metric(int _metricID, long _ts) {
    }

    public void setTimestamp(long _ts) {
    }

    public void szStringOut(ByteBuffer out, String iString) throws BufferOverflowException {
    }

    public void szStringOutPadToWord(ByteBuffer out, String aString) throws BufferOverflowException {
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return 0;
    }
}
