package com.carrieriq.iqagent.client.metrics.location;

import com.carrieriq.iqagent.client.Metric;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class LC18 extends Metric {
    public static final int ID = Metric.idFromString("LC18");
    public int lAltitude;
    public int lHeading;
    public int lLatitude;
    public int lLongitude;
    public int lUncertiantyAint;
    public int lUncertiantyAltitude;
    public int lUncertiantyAngle;
    public int lUncertiantyPerpendicular;
    public int lVelocityHorizontal;
    public int lVelocityVertical;
    public byte ucFieldsValid;
    public byte ucGpsRequestType;
    public byte ucGpsResult;
    public byte ucGpsSource;

    public LC18() {
        super(ID);
    }

    public void clear() {
    }

    public void setLatitude(double degrees) {
    }

    public void setLongitude(double degrees) {
    }

    public void setAltitude(double meters) {
    }

    public void setVelocity(float meters_per_sec) {
    }

    public void setBearing(float degrees) {
    }

    public int serialize(ByteBuffer out) throws BufferOverflowException {
        return -1;
    }
}
