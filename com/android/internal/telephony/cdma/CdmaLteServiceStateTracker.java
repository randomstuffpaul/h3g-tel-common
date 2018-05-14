package com.android.internal.telephony.cdma;

import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class CdmaLteServiceStateTracker extends CdmaServiceStateTracker {
    private static final int DAN_DELAY_TIMER = 5000;
    private static final int DCN_HYST_TIMER = 15000;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    private static final int EVENT_DCN_TIMER_START = 2000;
    private static final int EVENT_DCN_TIMER_STOP = 2001;
    private CDMALTEPhone mCdmaLtePhone;
    private final CellInfoLte mCellInfoLte;
    private boolean mDCNMessageTimer = false;
    private CellIdentityLte mLasteCellIdentityLte = new CellIdentityLte();
    int mLatestDataRadioTechnology;
    private CellIdentityLte mNewCellIdentityLte = new CellIdentityLte();

    static /* synthetic */ class C00741 {
        static final /* synthetic */ int[] f11x46dd5024 = new int[RadioState.values().length];

        static {
            try {
                f11x46dd5024[RadioState.RADIO_UNAVAILABLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f11x46dd5024[RadioState.RADIO_OFF.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    public CdmaLteServiceStateTracker(CDMALTEPhone phone) {
        super(phone, new CellInfoLte());
        this.mCdmaLtePhone = phone;
        this.mCellInfoLte = (CellInfoLte) this.mCellInfo;
        this.mLatestDataRadioTechnology = 0;
        ((CellInfoLte) this.mCellInfo).setCellSignalStrength(new CellSignalStrengthLte());
        ((CellInfoLte) this.mCellInfo).setCellIdentity(new CellIdentityLte());
        log("CdmaLteServiceStateTracker Constructors");
    }

    public void handleMessage(Message msg) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            switch (msg.what) {
                case 5:
                    log("handleMessage EVENT_POLL_STATE_GPRS");
                    handlePollStateResult(msg.what, msg.obj);
                    return;
                case 14:
                    log("handleMessage EVENT_POLL_STATE_NETWORK_SELECTION_MODE");
                    handlePollStateResult(msg.what, (AsyncResult) msg.obj);
                    return;
                case WspTypeDecoder.WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    updatePhoneObject();
                    RuimRecords ruim = null;
                    if (this.mIccRecords instanceof RuimRecords) {
                        ruim = this.mIccRecords;
                    } else {
                        log("IccRecords is not RuimRecords..");
                        if (null == null) {
                            return;
                        }
                    }
                    if (ruim != null) {
                        if (ruim.isProvisioned()) {
                            this.mMdn = ruim.getMdn();
                            this.mMin = ruim.getMin();
                            parseSidNid(ruim.getSid(), ruim.getNid());
                            this.mPrlVersion = ruim.getPrlVersion();
                            if ("false".equals(SystemProperties.get("ro.product_ship", "false"))) {
                                log("MDN: " + this.mMdn + ", MIN: " + this.mMin + ", PRL: " + this.mPrlVersion);
                            }
                            this.mIsMinInfoReady = true;
                        }
                        if (!"3".equals(SystemProperties.get("ril.otasp_state"))) {
                            updateOtaspState();
                        }
                    }
                    this.mPhone.prepareEri();
                    pollState();
                    if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                        displayTimeDisplayScheme(this.mSS.getOperatorNumeric(), this.mSS.getState());
                        return;
                    }
                    return;
                case 1001:
                    ProxyController.getInstance().unregisterForAllDataDisconnected(SubscriptionController.getInstance().getDefaultDataSubId(), this);
                    synchronized (this) {
                        if (this.mPendingRadioPowerOffAfterDataOff) {
                            log("EVENT_ALL_DATA_DISCONNECTED, turn radio off now.");
                            hangupAndPowerOff();
                            this.mPendingRadioPowerOffAfterDataOff = false;
                        } else {
                            log("EVENT_ALL_DATA_DISCONNECTED is stale");
                        }
                    }
                    return;
                case EVENT_DCN_TIMER_START /*2000*/:
                    Rlog.d("CdmaSST", "EVENT_DCN_TIMER Started");
                    this.mDCNMessageTimer = true;
                    sendMessageDelayed(obtainMessage(EVENT_DCN_TIMER_STOP), 5000);
                    return;
                case EVENT_DCN_TIMER_STOP /*2001*/:
                    Rlog.d("CdmaSST", "EVENT_DCN_TIMER expired");
                    this.mDCNMessageTimer = false;
                    SMSDispatcher smsDispatcher = this.mPhone.getSMSDispatcher();
                    if (smsDispatcher != null) {
                        Rlog.d("CdmaSST", "Sending domain change notification");
                        smsDispatcher.sendDomainChangeSms((byte) 0);
                        return;
                    }
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
        loge("Received message " + msg + "[" + msg.what + "]" + " while being destroyed. Ignoring.");
    }

    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        if (what == 5) {
            String[] states = (String[]) ar.result;
            log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states.length + " states=" + states);
            int type = 0;
            int regState = -1;
            if (states.length > 0) {
                try {
                    regState = Integer.parseInt(states[0]);
                    if (states.length >= 4 && states[3] != null) {
                        type = Integer.parseInt(states[3]);
                    }
                } catch (NumberFormatException ex) {
                    loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex);
                }
                if (states.length >= 10) {
                    int mcc;
                    int mnc;
                    int tac;
                    int pci;
                    int eci;
                    String str = null;
                    try {
                        str = this.mNewSS.getOperatorNumeric();
                        mcc = Integer.parseInt(str.substring(0, 3));
                    } catch (Exception e) {
                        try {
                            str = this.mSS.getOperatorNumeric();
                            mcc = Integer.parseInt(str.substring(0, 3));
                        } catch (Exception ex2) {
                            loge("handlePollStateResultMessage: bad mcc operatorNumeric=" + str + " ex=" + ex2);
                            str = "";
                            mcc = Integer.MAX_VALUE;
                        }
                    }
                    try {
                        mnc = Integer.parseInt(str.substring(3));
                    } catch (Exception e2) {
                        loge("handlePollStateResultMessage: bad mnc operatorNumeric=" + str + " e=" + e2);
                        mnc = Integer.MAX_VALUE;
                    }
                    try {
                        tac = Integer.decode(states[6]).intValue();
                    } catch (Exception e22) {
                        loge("handlePollStateResultMessage: bad tac states[6]=" + states[6] + " e=" + e22);
                        tac = Integer.MAX_VALUE;
                    }
                    try {
                        pci = Integer.decode(states[7]).intValue();
                    } catch (Exception e222) {
                        loge("handlePollStateResultMessage: bad pci states[7]=" + states[7] + " e=" + e222);
                        pci = Integer.MAX_VALUE;
                    }
                    try {
                        eci = Integer.decode(states[8]).intValue();
                    } catch (Exception e2222) {
                        loge("handlePollStateResultMessage: bad eci states[8]=" + states[8] + " e=" + e2222);
                        eci = Integer.MAX_VALUE;
                    }
                    try {
                        int csgid = Integer.decode(states[9]).intValue();
                    } catch (Exception e3) {
                    }
                    this.mNewCellIdentityLte = new CellIdentityLte(mcc, mnc, eci, pci, tac);
                    log("handlePollStateResultMessage: mNewLteCellIdentity=" + this.mNewCellIdentityLte);
                }
            }
            this.mNewSS.setRilDataRadioTechnology(type);
            int dataRegState = regCodeToServiceState(regState);
            this.mNewSS.setDataRegState(dataRegState);
            this.mDataRoaming = regCodeIsRoaming(regState);
            if (this.mDataRoaming) {
                this.mNewSS.setRoaming(true);
                this.mPhone.setSystemProperty("gsm.operator.ispsroaming", "true");
            } else {
                this.mPhone.setSystemProperty("gsm.operator.ispsroaming", "false");
            }
            log("handlPollStateResultMessage: CdmaLteSST setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + type);
        } else if (what == 14) {
            this.mNewSS.setIsManualSelection(((int[]) ((int[]) ar.result))[0] == 1);
        } else {
            super.handlePollStateResultMessage(what, ar);
        }
    }

    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (C00741.f11x46dd5024[this.mCi.getRadioState().ordinal()]) {
            case 1:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            case 2:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            default:
                int[] iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
                iArr = this.mPollingContext;
                iArr[0] = iArr[0] + 1;
                this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
                if ("LGT".equals("")) {
                    iArr = this.mPollingContext;
                    iArr[0] = iArr[0] + 1;
                    this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
                    return;
                }
                return;
        }
    }

    protected void pollStateDone() {
        boolean has4gHandoff;
        boolean hasMultiApnSupport;
        boolean hasLostMultiApnSupport;
        log("pollStateDone: lte 1 ss=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        useDataRegStateForDataOnlyDevices();
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        boolean hasDeregistered = this.mSS.getVoiceRegState() == 0 && this.mNewSS.getVoiceRegState() != 0;
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        if (this.mForceHasChanged) {
            hasChanged = true;
            this.mForceHasChanged = false;
            log("Change hasChanged to " + true);
        }
        boolean hasPlmnChanged = false;
        if (!(this.mSS.getOperatorNumeric() == null || this.mNewSS.getOperatorNumeric() == null || this.mSS.getOperatorNumeric() == this.mNewSS.getOperatorNumeric())) {
            hasPlmnChanged = true;
        }
        if (this.mNewSS.getDataRegState() == 0 && ((this.mSS.getRilDataRadioTechnology() == 14 && this.mNewSS.getRilDataRadioTechnology() == 13) || (this.mSS.getRilDataRadioTechnology() == 13 && this.mNewSS.getRilDataRadioTechnology() == 14))) {
            has4gHandoff = true;
        } else {
            has4gHandoff = false;
        }
        if ((this.mNewSS.getRilDataRadioTechnology() != 14 && this.mNewSS.getRilDataRadioTechnology() != 13) || this.mSS.getRilDataRadioTechnology() == 14 || this.mSS.getRilDataRadioTechnology() == 13) {
            hasMultiApnSupport = false;
        } else {
            hasMultiApnSupport = true;
        }
        if (this.mNewSS.getRilDataRadioTechnology() < 4 || this.mNewSS.getRilDataRadioTechnology() > 8) {
            hasLostMultiApnSupport = false;
        } else {
            hasLostMultiApnSupport = true;
        }
        log("pollStateDone: hasRegistered=" + hasRegistered + " hasDeegistered=" + hasDeregistered + " hasCdmaDataConnectionAttached=" + hasCdmaDataConnectionAttached + " hasCdmaDataConnectionDetached=" + hasCdmaDataConnectionDetached + " hasCdmaDataConnectionChanged=" + hasCdmaDataConnectionChanged + " hasVoiceRadioTechnologyChanged= " + hasVoiceRadioTechnologyChanged + " hasDataRadioTechnologyChanged=" + hasDataRadioTechnologyChanged + " hasChanged=" + hasChanged + " hasRoamingOn=" + hasRoamingOn + " hasRoamingOff=" + hasRoamingOff + " hasLocationChanged=" + hasLocationChanged + " has4gHandoff = " + has4gHandoff + " LatestDataRadioTechnology=" + this.mLatestDataRadioTechnology + " hasMultiApnSupport=" + hasMultiApnSupport + " hasLostMultiApnSupport=" + hasLostMultiApnSupport);
        if (!(this.mSS.getVoiceRegState() == this.mNewSS.getVoiceRegState() && this.mSS.getDataRegState() == this.mNewSS.getDataRegState())) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, new Object[]{Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState())});
        }
        if (this.mNewSS.getRilDataRadioTechnology() != 0) {
            this.mLatestDataRadioTechnology = this.mNewSS.getRilDataRadioTechnology();
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CdmaCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mNewSS.setStateOutOfService();
        if (hasVoiceRadioTechnologyChanged) {
            updatePhoneObject();
            if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                this.mPhone.setSystemProperty("gsm.voice.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()));
            }
        }
        if (hasDataRadioTechnologyChanged) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilDataRadioTechnology()));
            if (this.mSS.getRilDataRadioTechnology() != 14 || "CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                onSignalStrengthResult(false);
            } else {
                onSignalStrengthResult(true);
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (hasChanged) {
            boolean hasBrandOverride = this.mUiccController.getUiccCard() == null ? false : this.mUiccController.getUiccCard().getOperatorBrandOverride() != null;
            if (!hasBrandOverride && this.mCi.getRadioState().isOn() && this.mPhone.isEriFileLoaded()) {
                String eriText;
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else if (this.mSS.getVoiceRegState() == 3) {
                    eriText = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : null;
                    if (TextUtils.isEmpty(eriText)) {
                        eriText = SystemProperties.get("ro.cdma.home.operator.alpha");
                    }
                } else {
                    eriText = this.mPhone.getContext().getText(17039570).toString();
                }
                boolean useERItext = false;
                if ("0".equals("0")) {
                    useERItext = true;
                }
                if ("1".equals("0")) {
                    useERItext = true;
                }
                if ("3".equals("0")) {
                    useERItext = false;
                }
                if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                    useERItext = false;
                }
                if (useERItext) {
                    this.mSS.setOperatorAlphaLong(eriText);
                } else if (this.mSS.getState() != 0) {
                    eriText = this.mPhone.getContext().getText(17039570).toString();
                    if (!"CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                        this.mSS.setOperatorAlphaLong(eriText);
                    }
                    log("Set OperatorAlphaLong: " + eriText + ", Cause: ServiceState is " + this.mSS.getState());
                }
            }
            if (!(this.mUiccApplcation == null || this.mUiccApplcation.getState() != AppState.APPSTATE_READY || this.mIccRecords == null)) {
                boolean showSpn = ((RuimRecords) this.mIccRecords).getCsimSpnDisplayCondition();
                int iconIndex = this.mSS.getCdmaEriIconIndex();
                if (showSpn && iconIndex == 1) {
                    if (!(!isInHomeSidNid(this.mSS.getSystemId(), this.mSS.getNetworkId()) || this.mIccRecords == null || "1".equals("0") || "2".equals("0") || "CTC".equals(SystemProperties.get("ro.csc.sales_code")))) {
                        this.mSS.setOperatorAlphaLong(this.mIccRecords.getServiceProviderName());
                        log("Set OperatorAlphaLong: " + this.mSS.getOperatorAlphaLong() + ", Cause: SPN");
                    }
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = getSystemProperty("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                operatorNumeric = fixUnknownMcc(operatorNumeric, this.mSS.getSystemId());
            }
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (isInvalidOperatorNumeric(operatorNumeric)) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", isoCountryCode);
                this.mGotCountryCode = true;
                setOperatorIdd(operatorNumeric);
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.setSystemProperty("ril.servicestate", Integer.toString(this.mSS.getState()));
            updateSpnDisplay();
            if ("CG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGGS".equals("DGG")) {
                setTwochipDsdsOnRoaming();
            }
            if ("CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
                displayTimeDisplayScheme(this.mSS.getOperatorNumeric(), this.mSS.getState());
            }
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (hasCdmaDataConnectionAttached || has4gHandoff) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            this.mPhone.notifyDataConnection(null);
        }
        if (hasRoamingOn) {
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasRoamingOff) {
            this.mRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        if (hasPlmnChanged) {
            this.mPlmnChangeRegistrants.notifyRegistrants();
        }
        ArrayList<CellInfo> arrayCi = new ArrayList();
        synchronized (this.mCellInfo) {
            CellInfoLte cil = this.mCellInfo;
            boolean cidChanged = !this.mNewCellIdentityLte.equals(this.mLasteCellIdentityLte);
            if (hasRegistered || hasDeregistered || cidChanged) {
                long timeStamp = SystemClock.elapsedRealtime() * 1000;
                boolean registered = this.mSS.getVoiceRegState() == 0;
                this.mLasteCellIdentityLte = this.mNewCellIdentityLte;
                cil.setRegistered(registered);
                cil.setCellIdentity(this.mLasteCellIdentityLte);
                log("pollStateDone: hasRegistered=" + hasRegistered + " hasDeregistered=" + hasDeregistered + " cidChanged=" + cidChanged + " mCellInfo=" + this.mCellInfo);
                arrayCi.add(this.mCellInfo);
            }
            this.mPhoneBase.notifyCellInfo(arrayCi);
        }
    }

    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        if (this.mSS.getRilDataRadioTechnology() == 14 && !"CTC".equals(SystemProperties.get("ro.csc.sales_code"))) {
            isGsm = true;
        }
        boolean ssChanged = super.onSignalStrengthResult(ar, isGsm);
        synchronized (this.mCellInfo) {
            if (this.mSS.getRilDataRadioTechnology() == 14) {
                this.mCellInfoLte.setTimeStamp(SystemClock.elapsedRealtime() * 1000);
                this.mCellInfoLte.setTimeStampType(4);
                this.mCellInfoLte.getCellSignalStrength().initialize(this.mSignalStrength, Integer.MAX_VALUE);
            }
            if (this.mCellInfoLte.getCellIdentity() != null) {
                ArrayList<CellInfo> arrayCi = new ArrayList();
                arrayCi.add(this.mCellInfoLte);
                this.mPhoneBase.notifyCellInfo(arrayCi);
            }
        }
        return ssChanged;
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        if ((CscFeature.getInstance().getEnableStatus("CscFeature_RIL_SupportVolte") && this.mPhoneBase.getCallTracker().isAllActiveCallsOnLTE()) || this.mSS.getCssIndicator() == 1) {
            return true;
        }
        return false;
    }

    private boolean isInHomeSidNid(int sid, int nid) {
        if (isSidsAllZeros() || this.mHomeSystemId.length != this.mHomeNetworkId.length || sid == 0) {
            return true;
        }
        int i = 0;
        while (i < this.mHomeSystemId.length) {
            if (this.mHomeSystemId[i] == sid && (this.mHomeNetworkId[i] == 0 || this.mHomeNetworkId[i] == 65535 || nid == 0 || nid == 65535 || this.mHomeNetworkId[i] == nid)) {
                return true;
            }
            i++;
        }
        return false;
    }

    public List<CellInfo> getAllCellInfo() {
        if (this.mCi.getRilVersion() >= 8) {
            return super.getAllCellInfo();
        }
        List<CellInfo> arrayList = new ArrayList();
        synchronized (this.mCellInfo) {
            arrayList.add(this.mCellInfoLte);
        }
        log("getAllCellInfo: arrayList=" + arrayList);
        return arrayList;
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(((CDMALTEPhone) this.mPhone).getPhoneId(), 2);
    }

    protected void updateCdmaSubscription() {
        this.mCi.getCDMASubscription(obtainMessage(34));
    }

    public void powerOffRadioSafely(DcTrackerBase dcTracker) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                long dds = SubscriptionManager.getDefaultDataSubId();
                if (!dcTracker.isDisconnected() || (dds != this.mPhone.getSubId() && (dds == this.mPhone.getSubId() || !ProxyController.getInstance().isDataDisconnected(dds)))) {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (!(dds == this.mPhone.getSubId() || ProxyController.getInstance().isDataDisconnected(dds))) {
                        log("Data is active on DDS.  Wait for all data disconnect");
                        ProxyController.getInstance().registerForAllDataDisconnected(dds, this, 1001, null);
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    }
                    Message msg = Message.obtain(this);
                    msg.what = 38;
                    int i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                    this.mPendingRadioPowerOffAfterDataOffTag = i;
                    msg.arg1 = i;
                    if (sendMessageDelayed(msg, 30000)) {
                        log("Wait upto 30s for data to disconnect, then turn off radio.");
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    }
                } else {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                }
            }
        }
    }

    protected void updatePhoneObject() {
        int voiceRat = this.mSS.getRilVoiceRadioTechnology();
        if (this.mPhone.getContext().getResources().getBoolean(17956990)) {
            int volteReplacementRat = this.mPhoneBase.getContext().getResources().getInteger(17694810);
            Rlog.d("CdmaSST", "updatePhoneObject: volteReplacementRat=" + volteReplacementRat);
            if (voiceRat == 14 && volteReplacementRat == 0) {
                voiceRat = 6;
            }
            this.mPhoneBase.updatePhoneObject(voiceRat);
        }
    }

    protected void log(String s) {
        Rlog.d("CdmaSST", "[CdmaLteSST] " + s);
    }

    protected void loge(String s) {
        Rlog.e("CdmaSST", "[CdmaLteSST] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaLteServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mCdmaLtePhone=" + this.mCdmaLtePhone);
        pw.println(" mLatestDataRadioTechnology=" + this.mLatestDataRadioTechnology);
    }

    private boolean isDCNHystTimerRunning() {
        return this.mDCNMessageTimer;
    }

    private void setTwochipDsdsOnRoaming() {
        if (isTwochipDsdsOnRoamingModel() && this.mPhone.getPhoneId() == 0) {
            String operatorNumeric = this.mSS.getOperatorNumeric();
            if (TextUtils.isEmpty(operatorNumeric)) {
                SystemProperties.set("ril.twochip.roaming", "false");
            } else if (operatorNumeric.startsWith("460") || ("CTC".equals(SystemProperties.get("ro.csc.sales_code")) && operatorNumeric.startsWith("455"))) {
                SystemProperties.set("ril.twochip.roaming", "false");
            } else {
                SystemProperties.set("ril.twochip.roaming", "true");
            }
        }
    }
}
