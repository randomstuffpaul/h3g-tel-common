package com.android.internal.telephony.uicc;

public interface IsimRecords {
    String getAid();

    String getBtid();

    String getIsimChallengeResponse(String str);

    String getIsimDomain();

    String getIsimImpi();

    String[] getIsimImpu();

    String getIsimIst();

    String getIsimMsisdn();

    String[] getIsimPcscf();

    String getKeyLifetime();

    byte[] getRand();

    boolean isGbaSupported();
}
