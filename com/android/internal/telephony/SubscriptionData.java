package com.android.internal.telephony;

import java.util.Arrays;

public class SubscriptionData {
    public Subscription[] subscription;

    public SubscriptionData(int numSub) {
        this.subscription = new Subscription[numSub];
        for (int i = 0; i < numSub; i++) {
            this.subscription[i] = new Subscription();
        }
    }

    public int getLength() {
        if (this.subscription != null) {
            return this.subscription.length;
        }
        return 0;
    }

    public SubscriptionData copyFrom(SubscriptionData from) {
        if (from != null) {
            this.subscription = new Subscription[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                this.subscription[i] = new Subscription();
                this.subscription[i].copyFrom(from.subscription[i]);
            }
        }
        return this;
    }

    public String getIccId() {
        if (this.subscription.length <= 0 || this.subscription[0] == null) {
            return null;
        }
        return this.subscription[0].iccId;
    }

    public boolean hasSubscription(Subscription sub) {
        for (Subscription isSame : this.subscription) {
            if (isSame.isSame(sub)) {
                return true;
            }
        }
        return false;
    }

    public Subscription getSubscription(Subscription sub) {
        for (int i = 0; i < this.subscription.length; i++) {
            if (this.subscription[i].isSame(sub)) {
                return this.subscription[i];
            }
        }
        return null;
    }

    public String toString() {
        return Arrays.toString(this.subscription);
    }
}
