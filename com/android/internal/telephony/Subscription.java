package com.android.internal.telephony;

import android.telephony.Rlog;
import android.text.TextUtils;

public final class Subscription {
    private static final String LOG_TAG = "Subscription";
    public static final int SUBSCRIPTION_INDEX_INVALID = -1;
    private boolean DEBUG = false;
    public String appId;
    public String appLabel;
    public String appType;
    public String iccId;
    public int m3gpp2Index;
    public int m3gppIndex;
    public int slotId;
    public int subId;
    public SubscriptionStatus subStatus;

    public enum SubscriptionStatus {
        SUB_DEACTIVATE,
        SUB_ACTIVATE,
        SUB_ACTIVATED,
        SUB_DEACTIVATED,
        SUB_INVALID
    }

    public Subscription() {
        clear();
    }

    public String toString() {
        return "Subscription = { slotId = " + this.slotId + ", 3gppIndex = " + this.m3gppIndex + ", 3gpp2Index = " + this.m3gpp2Index + ", subId = " + this.subId + ", subStatus = " + this.subStatus + ", appId = " + this.appId + ", appLabel = " + this.appLabel + ", appType = " + this.appType + ", iccId = " + this.iccId + " }";
    }

    public boolean equals(Subscription sub) {
        if (sub == null) {
            Rlog.d(LOG_TAG, "Subscription.equals: sub == null");
        } else if (this.slotId == sub.slotId && this.m3gppIndex == sub.m3gppIndex && this.m3gpp2Index == sub.m3gpp2Index && this.subId == sub.subId && this.subStatus == sub.subStatus && (((TextUtils.isEmpty(this.appId) && TextUtils.isEmpty(sub.appId)) || TextUtils.equals(this.appId, sub.appId)) && (((TextUtils.isEmpty(this.appLabel) && TextUtils.isEmpty(sub.appLabel)) || TextUtils.equals(this.appLabel, sub.appLabel)) && (((TextUtils.isEmpty(this.appType) && TextUtils.isEmpty(sub.appType)) || TextUtils.equals(this.appType, sub.appType)) && ((TextUtils.isEmpty(this.iccId) && TextUtils.isEmpty(sub.iccId)) || TextUtils.equals(this.iccId, sub.iccId)))))) {
            return true;
        }
        return false;
    }

    public boolean isSame(Subscription sub) {
        if (sub != null) {
            if (this.DEBUG) {
                Rlog.d(LOG_TAG, "isSame(): this = " + this.m3gppIndex + ":" + this.m3gpp2Index + ":" + this.appId + ":" + this.appType + ":" + this.iccId);
                Rlog.d(LOG_TAG, "compare with = " + sub.m3gppIndex + ":" + sub.m3gpp2Index + ":" + sub.appId + ":" + sub.appType + ":" + sub.iccId);
            }
            if (this.m3gppIndex == sub.m3gppIndex && this.m3gpp2Index == sub.m3gpp2Index && (((TextUtils.isEmpty(this.appId) && TextUtils.isEmpty(sub.appId)) || TextUtils.equals(this.appId, sub.appId)) && (((TextUtils.isEmpty(this.appType) && TextUtils.isEmpty(sub.appType)) || TextUtils.equals(this.appType, sub.appType)) && ((TextUtils.isEmpty(this.iccId) && TextUtils.isEmpty(sub.iccId)) || TextUtils.equals(this.iccId, sub.iccId))))) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        this.slotId = -1;
        this.m3gppIndex = -1;
        this.m3gpp2Index = -1;
        this.subId = -1;
        this.subStatus = SubscriptionStatus.SUB_INVALID;
        this.appId = null;
        this.appLabel = null;
        this.appType = null;
        this.iccId = null;
    }

    public Subscription copyFrom(Subscription from) {
        if (from != null) {
            this.slotId = from.slotId;
            this.m3gppIndex = from.m3gppIndex;
            this.m3gpp2Index = from.m3gpp2Index;
            this.subId = from.subId;
            this.subStatus = from.subStatus;
            if (from.appId != null) {
                this.appId = new String(from.appId);
            }
            if (from.appLabel != null) {
                this.appLabel = new String(from.appLabel);
            }
            if (from.appType != null) {
                this.appType = new String(from.appType);
            }
            if (from.iccId != null) {
                this.iccId = new String(from.iccId);
            }
        }
        return this;
    }

    public int getAppIndex() {
        if (this.m3gppIndex != -1) {
            return this.m3gppIndex;
        }
        return this.m3gpp2Index;
    }
}
