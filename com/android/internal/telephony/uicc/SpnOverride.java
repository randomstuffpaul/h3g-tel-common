package com.android.internal.telephony.uicc;

import android.os.Environment;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class SpnOverride {
    private static final Comparator<SpnOverrideInfo> COMPARATOR_IMSI_SUBSET = new C01321();
    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH = "etc/spn-conf.xml";
    private HashMap<String, List<SpnOverrideInfo>> mCarrierSpnMap = new HashMap();

    static class C01321 implements Comparator<SpnOverrideInfo> {
        C01321() {
        }

        public int compare(SpnOverrideInfo o1, SpnOverrideInfo o2) {
            return -o1.getImsiSubset().compareTo(o2.getImsiSubset());
        }
    }

    class SpnOverrideInfo {
        private final String[] fake_home_on;
        private final String[] fake_roaming_on;
        private final String imsi_subset;
        private final String numeric;
        private final String spn;
        private final String spn_display_rule;
        private final String[] spn_override_only_on;
        final /* synthetic */ SpnOverride this$0;

        SpnOverrideInfo(SpnOverride spnOverride, String numeric, String spn, String spn_display_rule, String spn_override_only_on, String fake_home_on, String imsi_subset, String fake_roaming_on) {
            String[] split;
            String[] strArr = null;
            this.this$0 = spnOverride;
            this.numeric = numeric;
            this.spn = spn;
            this.spn_display_rule = spn_display_rule;
            if (spn_override_only_on != null) {
                split = spn_override_only_on.split(",");
            } else {
                split = null;
            }
            this.spn_override_only_on = split;
            if (fake_home_on != null) {
                split = fake_home_on.split(",");
            } else {
                split = null;
            }
            this.fake_home_on = split;
            if (imsi_subset == null) {
                imsi_subset = "";
            }
            this.imsi_subset = imsi_subset;
            if (fake_roaming_on != null) {
                strArr = fake_roaming_on.split(",");
            }
            this.fake_roaming_on = strArr;
        }

        public String getNumeric() {
            return this.numeric;
        }

        public String getSpn() {
            return this.spn;
        }

        public String getSpnDisplayRule() {
            return this.spn_display_rule;
        }

        public String[] getSpnOverrideOnlyOn() {
            return this.spn_override_only_on;
        }

        public String[] getFakeHomeOn() {
            return this.fake_home_on;
        }

        public String getImsiSubset() {
            return this.imsi_subset;
        }

        public String[] getFakeRoamingOn() {
            return this.fake_roaming_on;
        }

        public String toString() {
            return "SpnOverrideInfo [numeric=" + this.numeric + ", spn=" + this.spn + ", spn_display_rule=" + this.spn_display_rule + ", spn_override_only_on=" + Arrays.toString(this.spn_override_only_on) + ", fake_home_on=" + Arrays.toString(this.fake_home_on) + ", imsi_subset=" + this.imsi_subset + ", fake_roaming_on=" + Arrays.toString(this.fake_roaming_on) + "]";
        }
    }

    public SpnOverride() {
        loadSpnOverrides();
    }

    public boolean containsCarrier(String carrier) {
        return this.mCarrierSpnMap.containsKey(carrier);
    }

    public String getSpn(String carrier, String imsi) {
        SpnOverrideInfo soi = getMatchingSpnOverrideInfo(carrier, imsi);
        if (soi != null) {
            return soi.getSpn();
        }
        return null;
    }

    private void loadSpnOverrides() {
        try {
            FileReader spnReader = new FileReader(new File(Environment.getRootDirectory(), PARTNER_SPN_OVERRIDE_PATH));
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(spnReader);
                XmlUtils.beginDocument(parser, "spnOverrides");
                while (true) {
                    XmlUtils.nextElement(parser);
                    if (!"spnOverride".equals(parser.getName())) {
                        break;
                    }
                    SpnOverrideInfo newSoi = new SpnOverrideInfo(this, parser.getAttributeValue(null, Carriers.NUMERIC), parser.getAttributeValue(null, "spn"), parser.getAttributeValue(null, "spn_display_rule"), parser.getAttributeValue(null, "spn_override_only_on"), parser.getAttributeValue(null, "fake_home_on"), parser.getAttributeValue(null, "imsi_subset"), parser.getAttributeValue(null, "fake_roaming_on"));
                    List<SpnOverrideInfo> aSoi = (List) this.mCarrierSpnMap.get(newSoi.numeric);
                    if (aSoi == null) {
                        aSoi = new ArrayList(1);
                    }
                    aSoi.add(newSoi);
                    Collections.sort(aSoi, COMPARATOR_IMSI_SUBSET);
                    this.mCarrierSpnMap.put(newSoi.numeric, aSoi);
                }
                spnReader.close();
                if (spnReader != null) {
                    try {
                        spnReader.close();
                    } catch (IOException e) {
                    }
                }
            } catch (XmlPullParserException e2) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e2);
                if (spnReader != null) {
                    try {
                        spnReader.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (IOException e4) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e4);
                if (spnReader != null) {
                    try {
                        spnReader.close();
                    } catch (IOException e5) {
                    }
                }
            } catch (Throwable th) {
                if (spnReader != null) {
                    try {
                        spnReader.close();
                    } catch (IOException e6) {
                    }
                }
            }
        } catch (FileNotFoundException e7) {
            Rlog.w(LOG_TAG, "Can not open " + Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
        }
    }

    int getDisplayRule(String carrier, String imsi) {
        SpnOverrideInfo soi = getMatchingSpnOverrideInfo(carrier, imsi);
        if (soi == null) {
            return -1;
        }
        String rule = soi.getSpnDisplayRule();
        if (rule == null) {
            return -1;
        }
        int result = 0;
        if (rule.contains("SPN_RULE_SHOW_SPN")) {
            result = 0 + 1;
        }
        if (rule.contains("SPN_RULE_SHOW_PLMN")) {
            result += 2;
        }
        return result;
    }

    String[] getOverrideOnlyOn(String carrier, String imsi) {
        SpnOverrideInfo soi = getMatchingSpnOverrideInfo(carrier, imsi);
        if (soi == null) {
            return null;
        }
        return soi.getSpnOverrideOnlyOn();
    }

    String[] getFakeHomeOn(String carrier, String imsi) {
        SpnOverrideInfo soi = getMatchingSpnOverrideInfo(carrier, imsi);
        if (soi == null) {
            return null;
        }
        return soi.getFakeHomeOn();
    }

    String[] getFakeRoamingOn(String carrier, String imsi) {
        SpnOverrideInfo soi = getMatchingSpnOverrideInfo(carrier, imsi);
        if (soi == null) {
            return null;
        }
        return soi.getFakeRoamingOn();
    }

    private SpnOverrideInfo getMatchingSpnOverrideInfo(String carrier, String imsi) {
        if (imsi == null || carrier == null) {
            return null;
        }
        Rlog.d(LOG_TAG, "[SpnOverride] getMatchingSpnOverrideInfo, carrier=[" + carrier + "], simop=[" + imsi.substring(0, 5) + "]");
        List<SpnOverrideInfo> aSoi = (List) this.mCarrierSpnMap.get(carrier);
        if (aSoi == null) {
            Rlog.d(LOG_TAG, "[SpnOverride] getMatchingSpnOverrideInfo - no entry for carrier=[" + carrier + "]");
            return null;
        }
        for (SpnOverrideInfo soi : aSoi) {
            String imsiSubset = soi.getImsiSubset();
            Rlog.d(LOG_TAG, "[SpnOverride] getMatchingSpnOverrideInfo - imsiSubset=[" + imsiSubset + "]");
            if (imsi.regionMatches(carrier.length(), imsiSubset, 0, imsiSubset.length())) {
                return soi;
            }
        }
        Rlog.d(LOG_TAG, "[SpnOverride] getMatchingSpnOverrideInfo - no match found");
        return null;
    }
}
