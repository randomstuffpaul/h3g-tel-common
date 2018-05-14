package com.android.internal.telephony;

import android.app.AppGlobals;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.util.AtomicFile;
import android.util.Xml;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;
import com.sec.android.app.CscFeature;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class SmsUsageMonitor {
    private static final String ATTR_COUNTRY = "country";
    private static final String ATTR_FREE = "free";
    private static final String ATTR_PACKAGE_NAME = "name";
    private static final String ATTR_PACKAGE_SMS_POLICY = "sms-policy";
    private static final String ATTR_PATTERN = "pattern";
    private static final String ATTR_PREMIUM = "premium";
    private static final String ATTR_STANDARD = "standard";
    static final int CATEGORY_FREE_SHORT_CODE = 1;
    static final int CATEGORY_NOT_SHORT_CODE = 0;
    static final int CATEGORY_POSSIBLE_PREMIUM_SHORT_CODE = 3;
    static final int CATEGORY_PREMIUM_SHORT_CODE = 4;
    static final int CATEGORY_STANDARD_SHORT_CODE = 2;
    private static final boolean DBG = false;
    private static final int DEFAULT_SMS_CHECK_PERIOD = 600000;
    private static final int DEFAULT_SMS_MAX_COUNT = 1000;
    private static final String EVR_MCCMNC1 = "23430";
    private static final String EVR_MCCMNC2 = "23433";
    private static final String KT_ReadConfirmAddr = "#431";
    public static final int PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW = 3;
    public static final int PREMIUM_SMS_PERMISSION_ASK_USER = 1;
    public static final int PREMIUM_SMS_PERMISSION_NEVER_ALLOW = 2;
    public static final int PREMIUM_SMS_PERMISSION_UNKNOWN = 0;
    private static final String SHORT_CODE_PATH = "/data/misc/sms/codes";
    private static final String SMS_POLICY_FILE_DIRECTORY = "/data/misc/sms";
    private static final String SMS_POLICY_FILE_NAME = "premium_sms_policy.xml";
    private static final String TAG = "SmsUsageMonitor";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_SHORTCODE = "shortcode";
    private static final String TAG_SHORTCODES = "shortcodes";
    private static final String TAG_SMS_POLICY_BODY = "premium-sms-policy";
    private static final boolean VDBG = false;
    private final AtomicBoolean mCheckEnabled = new AtomicBoolean(true);
    private final int mCheckPeriod;
    private final Context mContext;
    private String mCurrentCountry;
    private ShortCodePatternMatcher mCurrentPatternMatcher;
    private final int mMaxAllowed;
    private final File mPatternFile = new File(SHORT_CODE_PATH);
    private long mPatternFileLastModified = 0;
    private AtomicFile mPolicyFile;
    private final HashMap<String, Integer> mPremiumSmsPolicy = new HashMap();
    private final SettingsObserverHandler mSettingsObserverHandler;
    private String mSimOperator;
    private final HashMap<String, ArrayList<Long>> mSmsStamp = new HashMap();

    class C00311 implements Runnable {
        C00311() {
        }

        public void run() {
            SmsUsageMonitor.this.writePremiumSmsPolicyDb();
        }
    }

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicBoolean mEnabled;

        SettingsObserver(Handler handler, Context context, AtomicBoolean enabled) {
            super(handler);
            this.mContext = context;
            this.mEnabled = enabled;
            onChange(false);
        }

        public void onChange(boolean selfChange) {
            boolean z = true;
            AtomicBoolean atomicBoolean = this.mEnabled;
            if (Global.getInt(this.mContext.getContentResolver(), "sms_short_code_confirmation", 1) == 0) {
                z = false;
            }
            atomicBoolean.set(z);
        }
    }

    private static class SettingsObserverHandler extends Handler {
        SettingsObserverHandler(Context context, AtomicBoolean enabled) {
            context.getContentResolver().registerContentObserver(Global.getUriFor("sms_short_code_confirmation"), false, new SettingsObserver(this, context, enabled));
        }
    }

    private static final class ShortCodePatternMatcher {
        private final Pattern mFreeShortCodePattern;
        private final Pattern mPremiumShortCodePattern;
        private final Pattern mShortCodePattern;
        private final Pattern mStandardShortCodePattern;

        ShortCodePatternMatcher(String shortCodeRegex, String premiumShortCodeRegex, String freeShortCodeRegex, String standardShortCodeRegex) {
            Pattern compile;
            Pattern pattern = null;
            this.mShortCodePattern = shortCodeRegex != null ? Pattern.compile(shortCodeRegex) : null;
            if (premiumShortCodeRegex != null) {
                compile = Pattern.compile(premiumShortCodeRegex);
            } else {
                compile = null;
            }
            this.mPremiumShortCodePattern = compile;
            if (freeShortCodeRegex != null) {
                compile = Pattern.compile(freeShortCodeRegex);
            } else {
                compile = null;
            }
            this.mFreeShortCodePattern = compile;
            if (standardShortCodeRegex != null) {
                pattern = Pattern.compile(standardShortCodeRegex);
            }
            this.mStandardShortCodePattern = pattern;
        }

        int getNumberCategory(String phoneNumber) {
            if (this.mFreeShortCodePattern != null && this.mFreeShortCodePattern.matcher(phoneNumber).matches()) {
                return 1;
            }
            if (this.mStandardShortCodePattern != null && this.mStandardShortCodePattern.matcher(phoneNumber).matches()) {
                return 2;
            }
            if (this.mPremiumShortCodePattern != null && this.mPremiumShortCodePattern.matcher(phoneNumber).matches()) {
                return 4;
            }
            if (this.mShortCodePattern == null || !this.mShortCodePattern.matcher(phoneNumber).matches()) {
                return 0;
            }
            return 3;
        }
    }

    public static int mergeShortCodeCategories(int type1, int type2) {
        return type1 > type2 ? type1 : type2;
    }

    public SmsUsageMonitor(Context context) {
        this.mContext = context;
        ContentResolver resolver = context.getContentResolver();
        this.mMaxAllowed = Global.getInt(resolver, "sms_outgoing_check_max_count", 1000);
        this.mCheckPeriod = Global.getInt(resolver, "sms_outgoing_check_interval_ms", DEFAULT_SMS_CHECK_PERIOD);
        this.mSettingsObserverHandler = new SettingsObserverHandler(this.mContext, this.mCheckEnabled);
        loadPremiumSmsPolicyDb();
    }

    private ShortCodePatternMatcher getPatternMatcherFromFile(String country) {
        Throwable th;
        FileNotFoundException e;
        FileReader patternReader = null;
        try {
            FileReader patternReader2 = new FileReader(this.mPatternFile);
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(patternReader2);
                ShortCodePatternMatcher patternMatcherFromXmlParser = getPatternMatcherFromXmlParser(parser, country);
                this.mPatternFileLastModified = this.mPatternFile.lastModified();
                if (patternReader2 != null) {
                    try {
                        patternReader2.close();
                    } catch (IOException e2) {
                    }
                }
                patternReader = patternReader2;
                return patternMatcherFromXmlParser;
            } catch (FileNotFoundException e3) {
                patternReader = patternReader2;
                try {
                    Rlog.e(TAG, "Short Code Pattern File not found");
                    this.mPatternFileLastModified = this.mPatternFile.lastModified();
                    if (patternReader != null) {
                        try {
                            patternReader.close();
                        } catch (IOException e4) {
                        }
                    }
                    return null;
                } catch (Throwable th2) {
                    th = th2;
                    this.mPatternFileLastModified = this.mPatternFile.lastModified();
                    if (patternReader != null) {
                        try {
                            patternReader.close();
                        } catch (IOException e5) {
                        }
                    }
                    throw th;
                }
            } catch (XmlPullParserException e6) {
                e = e6;
                patternReader = patternReader2;
                Rlog.e(TAG, "XML parser exception reading short code pattern file", e);
                this.mPatternFileLastModified = this.mPatternFile.lastModified();
                if (patternReader != null) {
                    try {
                        patternReader.close();
                    } catch (IOException e7) {
                    }
                }
                return null;
            } catch (Throwable th3) {
                th = th3;
                patternReader = patternReader2;
                this.mPatternFileLastModified = this.mPatternFile.lastModified();
                if (patternReader != null) {
                    patternReader.close();
                }
                throw th;
            }
        } catch (FileNotFoundException e8) {
            Rlog.e(TAG, "Short Code Pattern File not found");
            this.mPatternFileLastModified = this.mPatternFile.lastModified();
            if (patternReader != null) {
                patternReader.close();
            }
            return null;
        } catch (XmlPullParserException e9) {
            e = e9;
            Rlog.e(TAG, "XML parser exception reading short code pattern file", e);
            this.mPatternFileLastModified = this.mPatternFile.lastModified();
            if (patternReader != null) {
                patternReader.close();
            }
            return null;
        }
    }

    private ShortCodePatternMatcher getPatternMatcherFromResource(String country) {
        int id = 17891349;
        if (EVR_MCCMNC1.equals(this.mSimOperator) || EVR_MCCMNC2.equals(this.mSimOperator)) {
            id = 17891350;
            Rlog.d(TAG, "load pattern from sms_short_codes_evr");
        }
        XmlResourceParser parser = null;
        try {
            parser = this.mContext.getResources().getXml(id);
            ShortCodePatternMatcher patternMatcherFromXmlParser = getPatternMatcherFromXmlParser(parser, country);
            return patternMatcherFromXmlParser;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private ShortCodePatternMatcher getPatternMatcherFromXmlParser(XmlPullParser parser, String country) {
        try {
            XmlUtils.beginDocument(parser, TAG_SHORTCODES);
            while (true) {
                XmlUtils.nextElement(parser);
                String element = parser.getName();
                if (element == null) {
                    break;
                } else if (!element.equals(TAG_SHORTCODE)) {
                    Rlog.e(TAG, "Error: skipping unknown XML tag " + element);
                } else if (country.equals(parser.getAttributeValue(null, ATTR_COUNTRY))) {
                    return new ShortCodePatternMatcher(parser.getAttributeValue(null, ATTR_PATTERN), parser.getAttributeValue(null, ATTR_PREMIUM), parser.getAttributeValue(null, ATTR_FREE), parser.getAttributeValue(null, ATTR_STANDARD));
                }
            }
            Rlog.e(TAG, "Parsing pattern data found null");
        } catch (XmlPullParserException e) {
            Rlog.e(TAG, "XML parser exception reading short code patterns", e);
        } catch (IOException e2) {
            Rlog.e(TAG, "I/O exception reading short code patterns", e2);
        }
        return null;
    }

    void dispose() {
        this.mSmsStamp.clear();
    }

    public boolean check(String appName, int smsWaiting) {
        boolean isUnderLimit;
        synchronized (this.mSmsStamp) {
            removeExpiredTimestamps();
            ArrayList<Long> sentList = (ArrayList) this.mSmsStamp.get(appName);
            if (sentList == null) {
                sentList = new ArrayList();
                this.mSmsStamp.put(appName, sentList);
            }
            isUnderLimit = isUnderLimit(sentList, smsWaiting);
        }
        return isUnderLimit;
    }

    public int checkDestination(String destAddress, String countryIso) {
        int i = 0;
        synchronized (this.mSettingsObserverHandler) {
            if (PhoneNumberUtils.isEmergencyNumber(destAddress, countryIso)) {
            } else if (this.mCheckEnabled.get()) {
                if (countryIso != null) {
                    if (!(this.mCurrentCountry != null && countryIso.equals(this.mCurrentCountry) && this.mPatternFile.lastModified() == this.mPatternFileLastModified)) {
                        if ("ZTO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms")) || "ARO".equals(CscFeature.getInstance().getString("CscFeature_RIL_ConfigSsms"))) {
                            Rlog.d(TAG, "Loading SMS Short Code patterns from resource only");
                            this.mCurrentPatternMatcher = getPatternMatcherFromResource(countryIso);
                        } else if (this.mPatternFile.exists()) {
                            this.mCurrentPatternMatcher = getPatternMatcherFromFile(countryIso);
                        } else {
                            this.mCurrentPatternMatcher = getPatternMatcherFromResource(countryIso);
                        }
                        this.mCurrentCountry = countryIso;
                    }
                }
                if (this.mCurrentPatternMatcher == null || destAddress == null) {
                    Rlog.e(TAG, "No patterns for \"" + countryIso + "\": using generic short code rule");
                    if (destAddress == null || destAddress.length() > 5) {
                    } else {
                        i = 3;
                    }
                } else {
                    i = this.mCurrentPatternMatcher.getNumberCategory(destAddress);
                }
            }
        }
        return i;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void loadPremiumSmsPolicyDb() {
        /*
        r11 = this;
        r8 = r11.mPremiumSmsPolicy;
        monitor-enter(r8);
        r7 = r11.mPolicyFile;	 Catch:{ all -> 0x00f3 }
        if (r7 != 0) goto L_0x0043;
    L_0x0007:
        r0 = new java.io.File;	 Catch:{ all -> 0x00f3 }
        r7 = "/data/misc/sms";
        r0.<init>(r7);	 Catch:{ all -> 0x00f3 }
        r7 = new android.util.AtomicFile;	 Catch:{ all -> 0x00f3 }
        r9 = new java.io.File;	 Catch:{ all -> 0x00f3 }
        r10 = "premium_sms_policy.xml";
        r9.<init>(r0, r10);	 Catch:{ all -> 0x00f3 }
        r7.<init>(r9);	 Catch:{ all -> 0x00f3 }
        r11.mPolicyFile = r7;	 Catch:{ all -> 0x00f3 }
        r7 = r11.mPremiumSmsPolicy;	 Catch:{ all -> 0x00f3 }
        r7.clear();	 Catch:{ all -> 0x00f3 }
        r3 = 0;
        r7 = r11.mPolicyFile;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r3 = r7.openRead();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r5 = android.util.Xml.newPullParser();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r7 = 0;
        r5.setInput(r3, r7);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r7 = "premium-sms-policy";
        com.android.internal.util.XmlUtils.beginDocument(r5, r7);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
    L_0x0035:
        com.android.internal.util.XmlUtils.nextElement(r5);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r2 = r5.getName();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r2 != 0) goto L_0x0045;
    L_0x003e:
        if (r3 == 0) goto L_0x0043;
    L_0x0040:
        r3.close();	 Catch:{ IOException -> 0x00f6 }
    L_0x0043:
        monitor-exit(r8);	 Catch:{ all -> 0x00f3 }
        return;
    L_0x0045:
        r7 = "package";
        r7 = r2.equals(r7);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r7 == 0) goto L_0x00c0;
    L_0x004d:
        r7 = 0;
        r9 = "name";
        r4 = r5.getAttributeValue(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r7 = 0;
        r9 = "sms-policy";
        r6 = r5.getAttributeValue(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        if (r4 != 0) goto L_0x006e;
    L_0x005d:
        r7 = "SmsUsageMonitor";
        r9 = "Error: missing package name attribute";
        android.telephony.Rlog.e(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0065:
        r7 = move-exception;
        if (r3 == 0) goto L_0x0043;
    L_0x0068:
        r3.close();	 Catch:{ IOException -> 0x006c }
        goto L_0x0043;
    L_0x006c:
        r7 = move-exception;
        goto L_0x0043;
    L_0x006e:
        if (r6 != 0) goto L_0x0088;
    L_0x0070:
        r7 = "SmsUsageMonitor";
        r9 = "Error: missing package policy attribute";
        android.telephony.Rlog.e(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0078:
        r1 = move-exception;
        r7 = "SmsUsageMonitor";
        r9 = "Unable to read premium SMS policy database";
        android.telephony.Rlog.e(r7, r9, r1);	 Catch:{ all -> 0x00ec }
        if (r3 == 0) goto L_0x0043;
    L_0x0082:
        r3.close();	 Catch:{ IOException -> 0x0086 }
        goto L_0x0043;
    L_0x0086:
        r7 = move-exception;
        goto L_0x0043;
    L_0x0088:
        r7 = r11.mPremiumSmsPolicy;	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r9 = java.lang.Integer.parseInt(r6);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r9 = java.lang.Integer.valueOf(r9);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        r7.put(r4, r9);	 Catch:{ NumberFormatException -> 0x0096, FileNotFoundException -> 0x0065, IOException -> 0x0078, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x0096:
        r1 = move-exception;
        r7 = "SmsUsageMonitor";
        r9 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9.<init>();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r10 = "Error: non-numeric policy type ";
        r9 = r9.append(r10);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9 = r9.append(r6);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9 = r9.toString();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        android.telephony.Rlog.e(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x00b0:
        r1 = move-exception;
        r7 = "SmsUsageMonitor";
        r9 = "Unable to parse premium SMS policy database";
        android.telephony.Rlog.e(r7, r9, r1);	 Catch:{ all -> 0x00ec }
        if (r3 == 0) goto L_0x0043;
    L_0x00ba:
        r3.close();	 Catch:{ IOException -> 0x00be }
        goto L_0x0043;
    L_0x00be:
        r7 = move-exception;
        goto L_0x0043;
    L_0x00c0:
        r7 = "SmsUsageMonitor";
        r9 = new java.lang.StringBuilder;	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9.<init>();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r10 = "Error: skipping unknown XML tag ";
        r9 = r9.append(r10);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9 = r9.append(r2);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        r9 = r9.toString();	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        android.telephony.Rlog.e(r7, r9);	 Catch:{ FileNotFoundException -> 0x0065, IOException -> 0x0078, NumberFormatException -> 0x00b0, XmlPullParserException -> 0x00da }
        goto L_0x0035;
    L_0x00da:
        r1 = move-exception;
        r7 = "SmsUsageMonitor";
        r9 = "Unable to parse premium SMS policy database";
        android.telephony.Rlog.e(r7, r9, r1);	 Catch:{ all -> 0x00ec }
        if (r3 == 0) goto L_0x0043;
    L_0x00e4:
        r3.close();	 Catch:{ IOException -> 0x00e9 }
        goto L_0x0043;
    L_0x00e9:
        r7 = move-exception;
        goto L_0x0043;
    L_0x00ec:
        r7 = move-exception;
        if (r3 == 0) goto L_0x00f2;
    L_0x00ef:
        r3.close();	 Catch:{ IOException -> 0x00f9 }
    L_0x00f2:
        throw r7;	 Catch:{ all -> 0x00f3 }
    L_0x00f3:
        r7 = move-exception;
        monitor-exit(r8);	 Catch:{ all -> 0x00f3 }
        throw r7;
    L_0x00f6:
        r7 = move-exception;
        goto L_0x0043;
    L_0x00f9:
        r9 = move-exception;
        goto L_0x00f2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.SmsUsageMonitor.loadPremiumSmsPolicyDb():void");
    }

    private void writePremiumSmsPolicyDb() {
        synchronized (this.mPremiumSmsPolicy) {
            FileOutputStream outfile = null;
            try {
                outfile = this.mPolicyFile.startWrite();
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(outfile, "utf-8");
                out.startDocument(null, Boolean.valueOf(true));
                out.startTag(null, TAG_SMS_POLICY_BODY);
                for (Entry<String, Integer> policy : this.mPremiumSmsPolicy.entrySet()) {
                    out.startTag(null, "package");
                    out.attribute(null, "name", (String) policy.getKey());
                    out.attribute(null, ATTR_PACKAGE_SMS_POLICY, ((Integer) policy.getValue()).toString());
                    out.endTag(null, "package");
                }
                out.endTag(null, TAG_SMS_POLICY_BODY);
                out.endDocument();
                this.mPolicyFile.finishWrite(outfile);
            } catch (IOException e) {
                Rlog.e(TAG, "Unable to write premium SMS policy database", e);
                if (outfile != null) {
                    this.mPolicyFile.failWrite(outfile);
                }
            }
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        int i;
        checkCallerIsSystemOrSameApp(packageName);
        synchronized (this.mPremiumSmsPolicy) {
            Integer policy = (Integer) this.mPremiumSmsPolicy.get(packageName);
            if (policy == null) {
                i = 0;
            } else {
                i = policy.intValue();
            }
        }
        return i;
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        checkCallerIsSystemOrPhoneApp();
        if (permission < 1 || permission > 3) {
            throw new IllegalArgumentException("invalid SMS permission type " + permission);
        }
        synchronized (this.mPremiumSmsPolicy) {
            this.mPremiumSmsPolicy.put(packageName, Integer.valueOf(permission));
        }
        new Thread(new C00311()).start();
    }

    private static void checkCallerIsSystemOrSameApp(String pkg) {
        int uid = Binder.getCallingUid();
        if (UserHandle.getAppId(uid) != 1000 && uid != 0) {
            try {
                ApplicationInfo ai = AppGlobals.getPackageManager().getApplicationInfo(pkg, 0, UserHandle.getCallingUserId());
                if (!UserHandle.isSameApp(ai.uid, uid)) {
                    throw new SecurityException("Calling uid " + uid + " gave package" + pkg + " which is owned by uid " + ai.uid);
                }
            } catch (RemoteException re) {
                throw new SecurityException("Unknown package " + pkg + "\n" + re);
            }
        }
    }

    private static void checkCallerIsSystemOrPhoneApp() {
        int uid = Binder.getCallingUid();
        int appId = UserHandle.getAppId(uid);
        if (appId != 1000 && appId != CallFailCause.CDMA_DROP && uid != 0) {
            throw new SecurityException("Disallowed call for uid " + uid);
        }
    }

    private void removeExpiredTimestamps() {
        long beginCheckPeriod = System.currentTimeMillis() - ((long) this.mCheckPeriod);
        synchronized (this.mSmsStamp) {
            Iterator<Entry<String, ArrayList<Long>>> iter = this.mSmsStamp.entrySet().iterator();
            while (iter.hasNext()) {
                ArrayList<Long> oldList = (ArrayList) ((Entry) iter.next()).getValue();
                if (oldList.isEmpty() || ((Long) oldList.get(oldList.size() - 1)).longValue() < beginCheckPeriod) {
                    iter.remove();
                }
            }
        }
    }

    private boolean isUnderLimit(ArrayList<Long> sent, int smsWaiting) {
        Long ct = Long.valueOf(System.currentTimeMillis());
        long beginCheckPeriod = ct.longValue() - ((long) this.mCheckPeriod);
        while (!sent.isEmpty() && ((Long) sent.get(0)).longValue() < beginCheckPeriod) {
            sent.remove(0);
        }
        if (sent.size() + smsWaiting > this.mMaxAllowed) {
            return false;
        }
        for (int i = 0; i < smsWaiting; i++) {
            sent.add(ct);
        }
        return true;
    }

    private static void log(String msg) {
        Rlog.d(TAG, msg);
    }

    public void setSimOperator(String simOperator) {
        this.mSimOperator = simOperator;
        Rlog.d(TAG, "setSimOperator = " + this.mSimOperator);
    }
}
