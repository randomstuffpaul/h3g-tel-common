package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class IccPhoneBookInterfaceManager {
    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;
    protected static final boolean DBG;
    protected static final int EVENT_GET_RECORD_INFO_DONE = 5;
    protected static final int EVENT_GET_SIM_FILE_STATUS_INFO_DONE = 6;
    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_GET_USIM_PB_CAPA = 4;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;
    private final int EF_FDN = IccConstants.EF_FDN;
    protected AdnRecordCache mAdnCache;
    protected Handler mBaseHandler = new C00131();
    private UiccCardApplication mCurrentApp = null;
    private int mCurrentEfid;
    private boolean mIs3gCard = false;
    protected final Object mLock = new Object();
    protected PhoneBase mPhone;
    protected int[] mRecordSize;
    protected List<AdnRecord> mRecords;
    protected int mSimFileStatusInfo;
    protected boolean mSuccess;
    protected UsimPhonebookCapaInfo mUsimPhonebookCapaInfo;
    protected int[] recordInfo;
    protected int returnIndex;

    class C00131 extends Handler {
        C00131() {
        }

        public void handleMessage(Message msg) {
            boolean z = true;
            AsyncResult ar;
            switch (msg.what) {
                case 1:
                    ar = msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecordSize = (int[]) ar.result;
                            IccPhoneBookInterfaceManager.this.logd("GET_RECORD_SIZE Size " + IccPhoneBookInterfaceManager.this.mRecordSize[0] + " total " + IccPhoneBookInterfaceManager.this.mRecordSize[1] + " #record " + IccPhoneBookInterfaceManager.this.mRecordSize[2]);
                        }
                        notifyPending(ar);
                    }
                    return;
                case 2:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecords = (List) ar.result;
                        } else {
                            if (IccPhoneBookInterfaceManager.DBG) {
                                IccPhoneBookInterfaceManager.this.logd("Cannot load ADN records");
                            }
                            if (IccPhoneBookInterfaceManager.this.mRecords != null) {
                                IccPhoneBookInterfaceManager.this.mRecords.clear();
                            }
                        }
                        notifyPending(ar);
                    }
                    return;
                case 3:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager = IccPhoneBookInterfaceManager.this;
                        if (ar.exception != null) {
                            z = false;
                        }
                        iccPhoneBookInterfaceManager.mSuccess = z;
                        if (IccPhoneBookInterfaceManager.this.mSuccess) {
                            IccPhoneBookInterfaceManager.this.returnIndex = ((AdnRecord) ar.result).mRecordNumber;
                            IccPhoneBookInterfaceManager.this.logd("EVENT_UPDATE_DONE : [" + IccPhoneBookInterfaceManager.this.returnIndex + "]");
                            if (("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) && IccPhoneBookInterfaceManager.this.returnIndex == 0) {
                                IccPhoneBookInterfaceManager.this.returnIndex = new CommandException(Error.INVALID_RESPONSE).toApplicationError();
                            }
                        } else {
                            IccPhoneBookInterfaceManager.this.returnIndex = new CommandException(Error.INVALID_RESPONSE).toApplicationError();
                            IccPhoneBookInterfaceManager.this.logd("EVENT_UPDATE_DONE error : [" + IccPhoneBookInterfaceManager.this.returnIndex + "]");
                        }
                        IccPhoneBookInterfaceManager.this.mCurrentEfid = 0;
                        notifyPending(ar);
                    }
                    return;
                case 4:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.logd("UsimPhonebookCapaInfo ar.result length = " + ((int[]) ar.result).length);
                            IccPhoneBookInterfaceManager.this.mUsimPhonebookCapaInfo = new UsimPhonebookCapaInfo((int[]) ar.result);
                        }
                        notifyPending(ar);
                    }
                    return;
                case 5:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.recordInfo = (int[]) ar.result;
                            IccPhoneBookInterfaceManager.this.logd("EVENT_GET_RECORD_INFO_DONEtotal cnt " + IccPhoneBookInterfaceManager.this.recordInfo[0] + "used  cnt " + IccPhoneBookInterfaceManager.this.recordInfo[1] + "first index" + IccPhoneBookInterfaceManager.this.recordInfo[2] + "text max " + IccPhoneBookInterfaceManager.this.recordInfo[3] + "number max " + IccPhoneBookInterfaceManager.this.recordInfo[4]);
                        }
                        notifyPending(ar);
                    }
                    return;
                case 6:
                    ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.logd("Get SIM file status info : = " + ((int[]) ar.result)[0]);
                            IccPhoneBookInterfaceManager.this.mSimFileStatusInfo = ((int[]) ar.result)[0];
                        } else {
                            IccPhoneBookInterfaceManager.this.mSimFileStatusInfo = 0;
                        }
                        notifyPending(ar);
                    }
                    return;
                default:
                    return;
            }
        }

        private void notifyPending(AsyncResult ar) {
            if (ar.userObj != null) {
                ar.userObj.set(true);
            }
            IccPhoneBookInterfaceManager.this.mLock.notifyAll();
        }
    }

    public abstract int[] getAdnLikesInfo(int i);

    public abstract int getAdnLikesSimStatusInfo(int i);

    public abstract int[] getAdnRecordsSize(int i);

    public abstract UsimPhonebookCapaInfo getUsimPBCapaInfo();

    protected abstract void logd(String str);

    protected abstract void loge(String str);

    static {
        boolean z = true;
        if (SystemProperties.getInt("ro.debuggable", 0) != 1) {
            z = false;
        }
        DBG = z;
    }

    public IccPhoneBookInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        IccRecords r = (IccRecords) phone.mIccRecords.get();
        if (r != null) {
            this.mAdnCache = r.getAdnCache();
        }
    }

    public void dispose() {
    }

    public void updateIccRecords(IccRecords iccRecords) {
        if (iccRecords != null) {
            this.mAdnCache = iccRecords.getAdnCache();
        } else {
            this.mAdnCache = null;
        }
    }

    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        } else if (this.mAdnCache != null) {
            if (DBG) {
                logd("updateAdnRecordsInEfBySearch: efid=" + efid + " (" + oldTag + "," + oldPhoneNumber + ")" + "==>" + " (" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
            }
            synchronized (this.mLock) {
                checkThread();
                this.mSuccess = false;
                AtomicBoolean status = new AtomicBoolean(false);
                Message response = this.mBaseHandler.obtainMessage(3, status);
                AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
                AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
                if (this.mAdnCache != null) {
                    this.mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                    waitForResult(status);
                } else {
                    loge("Failure while trying to update by search due to uninitialised adncache");
                }
            }
            return this.mSuccess;
        } else if (!DBG) {
            return false;
        } else {
            logd("adnCache is null");
            return false;
        }
    }

    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        if (DBG) {
            logd("updateAdnRecordsInEfByIndex: efid=" + efid + " Index=" + index + " ==> " + "(" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
        }
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (this.mAdnCache != null) {
                this.mCurrentEfid = efid;
                this.mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    public int updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String Email, int index, String pin2) {
        this.returnIndex = -1;
        if (this.mPhone.mCi.getRadioState() == RadioState.RADIO_UNAVAILABLE) {
            return new CommandException(Error.RADIO_NOT_AVAILABLE).toApplicationError();
        }
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        } else if (this.mAdnCache == null) {
            if (DBG) {
                logd("adnCache is null");
            }
            return new CommandException(Error.OPER_NOT_ALLOWED).toApplicationError();
        } else {
            if (DBG) {
                logd("updateAdnRecordsInEfByIndex: efid=" + efid + " Index=" + index + " ==> " + "(" + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
            }
            synchronized (this.mLock) {
                checkThread();
                this.mSuccess = false;
                AtomicBoolean status = new AtomicBoolean(false);
                Message response = this.mBaseHandler.obtainMessage(3, status);
                String[] newEmails = new String[]{Email};
                this.mCurrentEfid = efid;
                this.mAdnCache.updateAdnByIndex(efid, new AdnRecord(efid, index, newTag, newPhoneNumber, newEmails), index, pin2, response);
                waitForResult(status);
            }
            logd("returnIndex <" + this.returnIndex + ">");
            return this.returnIndex;
        }
    }

    public int updateAdnRecordsInEfByIndexUsingAR(int efid, AdnRecord newAdn, int index, String pin2) {
        this.returnIndex = -1;
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        } else if (this.mAdnCache == null) {
            if (DBG) {
                logd("adnCache is null");
            }
            return new CommandException(Error.OPER_NOT_ALLOWED).toApplicationError();
        } else {
            if (DBG) {
                logd("updateAdnRecordsInEfByIndexUsingAR: efid=" + efid + " Index=" + index + " ==> " + " pin2=" + pin2);
            }
            synchronized (this.mLock) {
                checkThread();
                this.mSuccess = false;
                AtomicBoolean status = new AtomicBoolean(false);
                Message response = this.mBaseHandler.obtainMessage(3, status);
                if (this.mAdnCache != null) {
                    this.mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                    waitForResult(status);
                } else {
                    loge("Failure while trying to update by index due to uninitialised adncache");
                }
            }
            return this.returnIndex;
        }
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.READ_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.READ_CONTACTS permission");
        }
        if (DBG) {
            logd("getAdnRecordsInEF: efid=" + efid);
        }
        if (this.mAdnCache == null) {
            if (DBG) {
                logd("adnCache has not set");
            }
            return null;
        }
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(2, status);
            if (this.mAdnCache != null) {
                this.mAdnCache.requestLoadAllAdnLike(efid, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return this.mRecords;
    }

    public List<AdnRecord> getAdnRecordsInEfInit(int efid) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.READ_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.READ_CONTACTS permission");
        }
        if (DBG) {
            logd("getAdnRecordsInEfInit: efid=" + efid);
        }
        if (this.mAdnCache == null) {
            if (DBG) {
                logd("adnCache has not set");
            }
            return null;
        }
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(false);
            this.mAdnCache.requestLoadAllAdnLikeInit(efid, this.mBaseHandler.obtainMessage(2, status));
            waitForResult(status);
        }
        return this.mRecords;
    }

    protected void checkThread() {
        if (this.mBaseHandler.getLooper().equals(Looper.myLooper())) {
            loge("query() called on the main UI thread!");
            throw new IllegalStateException("You cannot call query on this provder from the main UI thread.");
        }
    }

    protected void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }

    private int updateEfForIccType(int efid) {
        if (efid == 28474 && this.mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            return IccConstants.EF_PBR;
        }
        return efid;
    }
}
