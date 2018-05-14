package com.android.internal.telephony;

import android.os.Environment;
import android.provider.Telephony.CellBroadcasts;
import android.util.Log;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class MVNOSupportList {
    private static final String LOG_TAG = "MVNOSupportList";
    static final String MVNO_LIST_PATH = "etc/mvno_list.xml";
    private HashMap<String, List<MVNOListInfo>> MVNOListMap = new HashMap();

    class MVNOListInfo {
        private final String mvnoName;
        private final String simIMSI;

        MVNOListInfo(String simIMSI, String mvnoName) {
            this.simIMSI = simIMSI;
            this.mvnoName = mvnoName;
        }

        public String getSimIMSI() {
            return this.simIMSI;
        }

        public String getMVNOName() {
            return this.mvnoName;
        }

        public String toString() {
            return "MVNOListInfo [simIMSI=" + this.simIMSI + ", getMvnoName=" + this.mvnoName + "]";
        }
    }

    public MVNOSupportList() {
        loadMVNOList();
    }

    public String getMVNOName(String operator, String imsiMVNO) {
        MVNOListInfo mvnolisti = getMatchingMVNOListInfo(operator, imsiMVNO);
        if (mvnolisti == null) {
            return null;
        }
        return mvnolisti.getMVNOName();
    }

    private MVNOListInfo getMatchingMVNOListInfo(String operator, String imsiMVNO) {
        Log.d(LOG_TAG, "[MVNO] getMatchingMVNOListInfo, operator=[" + operator + "], imsiMVNO[" + imsiMVNO + "]");
        if (operator == null || imsiMVNO == null) {
            return null;
        }
        List<MVNOListInfo> mvnolisti = (List) this.MVNOListMap.get(operator);
        if (mvnolisti == null) {
            Log.d(LOG_TAG, "[MVNO] getMatchingMVNOListInfo - no entry for operator=[" + operator + "]");
            return null;
        }
        for (MVNOListInfo mlist : mvnolisti) {
            Log.d(LOG_TAG, "[MVNO] operator has been found, matched list =[ " + mlist + " ]");
            if (imsiMVNO.startsWith(mlist.getSimIMSI())) {
                Log.d(LOG_TAG, "[MVNO] getMatchingMVNOListInfo - mvno has been found=[" + imsiMVNO + "]");
                return mlist;
            }
        }
        Log.d(LOG_TAG, "[MVNO] getMatchingMVNOListInfo - no match");
        return null;
    }

    public void loadMVNOList() {
        File mvnolistFile = new File(Environment.getRootDirectory(), MVNO_LIST_PATH);
        Log.d(LOG_TAG, "[MVNO] loadMVNOList");
        try {
            FileReader mvnolistReader = new FileReader(mvnolistFile);
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(mvnolistReader);
                XmlUtils.beginDocument(parser, "mvnoList");
                String carrierPLMN = null;
                List<MVNOListInfo> mvnolisti = null;
                while (true) {
                    XmlUtils.nextElement(parser);
                    String name = parser.getName();
                    Log.d(LOG_TAG, "[MVNO] loadMVNOList element name:" + parser.getName());
                    if (!"carrier".equals(name)) {
                        if (!"mvno".equals(name)) {
                            break;
                        }
                        MVNOListInfo newMVNOListInfo = new MVNOListInfo(parser.getAttributeValue(null, "simIMSI"), parser.getAttributeValue(null, "mvnoName"));
                        Log.d(LOG_TAG, "[MVNO] Creating new entry for mvno=" + newMVNOListInfo.toString());
                        mvnolisti.add(newMVNOListInfo);
                    } else {
                        if (!(mvnolisti == null || carrierPLMN == null)) {
                            Log.d(LOG_TAG, "[MVNO] Put list into MVNOListMap =[" + carrierPLMN + "]");
                            this.MVNOListMap.put(carrierPLMN, mvnolisti);
                        }
                        carrierPLMN = parser.getAttributeValue(null, CellBroadcasts.PLMN);
                        Log.d(LOG_TAG, "[MVNO] loadMVNOList carrierPLMN:" + carrierPLMN);
                        mvnolisti = (List) this.MVNOListMap.get(carrierPLMN);
                        if (mvnolisti == null) {
                            Log.d(LOG_TAG, "[MVNO] Creating new list for carrier=[" + carrierPLMN + "]");
                            mvnolisti = new ArrayList(1);
                        }
                    }
                }
                if (!(mvnolisti == null || carrierPLMN == null)) {
                    Log.d(LOG_TAG, "[MVNO] Last element - Put list into MVNOListMap =[" + carrierPLMN + "]");
                    this.MVNOListMap.put(carrierPLMN, mvnolisti);
                }
                if (mvnolistReader != null) {
                    try {
                        mvnolistReader.close();
                    } catch (IOException e) {
                    }
                }
            } catch (XmlPullParserException e2) {
                Log.w(LOG_TAG, "Exception in lteon_netlist parser " + e2);
                if (mvnolistReader != null) {
                    try {
                        mvnolistReader.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (IOException e4) {
                Log.w(LOG_TAG, "Exception in lteon_netlist parser " + e4);
                if (mvnolistReader != null) {
                    try {
                        mvnolistReader.close();
                    } catch (IOException e5) {
                    }
                }
            } catch (Throwable th) {
                if (mvnolistReader != null) {
                    try {
                        mvnolistReader.close();
                    } catch (IOException e6) {
                    }
                }
            }
        } catch (FileNotFoundException e7) {
            Log.w(LOG_TAG, "[MVNO] can not open " + Environment.getRootDirectory() + "/" + MVNO_LIST_PATH);
        }
    }
}
