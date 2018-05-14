package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.PhoneRestrictionPolicy;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import java.util.ArrayList;

public class AdnRecordLoader extends Handler {
    static final int ACCESS_TO_PB_ADD = 1;
    static final int ACCESS_TO_PB_DELETE = 2;
    static final int ACCESS_TO_PB_EDIT = 3;
    static final int EVENT_ADN_LOAD_ALL_DONE = 3;
    static final int EVENT_ADN_LOAD_DONE = 1;
    static final int EVENT_EF_LINEAR_RECORD_SIZE_DONE = 4;
    static final int EVENT_EXT_RECORD_LOAD_DONE = 2;
    static final int EVENT_PB_ENTRY_ACCESS_DONE = 7;
    static final int EVENT_PB_ENTRY_LOAD_ALL_DONE = 6;
    static final int EVENT_UPDATE_RECORD_DONE = 5;
    static final String LOG_TAG = "AdnRecordLoader";
    static final boolean VDBG = false;
    ArrayList<AdnRecord> mAdns;
    int mEf;
    int mExtensionEF;
    private IccFileHandler mFh;
    int mPendingExtLoads;
    PhoneRestrictionPolicy mPhoneRestrictionPolicy;
    String mPin2;
    int mRecordNumber;
    int mReloadingEF = 0;
    Object mResult;
    Message mUserResponse;

    AdnRecordLoader(IccFileHandler fh) {
        super(Looper.getMainLooper());
        this.mFh = fh;
        this.mPhoneRestrictionPolicy = EnterpriseDeviceManager.getInstance().getPhoneRestrictionPolicy();
    }

    public void loadFromEF(int ef, int extensionEF, int recordNumber, Message response) {
        this.mEf = ef;
        this.mExtensionEF = extensionEF;
        this.mRecordNumber = recordNumber;
        this.mUserResponse = response;
        this.mFh.loadEFLinearFixed(ef, recordNumber, obtainMessage(1));
    }

    public void loadAllFromEF(int ef, int extensionEF, Message response) {
        this.mEf = ef;
        this.mExtensionEF = extensionEF;
        this.mUserResponse = response;
        this.mFh.loadEFLinearFixedAll(ef, obtainMessage(3));
    }

    public void updateEF(AdnRecord adn, int ef, int extensionEF, int recordNumber, String pin2, Message response) {
        this.mEf = ef;
        this.mExtensionEF = extensionEF;
        this.mRecordNumber = recordNumber;
        this.mUserResponse = response;
        this.mPin2 = pin2;
        this.mFh.getEFLinearRecordSize(ef, obtainMessage(4, adn));
    }

    void loadAllFromPBEntry(int ef, Message response) {
        if (this.mReloadingEF != ef) {
            Rlog.i(LOG_TAG, "mReloadingEF is " + this.mReloadingEF + "ef is " + ef);
            if (this.mReloadingEF == 0) {
                this.mReloadingEF = ef;
            }
            this.mEf = ef;
            this.mUserResponse = response;
            this.mFh.loadItemInPhoneBookStorageAll(ef, obtainMessage(6));
        }
    }

    void editPBEntry(AdnRecord adn, int ef, int recordNumber, String pin2, Message response) {
        this.mEf = ef;
        this.mRecordNumber = recordNumber;
        this.mUserResponse = response;
        this.mPin2 = pin2;
        if (this.mPhoneRestrictionPolicy.isCopyContactToSimAllowed(3)) {
            Rlog.i(LOG_TAG, "editPBEntry index is " + recordNumber);
            this.mFh.mCi.accessPhoneBookEntry(3, ef, recordNumber, adn, pin2, obtainMessage(7, adn));
        }
    }

    void addPBEntry(AdnRecord adn, int ef, int recordNumber, String pin2, Message response) {
        this.mEf = ef;
        this.mRecordNumber = recordNumber;
        this.mUserResponse = response;
        this.mPin2 = pin2;
        if (this.mPhoneRestrictionPolicy.isCopyContactToSimAllowed(1)) {
            Rlog.i(LOG_TAG, "addPBEntry");
            this.mFh.mCi.accessPhoneBookEntry(1, ef, 0, adn, pin2, obtainMessage(7, adn));
        }
    }

    void deletePBEntry(AdnRecord adn, int ef, int recordNumber, String pin2, Message response) {
        this.mEf = ef;
        this.mRecordNumber = recordNumber;
        this.mUserResponse = response;
        this.mPin2 = pin2;
        Rlog.i(LOG_TAG, "updateEF - delete");
        Rlog.i(LOG_TAG, "deletePBEntry index is " + recordNumber);
        this.mFh.mCi.accessPhoneBookEntry(2, ef, recordNumber, adn, pin2, obtainMessage(7, adn));
    }

    public void handleMessage(Message msg) {
        try {
            AsyncResult ar;
            byte[] data;
            AdnRecord adn;
            switch (msg.what) {
                case 1:
                    ar = (AsyncResult) msg.obj;
                    data = (byte[]) ar.result;
                    if (ar.exception == null) {
                        adn = new AdnRecord(this.mEf, this.mRecordNumber, data);
                        this.mResult = adn;
                        if (adn.hasExtendedRecord()) {
                            this.mPendingExtLoads = 1;
                            this.mFh.loadEFLinearFixed(this.mExtensionEF, adn.mExtRecord, obtainMessage(2, adn));
                            break;
                        }
                    }
                    throw new RuntimeException("load failed", ar.exception);
                    break;
                case 2:
                    ar = (AsyncResult) msg.obj;
                    data = (byte[]) ar.result;
                    adn = (AdnRecord) ar.userObj;
                    if (ar.exception == null) {
                        Rlog.d(LOG_TAG, "ADN extension EF: 0x" + Integer.toHexString(this.mExtensionEF) + ":" + adn.mExtRecord + "\n" + IccUtils.bytesToHexString(data));
                        adn.appendExtRecord(data);
                        this.mPendingExtLoads--;
                        break;
                    }
                    throw new RuntimeException("load failed", ar.exception);
                case 3:
                    ar = (AsyncResult) msg.obj;
                    ArrayList<byte[]> datas = (ArrayList) ar.result;
                    if (ar.exception == null) {
                        this.mAdns = new ArrayList(datas.size());
                        this.mResult = this.mAdns;
                        this.mPendingExtLoads = 0;
                        int s = datas.size();
                        for (int i = 0; i < s; i++) {
                            adn = new AdnRecord(this.mEf, i + 1, (byte[]) datas.get(i));
                            this.mAdns.add(adn);
                            if (adn.hasExtendedRecord()) {
                                this.mPendingExtLoads++;
                                this.mFh.loadEFLinearFixed(this.mExtensionEF, adn.mExtRecord, obtainMessage(2, adn));
                            }
                        }
                        break;
                    }
                    throw new RuntimeException("load failed", ar.exception);
                case 4:
                    ar = (AsyncResult) msg.obj;
                    adn = (AdnRecord) ar.userObj;
                    if (ar.exception == null) {
                        int[] recordSize = (int[]) ar.result;
                        if (recordSize.length == 3 && this.mRecordNumber <= recordSize[2]) {
                            data = adn.buildAdnString(recordSize[0]);
                            if (data != null) {
                                this.mFh.updateEFLinearFixed(this.mEf, this.mRecordNumber, data, this.mPin2, obtainMessage(5));
                                this.mPendingExtLoads = 1;
                                break;
                            }
                            throw new RuntimeException("wrong ADN format", ar.exception);
                        }
                        throw new RuntimeException("get wrong EF record size format", ar.exception);
                    }
                    throw new RuntimeException("get EF record size failed", ar.exception);
                    break;
                case 5:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mPendingExtLoads = 0;
                        this.mResult = null;
                        break;
                    }
                    throw new RuntimeException("update EF adn record failed", ar.exception);
                case 6:
                    Rlog.i(LOG_TAG, "EVENT_PB_ENTRY_LOAD_ALL_DONE");
                    this.mReloadingEF = 0;
                    ar = (AsyncResult) msg.obj;
                    ArrayList<AdnRecord> adnDatas = (ArrayList) ar.result;
                    if (ar.exception == null) {
                        this.mResult = adnDatas;
                        break;
                    } else {
                        Rlog.i(LOG_TAG, "ar.exception != null");
                        throw new RuntimeException("load failed", ar.exception);
                    }
                case 7:
                    Rlog.i(LOG_TAG, "EVENT_PB_ENTRY_ACCESS_DONE");
                    ar = (AsyncResult) msg.obj;
                    adn = (AdnRecord) ar.userObj;
                    if (ar.exception != null) {
                        adn.mRecordNumber = ((CommandException) ar.exception).toApplicationError();
                        ar.exception = null;
                        Rlog.i(LOG_TAG, "EVENT_PB_ENTRY_ACCESS_DONE - Error is " + adn.mRecordNumber);
                    } else {
                        adn.mRecordNumber = ((int[]) ar.result)[0];
                        Rlog.i(LOG_TAG, "EVENT_PB_ENTRY_ACCESS_DONE - index is " + adn.mRecordNumber);
                    }
                    this.mPendingExtLoads = 0;
                    this.mResult = ar;
                    Rlog.i(LOG_TAG, "adn.mEfid = " + adn.mEfid);
                    if (adn.mEfid == IccConstants.EF_FDN) {
                        break;
                    }
                    break;
            }
            if (this.mUserResponse != null && this.mPendingExtLoads == 0) {
                AsyncResult.forMessage(this.mUserResponse).result = this.mResult;
                this.mUserResponse.sendToTarget();
                this.mUserResponse = null;
            }
        } catch (RuntimeException exc) {
            if (this.mUserResponse != null) {
                AsyncResult.forMessage(this.mUserResponse).exception = exc;
                this.mUserResponse.sendToTarget();
                this.mUserResponse = null;
            }
        }
    }
}
