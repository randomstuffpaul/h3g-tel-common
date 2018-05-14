package com.android.internal.telephony.uicc;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class UiccCarrierPrivilegeRules extends Handler {
    private static final String AID = "A00000015141434C00";
    private static final int CLA = 128;
    private static final int COMMAND = 202;
    private static final String DATA = "";
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 3;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 1;
    private static final int EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE = 2;
    private static final String LOG_TAG = "UiccCarrierPrivilegeRules";
    private static final int P1 = 255;
    private static final int P2 = 64;
    private static final int P3 = 0;
    private static final int STATE_ERROR = 2;
    private static final int STATE_LOADED = 1;
    private static final int STATE_LOADING = 0;
    private static final String TAG_ALL_REF_AR_DO = "FF40";
    private static final String TAG_AR_DO = "E3";
    private static final String TAG_DEVICE_APP_ID_REF_DO = "C1";
    private static final String TAG_PERM_AR_DO = "DB";
    private static final String TAG_PKG_REF_DO = "CA";
    private static final String TAG_REF_AR_DO = "E2";
    private static final String TAG_REF_DO = "E1";
    private List<AccessRule> mAccessRules;
    private Message mLoadedCallback;
    private AtomicInteger mState = new AtomicInteger(0);
    private UiccCard mUiccCard;

    private static class AccessRule {
        public long accessType;
        public byte[] certificateHash;
        public String packageName;

        AccessRule(byte[] certificateHash, String packageName, long accessType) {
            this.certificateHash = certificateHash;
            this.packageName = packageName;
            this.accessType = accessType;
        }

        boolean matches(byte[] certHash, String packageName) {
            return certHash != null && Arrays.equals(this.certificateHash, certHash) && (this.packageName == null || this.packageName.equals(packageName));
        }

        public String toString() {
            return "cert: " + this.certificateHash + " pkg: " + this.packageName + " access: " + this.accessType;
        }
    }

    private static class TLV {
        private Integer length;
        private String tag;
        private String value;

        public TLV(String tag) {
            this.tag = tag;
        }

        public String parse(String data, boolean shouldConsumeAll) {
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Parse TLV: " + this.tag);
            if (data.startsWith(this.tag)) {
                int index = this.tag.length();
                if (index + 2 > data.length()) {
                    throw new IllegalArgumentException("No length.");
                }
                this.length = new Integer(Integer.parseInt(data.substring(index, index + 2), 16) * 2);
                index += 2;
                int remainingLength = data.length() - (this.length.intValue() + index);
                if (remainingLength < 0) {
                    throw new IllegalArgumentException("Not enough data.");
                } else if (!shouldConsumeAll || remainingLength == 0) {
                    this.value = data.substring(index, this.length.intValue() + index);
                    Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Got TLV: " + this.tag + "," + this.length + "," + this.value);
                    return data.substring(this.length.intValue() + index);
                } else {
                    throw new IllegalArgumentException("Did not consume all.");
                }
            }
            throw new IllegalArgumentException("Tags don't match.");
        }
    }

    public UiccCarrierPrivilegeRules(UiccCard uiccCard, Message loadedCallback) {
        Rlog.d(LOG_TAG, "Creating UiccCarrierPrivilegeRules");
        this.mUiccCard = uiccCard;
        this.mLoadedCallback = loadedCallback;
        this.mUiccCard.iccOpenLogicalChannel(AID, obtainMessage(1, null));
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mState.get() != 0;
    }

    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        Rlog.d(LOG_TAG, "hasCarrierPrivileges: " + signature + " : " + packageName);
        int state = this.mState.get();
        if (state == 0) {
            Rlog.d(LOG_TAG, "Rules not loaded.");
            return -1;
        } else if (state == 2) {
            Rlog.d(LOG_TAG, "Error loading rules.");
            return -2;
        } else {
            byte[] certHash = getCertHash(signature);
            if (certHash == null) {
                return 0;
            }
            Rlog.e(LOG_TAG, "Checking: " + IccUtils.bytesToHexString(certHash) + " : " + packageName);
            for (AccessRule ar : this.mAccessRules) {
                if (ar.matches(certHash, packageName)) {
                    Rlog.d(LOG_TAG, "Match found!");
                    return 1;
                }
            }
            Rlog.d(LOG_TAG, "No matching rule found. Returning false.");
            return 0;
        }
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        try {
            PackageInfo pInfo = packageManager.getPackageInfo(packageName, 64);
            for (Signature sig : pInfo.signatures) {
                int accessStatus = getCarrierPrivilegeStatus(sig, pInfo.packageName);
                if (accessStatus != 0) {
                    return accessStatus;
                }
            }
        } catch (NameNotFoundException ex) {
            Rlog.e(LOG_TAG, "NameNotFoundException", ex);
        }
        return 0;
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        for (String pkg : packageManager.getPackagesForUid(Binder.getCallingUid())) {
            int accessStatus = getCarrierPrivilegeStatus(packageManager, pkg);
            if (accessStatus != 0) {
                return accessStatus;
            }
        }
        return 0;
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        List<String> packages = new ArrayList();
        List<ResolveInfo> receivers = new ArrayList();
        receivers.addAll(packageManager.queryBroadcastReceivers(intent, 0));
        receivers.addAll(packageManager.queryIntentContentProviders(intent, 0));
        receivers.addAll(packageManager.queryIntentActivities(intent, 0));
        receivers.addAll(packageManager.queryIntentServices(intent, 0));
        for (ResolveInfo resolveInfo : receivers) {
            if (resolveInfo.activityInfo != null) {
                String packageName = resolveInfo.activityInfo.packageName;
                int status = getCarrierPrivilegeStatus(packageManager, packageName);
                if (status == 1) {
                    packages.add(packageName);
                } else if (status != 0) {
                    return null;
                }
            }
        }
        return packages;
    }

    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 1:
                Rlog.d(LOG_TAG, "EVENT_OPEN_LOGICAL_CHANNEL_DONE");
                ar = msg.obj;
                if (ar.exception != null || ar.result == null) {
                    Rlog.e(LOG_TAG, "Error opening channel");
                    updateState(2);
                    return;
                }
                int channelId = ((int[]) ar.result)[0];
                this.mUiccCard.iccTransmitApduLogicalChannel(channelId, 128, COMMAND, 255, 64, 0, DATA, obtainMessage(2, new Integer(channelId)));
                return;
            case 2:
                Rlog.d(LOG_TAG, "EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null || ar.result == null) {
                    Rlog.e(LOG_TAG, "Error reading value from SIM.");
                    updateState(2);
                } else {
                    IccIoResult response = ar.result;
                    if (response.payload != null && response.sw1 == 144 && response.sw2 == 0) {
                        try {
                            this.mAccessRules = parseRules(IccUtils.bytesToHexString(response.payload));
                            updateState(1);
                        } catch (IllegalArgumentException ex) {
                            Rlog.e(LOG_TAG, "Error parsing rules: " + ex);
                            updateState(2);
                        }
                    } else {
                        Rlog.e(LOG_TAG, "Invalid response: payload=" + response.payload + " sw1=" + response.sw1 + " sw2=" + response.sw2);
                        updateState(2);
                    }
                }
                this.mUiccCard.iccCloseLogicalChannel(((Integer) ar.userObj).intValue(), obtainMessage(3));
                return;
            case 3:
                Rlog.d(LOG_TAG, "EVENT_CLOSE_LOGICAL_CHANNEL_DONE");
                return;
            default:
                Rlog.e(LOG_TAG, "Unknown event " + msg.what);
                return;
        }
    }

    private static List<AccessRule> parseRules(String rules) {
        rules = rules.toUpperCase(Locale.US);
        Rlog.d(LOG_TAG, "Got rules: " + rules);
        TLV allRefArDo = new TLV(TAG_ALL_REF_AR_DO);
        allRefArDo.parse(rules, true);
        String arDos = allRefArDo.value;
        List<AccessRule> accessRules = new ArrayList();
        while (!arDos.isEmpty()) {
            TLV refArDo = new TLV(TAG_REF_AR_DO);
            arDos = refArDo.parse(arDos, false);
            AccessRule accessRule = parseRefArdo(refArDo.value);
            if (accessRule != null) {
                accessRules.add(accessRule);
            } else {
                Rlog.e(LOG_TAG, "Skip unrecognized rule." + refArDo.value);
            }
        }
        return accessRules;
    }

    private static AccessRule parseRefArdo(String rule) {
        Rlog.d(LOG_TAG, "Got rule: " + rule);
        String certificateHash = null;
        String packageName = null;
        while (!rule.isEmpty()) {
            if (rule.startsWith(TAG_REF_DO)) {
                TLV refDo = new TLV(TAG_REF_DO);
                rule = refDo.parse(rule, false);
                if (!refDo.value.startsWith(TAG_DEVICE_APP_ID_REF_DO)) {
                    return null;
                }
                TLV deviceDo = new TLV(TAG_DEVICE_APP_ID_REF_DO);
                String tmp = deviceDo.parse(refDo.value, false);
                certificateHash = deviceDo.value;
                if (tmp.isEmpty()) {
                    packageName = null;
                } else if (!tmp.startsWith(TAG_PKG_REF_DO)) {
                    return null;
                } else {
                    TLV pkgDo = new TLV(TAG_PKG_REF_DO);
                    pkgDo.parse(tmp, true);
                    packageName = new String(IccUtils.hexStringToBytes(pkgDo.value));
                }
            } else if (rule.startsWith(TAG_AR_DO)) {
                TLV arDo = new TLV(TAG_AR_DO);
                rule = arDo.parse(rule, false);
                if (!arDo.value.startsWith(TAG_PERM_AR_DO)) {
                    return null;
                }
                TLV permDo = new TLV(TAG_PERM_AR_DO);
                permDo.parse(arDo.value, true);
                Rlog.e(LOG_TAG, permDo.value);
            } else {
                throw new RuntimeException("Invalid Rule type");
            }
        }
        Rlog.e(LOG_TAG, "Adding: " + certificateHash + " : " + packageName + " : " + 0);
        AccessRule accessRule = new AccessRule(IccUtils.hexStringToBytes(certificateHash), packageName, 0);
        Rlog.e(LOG_TAG, "Parsed rule: " + accessRule);
        return accessRule;
    }

    private static byte[] getCertHash(Signature signature) {
        try {
            return MessageDigest.getInstance("SHA").digest(((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(signature.toByteArray()))).getEncoded());
        } catch (CertificateException ex) {
            Rlog.e(LOG_TAG, "CertificateException: " + ex);
            Rlog.e(LOG_TAG, "Cannot compute cert hash");
            return null;
        } catch (NoSuchAlgorithmException ex2) {
            Rlog.e(LOG_TAG, "NoSuchAlgorithmException: " + ex2);
            Rlog.e(LOG_TAG, "Cannot compute cert hash");
            return null;
        }
    }

    private void updateState(int newState) {
        this.mState.set(newState);
        if (this.mLoadedCallback != null) {
            this.mLoadedCallback.sendToTarget();
        }
    }
}
