package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class SetupEventListParams extends CommandParams {
    int[] events;
    int numberOfEvents;

    SetupEventListParams(CommandDetails cmdDet, int numberOfEvents, int[] events) {
        super(cmdDet);
        this.numberOfEvents = numberOfEvents;
        this.events = events;
    }
}
