package com.android.internal.telephony.gsm;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;

public final class EccTable {
    static final String FILE_ECC_LIST = "/data/csc/ecclists.dat";
    private static final boolean LOCAL_DEBUG = false;
    static final String LOG_TAG = "GSM";
    static ArrayList<EccEntry> table = new ArrayList(100);
    static boolean updated = false;

    static class EccEntry implements Comparable<EccEntry> {
        String emergencyNumberWithSIM;
        String emergencyNumberWithoutSIM;
        String plmn;

        EccEntry(String plmn, String emergencyNumberWithSIM, String emergencyNumberWithoutSIM) {
            this.plmn = plmn;
            this.emergencyNumberWithSIM = emergencyNumberWithSIM;
            this.emergencyNumberWithoutSIM = emergencyNumberWithoutSIM;
        }

        public int compareTo(EccEntry o) {
            return this.plmn.compareTo(o.plmn);
        }
    }

    static {
        boolean z = false;
        String salesCode = SystemProperties.get("ro.csc.sales_code");
        if ("LYS".equals(salesCode) || "MID".equals(salesCode) || "MRT".equals(salesCode)) {
            z = true;
        }
        if (Boolean.valueOf(z).booleanValue()) {
            table.add(new EccEntry("", "08,112,911", "112,911,999,000,08,110,118,119,120,122"));
        } else {
            table.add(new EccEntry("", "112,911", "112,911,999,000,08,110,118,119"));
        }
        table.add(new EccEntry("450", "112,911,119", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("234", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("235", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("272", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("204", "112,911", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("724", "112,911,190", "112,911,999,000,08,110,118,119,190"));
        table.add(new EccEntry("33402", "112,911,060", "112,911,999,000,08,110,118,119,060"));
        table.add(new EccEntry("334020", "112,911,060", "112,911,999,000,08,110,118,119,060"));
        table.add(new EccEntry("33420", "112,911,060", "112,911,999,000,08,110,118,119,060"));
        table.add(new EccEntry("33403", "112,911,066", "112,911,999,000,08,110,118,119,066"));
        table.add(new EccEntry("334050", "112,911,066,060", "112,911,999,000,08,110,118,119,066,060"));
        table.add(new EccEntry("33450", "112,911,066,060", "112,911,999,000,08,110,118,119,066,060"));
        table.add(new EccEntry("33405", "112,911,066,060", "112,911,999,000,08,110,118,119,066,060"));
        table.add(new EccEntry("334090", "112,911,066", "112,911,999,000,08,110,118,119,066"));
        table.add(new EccEntry("33490", "112,911,066", "112,911,999,000,08,110,118,119,066"));
        table.add(new EccEntry("33409", "112,911,066", "112,911,999,000,08,110,118,119,066"));
        table.add(new EccEntry("748", "112,911,104,109", "112,911,999,000,08,110,118,119,104,109"));
        table.add(new EccEntry("732101", "112,911", "112,911"));
        table.add(new EccEntry("732123", "112,123", "112,123"));
        table.add(new EccEntry("73212", "112,123", "112,123"));
        table.add(new EccEntry("732103", "112,123", "112,123"));
        table.add(new EccEntry("732111", "112,123", "112,123"));
        table.add(new EccEntry("732130", "112,123", "112,123"));
        table.add(new EccEntry("732187", "112,123", "112,123"));
        table.add(new EccEntry("730", "112,911,133", "112,911,999,000,08,110,118,119,133"));
        table.add(new EccEntry("736", "112,911,110,129", "112,911,999,000,08,110,118,119,110,129"));
        table.add(new EccEntry("71606", "112,911,105", "112,911,999,000,08,110,118,119,105"));
        table.add(new EccEntry("73406", "112,911,*1,*171,171", "112,911,999,000,08,110,118,119,*1,*171,171"));
        table.add(new EccEntry("73401", "112,911,171", "112,911,999,000,08,110,118,119,*1,*171,171"));
        table.add(new EccEntry("73402", "112,911,171", "112,911,999,000,08,110,118,119,*1,*171,171"));
        table.add(new EccEntry("73403", "112,911,171", "112,911,999,000,08,110,118,119,*1,*171,171"));
        table.add(new EccEntry("73404", "112,911", "112,911,999,000,08,110,118,119,*1,*171,171"));
        table.add(new EccEntry("70403", "112,911,122", "112,911,999,000,08,110,118,119,122"));
        table.add(new EccEntry("70604", "112,911,122", "112,911,999,000,08,110,118,119,122"));
        table.add(new EccEntry("71030", "112,911,122", "112,911,999,000,08,110,118,119,122"));
        table.add(new EccEntry("710300", "112,911,122", "112,911,999,000,08,110,118,119,122"));
        table.add(new EccEntry("71401", "112,911,104", "112,911,999,000,08,110,118,119,104"));
        table.add(new EccEntry("310026", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310160", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310170", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310200", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310210", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310220", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310230", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310240", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310250", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310260", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310270", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310280", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310290", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310310", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310330", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310490", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310580", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310660", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310800", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310026", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("310026", "112,911", "112,911,999,000,110,118,119"));
        table.add(new EccEntry("454", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("505", "112,911,000", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("530", "112,911,000,111", "112,911,999,000,08,110,118,119,111"));
        table.add(new EccEntry("537", "110,111,112,911", "112,911,08,000,110,118,119,999,111"));
        table.add(new EccEntry("541", "111,112,113,114,911", "112,911,08,000,110,118,119,999,111,113,114"));
        table.add(new EccEntry("542", "110,111,112,910,911,913,915,917,919", "112,911,08,000,110,118,119,999,111,910,913,915,917,919"));
        table.add(new EccEntry("549", "994,995,996,112,911", "112,911,08,000,110,118,119,999,994,995,996"));
        table.add(new EccEntry("460", "112,911,999,000,08,110,118,119,120,122", "112,911,999,000,08,110,118,119,120,122"));
        table.add(new EccEntry("466", "112,911,110,119", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("440", "112,911,110,118,119", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("441", "112,911,110,118,119", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("424", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("430", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("431", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("434", "112,911", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("42501", "112,911,100", "112,911,100"));
        table.add(new EccEntry("42502", "112,911", "112,911"));
        table.add(new EccEntry("42503", "112,911,100", "112,911,100"));
        table.add(new EccEntry("42505", "112,911,08", "112,911"));
        table.add(new EccEntry("42506", "112,911,08", "112,911"));
        table.add(new EccEntry("416", "112,911,08", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("415", "112,911,08", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("418", "112,911,08", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("502", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("609", "112,911,08", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("606", "112,911,08", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("620", "112,911,999", "112,911,999,000,08,110,118,119"));
        table.add(new EccEntry("639", "112,911,999", "112,911,999,000,08,110,118,119"));
        Collections.sort(table);
    }

    private static EccEntry entryForPLMN(String plmn) {
        int index = 0;
        if (plmn != null) {
            index = Collections.binarySearch(table, new EccEntry(plmn, null, null));
            if (index < 0) {
                if (plmn.length() > 3) {
                    index = Collections.binarySearch(table, new EccEntry(plmn.substring(0, 3), null, null));
                }
                if (index < 0) {
                    index = 0;
                }
            }
        }
        return (EccEntry) table.get(index);
    }

    public static String emergencyNumbersForPLMN(String plmn, boolean withSIM) {
        EccEntry entry = entryForPLMN(plmn);
        if (entry == null) {
            return "";
        }
        if (withSIM) {
            return entry.emergencyNumberWithSIM;
        }
        return entry.emergencyNumberWithoutSIM;
    }

    public static boolean updateEmergencyNumbersForPLMN(String plmn, String emergencyNumberWithSIM, String emergencyNumberWithoutSIM) {
        if (plmn == null) {
            return false;
        }
        int updateIndex = Collections.binarySearch(table, new EccEntry(plmn, null, null));
        int defaultIndex = 0;
        if (updateIndex > 0) {
            defaultIndex = updateIndex;
        } else if (plmn.length() > 3) {
            defaultIndex = Collections.binarySearch(table, new EccEntry(plmn.substring(0, 3), null, null));
            if (defaultIndex < 0) {
                defaultIndex = 0;
            }
        }
        if (TextUtils.isEmpty(emergencyNumberWithSIM)) {
            emergencyNumberWithSIM = ((EccEntry) table.get(defaultIndex)).emergencyNumberWithSIM;
        } else {
            emergencyNumberWithSIM = ((EccEntry) table.get(defaultIndex)).emergencyNumberWithSIM + "," + emergencyNumberWithSIM;
        }
        if (TextUtils.isEmpty(emergencyNumberWithoutSIM)) {
            emergencyNumberWithoutSIM = ((EccEntry) table.get(defaultIndex)).emergencyNumberWithoutSIM;
        } else {
            emergencyNumberWithoutSIM = ((EccEntry) table.get(defaultIndex)).emergencyNumberWithoutSIM + "," + emergencyNumberWithoutSIM;
        }
        if (updateIndex >= 0) {
            EccEntry entry = (EccEntry) table.get(updateIndex);
            if (!TextUtils.isEmpty(emergencyNumberWithSIM)) {
                entry.emergencyNumberWithSIM = emergencyNumberWithSIM;
            }
            if (!TextUtils.isEmpty(emergencyNumberWithoutSIM)) {
                entry.emergencyNumberWithoutSIM = emergencyNumberWithoutSIM;
            }
            table.set(updateIndex, entry);
        } else if (TextUtils.isEmpty(emergencyNumberWithSIM) || TextUtils.isEmpty(emergencyNumberWithoutSIM)) {
            return false;
        } else {
            table.add(new EccEntry(plmn, emergencyNumberWithSIM, emergencyNumberWithoutSIM));
            Collections.sort(table);
        }
        return true;
    }

    public static synchronized void updateEccTable(String customerSpec) {
        Throwable th;
        IOException ioe;
        synchronized (EccTable.class) {
            BufferedReader reader = null;
            try {
                if (!updated && customerSpec != null) {
                    BufferedReader reader2 = new BufferedReader(new StringReader(customerSpec));
                    if (reader2 == null) {
                        if (reader2 != null) {
                            try {
                                reader2.close();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "updateEccTable() Exception : " + e);
                            } catch (Throwable th2) {
                                th = th2;
                                reader = reader2;
                                throw th;
                            }
                        }
                        reader = reader2;
                    } else {
                        try {
                            String[] conventionalSpec = customerSpec.split("\n", 3);
                            if (conventionalSpec.length != 2) {
                                while (true) {
                                    String str = reader2.readLine();
                                    if (str == null) {
                                        break;
                                    }
                                    String emergencyNumberWithSIM = null;
                                    String emergencyNumberWithoutSIM = null;
                                    String plmn = str;
                                    str = reader2.readLine();
                                    if (str != null) {
                                        emergencyNumberWithSIM = str;
                                    }
                                    str = reader2.readLine();
                                    if (str != null) {
                                        emergencyNumberWithoutSIM = str;
                                    }
                                    updateEmergencyNumbersForPLMN(plmn, emergencyNumberWithSIM, emergencyNumberWithoutSIM);
                                }
                            } else {
                                updateEmergencyNumbersForPLMN("", conventionalSpec[0], conventionalSpec[1]);
                            }
                            updated = true;
                            if (reader2 != null) {
                                try {
                                    reader2.close();
                                } catch (Exception e2) {
                                    Log.e(LOG_TAG, "updateEccTable() Exception : " + e2);
                                    reader = reader2;
                                }
                            }
                            reader = reader2;
                        } catch (FileNotFoundException e3) {
                            reader = reader2;
                        } catch (IOException e4) {
                            ioe = e4;
                            reader = reader2;
                        } catch (Throwable th3) {
                            th = th3;
                            reader = reader2;
                        }
                    }
                } else if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e22) {
                        Log.e(LOG_TAG, "updateEccTable() Exception : " + e22);
                    } catch (Throwable th4) {
                        th = th4;
                        throw th;
                    }
                }
            } catch (FileNotFoundException e5) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e222) {
                        Log.e(LOG_TAG, "updateEccTable() Exception : " + e222);
                    }
                }
            } catch (IOException e6) {
                ioe = e6;
                try {
                    Log.e(LOG_TAG, "updateEccTable() IOException : " + ioe);
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (Exception e2222) {
                            Log.e(LOG_TAG, "updateEccTable() Exception : " + e2222);
                        }
                    }
                } catch (Throwable th5) {
                    th = th5;
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (Exception e22222) {
                            Log.e(LOG_TAG, "updateEccTable() Exception : " + e22222);
                        }
                    }
                    throw th;
                }
            }
        }
    }

    public static void printEccTable() {
    }
}
