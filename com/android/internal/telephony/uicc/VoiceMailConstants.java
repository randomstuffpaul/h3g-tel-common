package com.android.internal.telephony.uicc;

import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

class VoiceMailConstants {
    static final String LOG_TAG = "VoiceMailConstants";
    static final int NAME = 0;
    static final int NUMBER = 1;
    static final String PARTNER_VOICEMAIL_PATH = "/data/misc/radio/voicemail-conf.xml";
    static final int SIZE = 3;
    static final int TAG = 2;
    private HashMap<String, String[]> CarrierVmMap = new HashMap();

    VoiceMailConstants() {
        loadVoiceMail();
    }

    boolean containsCarrier(String carrier) {
        return this.CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        return ((String[]) this.CarrierVmMap.get(carrier))[0];
    }

    String getVoiceMailNumber(String carrier) {
        return ((String[]) this.CarrierVmMap.get(carrier))[1];
    }

    String getVoiceMailTag(String carrier) {
        return ((String[]) this.CarrierVmMap.get(carrier))[2];
    }

    private void loadVoiceMail() {
        File vmFile = new File(PARTNER_VOICEMAIL_PATH);
        Rlog.e(LOG_TAG, "[Voicemail] loadVoiceMail ");
        try {
            FileReader vmReader = new FileReader(vmFile);
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(vmReader);
                XmlUtils.beginDocument(parser, "voicemail");
                while (true) {
                    XmlUtils.nextElement(parser);
                    if (!"voicemail".equals(parser.getName())) {
                        break;
                    }
                    data = new String[3];
                    String carrier = parser.getAttributeValue(null, "carrier");
                    data[0] = parser.getAttributeValue(null, Carriers.NUMERIC);
                    data[1] = parser.getAttributeValue(null, "vmnumber");
                    data[2] = parser.getAttributeValue(null, "vmtag");
                    Rlog.e(LOG_TAG, "[Voicemail] carrier " + carrier + " numeric : " + data[0] + "vmnumber : " + data[1] + " tag : " + data[2]);
                    this.CarrierVmMap.put(carrier, data);
                }
                if (vmReader != null) {
                    try {
                        vmReader.close();
                    } catch (IOException e) {
                    }
                }
            } catch (XmlPullParserException e2) {
                Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e2);
                if (vmReader != null) {
                    try {
                        vmReader.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (IOException e4) {
                Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e4);
                if (vmReader != null) {
                    try {
                        vmReader.close();
                    } catch (IOException e5) {
                    }
                }
            } catch (Throwable th) {
                if (vmReader != null) {
                    try {
                        vmReader.close();
                    } catch (IOException e6) {
                    }
                }
            }
        } catch (FileNotFoundException e7) {
            Rlog.w(LOG_TAG, "Can't open /data/misc/radio/voicemail-conf.xml");
        }
    }
}
