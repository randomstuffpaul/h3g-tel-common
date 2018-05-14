package com.android.internal.telephony;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.HbpcdLookup.MccIdd;
import com.android.internal.telephony.HbpcdLookup.MccLookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class SmsNumberUtils {
    private static int[] ALL_COUNTRY_CODES = null;
    private static final int CDMA_HOME_NETWORK = 1;
    private static final int CDMA_ROAMING_NETWORK = 2;
    private static final boolean DBG = false;
    private static final int GSM_UMTS_NETWORK = 0;
    private static HashMap<String, ArrayList<String>> IDDS_MAPS = new HashMap();
    private static int MAX_COUNTRY_CODES_LENGTH = 0;
    private static final int MIN_COUNTRY_AREA_LOCAL_LENGTH = 10;
    private static final int NANP_CC = 1;
    private static final String NANP_IDD = "011";
    private static final int NANP_LONG_LENGTH = 11;
    private static final int NANP_MEDIUM_LENGTH = 10;
    private static final String NANP_NDD = "1";
    private static final int NANP_SHORT_LENGTH = 7;
    private static final int NP_CC_AREA_LOCAL = 104;
    private static final int NP_HOMEIDD_CC_AREA_LOCAL = 101;
    private static final int NP_INTERNATIONAL_BEGIN = 100;
    private static final int NP_LOCALIDD_CC_AREA_LOCAL = 103;
    private static final int NP_NANP_AREA_LOCAL = 2;
    private static final int NP_NANP_BEGIN = 1;
    private static final int NP_NANP_LOCAL = 1;
    private static final int NP_NANP_LOCALIDD_CC_AREA_LOCAL = 5;
    private static final int NP_NANP_NBPCD_CC_AREA_LOCAL = 4;
    private static final int NP_NANP_NBPCD_HOMEIDD_CC_AREA_LOCAL = 6;
    private static final int NP_NANP_NDD_AREA_LOCAL = 3;
    private static final int NP_NBPCD_CC_AREA_LOCAL = 102;
    private static final int NP_NBPCD_HOMEIDD_CC_AREA_LOCAL = 100;
    private static final int NP_NONE = 0;
    private static final String PLUS_SIGN = "+";
    private static final String TAG = "SmsNumberUtils";
    private static final List<String> sVZWNetworkOperatorList = Arrays.asList(new String[]{"310004", "310005", "310012", "311480"});
    private static final List<String> sVZWSimcardOperatorList = Arrays.asList(new String[]{"20404", "310004", "311480"});

    private static class NumberEntry {
        public String IDD;
        public int countryCode;
        public String number;

        public NumberEntry(String number) {
            this.number = number;
        }
    }

    private static String formatNumber(Context context, String number, String activeMcc, int networkType) {
        int iddLength = 0;
        if (number == null) {
            throw new IllegalArgumentException("number is null");
        } else if (activeMcc == null || activeMcc.trim().length() == 0) {
            throw new IllegalArgumentException("activeMcc is null or empty!");
        } else {
            String networkPortionNumber = PhoneNumberUtils.extractNetworkPortion(number);
            if (networkPortionNumber == null || networkPortionNumber.length() == 0) {
                throw new IllegalArgumentException("Number is invalid!");
            }
            NumberEntry numberEntry = new NumberEntry(networkPortionNumber);
            ArrayList<String> allIDDs = getAllIDDs(context, activeMcc);
            int nanpState = checkNANP(numberEntry, allIDDs);
            if (nanpState == 1 || nanpState == 2 || nanpState == 3) {
                return networkPortionNumber;
            }
            if (nanpState != 4) {
                if (nanpState == 5) {
                    if (networkType == 1) {
                        return networkPortionNumber;
                    }
                    if (networkType == 0) {
                        if (numberEntry.IDD != null) {
                            iddLength = numberEntry.IDD.length();
                        }
                        return PLUS_SIGN + networkPortionNumber.substring(iddLength);
                    } else if (networkType == 2) {
                        if (numberEntry.IDD != null) {
                            iddLength = numberEntry.IDD.length();
                        }
                        return networkPortionNumber.substring(iddLength);
                    }
                }
                String returnNumber = null;
                switch (checkInternationalNumberPlan(context, numberEntry, allIDDs, NANP_IDD)) {
                    case 100:
                        if (networkType == 0) {
                            returnNumber = networkPortionNumber.substring(1);
                            break;
                        }
                        break;
                    case 101:
                        returnNumber = networkPortionNumber;
                        break;
                    case 102:
                        returnNumber = NANP_IDD + networkPortionNumber.substring(1);
                        break;
                    case 103:
                        if (networkType == 0 || networkType == 2) {
                            if (numberEntry.IDD != null) {
                                iddLength = numberEntry.IDD.length();
                            }
                            returnNumber = NANP_IDD + networkPortionNumber.substring(iddLength);
                            break;
                        }
                    case 104:
                        int countryCode = numberEntry.countryCode;
                        if (!(inExceptionListForNpCcAreaLocal(numberEntry) || networkPortionNumber.length() < 11 || countryCode == 1)) {
                            returnNumber = NANP_IDD + networkPortionNumber;
                            break;
                        }
                    default:
                        if (networkPortionNumber.startsWith(PLUS_SIGN) && (networkType == 1 || networkType == 2)) {
                            if (!networkPortionNumber.startsWith("+011")) {
                                returnNumber = NANP_IDD + networkPortionNumber.substring(1);
                                break;
                            }
                            returnNumber = networkPortionNumber.substring(1);
                            break;
                        }
                }
                if (returnNumber == null) {
                    returnNumber = networkPortionNumber;
                }
                return returnNumber;
            } else if (networkType == 1 || networkType == 2) {
                return networkPortionNumber.substring(1);
            } else {
                return networkPortionNumber;
            }
        }
    }

    private static ArrayList<String> getAllIDDs(Context context, String mcc) {
        ArrayList<String> allIDDs = (ArrayList) IDDS_MAPS.get(mcc);
        if (allIDDs != null) {
            return allIDDs;
        }
        allIDDs = new ArrayList();
        String[] projection = new String[]{MccIdd.IDD, "MCC"};
        String where = null;
        String[] selectionArgs = null;
        if (mcc != null) {
            where = "MCC=?";
            selectionArgs = new String[]{mcc};
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(MccIdd.CONTENT_URI, projection, where, selectionArgs, null);
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String idd = cursor.getString(0);
                    if (!allIDDs.contains(idd)) {
                        allIDDs.add(idd);
                    }
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            Rlog.e(TAG, "Can't access HbpcdLookup database", e);
            IDDS_MAPS.put(mcc, allIDDs);
            return allIDDs;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        IDDS_MAPS.put(mcc, allIDDs);
        return allIDDs;
    }

    private static int checkNANP(NumberEntry numberEntry, ArrayList<String> allIDDs) {
        boolean isNANP = false;
        String number = numberEntry.number;
        if (number.length() == 7) {
            char firstChar = number.charAt(0);
            if (firstChar >= '2' && firstChar <= '9') {
                isNANP = true;
                for (int i = 1; i < 7; i++) {
                    if (!PhoneNumberUtils.isISODigit(number.charAt(i))) {
                        isNANP = false;
                        break;
                    }
                }
            }
            if (isNANP) {
                return 1;
            }
        } else if (number.length() == 10) {
            if (isNANP(number)) {
                return 2;
            }
        } else if (number.length() == 11) {
            if (isNANP(number)) {
                return 3;
            }
        } else if (number.startsWith(PLUS_SIGN)) {
            number = number.substring(1);
            if (number.length() == 11) {
                if (isNANP(number)) {
                    return 4;
                }
            } else if (number.startsWith(NANP_IDD) && number.length() == 14 && isNANP(number.substring(3))) {
                return 6;
            }
        } else {
            Iterator i$ = allIDDs.iterator();
            while (i$.hasNext()) {
                String idd = (String) i$.next();
                if (number.startsWith(idd)) {
                    String number2 = number.substring(idd.length());
                    if (number2 != null && number2.startsWith(String.valueOf(1)) && isNANP(number2)) {
                        numberEntry.IDD = idd;
                        return 5;
                    }
                }
            }
        }
        return 0;
    }

    private static boolean isNANP(String number) {
        if (number.length() != 10 && (number.length() != 11 || !number.startsWith(NANP_NDD))) {
            return false;
        }
        if (number.length() == 11) {
            number = number.substring(1);
        }
        return PhoneNumberUtils.isNanp(number);
    }

    private static int checkInternationalNumberPlan(Context context, NumberEntry numberEntry, ArrayList<String> allIDDs, String homeIDD) {
        String number = numberEntry.number;
        int countryCode;
        if (number.startsWith(PLUS_SIGN)) {
            String numberNoNBPCD = number.substring(1);
            if (numberNoNBPCD.startsWith(homeIDD)) {
                countryCode = getCountryCode(context, numberNoNBPCD.substring(homeIDD.length()));
                if (countryCode > 0) {
                    numberEntry.countryCode = countryCode;
                    return 100;
                }
            }
            countryCode = getCountryCode(context, numberNoNBPCD);
            if (countryCode > 0) {
                numberEntry.countryCode = countryCode;
                return 102;
            }
        } else if (number.startsWith(homeIDD)) {
            countryCode = getCountryCode(context, number.substring(homeIDD.length()));
            if (countryCode > 0) {
                numberEntry.countryCode = countryCode;
                return 101;
            }
        } else {
            Iterator i$ = allIDDs.iterator();
            while (i$.hasNext()) {
                String exitCode = (String) i$.next();
                if (number.startsWith(exitCode)) {
                    countryCode = getCountryCode(context, number.substring(exitCode.length()));
                    if (countryCode > 0) {
                        numberEntry.countryCode = countryCode;
                        numberEntry.IDD = exitCode;
                        return 103;
                    }
                }
            }
            if (!number.startsWith("0")) {
                countryCode = getCountryCode(context, number);
                if (countryCode > 0) {
                    numberEntry.countryCode = countryCode;
                    return 104;
                }
            }
        }
        return 0;
    }

    private static int getCountryCode(Context context, String number) {
        if (number.length() < 10) {
            return -1;
        }
        int[] allCCs = getAllCountryCodes(context);
        if (allCCs == null) {
            return -1;
        }
        int[] ccArray = new int[MAX_COUNTRY_CODES_LENGTH];
        for (int i = 0; i < MAX_COUNTRY_CODES_LENGTH; i++) {
            ccArray[i] = Integer.valueOf(number.substring(0, i + 1)).intValue();
        }
        for (int tempCC : allCCs) {
            for (int j = 0; j < MAX_COUNTRY_CODES_LENGTH; j++) {
                if (tempCC == ccArray[j]) {
                    return tempCC;
                }
            }
        }
        return -1;
    }

    private static int[] getAllCountryCodes(Context context) {
        if (ALL_COUNTRY_CODES != null) {
            return ALL_COUNTRY_CODES;
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(MccLookup.CONTENT_URI, new String[]{MccLookup.COUNTRY_CODE}, null, null, null);
            if (cursor.getCount() > 0) {
                ALL_COUNTRY_CODES = new int[cursor.getCount()];
                int i = 0;
                while (cursor.moveToNext()) {
                    int countryCode = cursor.getInt(0);
                    int i2 = i + 1;
                    ALL_COUNTRY_CODES[i] = countryCode;
                    int length = String.valueOf(countryCode).trim().length();
                    if (length > MAX_COUNTRY_CODES_LENGTH) {
                        MAX_COUNTRY_CODES_LENGTH = length;
                    }
                    i = i2;
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            Rlog.e(TAG, "Can't access HbpcdLookup database", e);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return ALL_COUNTRY_CODES;
    }

    private static boolean inExceptionListForNpCcAreaLocal(NumberEntry numberEntry) {
        int countryCode = numberEntry.countryCode;
        return numberEntry.number.length() == 12 && (countryCode == 7 || countryCode == 20 || countryCode == 65 || countryCode == 90);
    }

    private static String getNumberPlanType(int state) {
        String numberPlanType = "Number Plan type (" + state + "): ";
        if (state == 1) {
            return "NP_NANP_LOCAL";
        }
        if (state == 2) {
            return "NP_NANP_AREA_LOCAL";
        }
        if (state == 3) {
            return "NP_NANP_NDD_AREA_LOCAL";
        }
        if (state == 4) {
            return "NP_NANP_NBPCD_CC_AREA_LOCAL";
        }
        if (state == 5) {
            return "NP_NANP_LOCALIDD_CC_AREA_LOCAL";
        }
        if (state == 6) {
            return "NP_NANP_NBPCD_HOMEIDD_CC_AREA_LOCAL";
        }
        if (state == 100) {
            return "NP_NBPCD_IDD_CC_AREA_LOCAL";
        }
        if (state == 101) {
            return "NP_IDD_CC_AREA_LOCAL";
        }
        if (state == 102) {
            return "NP_NBPCD_CC_AREA_LOCAL";
        }
        if (state == 103) {
            return "NP_IDD_CC_AREA_LOCAL";
        }
        if (state == 104) {
            return "NP_CC_AREA_LOCAL";
        }
        return "Unknown type";
    }

    public static String filterDestAddr(Context context, String destAddr) {
        if (destAddr == null || !PhoneNumberUtils.isGlobalPhoneNumber(destAddr)) {
            Rlog.w(TAG, "destAddr" + destAddr + " is not a global phone number!");
            return destAddr;
        }
        String networkOperator = TelephonyManager.getDefault().getNetworkOperator();
        String result = null;
        if (isVZWSimCard()) {
            int networkType = getNetworkType(networkOperator);
            if (networkType != -1) {
                String networkMcc = networkOperator.substring(0, 3);
                if (networkMcc != null && networkMcc.trim().length() > 0) {
                    result = formatNumber(context, destAddr, networkMcc, networkType);
                }
            }
        }
        return result == null ? destAddr : result;
    }

    private static int getNetworkType(String networkOperator) {
        int phoneType = TelephonyManager.getDefault().getPhoneType();
        if (phoneType == 1) {
            return 0;
        }
        if (phoneType != 2) {
            return -1;
        }
        if (isVZWNetwork(networkOperator)) {
            return 1;
        }
        return 2;
    }

    private static boolean isVZWSimCard() {
        return sVZWSimcardOperatorList.contains(SystemProperties.get("gsm.sim.operator.numeric"));
    }

    private static boolean isVZWNetwork(String networkOperator) {
        return sVZWNetworkOperatorList.contains(networkOperator);
    }
}
