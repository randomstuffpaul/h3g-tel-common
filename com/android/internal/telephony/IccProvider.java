package com.android.internal.telephony;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.internal.telephony.IIccPhoneBook.Stub;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.UsimPhonebookCapaInfo;
import com.samsung.android.telephony.MultiSimManager;
import java.util.ArrayList;
import java.util.List;

public class IccProvider extends ContentProvider {
    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[]{"name", STR_NUMBER, STR_ANR_NUMBER, STR_ANRA_NUMBER, STR_ANRB_NUMBER, STR_ANRC_NUMBER, STR_EMAILS, STR_INDEX, "_id"};
    protected static final int ADN = 1;
    protected static final int ADN_ALL = 7;
    private static final String[] ADN_CAPA_COLUMN_NAMES = new String[]{"name_MaxCount", "name_UsedCount", "name_MaxLength", "number_MaxCount", "number_UsedCount", "number_MaxLength", "email_MaxCount", "email_UsedCount", "email_MaxLength"};
    protected static final int ADN_EMAILS = 16;
    protected static final int ADN_EMAILS_SUB = 17;
    protected static final int ADN_EXPANSION = 20;
    protected static final int ADN_EXPANSION_SUB = 21;
    protected static final int ADN_FROM_CONTACTS = 10;
    protected static final int ADN_FROM_CONTACTS_SUB = 11;
    protected static final int ADN_INIT = 22;
    protected static final int ADN_INIT_SUB = 23;
    private static final String[] ADN_LIKE_CAPA_COLUMN_NAMES = new String[]{"maxCount", "usedCount", "firstIndex", "name_MaxLength", "number_MaxLength"};
    protected static final int ADN_SUB = 2;
    private static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final String[] EMAIL_COLUMN_NAMES = new String[]{STR_EMAILS, STR_INDEX, "_id"};
    protected static final int FDN = 3;
    protected static final int FDN_FROM_CONTACTS = 12;
    protected static final int FDN_FROM_CONTACTS_SUB = 13;
    protected static final int FDN_SUB = 4;
    protected static final int ICC_CAPA_INFO = 18;
    protected static final int ICC_CAPA_INFO_SUB = 19;
    protected static final int MSISDN = 8;
    protected static final int MSISDN_FROM_CONTACTS = 14;
    protected static final int MSISDN_FROM_CONTACTS_SUB = 15;
    protected static final int MSISDN_SUB = 9;
    protected static final int SDN = 5;
    protected static final int SDN_SUB = 6;
    protected static final String STR_ANRA_NUMBER = "anrA_number";
    protected static final String STR_ANRB_NUMBER = "anrB_number";
    protected static final String STR_ANRC_NUMBER = "anrC_number";
    protected static final String STR_ANR_NUMBER = "anr_number";
    protected static final String STR_EMAILS = "emails";
    protected static final String STR_INDEX = "adn_index";
    protected static final String STR_NUMBER = "number";
    protected static final String STR_PIN2 = "pin2";
    protected static final String STR_TAG = "tag";
    private static final String TAG = "IccProvider";
    private static final UriMatcher URL_MATCHER = new UriMatcher(-1);

    static {
        URL_MATCHER.addURI("icc", "adn", 1);
        URL_MATCHER.addURI("icc", "adn/subId/#", 2);
        URL_MATCHER.addURI("icc", "fdn", 3);
        URL_MATCHER.addURI("icc", "fdn/subId/#", 4);
        URL_MATCHER.addURI("icc", "sdn", 5);
        URL_MATCHER.addURI("icc", "sdn/subId/#", 6);
        URL_MATCHER.addURI("icc", "msisdn", 8);
        URL_MATCHER.addURI("icc", "msisdn/subId/#", 9);
        URL_MATCHER.addURI("icc", "adn/from_contacts", 10);
        URL_MATCHER.addURI("icc", "adn/from_contacts/subId/#", 11);
        URL_MATCHER.addURI("icc", "fdn/from_contacts", 12);
        URL_MATCHER.addURI("icc", "fdn/from_contacts/subId/#", 13);
        URL_MATCHER.addURI("icc", "msisdn/from_contacts", 14);
        URL_MATCHER.addURI("icc", "msisdn/from_contacts/subId/#", 15);
        URL_MATCHER.addURI("icc", "adn/emails", 16);
        URL_MATCHER.addURI("icc", "adn/emails/subId/#", 17);
        URL_MATCHER.addURI("icc", "capacity", 18);
        URL_MATCHER.addURI("icc", "capacity/subId/#", 19);
        URL_MATCHER.addURI("icc", "adn/expansion", 20);
        URL_MATCHER.addURI("icc", "adn/expansion/subId/#", 21);
        URL_MATCHER.addURI("icc", "adn/init", 22);
        URL_MATCHER.addURI("icc", "adn/init/subId/#", 23);
    }

    public boolean onCreate() {
        return true;
    }

    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        String key;
        String value;
        String ADNTYPE;
        if (DBG) {
            log("query");
        }
        switch (URL_MATCHER.match(url)) {
            case 1:
                return loadFromEf(28474, false, SubscriptionManager.getDefaultSubId());
            case 2:
                return loadFromEf(28474, false, getRequestSubId(url));
            case 3:
                return loadFromEf(IccConstants.EF_FDN, false, SubscriptionManager.getDefaultSubId());
            case 4:
                return loadFromEf(IccConstants.EF_FDN, false, getRequestSubId(url));
            case 5:
                return loadFromEf(IccConstants.EF_SDN, false, SubscriptionManager.getDefaultSubId());
            case 6:
                return loadFromEf(IccConstants.EF_SDN, false, getRequestSubId(url));
            case 7:
                return loadAllSimContacts(28474);
            case 8:
                return loadFromEf(IccConstants.EF_MSISDN, false, SubscriptionManager.getDefaultSubId());
            case 9:
                return loadFromEf(IccConstants.EF_MSISDN, false, getRequestSubId(url));
            case 16:
                return loadFromEf(28474, true, SubscriptionManager.getDefaultSubId());
            case 17:
                return loadFromEf(28474, true, getRequestSubId(url));
            case 18:
                if (TextUtils.isEmpty(selection)) {
                    throw new IllegalArgumentException("Unknown URL " + url);
                }
                String[] pair = selection.split("=");
                key = pair[0].trim();
                value = pair[1].trim();
                if ("EF_TYPE".equals(key)) {
                    switch (Integer.parseInt(value)) {
                        case 28474:
                            String TWOG = "1";
                            String THREEG = "2";
                            ADNTYPE = SystemProperties.get("ril.ICC_TYPE", "1");
                            if ("1".equals(ADNTYPE)) {
                                return getAdnLikesInfo(28474);
                            }
                            if ("2".equals(ADNTYPE)) {
                                return getUSIMPBCapa();
                            }
                            break;
                        case IccConstants.EF_FDN /*28475*/:
                            return getAdnLikesInfo(IccConstants.EF_FDN);
                        case IccConstants.EF_MSISDN /*28480*/:
                            return getAdnLikesInfo(IccConstants.EF_MSISDN);
                        case IccConstants.EF_SDN /*28489*/:
                            return getAdnLikesInfo(IccConstants.EF_SDN);
                        default:
                            throw new IllegalArgumentException("Unknown URL " + url);
                    }
                }
                throw new IllegalArgumentException("Unknown URL " + url);
            case 19:
                break;
            case 22:
                return loadFromEfInit(28474, SubscriptionManager.getDefaultSubId());
            case 23:
                return loadFromEfInit(28474, getRequestSubId(url));
        }
        if (TextUtils.isEmpty(selection)) {
            throw new IllegalArgumentException("Unknown URL " + url);
        }
        pair = selection.split("=");
        key = pair[0].trim();
        value = pair[1].trim();
        long subId = getRequestSubId(url);
        if ("EF_TYPE".equals(key)) {
            switch (Integer.parseInt(value)) {
                case 28474:
                    TWOG = "1";
                    THREEG = "2";
                    ADNTYPE = SystemProperties.get(MultiSimManager.appendSimSlot("ril.ICC_TYPE", SubscriptionManager.getSlotId(subId)), "0");
                    if ("1".equals(ADNTYPE)) {
                        return getAdnLikesInfoForSubscriber(subId, 28474);
                    }
                    if ("2".equals(ADNTYPE)) {
                        return getUsimPBCapaInfoForSubscriber(subId);
                    }
                    break;
                case IccConstants.EF_FDN /*28475*/:
                    return getAdnLikesInfoForSubscriber(subId, IccConstants.EF_FDN);
                case IccConstants.EF_MSISDN /*28480*/:
                    return getAdnLikesInfoForSubscriber(subId, IccConstants.EF_MSISDN);
                case IccConstants.EF_SDN /*28489*/:
                    return getAdnLikesInfoForSubscriber(subId, IccConstants.EF_SDN);
                default:
                    throw new IllegalArgumentException("Unknown URL " + url);
            }
            throw new IllegalArgumentException("Unknown URL " + url);
        }
        throw new IllegalArgumentException("Unknown URL " + url);
    }

    private Cursor loadAllSimContacts(int efType) {
        Cursor[] result;
        List<SubInfoRecord> subInfoList = SubscriptionManager.getActiveSubInfoList();
        if (subInfoList == null || subInfoList.size() == 0) {
            result = new Cursor[0];
        } else {
            int subIdCount = subInfoList.size();
            result = new Cursor[subIdCount];
            for (int i = 0; i < subIdCount; i++) {
                long subId = ((SubInfoRecord) subInfoList.get(i)).subId;
                result[i] = loadFromEf(efType, false, subId);
                Rlog.i(TAG, "ADN Records loaded for Subscription ::" + subId);
            }
        }
        return new MergeCursor(result);
    }

    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return "vnd.android.cursor.dir/sim-contact";
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    public Uri insert(Uri url, ContentValues initialValues) {
        int efType;
        long subId;
        String pin2 = null;
        boolean isFromContacts = false;
        boolean AdnExpansion = false;
        if (DBG) {
            log("insert");
        }
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = initialValues.getAsString(STR_PIN2);
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = initialValues.getAsString(STR_PIN2);
                break;
            case 8:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 9:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                break;
            case 10:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                break;
            case 11:
                efType = 28474;
                subId = getRequestSubId(url);
                isFromContacts = true;
                break;
            case 12:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = initialValues.getAsString(STR_PIN2);
                isFromContacts = true;
                break;
            case 13:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = initialValues.getAsString(STR_PIN2);
                isFromContacts = true;
                break;
            case 14:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                break;
            case 15:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                isFromContacts = true;
                break;
            case 20:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                AdnExpansion = true;
                break;
            case 21:
                efType = 28474;
                subId = getRequestSubId(url);
                isFromContacts = true;
                AdnExpansion = true;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = initialValues.getAsString(STR_TAG);
        String number = initialValues.getAsString(STR_NUMBER);
        String AnrNumber = null;
        String AnrANumber = null;
        String AnrBNumber = null;
        String AnrCNumber = null;
        String email = null;
        String[] emails = new String[1];
        if (initialValues.containsKey("AnrNumber")) {
            AnrNumber = initialValues.getAsString("AnrNumber");
        }
        if (initialValues.containsKey("AnrANumber")) {
            AnrANumber = initialValues.getAsString("AnrANumber");
        }
        if (initialValues.containsKey("AnrBNumber")) {
            AnrBNumber = initialValues.getAsString("AnrBNumber");
        }
        if (initialValues.containsKey("AnrCNumber")) {
            AnrCNumber = initialValues.getAsString("AnrCNumber");
        }
        if (initialValues.containsKey(STR_EMAILS)) {
            email = initialValues.getAsString(STR_EMAILS);
            emails[0] = initialValues.getAsString(STR_EMAILS);
            if (emails[0] == null) {
                emails[0] = "";
            }
        }
        if (tag == null) {
            tag = "";
        }
        if (number == null) {
            number = "";
        }
        if (email == null) {
            email = "";
        }
        if (AnrNumber == null) {
            AnrNumber = "";
        }
        if (AnrANumber == null) {
            AnrANumber = "";
        }
        if (AnrBNumber == null) {
            AnrBNumber = "";
        }
        if (AnrCNumber == null) {
            AnrCNumber = "";
        }
        if (DBG) {
            log("insert name : " + tag);
        }
        StringBuilder stringBuilder = new StringBuilder("content://icc/");
        if (isFromContacts) {
            int index;
            if (AdnExpansion) {
                log("Insert AdnExpansion");
                index = addExpansionIccRecordToEfByIndex(efType, tag, number, AnrNumber, AnrANumber, AnrBNumber, AnrCNumber, email, pin2, subId);
                log("After InsertExpansion, index : " + index);
            } else {
                log("Insert Adn");
                index = addIccRecordToEfByIndex(efType, tag, number, email, pin2, subId);
                log("After InsertAdn, index : " + index);
            }
            switch (match) {
                case 10:
                    log("insert :ADN_FROM_CONTACTS");
                    stringBuilder.append("adn/");
                    break;
            }
            log(" insert [" + index + "]");
            stringBuilder.append(index);
        } else if (!addIccRecordToEf(efType, tag, number, null, pin2, subId)) {
            return null;
        } else {
            switch (match) {
                case 1:
                    stringBuilder.append("adn/");
                    break;
                case 2:
                    stringBuilder.append("adn/subId/");
                    break;
                case 3:
                    stringBuilder.append("fdn/");
                    break;
                case 4:
                    stringBuilder.append("fdn/subId/");
                    break;
                case 8:
                    stringBuilder.append("msisdn/");
                    break;
                case 9:
                    stringBuilder.append("msisdn/");
                    break;
            }
            stringBuilder.append(0);
        }
        Uri resultUri = Uri.parse(stringBuilder.toString());
        getContext().getContentResolver().notifyChange(url, null);
        return resultUri;
    }

    private String normalizeValue(String inVal) {
        int len = inVal.length();
        if (len != 0) {
            String retVal = inVal;
            if (inVal.charAt(0) == '\'' && inVal.charAt(len - 1) == '\'') {
                retVal = inVal.substring(1, len - 1);
            }
            return retVal;
        } else if (!DBG) {
            return inVal;
        } else {
            log("len of input String is 0");
            return inVal;
        }
    }

    public int delete(Uri url, String where, String[] whereArgs) {
        int efType;
        long subId;
        int index = -1;
        switch (URL_MATCHER.match(url)) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                break;
            case 8:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 9:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                break;
            case 10:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 11:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 12:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 13:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                break;
            case 14:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 15:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        if (DBG) {
            log("delete");
        }
        String tag = null;
        String number = null;
        String[] emails = null;
        String pin2 = null;
        String[] tokens = where.split("AND");
        int n = tokens.length;
        while (true) {
            n--;
            if (n >= 0) {
                String param = tokens[n];
                if (DBG) {
                    log("parsing '" + param + "'");
                }
                String[] pair = param.split("=");
                String key = pair[0].trim();
                String val = pair[1].trim();
                if (pair.length != 2) {
                    if (STR_TAG.equals(key)) {
                        String str = param;
                        val = str.substring(param.indexOf("=") + 1).trim();
                    } else {
                        Rlog.e(TAG, "resolve: bad whereClause parameter: " + param);
                    }
                }
                if (STR_TAG.equals(key)) {
                    tag = normalizeValue(val);
                    if ("null".equals(tag)) {
                        if (DBG) {
                            log("Change null");
                        }
                        tag = "";
                    }
                } else if (STR_NUMBER.equals(key)) {
                    number = normalizeValue(val);
                    if ("null".equals(number)) {
                        if (DBG) {
                            log("Change null");
                        }
                        number = "";
                    }
                } else if (STR_EMAILS.equals(key)) {
                    emails = null;
                } else if (STR_PIN2.equals(key)) {
                    pin2 = normalizeValue(val);
                } else if (STR_INDEX.equals(key)) {
                    index = (int) Long.parseLong(val);
                }
            } else if (efType == 3 && TextUtils.isEmpty(pin2)) {
                return 0;
            } else {
                if (index < 0) {
                    if (tag == null) {
                        tag = "";
                    }
                    if (number == null) {
                        number = "";
                    }
                    if (!deleteIccRecordFromEf(efType, tag, number, emails, pin2, subId)) {
                        return 0;
                    }
                    getContext().getContentResolver().notifyChange(url, null);
                    return 1;
                }
                getContext().getContentResolver().notifyChange(url, null);
                return deleteIccRecordFromEfByIndex(efType, index, pin2, subId);
            }
        }
    }

    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int efType;
        long subId;
        String pin2 = null;
        int index = -1;
        boolean isFromContacts = false;
        boolean AdnExpansion = false;
        if (DBG) {
            log("update");
        }
        switch (URL_MATCHER.match(url)) {
            case 1:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 2:
                efType = 28474;
                subId = getRequestSubId(url);
                break;
            case 3:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = values.getAsString(STR_PIN2);
                break;
            case 4:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = values.getAsString(STR_PIN2);
                break;
            case 8:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case 9:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                break;
            case 10:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                break;
            case 11:
                efType = 28474;
                subId = getRequestSubId(url);
                isFromContacts = true;
                break;
            case 12:
                pin2 = values.getAsString(STR_PIN2);
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                break;
            case 13:
                pin2 = values.getAsString(STR_PIN2);
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                isFromContacts = true;
                break;
            case 14:
                efType = IccConstants.EF_MSISDN;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                break;
            case 15:
                efType = IccConstants.EF_MSISDN;
                subId = getRequestSubId(url);
                isFromContacts = true;
                break;
            case 20:
                efType = 28474;
                subId = SubscriptionManager.getDefaultSubId();
                isFromContacts = true;
                AdnExpansion = true;
                break;
            case 21:
                efType = 28474;
                subId = getRequestSubId(url);
                isFromContacts = true;
                AdnExpansion = true;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = values.getAsString(STR_TAG);
        String number = values.getAsString(STR_NUMBER);
        String newTag = values.getAsString("newTag");
        String newNumber = values.getAsString("newNumber");
        String newAnrNumber = null;
        String newAnrANumber = null;
        String newAnrBNumber = null;
        String newAnrCNumber = null;
        String email = null;
        if (values.containsKey("newAnrNumber")) {
            newAnrNumber = values.getAsString("newAnrNumber");
        }
        if (values.containsKey("newAnrANumber")) {
            newAnrANumber = values.getAsString("newAnrANumber");
        }
        if (values.containsKey("newAnrBNumber")) {
            newAnrBNumber = values.getAsString("newAnrBNumber");
        }
        if (values.containsKey("newAnrCNumber")) {
            newAnrCNumber = values.getAsString("newAnrCNumber");
        }
        if (values.containsKey("newEmails")) {
            email = values.getAsString("newEmails");
        }
        if (email == null) {
            email = "";
        }
        if (tag == null) {
            tag = "";
        }
        if (number == null) {
            number = "";
        }
        if (newTag == null) {
            newTag = "";
        }
        if (newNumber == null) {
            newNumber = "";
        }
        if (newAnrNumber == null) {
            newAnrNumber = "";
        }
        if (newAnrANumber == null) {
            newAnrANumber = "";
        }
        if (newAnrBNumber == null) {
            newAnrBNumber = "";
        }
        if (newAnrCNumber == null) {
            newAnrCNumber = "";
        }
        if (values.containsKey(STR_INDEX)) {
            index = values.getAsInteger(STR_INDEX).intValue();
        }
        if (isFromContacts) {
            log("isFromContacts");
            if (AdnExpansion) {
                index = updateExpansionIccRecordInEfByIndex(efType, newTag, newNumber, newAnrNumber, newAnrANumber, newAnrBNumber, newAnrCNumber, email, index, pin2, subId);
            } else {
                index = updateIccRecordInEfByIndex(efType, newTag, newNumber, email, index, pin2, subId);
            }
            getContext().getContentResolver().notifyChange(url, null);
            return index;
        }
        log("Update Adn");
        if (!updateIccRecordInEf(efType, tag, number, newTag, newNumber, pin2, subId)) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(url, null);
        return 1;
    }

    private MatrixCursor loadFromEf(int efType, boolean isEmailOnly, long subId) {
        if (DBG) {
            log("loadFromEf: efType=" + efType + ", subscription=" + subId);
        }
        List<AdnRecord> adnRecords = null;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEfForSubscriber(subId, efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (adnRecords != null) {
            int N = adnRecords.size();
            if (DBG) {
                log("adnRecords.size=" + N);
            }
            MatrixCursor matrixCursor;
            int i;
            if (isEmailOnly) {
                matrixCursor = new MatrixCursor(EMAIL_COLUMN_NAMES, N);
                for (i = 0; i < N; i++) {
                    loadEmailRecord((AdnRecord) adnRecords.get(i), matrixCursor, i);
                }
                return matrixCursor;
            }
            matrixCursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            for (i = 0; i < N; i++) {
                loadRecord((AdnRecord) adnRecords.get(i), matrixCursor, i);
            }
            return matrixCursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    private MatrixCursor loadFromEfInit(int efType, long subId) {
        List<AdnRecord> adnRecords = null;
        if (DBG) {
            log("loadFromEfInit: efType=" + efType);
        }
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEfInitForSubscriber(subId, efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException e2) {
        }
        if (adnRecords == null) {
            return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
        }
        int N = adnRecords.size();
        MatrixCursor matrixCursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
        if (DBG) {
            log("adnRecords.size=" + N);
        }
        for (int i = 0; i < N; i++) {
            loadRecord((AdnRecord) adnRecords.get(i), matrixCursor, i);
        }
        return matrixCursor;
    }

    private MatrixCursor getUSIMPBCapa() {
        if (DBG) {
            log("getUSIMPBCapa");
        }
        UsimPhonebookCapaInfo usimPhonebookCapaInfo = new UsimPhonebookCapaInfo();
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                usimPhonebookCapaInfo = iccIpb.getUsimPBCapaInfo();
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (usimPhonebookCapaInfo == null) {
            return new MatrixCursor(ADN_CAPA_COLUMN_NAMES);
        }
        ArrayList<Integer> list = new ArrayList();
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 2)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 2)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 2)));
        MatrixCursor cursor = new MatrixCursor(ADN_CAPA_COLUMN_NAMES, 1);
        cursor.addRow(list);
        return cursor;
    }

    private MatrixCursor getUsimPBCapaInfoForSubscriber(long subId) {
        if (DBG) {
            log("getUSIMPBCapa");
        }
        UsimPhonebookCapaInfo usimPhonebookCapaInfo = new UsimPhonebookCapaInfo();
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                usimPhonebookCapaInfo = iccIpb.getUsimPBCapaInfoForSubscriber(subId);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (usimPhonebookCapaInfo == null) {
            return new MatrixCursor(ADN_CAPA_COLUMN_NAMES);
        }
        ArrayList<Integer> list = new ArrayList();
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(1, 2)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(2, 2)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 1)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 3)));
        list.add(Integer.valueOf(usimPhonebookCapaInfo.getFieldInfo(4, 2)));
        MatrixCursor cursor = new MatrixCursor(ADN_CAPA_COLUMN_NAMES, 1);
        cursor.addRow(list);
        return cursor;
    }

    private MatrixCursor getAdnLikesInfo(int efType) {
        int[] recordInfo = new int[5];
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                recordInfo = iccIpb.getAdnLikesInfo(efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException e2) {
        }
        if (recordInfo == null) {
            return new MatrixCursor(ADN_LIKE_CAPA_COLUMN_NAMES);
        }
        ArrayList<Integer> list = new ArrayList();
        list.add(Integer.valueOf(recordInfo[0]));
        list.add(Integer.valueOf(recordInfo[1]));
        list.add(Integer.valueOf(recordInfo[2]));
        list.add(Integer.valueOf(recordInfo[3]));
        list.add(Integer.valueOf(recordInfo[4]));
        MatrixCursor cursor = new MatrixCursor(ADN_LIKE_CAPA_COLUMN_NAMES, 1);
        cursor.addRow(list);
        return cursor;
    }

    private MatrixCursor getAdnLikesInfoForSubscriber(long subId, int efType) {
        int[] recordInfo = new int[5];
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                recordInfo = iccIpb.getAdnLikesInfoForSubscriber(subId, efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException e2) {
        }
        if (recordInfo == null) {
            return new MatrixCursor(ADN_LIKE_CAPA_COLUMN_NAMES);
        }
        ArrayList<Integer> list = new ArrayList();
        list.add(Integer.valueOf(recordInfo[0]));
        list.add(Integer.valueOf(recordInfo[1]));
        list.add(Integer.valueOf(recordInfo[2]));
        list.add(Integer.valueOf(recordInfo[3]));
        list.add(Integer.valueOf(recordInfo[4]));
        MatrixCursor cursor = new MatrixCursor(ADN_LIKE_CAPA_COLUMN_NAMES, 1);
        cursor.addRow(list);
        return cursor;
    }

    private boolean addIccRecordToEf(int efType, String name, String number, String[] emails, String pin2, long subId) {
        if (DBG) {
            log("addIccRecordToEf: efType=" + efType + ", name=" + name + ", number=" + number + ", emails=" + emails + ", subscription=" + subId);
        }
        boolean success = false;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType, "", "", name, number, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("addIccRecordToEf: " + success);
        }
        return success;
    }

    private int addIccRecordToEfByIndex(int efType, String name, String number, String email, String pin2, long subId) {
        if (DBG) {
            log("addIccRecordToEfByIndex: efType=" + efType + ", name=" + name + ", number=" + number + ", email=" + email + ", subscription=" + subId);
        }
        int index = 65535;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                index = iccIpb.updateAdnRecordsInEfByIndexUsingSubId(subId, efType, name, number, email, 65535, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("addIccRecordToEfByIndex: index =  " + index);
        }
        return index;
    }

    private int addExpansionIccRecordToEfByIndex(int efType, String name, String number, String anrNumber, String anrANumber, String anrBNumber, String anrCNumber, String email, String pin2, long subId) {
        if (DBG) {
            log("addExpansionIccRecordToEfByIndex: efType=" + efType + ", name=" + name + ", number=" + number + ", anrNumber=" + anrNumber + ", anrANumber=" + anrANumber + ", anrBNumber=" + anrBNumber + ", anrCNumber=" + anrCNumber + ", email=" + email + ", subscription=" + subId);
        }
        int index = 65535;
        AdnRecord newAdn = new AdnRecord("", "");
        newAdn.mAlphaTag = name;
        newAdn.mNumber = number;
        newAdn.mAnr = anrNumber;
        newAdn.mAnrA = anrANumber;
        newAdn.mAnrB = anrBNumber;
        newAdn.mAnrC = anrCNumber;
        newAdn.mEmails[0] = email;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                index = iccIpb.updateAdnRecordsInEfByIndexUsingARnSubId(subId, efType, newAdn, 65535, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("addExpansionIccRecordToEfByIndex: index =  " + index);
        }
        return index;
    }

    private boolean updateIccRecordInEf(int efType, String oldName, String oldNumber, String newName, String newNumber, String pin2, long subId) {
        if (DBG) {
            log("updateIccRecordInEf: efType=" + efType + ", oldname=" + oldName + ", oldnumber=" + oldNumber + ", newname=" + newName + ", newnumber=" + newNumber + ", subscription=" + subId);
        }
        boolean success = false;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType, oldName, oldNumber, newName, newNumber, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("updateIccRecordInEf: " + success);
        }
        return success;
    }

    private int updateIccRecordInEfByIndex(int efType, String newName, String newNumber, String newEmail, int index, String pin2, long subId) {
        if (DBG) {
            log("updateIccRecordInEfByIndex: efType=" + efType + ", index =" + index + ", newname=" + newName + ", newnumber=" + newNumber + ", subscription=" + subId);
        }
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                index = iccIpb.updateAdnRecordsInEfByIndexUsingSubId(subId, efType, newName, newNumber, newEmail, index, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("updateIccRecordInEfByIndex: index =  " + index);
        }
        return index;
    }

    private int updateExpansionIccRecordInEfByIndex(int efType, String newName, String newNumber, String newAnrNumber, String newAnrANumber, String newAnrBNumber, String newAnrCNumber, String newEmail, int index, String pin2, long subId) {
        if (DBG) {
            log("updateExpansionIccRecordInEfByIndex: efType=" + efType + ", index =" + index + ", newname=" + newName + ", newnumber=" + newNumber + ", newanrnumber=" + newAnrNumber + ", newanrAnumber=" + newAnrANumber + ", newanrBnumber=" + newAnrBNumber + ", newanrCnumber=" + newAnrCNumber + ", subscription=" + subId);
        }
        AdnRecord newAdn = new AdnRecord("", "");
        newAdn.mAlphaTag = newName;
        newAdn.mNumber = newNumber;
        newAdn.mAnr = newAnrNumber;
        newAdn.mAnrA = newAnrANumber;
        newAdn.mAnrB = newAnrBNumber;
        newAdn.mAnrC = newAnrCNumber;
        newAdn.mEmails[0] = newEmail;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                iccIpb.getAdnRecordsInEf(efType);
                index = iccIpb.updateAdnRecordsInEfByIndexUsingARnSubId(subId, efType, newAdn, index, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("updateExpansionIccRecordInEfByIndex: index =\t" + index);
        }
        return index;
    }

    private boolean deleteIccRecordFromEf(int efType, String name, String number, String[] emails, String pin2, long subId) {
        if (DBG) {
            log("deleteIccRecordFromEf: efType=" + efType + ", name=" + name + ", number=" + number + ", emails=" + emails + ", pin2=" + pin2 + ", subscription=" + subId);
        }
        boolean success = false;
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType, name, number, "", "", pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("deleteIccRecordFromEf: " + success);
        }
        return success;
    }

    private int deleteIccRecordFromEfByIndex(int efType, int index, String pin2, long subId) {
        if (DBG) {
            log("deleteIccRecordFromEfByIndex: efType=" + efType + ", index=" + index + ", pin2=" + pin2 + ", subscription=" + subId);
        }
        try {
            IIccPhoneBook iccIpb = Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                index = iccIpb.updateAdnRecordsInEfByIndexUsingSubId(subId, efType, "", "", "", index, pin2);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            if (DBG) {
                log(ex.toString());
            }
        }
        if (DBG) {
            log("deleteIccRecordFromEfByIndex: index " + index);
        }
        return index;
    }

    private void loadRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            Object[] contact = new Object[9];
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();
            String ANRNumber = record.getAnr();
            String ANRANumber = record.getAnrA();
            String ANRBNumber = record.getAnrB();
            String ANRCNumber = record.getAnrC();
            if (DBG) {
                log("loadRecord: " + alphaTag + ", " + number + ",");
            }
            if (TextUtils.isEmpty(alphaTag)) {
                contact[0] = null;
            } else {
                contact[0] = alphaTag;
            }
            if (TextUtils.isEmpty(number)) {
                contact[1] = null;
            } else {
                contact[1] = number;
            }
            if (TextUtils.isEmpty(ANRNumber)) {
                contact[2] = null;
            } else {
                contact[2] = ANRNumber;
            }
            if (TextUtils.isEmpty(ANRANumber)) {
                contact[3] = null;
            } else {
                contact[3] = ANRANumber;
            }
            if (TextUtils.isEmpty(ANRBNumber)) {
                contact[4] = null;
            } else {
                contact[4] = ANRBNumber;
            }
            if (TextUtils.isEmpty(ANRCNumber)) {
                contact[5] = null;
            } else {
                contact[5] = ANRCNumber;
            }
            String[] emails = record.getEmails();
            if (emails != null) {
                StringBuilder emailString = new StringBuilder();
                for (String email : emails) {
                    if (DBG) {
                        log("Adding email:" + email);
                    }
                    emailString.append(email);
                    emailString.append(",");
                }
                contact[6] = emailString.toString();
            }
            contact[7] = record.getRecordNumber() + "";
            contact[8] = Integer.valueOf(id);
            cursor.addRow(contact);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, "[IccProvider] " + msg);
    }

    private long getRequestSubId(Uri url) {
        if (DBG) {
            log("getRequestSubId url: " + url);
        }
        try {
            return Long.parseLong(url.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    private void loadEmailRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            Object[] contact = new Object[3];
            String[] emails = record.getEmails();
            String index = record.getRecordNumber() + "";
            if (emails != null) {
                for (String email : emails) {
                    if (!TextUtils.isEmpty(email)) {
                        contact[0] = email;
                        contact[1] = index;
                        contact[2] = Integer.valueOf(id);
                        cursor.addRow(contact);
                    }
                }
            }
        }
    }
}
