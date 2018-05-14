package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.provider.Settings.System;
import android.telephony.Rlog;
import com.android.internal.telephony.cat.AppInterface;
import com.samsung.android.telephony.MultiSimManager;

public class TelephonyPropertiesEdit {
    static final String LOG_TAG = "TelephonyPropertiesEdit";
    private Context mContext;
    BroadcastReceiver mIntentReceiver = new C00361();
    private PhoneBase mPhone;

    class C00361 extends BroadcastReceiver {
        C00361() {
        }

        public void onReceive(Context context, Intent intent) {
            Rlog.e(TelephonyPropertiesEdit.LOG_TAG, "onReceive : " + intent.getAction());
            if (intent.getAction().equals("NEW_CARD_CHECK")) {
                TelephonyPropertiesEdit.this.setPropertyNewCard(intent.getIntExtra("slotWitch", 0));
            } else if (intent.getAction().equals("android.settings.SIMCARD_MGT")) {
                TelephonyPropertiesEdit.this.setPropertyIconName(intent.getIntExtra("simcard_sim_id", 0), (String) intent.getExtra("simcard_sim_icon"), (String) intent.getExtra("simcard_sim_name"));
            } else if (intent.getAction().equals("ACTION_REGCARD_ICON_CHANGED")) {
                extras = intent.getExtras();
                TelephonyPropertiesEdit.this.setPropertyIcon(extras.getString("CDMA01_ICON_INDEX", "6"), extras.getString("GSM_ICON_INDEX", "7"), extras.getString("GSM02_ICON_INDEX", "8"));
            } else if (intent.getAction().equals("ACTION_REGCARD_CARDNAME_CHANGED")) {
                extras = intent.getExtras();
                TelephonyPropertiesEdit.this.setPropertyCardname(extras.getString("CDMA01_CARDNAME", "Slot 1"), extras.getString("GSM_CARDNAME", "Slot 2"), extras.getString("GSM02_CARDNAME", "Slot 1"));
            } else if (intent.getAction().equals("ACTION_NETWORK_ACTIVATE_STATE")) {
                extras = intent.getExtras();
                TelephonyPropertiesEdit.this.setPropertyActivity(extras.getString("CARDTYPE_CDMA01"), extras.getString("CARDTYPE_GSM"), extras.getString("CARDTYPE_GSM02"));
            } else if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
                int slot = intent.getIntExtra("slot", 0);
                TelephonyPropertiesEdit.this.setPropertyState(0);
                TelephonyPropertiesEdit.this.setPropertyState(1);
                if ("CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                    Intent i = new Intent();
                    i.setAction("ACTION_SET_PROPERTY_STATE");
                    i.putExtra("property_item", "SIM_STATE_CHANGED");
                    i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, slot);
                    context.sendBroadcast(i);
                }
            } else if (intent.getAction().equals("ACTION_PLMN_UPDATE")) {
                extras = intent.getExtras();
                TelephonyPropertiesEdit.this.setPropertyPLMN(extras.getString("slot1"), extras.getString("slot2"), extras.getBoolean("slot1State"), extras.getBoolean("slot2State"));
            } else if (intent.getAction().equals("com.samsung.intent.action.Slot1OffCompleted") || intent.getAction().equals("com.samsung.intent.action.Slot2OffCompleted") || intent.getAction().equals("com.samsung.intent.action.Slot1OnCompleted") || intent.getAction().equals("com.samsung.intent.action.Slot2OnCompleted")) {
                TelephonyPropertiesEdit.this.handleCardOnOffCompleted(intent.getAction(), context);
            }
        }
    }

    public TelephonyPropertiesEdit(PhoneBase phone, Context context) {
        this.mPhone = phone;
        this.mContext = context;
        Rlog.d(LOG_TAG, "Creating TelephonyPropertiesEdit");
        if ("NULL".equals(SystemProperties.get("gsm.sim.currentcardstatus", "NULL"))) {
            resetProperties();
        } else {
            Rlog.d(LOG_TAG, "skip resetProperties");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("NEW_CARD_CHECK");
        filter.addAction("ACTION_REGCARD_ICON_CHANGED");
        filter.addAction("ACTION_REGCARD_CARDNAME_CHANGED");
        filter.addAction("ACTION_NETWORK_ACTIVATE_STATE");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("ACTION_PLMN_UPDATE");
        filter.addAction("com.samsung.intent.action.DATA_SERVICE_NETWORK");
        filter.addAction("com.samsung.intent.action.Slot1OnCompleted");
        filter.addAction("com.samsung.intent.action.Slot2OnCompleted");
        filter.addAction("com.samsung.intent.action.Slot1OffCompleted");
        filter.addAction("com.samsung.intent.action.Slot2OffCompleted");
        filter.addAction("android.settings.SIMCARD_MGT");
        this.mContext.registerReceiver(this.mIntentReceiver, filter);
    }

    void resetProperties() {
        Rlog.d(LOG_TAG, "resetProperties");
        setSystemProperty("gsm.sim.newCheck", 0, "false");
        setSystemProperty("gsm.sim.newCheck", 1, "false");
        setSystemProperty("gsm.sim.availability", 0, "false");
        setSystemProperty("gsm.sim.availability", 1, "false");
        setSystemProperty("gsm.sim.pplock", 0, "");
        setSystemProperty("gsm.sim.pplock", 1, "");
        if ("DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG") || "CG".equals("DGG")) {
            setSystemProperty("gsm.sim.cardname", 0, "Slot 1");
            setSystemProperty("gsm.sim.cardname", 1, "Slot 2");
            SystemProperties.set("gsm.sim.cardname.dual", "Slot 1");
            setSystemProperty("gsm.sim.cardnoti", 0, "0");
            setSystemProperty("gsm.sim.cardnoti", 1, "0");
            setSystemProperty("gsm.sim.state", 0, "UNKNOWN");
            setSystemProperty("gsm.sim.state", 1, "UNKNOWN");
            SystemProperties.set("gsm.sim.selectnetwork", "CDMA");
            SystemProperties.set("gsm.sim.slotswitching", "0");
        } else {
            setSystemProperty("gsm.sim.cardname", 0, "");
            setSystemProperty("gsm.sim.cardname", 1, "");
            SystemProperties.set("gsm.sim.cardname.dual", "");
        }
        if ("DGG".equals("DGG")) {
            int icon1 = System.getInt(this.mContext.getContentResolver(), "select_icon_1", 2);
            int icon2 = System.getInt(this.mContext.getContentResolver(), "select_icon_2", 3);
            setSystemProperty("gsm.sim.icon", 0, Integer.toString(icon1));
            setSystemProperty("gsm.sim.icon", 1, Integer.toString(icon2));
        } else {
            setSystemProperty("gsm.sim.icon", 0, "0");
            setSystemProperty("gsm.sim.icon", 1, "1");
        }
        SystemProperties.set("gsm.sim.icon.dual", "0");
        setSystemProperty("gsm.sim.activity", 0, "false");
        setSystemProperty("gsm.sim.activity", 1, "false");
        SystemProperties.set("gsm.sim.activity.dual", "false");
        setSystemProperty("gsm.sim.currentcardstatus", 0, "9");
        setSystemProperty("gsm.sim.currentcardstatus", 1, "9");
        setSystemProperty("gsm.sim.active", 0, "0");
        setSystemProperty("gsm.sim.active", 1, "0");
    }

    private void setPropertyPLMN(String sSwitchStateC, String sSwitchStateG, boolean bSwitchImgViewC, boolean bSwitchImgViewG) {
        Rlog.d(LOG_TAG, "Cdma plmnstring + " + sSwitchStateC + ", plmnstate + " + bSwitchImgViewC);
        Rlog.d(LOG_TAG, "Gsm plmnstring + " + sSwitchStateG + ", plmnstate + " + bSwitchImgViewG);
        if (bSwitchImgViewC) {
            SystemProperties.set("gsm.plmnstring", sSwitchStateC);
            SystemProperties.set("gsm.plmnstate", "1");
        } else if (bSwitchImgViewG) {
            SystemProperties.set("gsm.plmnstring", sSwitchStateG);
            SystemProperties.set("gsm.plmnstate", "2");
        } else {
            SystemProperties.set("gsm.plmnstring", sSwitchStateC);
            SystemProperties.set("gsm.plmnstate", "0");
        }
    }

    private void setPropertyNewCard(int slot) {
        Rlog.e(LOG_TAG, "onReceive CDMAPHONE NEW_CARD_CHECK + " + slot);
        if (slot == 1) {
            setSystemProperty("gsm.sim.newCheck", 0, "true");
        } else if (slot == 2) {
            setSystemProperty("gsm.sim.newCheck", 1, "true");
        } else if (slot == 3) {
            setSystemProperty("gsm.sim.newCheck", 0, "true");
            setSystemProperty("gsm.sim.newCheck", 1, "true");
        }
    }

    private void setPropertyIconName(int simId, String simIconIndex, String simName) {
        Rlog.e(LOG_TAG, "setPropertyIconName");
        setSystemProperty("gsm.sim.icon", simId, simIconIndex);
        setSystemProperty("gsm.sim.cardname", simId, simName);
        if (simId == 0) {
            if (!(simIconIndex == null || simIconIndex == "")) {
                System.putInt(this.mContext.getContentResolver(), "select_icon_1", Integer.parseInt(simIconIndex));
            }
            if (simName != null && simName != "") {
                System.putString(this.mContext.getContentResolver(), "select_name_1", simName);
                return;
            }
            return;
        }
        if (!(simIconIndex == null || simIconIndex == "")) {
            System.putInt(this.mContext.getContentResolver(), "select_icon_2", Integer.parseInt(simIconIndex));
        }
        if (simName != null && simName != "") {
            System.putString(this.mContext.getContentResolver(), "select_name_2", simName);
        }
    }

    private void setPropertyCardname(String cdmaName, String gsm01Name, String gsm02Name) {
        Rlog.e(LOG_TAG, "onReceive ACTION_REGCARD_CARDNAME_CHANGED");
        setSystemProperty("gsm.sim.cardname", 0, cdmaName);
        setSystemProperty("gsm.sim.cardname", 1, gsm01Name);
        SystemProperties.set("gsm.sim.cardname.dual", gsm02Name);
    }

    private void setPropertyIcon(String cdmaIcon, String gsm01Icon, String gsm02Icon) {
        Rlog.e(LOG_TAG, "onReceive ACTION_REGCARD_ICON_CHANGED");
        setSystemProperty("gsm.sim.icon", 0, cdmaIcon);
        setSystemProperty("gsm.sim.icon", 1, gsm01Icon);
        SystemProperties.set("gsm.sim.icon.dual", gsm02Icon);
    }

    private void setPropertyActivity(String cdmaAct, String gsm01Act, String gsm02Act) {
        Rlog.e(LOG_TAG, "onReceive ACTION_NETWORK_ACTIVATE_STATE");
        Rlog.e(LOG_TAG, cdmaAct + " , " + gsm01Act + " , " + gsm02Act);
        String cdmAct = "1".equals(cdmaAct) ? "true" : "false";
        String gs1Act = "1".equals(gsm01Act) ? "true" : "false";
        String gs2Act = "1".equals(gsm02Act) ? "true" : "false";
        Rlog.e(LOG_TAG, cdmAct + " , " + gs1Act + " , " + gs2Act);
        setSystemProperty("gsm.sim.activity", 0, cdmAct);
        setSystemProperty("gsm.sim.activity", 1, gs1Act);
        SystemProperties.set("gsm.sim.availability", gs2Act);
    }

    private void handleCardOnOffCompleted(String cardStatus, Context context) {
        Intent i;
        if ("com.samsung.intent.action.Slot1OffCompleted".equals(cardStatus)) {
            setSystemProperty("gsm.sim.active", 0, "0");
            setSystemProperty("gsm.sim.currentcardstatus", 0, "2");
            System.putInt(context.getContentResolver(), "phone1_on", 0);
            if ("CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                i = new Intent();
                i.setAction("ACTION_SET_PROPERTY_STATE");
                i.putExtra("property_item", "Slot1OffCompleted");
                i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, 0);
                context.sendBroadcast(i);
            }
        } else if ("com.samsung.intent.action.Slot1OnCompleted".equals(cardStatus)) {
            setSystemProperty("gsm.sim.active", 0, "0");
            if (!(Integer.parseInt(getSystemProperty("gsm.sim.currentcardstatus", 0, "9")) == 1 || "CG".equals("DGG"))) {
                setSystemProperty("gsm.sim.currentcardstatus", 0, "3");
            }
            System.putInt(context.getContentResolver(), "phone1_on", 1);
            if ("DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                i = new Intent();
                i.setAction("ACTION_SET_PROPERTY_STATE");
                i.putExtra("property_item", "Slot1OnCompleted");
                i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, 0);
                context.sendBroadcast(i);
            }
        } else if ("com.samsung.intent.action.Slot2OffCompleted".equals(cardStatus)) {
            setSystemProperty("gsm.sim.active", 1, "0");
            setSystemProperty("gsm.sim.currentcardstatus", 1, "2");
            System.putInt(context.getContentResolver(), "phone2_on", 0);
            if ("CG".equals("DGG") || "DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                i = new Intent();
                i.setAction("ACTION_SET_PROPERTY_STATE");
                i.putExtra("property_item", "Slot2OffCompleted");
                i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, 1);
                context.sendBroadcast(i);
            }
        } else if ("com.samsung.intent.action.Slot2OnCompleted".equals(cardStatus)) {
            setSystemProperty("gsm.sim.active", 1, "0");
            if (!(Integer.parseInt(getSystemProperty("gsm.sim.currentcardstatus", 1, "9")) == 1 || "CG".equals("DGG"))) {
                setSystemProperty("gsm.sim.currentcardstatus", 1, "3");
            }
            System.putInt(context.getContentResolver(), "phone2_on", 1);
            if ("DGG".equals("DGG") || "DCG".equals("DGG") || "DCGG".equals("DGG") || "DCGS".equals("DGG") || "DCGGS".equals("DGG")) {
                i = new Intent();
                i.setAction("ACTION_SET_PROPERTY_STATE");
                i.putExtra("property_item", "Slot2OnCompleted");
                i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, 1);
                context.sendBroadcast(i);
            }
        }
    }

    private void setPropertyState(int slot) {
        Rlog.d(LOG_TAG, "onReceive setPropertyState slot : " + slot);
        String mStateSlot = getSystemProperty("gsm.sim.state", slot, "ABSENT");
        int cardnoti = Integer.parseInt(SystemProperties.get(MultiSimManager.appendSimSlot("ril.cardnoti", slot), "0"));
        int cardstatus = Integer.parseInt(getSystemProperty("gsm.sim.currentcardstatus", slot, "0"));
        Rlog.d(LOG_TAG, "mStateSlot : " + mStateSlot);
        Rlog.i(LOG_TAG, "cardnoti : " + cardnoti);
        Rlog.i(LOG_TAG, "cardstatus : " + cardstatus);
        if (cardnoti == 0) {
            Rlog.d(LOG_TAG, "cardnoti 0, skip setPropertyState slot : " + slot);
        } else if ("ABSENT".equals(mStateSlot)) {
            setSystemProperty("gsm.sim.availability", slot, "false");
            setSystemProperty("gsm.sim.pplock", slot, "");
            setSystemProperty("gsm.sim.currentcardstatus", slot, "0");
            setSystemProperty("gsm.sim.cardnoti", slot, "1");
        } else if ("UNKNOWN".equals(mStateSlot) || "NOT_READY".equals(mStateSlot)) {
            String icctype = getSystemProperty("ril.ICC_TYPE", slot, "0");
            if (icctype == null || icctype.equals("")) {
                icctype = "0";
            }
            int type = Integer.parseInt(icctype);
            Rlog.i(LOG_TAG, "ril.ICC_TYPE :" + type);
            Rlog.i(LOG_TAG, "switching.slot :" + System.getInt(this.mContext.getContentResolver(), "switching.slot", 0));
            if (slot == 0) {
                if (type == 3 || type == 4) {
                    setSystemProperty("gsm.sim.availability", slot, "true");
                } else if (System.getInt(this.mContext.getContentResolver(), "switching.slot", 0) != 0) {
                    setSystemProperty("gsm.sim.availability", slot, "true");
                } else if ("DGG".equals("DGG")) {
                    setSystemProperty("gsm.sim.pplock", slot, "");
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "0");
                } else {
                    setSystemProperty("gsm.sim.pplock", slot, "");
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "0");
                }
            } else if (type == 1) {
                setSystemProperty("gsm.sim.availability", slot, "true");
            } else if (type == 3 || type == 4) {
                if (System.getInt(this.mContext.getContentResolver(), "switching.slot", 0) == 0) {
                    setSystemProperty("gsm.sim.availability", slot, "false");
                    if (type != 0) {
                        setSystemProperty("gsm.sim.state", slot, "ABSENT");
                    }
                    setSystemProperty("gsm.sim.pplock", slot, "");
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "0");
                } else {
                    setSystemProperty("gsm.sim.availability", slot, "true");
                }
            }
            if ("DTC".equals("DGG")) {
                int mPhoneOnMode;
                if (slot == 1) {
                    mPhoneOnMode = System.getInt(this.mContext.getContentResolver(), "phone2_on", 9);
                } else {
                    mPhoneOnMode = System.getInt(this.mContext.getContentResolver(), "phone1_on", 9);
                }
                if (mPhoneOnMode == 0) {
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "2");
                    setSystemProperty("gsm.sim.state", slot, "UNKNOWN");
                }
            }
        } else if ("READY".equals(mStateSlot)) {
            setSystemProperty("gsm.sim.availability", slot, "true");
            setSystemProperty("gsm.sim.pplock", slot, "unlock");
            if (cardstatus != 2) {
                setSystemProperty("gsm.sim.currentcardstatus", slot, "3");
            }
            if (("CG".equals("DGG") || "DTC".equals("DGG")) && Integer.parseInt(getSystemProperty("gsm.sim.active", slot, "0")) == 2) {
                Intent i;
                setSystemProperty("gsm.sim.active", slot, "0");
                if (slot == 1) {
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "3");
                    System.putInt(this.mContext.getContentResolver(), "phone2_on", 1);
                    i = new Intent();
                    i.setAction("com.samsung.intent.action.Slot2OnCompleted");
                    this.mContext.sendBroadcast(i);
                } else {
                    setSystemProperty("gsm.sim.currentcardstatus", slot, "3");
                    System.putInt(this.mContext.getContentResolver(), "phone1_on", 1);
                    i = new Intent();
                    i.setAction("com.samsung.intent.action.Slot1OnCompleted");
                    this.mContext.sendBroadcast(i);
                }
                i = new Intent();
                i.setAction("ACTION_SET_PROPERTY_STATE");
                i.putExtra("property_item", "currentcardstatuson");
                i.putExtra(AppInterface.CAT_EXTRA_SIM_SLOT, slot);
                this.mContext.sendBroadcast(i);
            }
            if ("DTC".equals("DGG") && slot == 1) {
                SystemProperties.set("ril.Slotswitching", "0");
            }
            setSystemProperty("gsm.sim.cardnoti", slot, "2");
        } else {
            setSystemProperty("gsm.sim.availability", slot, "true");
            setSystemProperty("gsm.sim.pplock", slot, mStateSlot);
            setSystemProperty("gsm.sim.currentcardstatus", slot, "1");
            setSystemProperty("gsm.sim.cardnoti", slot, "2");
        }
    }

    private void setSystemProperty(String property, int slotId, String value) {
        MultiSimManager.setTelephonyProperty(property, slotId, value);
    }

    private String getSystemProperty(String property, int slotId, String defValue) {
        return MultiSimManager.getTelephonyProperty(property, slotId, defValue);
    }
}
