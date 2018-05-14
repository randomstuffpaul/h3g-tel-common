package com.android.internal.telephony;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.IIccPhoneBook.Stub;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import java.util.List;

public class UiccPhoneBookController extends Stub {
    private static final String TAG = "UiccPhoneBookController";
    private Phone[] mPhone;

    public UiccPhoneBookController(Phone[] phone) {
        if (ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
        this.mPhone = phone;
    }

    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) throws RemoteException {
        return updateAdnRecordsInEfBySearchForSubscriber(getDefaultSubscription(), efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public boolean updateAdnRecordsInEfBySearchForSubscriber(long subId, int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfBySearch(efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is null for Subscription:" + subId);
        return false;
    }

    public int updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String email, int index, String pin2) throws RemoteException {
        return updateAdnRecordsInEfByIndexUsingSubId(getDefaultSubscription(), efid, newTag, newPhoneNumber, email, index, pin2);
    }

    public int updateAdnRecordsInEfByIndexUsingSubId(long subId, int efid, String newTag, String newPhoneNumber, String email, int index, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndex(efid, newTag, newPhoneNumber, email, index, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfByIndex iccPbkIntMgrProxy is null for Subscription:" + subId);
        return new CommandException(Error.OPER_NOT_ALLOWED).toApplicationError();
    }

    public int updateAdnRecordsInEfByIndexUsingAR(int efid, AdnRecord newAdn, int index, String pin2) throws RemoteException {
        return updateAdnRecordsInEfByIndexUsingARnSubId(getDefaultSubscription(), efid, newAdn, index, pin2);
    }

    public int updateAdnRecordsInEfByIndexUsingARnSubId(long subId, int efid, AdnRecord newAdn, int index, String pin2) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndexUsingAR(efid, newAdn, index, pin2);
        }
        Rlog.e(TAG, "updateAdnRecordsInEfByIndexUsingARnSubId iccPbkIntMgrProxy is null for Subscription:" + subId);
        return new CommandException(Error.OPER_NOT_ALLOWED).toApplicationError();
    }

    public int[] getAdnRecordsSize(int efid) throws RemoteException {
        return getAdnRecordsSizeForSubscriber(getDefaultSubscription(), efid);
    }

    public int[] getAdnRecordsSizeForSubscriber(long subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsSize(efid);
        }
        Rlog.e(TAG, "getAdnRecordsSize iccPbkIntMgrProxy is null for Subscription:" + subId);
        return null;
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) throws RemoteException {
        return getAdnRecordsInEfForSubscriber(getDefaultSubscription(), efid);
    }

    public List<AdnRecord> getAdnRecordsInEfForSubscriber(long subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEf(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    private IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy(long subId) {
        try {
            return ((PhoneProxy) this.mPhone[(int) ((long) SubscriptionController.getInstance().getPhoneId(subId))]).getIccPhoneBookInterfaceManagerProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :" + e.toString() + " For subscription :" + subId);
            e.printStackTrace();
            return null;
        } catch (ArrayIndexOutOfBoundsException e2) {
            Rlog.e(TAG, "Exception is :" + e2.toString() + " For subscription :" + subId);
            e2.printStackTrace();
            return null;
        }
    }

    private long getDefaultSubscription() {
        return PhoneFactory.getDefaultSubscription();
    }

    public List<AdnRecord> getAdnRecordsInEfInit(int efid) throws RemoteException {
        long subId = getDefaultSubscription();
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEfInit(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public UsimPhonebookCapaInfo getUsimPBCapaInfo() throws RemoteException {
        long subId = getDefaultSubscription();
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getUsimPBCapaInfo();
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public int[] getAdnLikesInfo(int efid) throws RemoteException {
        long subId = getDefaultSubscription();
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnLikesInfo(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public int getAdnLikesSimStatusInfo(int efid) throws RemoteException {
        long subId = getDefaultSubscription();
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnLikesSimStatusInfo(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }

    public List<AdnRecord> getAdnRecordsInEfInitForSubscriber(long subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEfInit(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public UsimPhonebookCapaInfo getUsimPBCapaInfoForSubscriber(long subId) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getUsimPBCapaInfo();
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public int[] getAdnLikesInfoForSubscriber(long subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnLikesInfo(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return null;
    }

    public int getAdnLikesSimStatusInfoForSubscriber(long subId, int efid) throws RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy = getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnLikesSimStatusInfo(efid);
        }
        Rlog.e(TAG, "getAdnRecordsInEf iccPbkIntMgrProxy isnull for Subscription:" + subId);
        return 0;
    }
}
