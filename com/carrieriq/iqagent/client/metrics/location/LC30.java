package com.carrieriq.iqagent.client.metrics.location;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class LC30 extends Metric {
    public static final int ID = Metric.idFromString("LC30");
    public static byte IQ_LOC_METHOD_ALT = (byte) 3;
    public static byte IQ_LOC_METHOD_CELLULAR = (byte) 1;
    public static byte IQ_LOC_METHOD_UNKNOWN = (byte) 0;
    public static byte IQ_LOC_METHOD_WIFI = (byte) 2;
    public static byte IQ_LOC_RESULTS_IN_USE = (byte) 3;
    public static byte IQ_LOC_RESULTS_SUCCESS = (byte) 1;
    public static byte IQ_LOC_RESULTS_UNAVAILABLE = (byte) 2;
    public static byte IQ_LOC_RESULTS_UNKNOWN = (byte) 0;
    public static byte IQ_LOC_RESULTS_USER_DENIED = (byte) 4;
    public static byte IQ_LOC_RESULTS_USER_UNAUTHORIZED = (byte) 5;
    public int lLatitude;
    public int lLongitude;
    public long tTimestamp;
    public byte ucMethod;
    public byte ucResultsValid;
    public short wAccuracy;

    public LC30() {
        super(ID);
    }

    public void clear() {
    }

    public void setLatitude(double degrees) {
    }

    public void setLongitude(double degrees) {
    }

    public void setAccuracy(short meters) {
    }

    public void setGPSTimeStamp(long timestamp) {
    }

    public void setResults(byte results) {
    }

    public void setUnixTimeStamp(long timestamp) {
    }

    public void setMethod(byte method) {
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
