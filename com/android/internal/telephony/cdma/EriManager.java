package com.android.internal.telephony.cdma;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.util.XmlUtils;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public final class EriManager {
    private static final boolean DBG = true;
    static final int ERI_FROM_FILE_SYSTEM = 1;
    static final int ERI_FROM_MODEM = 2;
    static final int ERI_FROM_XML = 0;
    private static final String LOG_TAG = "CDMA";
    private static final boolean VDBG = false;
    private Context mContext;
    private EriFile mEriFile;
    private int mEriFileSource = 0;
    private boolean mIsEriFileLoaded;

    class EriDisplayInformation {
        int mEriIconIndex;
        int mEriIconMode;
        String mEriIconText;

        EriDisplayInformation(int eriIconIndex, int eriIconMode, String eriIconText) {
            this.mEriIconIndex = eriIconIndex;
            this.mEriIconMode = eriIconMode;
            this.mEriIconText = eriIconText;
        }

        public String toString() {
            return "EriDisplayInformation: { IconIndex: " + this.mEriIconIndex + " EriIconMode: " + this.mEriIconMode + " EriIconText: " + this.mEriIconText + " }";
        }
    }

    class EriFile {
        String[] mCallPromptId = new String[]{"", "", ""};
        int mEriFileType = -1;
        int mNumberOfEriEntries = 0;
        HashMap<Integer, EriInfo> mRoamIndTable = new HashMap();
        int mVersionNumber = -1;

        EriFile() {
        }
    }

    public EriManager(PhoneBase phone, Context context, int eriFileSource) {
        this.mContext = context;
        this.mEriFileSource = eriFileSource;
        this.mEriFile = new EriFile();
    }

    public void dispose() {
        this.mEriFile = new EriFile();
        this.mIsEriFileLoaded = false;
    }

    public void loadEriFile() {
        switch (this.mEriFileSource) {
            case 1:
                loadEriFileFromFileSystem();
                return;
            case 2:
                loadEriFileFromModem();
                return;
            default:
                loadEriFileFromXml();
                return;
        }
    }

    private void loadEriFileFromModem() {
    }

    private void loadEriFileFromFileSystem() {
    }

    private void loadEriFileFromXml() {
        XmlPullParser parser;
        InputStream stream;
        int parsedEriEntries;
        String name;
        int id;
        String text;
        int roamingIndicator;
        int iconIndex;
        int iconMode;
        String eriText;
        int callPromptId;
        int alertId;
        HashMap hashMap;
        FileInputStream stream2 = null;
        Resources r = this.mContext.getResources();
        try {
            Rlog.d(LOG_TAG, "loadEriFileFromXml: check for alternate file");
            InputStream fileInputStream = new FileInputStream(r.getString(17040717));
            try {
                parser = Xml.newPullParser();
                parser.setInput(fileInputStream, null);
                Rlog.d(LOG_TAG, "loadEriFileFromXml: opened alternate file");
                stream2 = fileInputStream;
            } catch (FileNotFoundException e) {
                stream = fileInputStream;
                Rlog.d(LOG_TAG, "loadEriFileFromXml: no alternate file");
                parser = null;
                if (parser == null) {
                    Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
                    parser = r.getXml(17891335);
                }
                XmlUtils.beginDocument(parser, "EriFile");
                this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
                this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
                this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
                parsedEriEntries = 0;
                while (true) {
                    XmlUtils.nextElement(parser);
                    name = parser.getName();
                    if (name == null) {
                        break;
                    } else if (name.equals("CallPromptId")) {
                        id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                        text = parser.getAttributeValue(null, "CallPromptText");
                        if (id >= 0) {
                        }
                        Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                    } else if (name.equals("EriInfo")) {
                        roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                        iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                        iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                        eriText = parser.getAttributeValue(null, "EriText");
                        callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                        alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                        parsedEriEntries++;
                        hashMap = this.mEriFile.mRoamIndTable;
                        hashMap.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                    }
                }
                if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                    Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
                }
                Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
                this.mIsEriFileLoaded = true;
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                }
                if (stream2 != null) {
                    try {
                        stream2.close();
                    } catch (IOException e2) {
                        return;
                    }
                }
            } catch (XmlPullParserException e3) {
                stream = fileInputStream;
                Rlog.d(LOG_TAG, "loadEriFileFromXml: no parser for alternate file");
                parser = null;
                if (parser == null) {
                    Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
                    parser = r.getXml(17891335);
                }
                XmlUtils.beginDocument(parser, "EriFile");
                this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
                this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
                this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
                parsedEriEntries = 0;
                while (true) {
                    XmlUtils.nextElement(parser);
                    name = parser.getName();
                    if (name == null) {
                        break;
                    } else if (name.equals("CallPromptId")) {
                        id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                        text = parser.getAttributeValue(null, "CallPromptText");
                        if (id >= 0) {
                        }
                        Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                    } else if (name.equals("EriInfo")) {
                        roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                        iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                        iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                        eriText = parser.getAttributeValue(null, "EriText");
                        callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                        alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                        parsedEriEntries++;
                        hashMap = this.mEriFile.mRoamIndTable;
                        hashMap.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                    }
                }
                if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                    Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
                }
                Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
                this.mIsEriFileLoaded = true;
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                }
                if (stream2 != null) {
                    stream2.close();
                }
            }
        } catch (FileNotFoundException e4) {
            Rlog.d(LOG_TAG, "loadEriFileFromXml: no alternate file");
            parser = null;
            if (parser == null) {
                Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
                parser = r.getXml(17891335);
            }
            XmlUtils.beginDocument(parser, "EriFile");
            this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
            this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
            this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
            parsedEriEntries = 0;
            while (true) {
                XmlUtils.nextElement(parser);
                name = parser.getName();
                if (name == null) {
                    break;
                } else if (name.equals("CallPromptId")) {
                    id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                    text = parser.getAttributeValue(null, "CallPromptText");
                    if (id >= 0) {
                    }
                    Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                } else if (name.equals("EriInfo")) {
                    roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                    iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                    iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                    eriText = parser.getAttributeValue(null, "EriText");
                    callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                    alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                    parsedEriEntries++;
                    hashMap = this.mEriFile.mRoamIndTable;
                    hashMap.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                }
            }
            if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
            }
            Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
            this.mIsEriFileLoaded = true;
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        } catch (XmlPullParserException e5) {
            Rlog.d(LOG_TAG, "loadEriFileFromXml: no parser for alternate file");
            parser = null;
            if (parser == null) {
                Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
                parser = r.getXml(17891335);
            }
            XmlUtils.beginDocument(parser, "EriFile");
            this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
            this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
            this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
            parsedEriEntries = 0;
            while (true) {
                XmlUtils.nextElement(parser);
                name = parser.getName();
                if (name == null) {
                    break;
                } else if (name.equals("CallPromptId")) {
                    id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                    text = parser.getAttributeValue(null, "CallPromptText");
                    if (id >= 0) {
                    }
                    Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                } else if (name.equals("EriInfo")) {
                    roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                    iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                    iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                    eriText = parser.getAttributeValue(null, "EriText");
                    callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                    alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                    parsedEriEntries++;
                    hashMap = this.mEriFile.mRoamIndTable;
                    hashMap.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                }
            }
            if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
            }
            Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
            this.mIsEriFileLoaded = true;
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        }
        if (parser == null) {
            Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
            parser = r.getXml(17891335);
        }
        try {
            XmlUtils.beginDocument(parser, "EriFile");
            this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
            this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
            this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
            parsedEriEntries = 0;
            while (true) {
                XmlUtils.nextElement(parser);
                name = parser.getName();
                if (name == null) {
                    break;
                } else if (name.equals("CallPromptId")) {
                    id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                    text = parser.getAttributeValue(null, "CallPromptText");
                    if (id >= 0 || id > 2) {
                        Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                    } else {
                        this.mEriFile.mCallPromptId[id] = text;
                    }
                } else if (name.equals("EriInfo")) {
                    roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                    iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                    iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                    eriText = parser.getAttributeValue(null, "EriText");
                    callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                    alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                    parsedEriEntries++;
                    hashMap = this.mEriFile.mRoamIndTable;
                    hashMap.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                }
            }
            if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
            }
            Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
            this.mIsEriFileLoaded = true;
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream2 != null) {
                stream2.close();
            }
        } catch (Exception e6) {
            Rlog.e(LOG_TAG, "Got exception while loading ERI file.", e6);
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream2 != null) {
                try {
                    stream2.close();
                } catch (IOException e7) {
                }
            }
        } catch (Throwable th) {
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream2 != null) {
                try {
                    stream2.close();
                } catch (IOException e8) {
                }
            }
        }
    }

    public int getEriFileVersion() {
        return this.mEriFile.mVersionNumber;
    }

    public int getEriNumberOfEntries() {
        return this.mEriFile.mNumberOfEriEntries;
    }

    public int getEriFileType() {
        return this.mEriFile.mEriFileType;
    }

    public boolean isEriFileLoaded() {
        return this.mIsEriFileLoaded;
    }

    private EriInfo getEriInfo(int roamingIndicator) {
        if (this.mEriFile.mRoamIndTable.containsKey(Integer.valueOf(roamingIndicator))) {
            return (EriInfo) this.mEriFile.mRoamIndTable.get(Integer.valueOf(roamingIndicator));
        }
        return null;
    }

    private EriDisplayInformation getEriDisplayInformation(int roamInd, int defRoamInd) {
        EriInfo eriInfo;
        EriDisplayInformation ret;
        if (this.mIsEriFileLoaded) {
            eriInfo = getEriInfo(roamInd);
            if (eriInfo != null) {
                return new EriDisplayInformation(eriInfo.iconIndex, eriInfo.iconMode, eriInfo.eriText);
            }
        }
        switch (roamInd) {
            case 0:
                ret = new EriDisplayInformation(0, 0, this.mContext.getText(17039557).toString());
                break;
            case 1:
                ret = new EriDisplayInformation(1, 0, this.mContext.getText(17039558).toString());
                break;
            case 2:
                ret = new EriDisplayInformation(2, 1, this.mContext.getText(17039559).toString());
                break;
            case 3:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039560).toString());
                break;
            case 4:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039561).toString());
                break;
            case 5:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039562).toString());
                break;
            case 6:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039563).toString());
                break;
            case 7:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039564).toString());
                break;
            case 8:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039565).toString());
                break;
            case 9:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039566).toString());
                break;
            case 10:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039567).toString());
                break;
            case 11:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039568).toString());
                break;
            case 12:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(17039569).toString());
                break;
            default:
                if (!this.mIsEriFileLoaded) {
                    Rlog.d(LOG_TAG, "ERI File not loaded");
                    if (defRoamInd <= 2) {
                        switch (defRoamInd) {
                            case 0:
                                ret = new EriDisplayInformation(0, 0, this.mContext.getText(17039557).toString());
                                break;
                            case 1:
                                ret = new EriDisplayInformation(1, 0, this.mContext.getText(17039558).toString());
                                break;
                            case 2:
                                ret = new EriDisplayInformation(2, 1, this.mContext.getText(17039559).toString());
                                break;
                            default:
                                ret = new EriDisplayInformation(-1, -1, "ERI text");
                                break;
                        }
                    }
                    ret = new EriDisplayInformation(2, 1, this.mContext.getText(17039559).toString());
                    break;
                }
                eriInfo = getEriInfo(roamInd);
                EriInfo defEriInfo = getEriInfo(defRoamInd);
                if (eriInfo == null) {
                    if (defEriInfo != null) {
                        ret = new EriDisplayInformation(defEriInfo.iconIndex, defEriInfo.iconMode, defEriInfo.eriText);
                        break;
                    }
                    Rlog.e(LOG_TAG, "ERI defRoamInd " + defRoamInd + " not found in ERI file ...on");
                    ret = new EriDisplayInformation(0, 0, this.mContext.getText(17039557).toString());
                    break;
                }
                ret = new EriDisplayInformation(eriInfo.iconIndex, eriInfo.iconMode, eriInfo.eriText);
                break;
        }
        return ret;
    }

    public int getCdmaEriIconIndex(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconIndex;
    }

    public int getCdmaEriIconMode(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconMode;
    }

    public String getCdmaEriText(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconText;
    }

    public boolean IsInternationalRoaming(int roamInd, int defRoamInd) {
        if (roamInd == -1) {
            return false;
        }
        String eriText = getEriDisplayInformation(roamInd, defRoamInd).mEriIconText;
        if (eriText.equals("International Roaming") || eriText.equals("International") || eriText.equals("Premium International") || eriText.equals("International Services") || eriText.equals("International Voice") || eriText.equals("Int'l Voice/Data")) {
            return true;
        }
        return false;
    }

    public boolean IsDomesticRoaming(int roamInd, int defRoamInd) {
        if (roamInd == -1 || roamInd == 1 || IsInternationalRoaming(roamInd, defRoamInd)) {
            return false;
        }
        return true;
    }
}
