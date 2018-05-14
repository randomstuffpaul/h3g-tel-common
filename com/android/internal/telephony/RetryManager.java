package com.android.internal.telephony;

import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class RetryManager {
    public static final boolean DBG = false;
    public static final String LOG_TAG = "RetryManager";
    public static final boolean VDBG = false;
    private String mConfig;
    private int mCurMaxRetryCount;
    private int mMaxRetryCount;
    private ArrayList<RetryRec> mRetryArray = new ArrayList();
    private int mRetryCount;
    private boolean mRetryForever;
    private Random mRng = new Random();

    private static class RetryRec {
        int mDelayTime;
        int mRandomizationTime;

        RetryRec(int delayTime, int randomizationTime) {
            this.mDelayTime = delayTime;
            this.mRandomizationTime = randomizationTime;
        }
    }

    public String toString() {
        String ret = "RetryManager: { forever=" + this.mRetryForever + " maxRetry=" + this.mMaxRetryCount + " curMaxRetry=" + this.mCurMaxRetryCount + " retry=" + this.mRetryCount + " config={" + this.mConfig + "} retryArray={";
        Iterator i$ = this.mRetryArray.iterator();
        while (i$.hasNext()) {
            RetryRec r = (RetryRec) i$.next();
            ret = ret + r.mDelayTime + ":" + r.mRandomizationTime + " ";
        }
        return ret + "}}";
    }

    public boolean configure(int maxRetryCount, int retryTime, int randomizationTime) {
        if (!validateNonNegativeInt("maxRetryCount", maxRetryCount) || !validateNonNegativeInt("retryTime", retryTime) || !validateNonNegativeInt("randomizationTime", randomizationTime)) {
            return false;
        }
        this.mMaxRetryCount = maxRetryCount;
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        resetRetryCount();
        this.mRetryArray.clear();
        this.mRetryArray.add(new RetryRec(retryTime, randomizationTime));
        return true;
    }

    public boolean configure(String configStr) {
        if (configStr.startsWith("\"") && configStr.endsWith("\"")) {
            configStr = configStr.substring(1, configStr.length() - 1);
        }
        this.mConfig = configStr;
        if (TextUtils.isEmpty(configStr)) {
            return false;
        }
        int defaultRandomization = 0;
        this.mMaxRetryCount = 0;
        resetRetryCount();
        this.mRetryArray.clear();
        String[] strArray = configStr.split(",");
        for (int i = 0; i < strArray.length; i++) {
            String[] splitStr = strArray[i].split("=", 2);
            splitStr[0] = splitStr[0].trim();
            Pair<Boolean, Integer> value;
            if (splitStr.length > 1) {
                splitStr[1] = splitStr[1].trim();
                if (TextUtils.equals(splitStr[0], "default_randomization")) {
                    value = parseNonNegativeInt(splitStr[0], splitStr[1]);
                    if (!((Boolean) value.first).booleanValue()) {
                        return false;
                    }
                    defaultRandomization = ((Integer) value.second).intValue();
                } else if (!TextUtils.equals(splitStr[0], "max_retries")) {
                    Rlog.e(LOG_TAG, "Unrecognized configuration name value pair: " + strArray[i]);
                    return false;
                } else if (TextUtils.equals("infinite", splitStr[1])) {
                    this.mRetryForever = true;
                } else {
                    value = parseNonNegativeInt(splitStr[0], splitStr[1]);
                    if (!((Boolean) value.first).booleanValue()) {
                        return false;
                    }
                    this.mMaxRetryCount = ((Integer) value.second).intValue();
                }
            } else {
                splitStr = strArray[i].split(":", 2);
                splitStr[0] = splitStr[0].trim();
                RetryRec rr = new RetryRec(0, 0);
                value = parseNonNegativeInt("delayTime", splitStr[0]);
                if (!((Boolean) value.first).booleanValue()) {
                    return false;
                }
                rr.mDelayTime = ((Integer) value.second).intValue();
                if (splitStr.length > 1) {
                    splitStr[1] = splitStr[1].trim();
                    value = parseNonNegativeInt("randomizationTime", splitStr[1]);
                    if (!((Boolean) value.first).booleanValue()) {
                        return false;
                    }
                    rr.mRandomizationTime = ((Integer) value.second).intValue();
                } else {
                    rr.mRandomizationTime = defaultRandomization;
                }
                this.mRetryArray.add(rr);
            }
        }
        if (this.mRetryArray.size() > this.mMaxRetryCount) {
            this.mMaxRetryCount = this.mRetryArray.size();
        }
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        return true;
    }

    public boolean isRetryNeeded() {
        return this.mRetryForever || this.mRetryCount < this.mCurMaxRetryCount;
    }

    public int getRetryTimer() {
        int index;
        if (this.mRetryCount < this.mRetryArray.size()) {
            index = this.mRetryCount;
        } else {
            index = this.mRetryArray.size() - 1;
        }
        if (index < 0 || index >= this.mRetryArray.size()) {
            return 0;
        }
        return ((RetryRec) this.mRetryArray.get(index)).mDelayTime + nextRandomizationTime(index);
    }

    public int getRetryCount() {
        return this.mRetryCount;
    }

    public void increaseRetryCount() {
        this.mRetryCount++;
        if (this.mRetryCount > this.mCurMaxRetryCount) {
            this.mRetryCount = this.mCurMaxRetryCount;
        }
    }

    public void setRetryCount(int count) {
        this.mRetryCount = count;
        if (this.mRetryCount > this.mCurMaxRetryCount) {
            this.mRetryCount = this.mCurMaxRetryCount;
        }
        if (this.mRetryCount < 0) {
            this.mRetryCount = 0;
        }
    }

    public void setCurMaxRetryCount(int count) {
        this.mCurMaxRetryCount = count;
        if (this.mCurMaxRetryCount < 0) {
            this.mCurMaxRetryCount = 0;
        }
        setRetryCount(this.mRetryCount);
    }

    public void restoreCurMaxRetryCount() {
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        setRetryCount(this.mRetryCount);
    }

    public void setRetryForever(boolean retryForever) {
        this.mRetryForever = retryForever;
    }

    public void resetRetryCount() {
        this.mRetryCount = 0;
    }

    public void retryForeverUsingLastTimeout() {
        this.mRetryCount = this.mCurMaxRetryCount;
        this.mRetryForever = true;
    }

    public boolean isRetryForever() {
        return this.mRetryForever;
    }

    private Pair<Boolean, Integer> parseNonNegativeInt(String name, String stringValue) {
        try {
            int value = Integer.parseInt(stringValue);
            return new Pair(Boolean.valueOf(validateNonNegativeInt(name, value)), Integer.valueOf(value));
        } catch (NumberFormatException e) {
            Rlog.e(LOG_TAG, name + " bad value: " + stringValue, e);
            return new Pair(Boolean.valueOf(false), Integer.valueOf(0));
        }
    }

    private boolean validateNonNegativeInt(String name, int value) {
        if (value >= 0) {
            return true;
        }
        Rlog.e(LOG_TAG, name + " bad value: is < 0");
        return false;
    }

    private int nextRandomizationTime(int index) {
        int randomTime = ((RetryRec) this.mRetryArray.get(index)).mRandomizationTime;
        if (randomTime == 0) {
            return 0;
        }
        return this.mRng.nextInt(randomTime);
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, "[RM] " + s);
    }
}
