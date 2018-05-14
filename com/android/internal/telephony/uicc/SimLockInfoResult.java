package com.android.internal.telephony.uicc;

import android.util.Log;

public class SimLockInfoResult {
    private static final int LOCK_ACL = 11;
    private static final int LOCK_CORP_PERS = 8;
    private static final int LOCK_FD = 4;
    private static final int LOCK_NETWORK_PERS = 5;
    private static final int LOCK_NETWORK_SUBSET_PERS = 6;
    private static final int LOCK_PH_FSIM = 2;
    private static final int LOCK_PH_SIM = 1;
    private static final int LOCK_PIN2 = 9;
    private static final int LOCK_PUK2 = 10;
    private static final int LOCK_READY = 0;
    private static final int LOCK_SIM = 3;
    private static final int LOCK_SP_PERS = 7;
    static final String LOG_TAG = "SimLockInfoResult";
    private static final int NOT_NEED = 0;
    private static final int NO_SIM = 128;
    private static final int PERM_BLOCKED = 5;
    public static final int PIN = 1;
    public static final int PIN2 = 3;
    private static final int PIN2_DISABLE = 6;
    public static final int PUK = 2;
    public static final int PUK2 = 4;
    static int Pin2_Retry = 0;
    static int Pin_Retry = 0;
    static int Puk2_Retry = 0;
    static int Puk_Retry = 0;
    static int isPermBlocked = 0;
    static int lockPin2Key = 0;
    static int lockPinKey = 0;
    int lockKey = 0;
    int lockType = 0;
    int numRetry = 0;
    int num_lock_type = 0;

    public SimLockInfoResult(int num_lock_type, int lockType, int lockKey, int numRetry) {
        this.num_lock_type = num_lock_type;
        this.lockType = lockType;
        this.lockKey = lockKey;
        this.numRetry = numRetry;
        Log.i(LOG_TAG, "num:" + num_lock_type + ", lockType:" + lockType + ", lock_key:" + lockKey + ", numRetry:" + numRetry);
        if (lockType == 10) {
            Puk2_Retry = numRetry;
            return;
        }
        switch (lockKey) {
            case 0:
                Pin_Retry = numRetry;
                lockPinKey = lockKey;
                Log.i(LOG_TAG, "NOT_NEED numRetry: " + Pin_Retry);
                return;
            case 1:
                Pin_Retry = numRetry;
                lockPinKey = lockKey;
                Log.i(LOG_TAG, "PIN numRetry: " + Pin_Retry);
                return;
            case 2:
                Puk_Retry = numRetry;
                lockPinKey = lockKey;
                Log.i(LOG_TAG, "PUK numRetry: " + Puk_Retry);
                return;
            case 3:
            case 6:
                Pin2_Retry = numRetry;
                lockPin2Key = lockKey;
                Log.i(LOG_TAG, "PIN2 numRetry: " + Pin2_Retry);
                return;
            case 4:
                Puk2_Retry = numRetry;
                lockPin2Key = lockKey;
                Log.i(LOG_TAG, "PUK2 numRetry: " + Puk2_Retry);
                return;
            case 5:
                if (lockType == 3) {
                    Pin_Retry = 0;
                    Puk_Retry = 0;
                    isPermBlocked = 1;
                    lockPinKey = lockKey;
                } else if (lockType == 9) {
                    Pin2_Retry = 0;
                    Puk2_Retry = 0;
                    lockPin2Key = lockKey;
                }
                Log.i(LOG_TAG, "Permernet blocked");
                return;
            default:
                return;
        }
    }

    void setLockInfoResult(SimLockInfoResult simLockInfoResult) {
        this.num_lock_type = simLockInfoResult.num_lock_type;
        this.lockType = simLockInfoResult.lockType;
        this.lockKey = simLockInfoResult.lockKey;
        this.numRetry = simLockInfoResult.numRetry;
        Log.i(LOG_TAG, "num:" + this.num_lock_type + ", lockType:" + this.lockType + ", lock_key:" + this.lockKey + ", numRetry:" + this.numRetry);
        if (simLockInfoResult.lockType == 10) {
            Puk2_Retry = simLockInfoResult.numRetry;
            return;
        }
        switch (simLockInfoResult.lockKey) {
            case 0:
                Pin_Retry = simLockInfoResult.numRetry;
                lockPinKey = simLockInfoResult.lockKey;
                Log.i(LOG_TAG, "NOT_NEED numRetry: " + Pin_Retry);
                return;
            case 1:
                Pin_Retry = simLockInfoResult.numRetry;
                lockPinKey = simLockInfoResult.lockKey;
                Log.i(LOG_TAG, "PIN numRetry: " + Pin_Retry);
                return;
            case 2:
                Puk_Retry = simLockInfoResult.numRetry;
                lockPinKey = simLockInfoResult.lockKey;
                Log.i(LOG_TAG, "PUK numRetry: " + Puk_Retry);
                return;
            case 3:
            case 6:
                lockPin2Key = simLockInfoResult.lockKey;
                Pin2_Retry = simLockInfoResult.numRetry;
                Log.i(LOG_TAG, "PIN2 numRetry: " + Pin2_Retry);
                return;
            case 4:
                Puk2_Retry = simLockInfoResult.numRetry;
                lockPin2Key = simLockInfoResult.lockKey;
                Log.i(LOG_TAG, "PUK2 numRetry: " + Puk2_Retry);
                return;
            case 5:
                if (simLockInfoResult.lockType == 3) {
                    Pin_Retry = 0;
                    Puk_Retry = 0;
                    isPermBlocked = 1;
                    lockPinKey = simLockInfoResult.lockKey;
                } else if (simLockInfoResult.lockType == 9) {
                    Pin2_Retry = 0;
                    Puk2_Retry = 0;
                    lockPin2Key = simLockInfoResult.lockKey;
                }
                Log.i(LOG_TAG, "Permernet blocked");
                return;
            default:
                return;
        }
    }

    public void setLockInfoResult(int Pin_Retry, int Puk_Retry, int Pin2_Retry, int Puk2_Retry) {
        Log.i(LOG_TAG, "Pin_Retry:" + Pin_Retry + ", Puk_Retry:" + Puk_Retry + ", Pin2_Retry:" + Pin2_Retry + ", Puk2_Retry:" + Puk2_Retry);
        if (Pin_Retry != -1) {
            Pin_Retry = Pin_Retry;
        }
        if (Puk_Retry != -1) {
            Puk_Retry = Puk_Retry;
        }
        if (Pin2_Retry != -1) {
            Pin2_Retry = Pin2_Retry;
        }
        if (Puk2_Retry != -1) {
            Puk2_Retry = Puk2_Retry;
        }
    }

    public void setLockInfoResult(int Pin_Retry, int Puk_Retry, int Pin2_Retry, int Puk2_Retry, int lockKey, int lockKey2) {
        Log.i(LOG_TAG, "Pin_Retry:" + Pin_Retry + ", Puk_Retry:" + Puk_Retry + ", Pin2_Retry:" + Pin2_Retry + ", Puk2_Retry:" + Puk2_Retry + ", lockKey:" + lockKey + ", lockKey2:" + lockKey2);
        if (Pin_Retry != -1) {
            Pin_Retry = Pin_Retry;
        }
        if (Puk_Retry != -1) {
            Puk_Retry = Puk_Retry;
        }
        if (Pin2_Retry != -1) {
            Pin2_Retry = Pin2_Retry;
        }
        if (Puk2_Retry != -1) {
            Puk2_Retry = Puk2_Retry;
        }
        lockPinKey = lockKey;
        lockPin2Key = lockKey2;
    }

    public int getLockPinKey() {
        return lockPinKey;
    }

    public int getLockPin2Key() {
        return lockPin2Key;
    }

    public int getPinRetry() {
        return Pin_Retry;
    }

    public int getPin2Retry() {
        return Pin2_Retry;
    }

    public int getPukRetry() {
        return Puk_Retry;
    }

    public int getPuk2Retry() {
        return Puk2_Retry;
    }

    public int isSimBlocked() {
        return isPermBlocked;
    }
}
