package com.android.internal.telephony;

import android.os.RemoteException;
import com.android.internal.telephony.IPhoneSubInfo.Stub;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class PhoneSubInfoProxy extends Stub {
    private PhoneSubInfo mPhoneSubInfo;

    public PhoneSubInfoProxy(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    public void setmPhoneSubInfo(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    public String getDeviceId() {
        return this.mPhoneSubInfo.getDeviceId();
    }

    public String getImei() {
        return this.mPhoneSubInfo.getImei();
    }

    public String getDeviceSvn() {
        return this.mPhoneSubInfo.getDeviceSvn();
    }

    public String getSubscriberId() {
        return this.mPhoneSubInfo.getSubscriberId();
    }

    public String getGroupIdLevel1() {
        return this.mPhoneSubInfo.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        return this.mPhoneSubInfo.getIccSerialNumber();
    }

    public String getLine1Number() {
        return this.mPhoneSubInfo.getLine1Number();
    }

    public String getLine1AlphaTag() {
        return this.mPhoneSubInfo.getLine1AlphaTag();
    }

    public String getMsisdn() {
        return this.mPhoneSubInfo.getMsisdn();
    }

    public String getVoiceMailNumber() {
        return this.mPhoneSubInfo.getVoiceMailNumber();
    }

    public String getCompleteVoiceMailNumber() {
        return this.mPhoneSubInfo.getCompleteVoiceMailNumber();
    }

    public String getVoiceMailAlphaTag() {
        return this.mPhoneSubInfo.getVoiceMailAlphaTag();
    }

    public String getIsimImpi() {
        return this.mPhoneSubInfo.getIsimImpi();
    }

    public String getIsimDomain() {
        return this.mPhoneSubInfo.getIsimDomain();
    }

    public String[] getIsimImpu() {
        return this.mPhoneSubInfo.getIsimImpu();
    }

    public String getDeviceIdForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getImeiForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getSubscriberIdForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getGroupIdLevel1ForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getIccSerialNumberForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getLine1NumberForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getLine1AlphaTagForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getMsisdnForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getVoiceMailNumberForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String[] getSponImsi() {
        return this.mPhoneSubInfo.getSponImsi();
    }

    public String getCompleteVoiceMailNumberForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getVoiceMailAlphaTagForSubscriber(long subId) throws RemoteException {
        return null;
    }

    public String getIsimIst() {
        return this.mPhoneSubInfo.getIsimIst();
    }

    public String[] getIsimPcscf() {
        return this.mPhoneSubInfo.getIsimPcscf();
    }

    public boolean hasIsim() {
        return this.mPhoneSubInfo.hasIsim();
    }

    public byte[] getPsismsc() {
        return this.mPhoneSubInfo.getPsismsc();
    }

    public String getIsimChallengeResponse(String nonce) {
        return this.mPhoneSubInfo.getIsimChallengeResponse(nonce);
    }

    public String getIccSimChallengeResponse(long subId, int appType, String data) {
        return this.mPhoneSubInfo.getIccSimChallengeResponse(subId, appType, data);
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mPhoneSubInfo.dump(fd, pw, args);
    }

    public boolean isImsRegistered() {
        return this.mPhoneSubInfo.isImsRegistered();
    }

    public boolean isVolteRegistered() {
        return this.mPhoneSubInfo.isVolteRegistered();
    }

    public boolean isWfcRegistered() {
        return this.mPhoneSubInfo.isWfcRegistered();
    }

    public int getImsRegisteredFeature() {
        return this.mPhoneSubInfo.getImsRegisteredFeature();
    }

    public boolean hasCall(String callType) {
        return this.mPhoneSubInfo.hasCall(callType);
    }

    public boolean setDrxMode(int drxMode) {
        return this.mPhoneSubInfo.setDrxMode(drxMode);
    }

    public int getDrxValue() {
        return this.mPhoneSubInfo.getDrxValue();
    }

    public boolean setReducedCycleTime(int cycleTime) {
        return this.mPhoneSubInfo.setReducedCycleTime(cycleTime);
    }

    public int getReducedCycleTime() {
        return this.mPhoneSubInfo.getReducedCycleTime();
    }

    public String getSktImsiM() {
        return this.mPhoneSubInfo.getSktImsiM();
    }

    public String getSktIrm() {
        return this.mPhoneSubInfo.getSktIrm();
    }

    public boolean isGbaSupported() {
        return this.mPhoneSubInfo.isGbaSupported();
    }

    public String getLine1NumberType(int SimType) {
        return this.mPhoneSubInfo.getLine1NumberType(SimType);
    }

    public String getSubscriberIdType(int SimType) {
        return this.mPhoneSubInfo.getSubscriberIdType(SimType);
    }

    public byte[] getRand() {
        return this.mPhoneSubInfo.getRand();
    }

    public String getBtid() {
        return this.mPhoneSubInfo.getBtid();
    }

    public String getKeyLifetime() {
        return this.mPhoneSubInfo.getKeyLifetime();
    }

    public String getIsimAid() {
        return this.mPhoneSubInfo.getIsimAid();
    }
}
