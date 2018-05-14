package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.util.SparseArray;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import java.util.ArrayList;
import java.util.Iterator;

public final class AdnRecordCache extends Handler implements IccConstants {
    static final int EVENT_ADD_ADN_DONE = 3;
    static final int EVENT_DELETE_ADN_DONE = 4;
    static final int EVENT_LOAD_ALL_ADN_LIKE_AGAIN_DONE = 5;
    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;
    protected static final String ICC_TYPE = "ril.ICC_TYPE";
    static final String LOG_TAG = "AdnRecordCache";
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles = new SparseArray();
    SparseArray<ArrayList<Message>> mAdnLikeWaiters = new SparseArray();
    private IccFileHandler mFh;
    SparseArray<Message> mUserWriteResponse = new SparseArray();
    private UsimPhoneBookManager mUsimPhoneBookManager;

    AdnRecordCache(IccFileHandler fh) {
        this.mFh = fh;
        this.mUsimPhoneBookManager = new UsimPhoneBookManager(this.mFh, this);
    }

    public void reset() {
        this.mAdnLikeFiles.clear();
        this.mUsimPhoneBookManager.reset();
        clearWaiters();
        clearUserWriters();
    }

    private void clearWaiters() {
        int size = this.mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            notifyWaiters((ArrayList) this.mAdnLikeWaiters.valueAt(i), new AsyncResult(null, null, new RuntimeException("AdnCache reset")));
        }
        this.mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = this.mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse((Message) this.mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        this.mUserWriteResponse.clear();
    }

    public ArrayList<AdnRecord> getRecordsIfLoaded(int efid) {
        return (ArrayList) this.mAdnLikeFiles.get(efid);
    }

    public int extensionEfForEf(int efid) {
        switch (efid) {
            case IccConstants.EF_PBR /*20272*/:
                return 0;
            case 28474:
                return IccConstants.EF_EXT1;
            case IccConstants.EF_FDN /*28475*/:
                return IccConstants.EF_EXT2;
            case IccConstants.EF_MSISDN /*28480*/:
                String iccType = SystemProperties.get(ICC_TYPE);
                Rlog.i(LOG_TAG, "iccType =" + iccType);
                if (iccType.equals("1")) {
                    return IccConstants.EF_EXT1;
                }
                return iccType.equals("2") ? IccConstants.EF_EXT5 : 0;
            case IccConstants.EF_SDN /*28489*/:
                return IccConstants.EF_EXT3;
            case IccConstants.EF_MBDN /*28615*/:
                return IccConstants.EF_EXT6;
            default:
                return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            AsyncResult.forMessage(response).exception = new RuntimeException(errString);
            response.sendToTarget();
        }
    }

    public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2, Message response) {
        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        Rlog.i(LOG_TAG, "updateAdnByIndex enter");
        String checkEmpty = "";
        int count = 0;
        if (((Message) this.mUserWriteResponse.get(efid)) != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
        } else if (recordIndex == 65535) {
            Rlog.i(LOG_TAG, "updateAdnByIndex - add");
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).addPBEntry(adn, efid, recordIndex, pin2, obtainMessage(3, efid, 0, adn));
        } else {
            Rlog.i(LOG_TAG, "updateAdnByIndex - update or delete");
            Rlog.i(LOG_TAG, "target index  is " + recordIndex);
            if (oldAdnList == null) {
                Rlog.i(LOG_TAG, "requestLoadAllAdnLikeAgain because oldAdnList is null");
                requestLoadAllAdnLikeAgain(efid);
                sendErrorResponse(response, "ADNlike list is not init for EF:" + efid);
                return;
            }
            Iterator<AdnRecord> it = oldAdnList.iterator();
            while (it.hasNext()) {
                AdnRecord tmpAdn = (AdnRecord) it.next();
                if (recordIndex == tmpAdn.mRecordNumber) {
                    int index = tmpAdn.mRecordNumber;
                    count++;
                    break;
                }
                count++;
            }
            if (checkEmpty.equals(adn.mAlphaTag) && checkEmpty.equals(adn.mNumber)) {
                this.mUserWriteResponse.put(efid, response);
                new AdnRecordLoader(this.mFh).deletePBEntry(adn, efid, recordIndex, pin2, obtainMessage(4, efid, count, adn));
                return;
            }
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).editPBEntry(adn, efid, recordIndex, pin2, obtainMessage(2, efid, count, adn));
        }
    }

    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn, String pin2, Message response) {
        Rlog.i(LOG_TAG, "updateAdnBySearch enter");
        String checkEmpty = "";
        int index = -1;
        int count = 0;
        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        if (((Message) this.mUserWriteResponse.get(efid)) != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }
        this.mUserWriteResponse.put(efid, response);
        if (oldAdn.mAlphaTag.equals(checkEmpty) && oldAdn.mNumber.equals(checkEmpty)) {
            Rlog.i(LOG_TAG, "updateAdnBySearch - add");
            new AdnRecordLoader(this.mFh).addPBEntry(newAdn, efid, -1, pin2, obtainMessage(3, efid, 0, newAdn));
        } else if (oldAdnList == null) {
            Rlog.i(LOG_TAG, "updateAdnBySearch oldAdnList == null");
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
        } else {
            Rlog.i(LOG_TAG, "updateAdnBySearch - update or delete");
            Rlog.i(LOG_TAG, "oldAdn.recordNumber is " + oldAdn.mRecordNumber);
            Iterator<AdnRecord> it = oldAdnList.iterator();
            while (it.hasNext()) {
                AdnRecord tmpAdn = (AdnRecord) it.next();
                if (oldAdn.isEqual(tmpAdn)) {
                    index = tmpAdn.mRecordNumber;
                    count++;
                    break;
                }
                count++;
            }
            Rlog.i(LOG_TAG, "updateAdnBySearch: index  is " + index);
            if (index == -1) {
                sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            } else if (newAdn.mAlphaTag.equals(checkEmpty) && newAdn.mNumber.equals(checkEmpty)) {
                new AdnRecordLoader(this.mFh).deletePBEntry(newAdn, efid, index, pin2, obtainMessage(4, efid, count, newAdn));
            } else {
                new AdnRecordLoader(this.mFh).editPBEntry(newAdn, efid, index, pin2, obtainMessage(2, efid, count, newAdn));
            }
        }
    }

    public void requestLoadAllAdnLike(int efid, int extensionEf, Message response) {
        ArrayList<AdnRecord> result;
        if (efid == IccConstants.EF_PBR) {
            result = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
        }
        if (result == null) {
            ArrayList<Message> waiters = (ArrayList) this.mAdnLikeWaiters.get(efid);
            if (waiters != null) {
                waiters.add(response);
                return;
            }
            waiters = new ArrayList();
            waiters.add(response);
            this.mAdnLikeWaiters.put(efid, waiters);
            if (extensionEf >= 0) {
                new AdnRecordLoader(this.mFh).loadAllFromEF(efid, extensionEf, obtainMessage(1, efid, 0));
            } else if (response != null) {
                AsyncResult.forMessage(response).exception = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }
        } else if (response != null) {
            AsyncResult.forMessage(response).result = result;
            response.sendToTarget();
        }
    }

    public void requestLoadAllAdnLike(int efid, Message response) {
        ArrayList<AdnRecord> result = getRecordsIfLoaded(efid);
        if (result == null || result.size() == 0) {
            ArrayList<Message> waiters = (ArrayList) this.mAdnLikeWaiters.get(efid);
            if (waiters != null) {
                waiters.add(response);
                return;
            }
            waiters = new ArrayList();
            waiters.add(response);
            this.mAdnLikeWaiters.put(efid, waiters);
            new AdnRecordLoader(this.mFh).loadAllFromPBEntry(efid, obtainMessage(1, efid, 0));
        } else if (response != null) {
            AsyncResult.forMessage(response).result = result;
            response.sendToTarget();
        }
    }

    public void requestLoadAllAdnLikeAgain(int efid) {
        if (getRecordsIfLoaded(efid) != null) {
            Rlog.i(LOG_TAG, "requestLoadAllAdnLikeAgain - already loaded : " + efid);
        } else {
            new AdnRecordLoader(this.mFh).loadAllFromPBEntry(efid, obtainMessage(5, efid, 0));
        }
    }

    public void requestLoadAllAdnLikeInit(int efid, Message response) {
        ArrayList<AdnRecord> result = getRecordsIfLoaded(efid);
        ArrayList<Message> waiters = (ArrayList) this.mAdnLikeWaiters.get(efid);
        if (waiters != null) {
            waiters.add(response);
            return;
        }
        waiters = new ArrayList();
        waiters.add(response);
        this.mAdnLikeWaiters.put(efid, waiters);
        new AdnRecordLoader(this.mFh).loadAllFromPBEntry(efid, obtainMessage(1, efid, 0));
    }

    private void notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {
        if (waiters != null) {
            int s = waiters.size();
            for (int i = 0; i < s; i++) {
                Message waiter = (Message) waiters.get(i);
                AsyncResult.forMessage(waiter, ar.result, ar.exception);
                waiter.sendToTarget();
            }
        }
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        int efid;
        AdnRecord adn;
        Message response;
        switch (msg.what) {
            case 1:
                ar = msg.obj;
                efid = msg.arg1;
                ArrayList<Message> waiters = (ArrayList) this.mAdnLikeWaiters.get(efid);
                this.mAdnLikeWaiters.delete(efid);
                if (ar.exception == null) {
                    this.mAdnLikeFiles.put(efid, (ArrayList) ar.result);
                }
                notifyWaiters(waiters, ar);
                return;
            case 2:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                int index = msg.arg2;
                adn = (AdnRecord) ar.userObj;
                if (ar.exception == null) {
                    if (this.mAdnLikeFiles.get(efid) == null) {
                        Rlog.i(LOG_TAG, "mAdnLikeFiles.get(efid) null fail, reinit framework member value : " + efid);
                        requestLoadAllAdnLikeAgain(efid);
                    } else if (adn.mRecordNumber > 0) {
                        try {
                            ((ArrayList) this.mAdnLikeFiles.get(efid)).set(index - 1, adn);
                            this.mUsimPhoneBookManager.invalidateCache();
                        } catch (Exception e) {
                            Rlog.i(LOG_TAG, "get from F/W PB list error, index '" + index + "' is not available");
                        }
                    }
                }
                response = (Message) this.mUserWriteResponse.get(efid);
                this.mUserWriteResponse.delete(efid);
                if (response != null) {
                    try {
                        AsyncResult.forMessage(response, adn, ar.exception);
                        response.sendToTarget();
                        return;
                    } catch (RuntimeException ex) {
                        Rlog.e(LOG_TAG, "AsyncResult.forMessage", ex);
                        return;
                    }
                }
                return;
            case 3:
                Rlog.i(LOG_TAG, "EVENT_ADD_ADN_DONE");
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                Rlog.i(LOG_TAG, "cnt is " + msg.arg2);
                adn = (AdnRecord) ar.userObj;
                if (ar.exception == null) {
                    if (this.mAdnLikeFiles.get(efid) == null) {
                        Rlog.i(LOG_TAG, "mAdnLikeFiles.get(efid) null fail, reinit framework member value : " + efid);
                        requestLoadAllAdnLikeAgain(efid);
                    } else if (adn.mRecordNumber > 0) {
                        ((ArrayList) this.mAdnLikeFiles.get(efid)).add(adn);
                    }
                }
                response = (Message) this.mUserWriteResponse.get(efid);
                this.mUserWriteResponse.delete(efid);
                if (response != null) {
                    try {
                        AsyncResult.forMessage(response, adn, ar.exception);
                        response.sendToTarget();
                        return;
                    } catch (RuntimeException ex2) {
                        Rlog.e(LOG_TAG, "AsyncResult.forMessage", ex2);
                        return;
                    }
                }
                return;
            case 4:
                Rlog.i(LOG_TAG, "EVENT_DELETE_ADN_DONE");
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                int cnt = msg.arg2;
                Rlog.i(LOG_TAG, "cnt is " + cnt);
                adn = (AdnRecord) ar.userObj;
                Rlog.i(LOG_TAG, "adn-alpha is  " + adn.mAlphaTag);
                Rlog.i(LOG_TAG, "adn-number is  " + adn.mNumber);
                Rlog.i(LOG_TAG, "adn-record index is  " + adn.mRecordNumber);
                if (ar.exception == null) {
                    if (this.mAdnLikeFiles.get(efid) == null) {
                        Rlog.i(LOG_TAG, "mAdnLikeFiles.get(efid) null fail, reinit framework member value : " + efid);
                        requestLoadAllAdnLikeAgain(efid);
                    } else if (adn.mRecordNumber > 0) {
                        try {
                            ((ArrayList) this.mAdnLikeFiles.get(efid)).remove(cnt - 1);
                        } catch (ArrayIndexOutOfBoundsException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                response = (Message) this.mUserWriteResponse.get(efid);
                this.mUserWriteResponse.delete(efid);
                if (response != null) {
                    try {
                        AsyncResult.forMessage(response, adn, ar.exception);
                        response.sendToTarget();
                        return;
                    } catch (RuntimeException ex22) {
                        Rlog.e(LOG_TAG, "AsyncResult.forMessage", ex22);
                        return;
                    }
                }
                return;
            case 5:
                Rlog.i(LOG_TAG, "EVENT_LOAD_ALL_ADN_LIKE_AGAIN_DONE");
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                if (ar.exception == null) {
                    Rlog.i(LOG_TAG, "ar.exception == null");
                    this.mAdnLikeFiles.put(efid, (ArrayList) ar.result);
                }
                Rlog.i(LOG_TAG, "EVENT_LOAD_ALL_ADN_LIKE_DONE - end");
                return;
            default:
                return;
        }
    }
}
