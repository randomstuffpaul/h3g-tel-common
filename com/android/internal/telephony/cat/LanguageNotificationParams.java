package com.android.internal.telephony.cat;

/* compiled from: CommandParams */
class LanguageNotificationParams extends CommandParams {
    boolean mInitialLanguage;
    String mLanguage;

    LanguageNotificationParams(CommandDetails cmdDet, String language, boolean initialLanguage) {
        super(cmdDet);
        this.mLanguage = language;
        this.mInitialLanguage = initialLanguage;
    }
}
