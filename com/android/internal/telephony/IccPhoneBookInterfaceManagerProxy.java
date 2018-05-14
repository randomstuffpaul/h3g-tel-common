package com.android.internal.telephony;

import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import java.util.List;

public class IccPhoneBookInterfaceManagerProxy {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public void setmIccPhoneBookInterfaceManager(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public int updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String email, int index, String pin2) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid, newTag, newPhoneNumber, email, index, pin2);
    }

    public int updateAdnRecordsInEfByIndexUsingAR(int efid, AdnRecord newAdn, int index, String pin2) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndexUsingAR(efid, newAdn, index, pin2);
    }

    public List<AdnRecord> getAdnRecordsInEfInit(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsInEfInit(efid);
    }

    public int[] getAdnRecordsSize(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    public UsimPhonebookCapaInfo getUsimPBCapaInfo() {
        return this.mIccPhoneBookInterfaceManager.getUsimPBCapaInfo();
    }

    public int[] getAdnLikesInfo(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnLikesInfo(efid);
    }

    public int getAdnLikesSimStatusInfo(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnLikesSimStatusInfo(efid);
    }
}
