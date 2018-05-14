package com.google.android.mms.pdu;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.drm.DrmManagerClient;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.Mms.Draft;
import android.provider.Telephony.Mms.Inbox;
import android.provider.Telephony.Mms.Outbox;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Mms.Sent;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.gsm.SmsCbConstants;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MmsException;
import com.google.android.mms.util.DownloadDrmHelper;
import com.google.android.mms.util.DrmConvertSession;
import com.google.android.mms.util.PduCache;
import com.google.android.mms.util.PduCacheEntry;
import com.google.android.mms.util.SqliteWrapper;
import com.sec.android.app.CscFeature;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

public class PduPersister {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static final int[] ADDRESS_FIELDS = new int[]{129, 130, 137, 151};
    private static final HashMap<Integer, Integer> CHARSET_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> CHARSET_COLUMN_NAME_MAP = new HashMap();
    private static final boolean DEBUG = false;
    private static final long DUMMY_THREAD_ID = Long.MAX_VALUE;
    private static final HashMap<Integer, Integer> ENCODED_STRING_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> ENCODED_STRING_COLUMN_NAME_MAP = new HashMap();
    private static final boolean LOCAL_LOGV = false;
    private static final HashMap<Integer, Integer> LONG_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> LONG_COLUMN_NAME_MAP = new HashMap();
    private static final HashMap<Uri, Integer> MESSAGE_BOX_MAP = new HashMap();
    private static final HashMap<Integer, Integer> OCTET_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> OCTET_COLUMN_NAME_MAP = new HashMap();
    private static final int PART_COLUMN_CHARSET = 1;
    private static final int PART_COLUMN_CONTENT_DISPOSITION = 2;
    private static final int PART_COLUMN_CONTENT_ID = 3;
    private static final int PART_COLUMN_CONTENT_LOCATION = 4;
    private static final int PART_COLUMN_CONTENT_TYPE = 5;
    private static final int PART_COLUMN_FILENAME = 6;
    private static final int PART_COLUMN_ID = 0;
    private static final int PART_COLUMN_NAME = 7;
    private static final int PART_COLUMN_TEXT = 8;
    private static final String[] PART_PROJECTION = new String[]{"_id", Part.CHARSET, Part.CONTENT_DISPOSITION, "cid", Part.CONTENT_LOCATION, Part.CONTENT_TYPE, Part.FILENAME, "name", Part.TEXT};
    private static final PduCache PDU_CACHE_INSTANCE = PduCache.getInstance();
    private static final int PDU_COLUMN_CONTENT_CLASS = 11;
    private static final int PDU_COLUMN_CONTENT_LOCATION = 5;
    private static final int PDU_COLUMN_CONTENT_TYPE = 6;
    private static final int PDU_COLUMN_DATE = 21;
    private static final int PDU_COLUMN_DELIVERY_REPORT = 12;
    private static final int PDU_COLUMN_DELIVERY_TIME = 22;
    private static final int PDU_COLUMN_EXPIRY = 23;
    private static final int PDU_COLUMN_ID = 0;
    private static final int PDU_COLUMN_MESSAGE_BOX = 1;
    private static final int PDU_COLUMN_MESSAGE_CLASS = 7;
    private static final int PDU_COLUMN_MESSAGE_ID = 8;
    private static final int PDU_COLUMN_MESSAGE_SIZE = 24;
    private static final int PDU_COLUMN_MESSAGE_TYPE = 13;
    private static final int PDU_COLUMN_MMS_VERSION = 14;
    private static final int PDU_COLUMN_PRIORITY = 15;
    private static final int PDU_COLUMN_READ_REPORT = 16;
    private static final int PDU_COLUMN_READ_STATUS = 17;
    private static final int PDU_COLUMN_REPORT_ALLOWED = 18;
    private static final int PDU_COLUMN_RESPONSE_TEXT = 9;
    private static final int PDU_COLUMN_RETRIEVE_STATUS = 19;
    private static final int PDU_COLUMN_RETRIEVE_TEXT = 3;
    private static final int PDU_COLUMN_RETRIEVE_TEXT_CHARSET = 26;
    private static final int PDU_COLUMN_STATUS = 20;
    private static final int PDU_COLUMN_SUBJECT = 4;
    private static final int PDU_COLUMN_SUBJECT_CHARSET = 25;
    private static final int PDU_COLUMN_THREAD_ID = 2;
    private static final int PDU_COLUMN_TRANSACTION_ID = 10;
    private static final String[] PDU_PROJECTION = new String[]{"_id", BaseMmsColumns.MESSAGE_BOX, "thread_id", BaseMmsColumns.RETRIEVE_TEXT, BaseMmsColumns.SUBJECT, BaseMmsColumns.CONTENT_LOCATION, BaseMmsColumns.CONTENT_TYPE, BaseMmsColumns.MESSAGE_CLASS, BaseMmsColumns.MESSAGE_ID, BaseMmsColumns.RESPONSE_TEXT, BaseMmsColumns.TRANSACTION_ID, BaseMmsColumns.CONTENT_CLASS, BaseMmsColumns.DELIVERY_REPORT, BaseMmsColumns.MESSAGE_TYPE, BaseMmsColumns.MMS_VERSION, "pri", BaseMmsColumns.READ_REPORT, BaseMmsColumns.READ_STATUS, BaseMmsColumns.REPORT_ALLOWED, BaseMmsColumns.RETRIEVE_STATUS, BaseMmsColumns.STATUS, "date", BaseMmsColumns.DELIVERY_TIME, BaseMmsColumns.EXPIRY, BaseMmsColumns.MESSAGE_SIZE, BaseMmsColumns.SUBJECT_CHARSET, BaseMmsColumns.RETRIEVE_TEXT_CHARSET};
    public static final int PROC_STATUS_COMPLETED = 3;
    public static final int PROC_STATUS_PERMANENTLY_FAILURE = 2;
    public static final int PROC_STATUS_TRANSIENT_FAILURE = 1;
    private static final String TAG = "PduPersister";
    public static final String TEMPORARY_DRM_OBJECT_URI = "content://mms/9223372036854775807/part";
    private static final HashMap<Integer, Integer> TEXT_STRING_COLUMN_INDEX_MAP = new HashMap();
    private static final HashMap<Integer, String> TEXT_STRING_COLUMN_NAME_MAP = new HashMap();
    private static PduPersister sPersister;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final CscFeature mCscFeature = CscFeature.getInstance();
    private final DrmManagerClient mDrmManagerClient;
    private final TelephonyManager mTelephonyManager;

    static {
        boolean z;
        if (PduPersister.class.desiredAssertionStatus()) {
            z = false;
        } else {
            z = true;
        }
        $assertionsDisabled = z;
        MESSAGE_BOX_MAP.put(Inbox.CONTENT_URI, Integer.valueOf(1));
        MESSAGE_BOX_MAP.put(Sent.CONTENT_URI, Integer.valueOf(2));
        MESSAGE_BOX_MAP.put(Draft.CONTENT_URI, Integer.valueOf(3));
        MESSAGE_BOX_MAP.put(Outbox.CONTENT_URI, Integer.valueOf(4));
        MESSAGE_BOX_MAP.put(Uri.parse("content://spammms/inbox"), Integer.valueOf(1));
        CHARSET_COLUMN_INDEX_MAP.put(Integer.valueOf(150), Integer.valueOf(25));
        CHARSET_COLUMN_INDEX_MAP.put(Integer.valueOf(154), Integer.valueOf(26));
        CHARSET_COLUMN_NAME_MAP.put(Integer.valueOf(150), BaseMmsColumns.SUBJECT_CHARSET);
        CHARSET_COLUMN_NAME_MAP.put(Integer.valueOf(154), BaseMmsColumns.RETRIEVE_TEXT_CHARSET);
        ENCODED_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(154), Integer.valueOf(3));
        ENCODED_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(150), Integer.valueOf(4));
        ENCODED_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(154), BaseMmsColumns.RETRIEVE_TEXT);
        ENCODED_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(150), BaseMmsColumns.SUBJECT);
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(131), Integer.valueOf(5));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(132), Integer.valueOf(6));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(138), Integer.valueOf(7));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(139), Integer.valueOf(8));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(147), Integer.valueOf(9));
        TEXT_STRING_COLUMN_INDEX_MAP.put(Integer.valueOf(152), Integer.valueOf(10));
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(131), BaseMmsColumns.CONTENT_LOCATION);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(132), BaseMmsColumns.CONTENT_TYPE);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(138), BaseMmsColumns.MESSAGE_CLASS);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(139), BaseMmsColumns.MESSAGE_ID);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(147), BaseMmsColumns.RESPONSE_TEXT);
        TEXT_STRING_COLUMN_NAME_MAP.put(Integer.valueOf(152), BaseMmsColumns.TRANSACTION_ID);
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(PduHeaders.CONTENT_CLASS), Integer.valueOf(11));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(134), Integer.valueOf(12));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(140), Integer.valueOf(13));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(141), Integer.valueOf(14));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(143), Integer.valueOf(15));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(144), Integer.valueOf(16));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(155), Integer.valueOf(17));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(145), Integer.valueOf(18));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(153), Integer.valueOf(19));
        OCTET_COLUMN_INDEX_MAP.put(Integer.valueOf(149), Integer.valueOf(20));
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(PduHeaders.CONTENT_CLASS), BaseMmsColumns.CONTENT_CLASS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(134), BaseMmsColumns.DELIVERY_REPORT);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(140), BaseMmsColumns.MESSAGE_TYPE);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(141), BaseMmsColumns.MMS_VERSION);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(143), "pri");
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(144), BaseMmsColumns.READ_REPORT);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(155), BaseMmsColumns.READ_STATUS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(145), BaseMmsColumns.REPORT_ALLOWED);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(153), BaseMmsColumns.RETRIEVE_STATUS);
        OCTET_COLUMN_NAME_MAP.put(Integer.valueOf(149), BaseMmsColumns.STATUS);
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(133), Integer.valueOf(21));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(135), Integer.valueOf(22));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(136), Integer.valueOf(23));
        LONG_COLUMN_INDEX_MAP.put(Integer.valueOf(142), Integer.valueOf(24));
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(133), "date");
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(135), BaseMmsColumns.DELIVERY_TIME);
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(136), BaseMmsColumns.EXPIRY);
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(142), BaseMmsColumns.MESSAGE_SIZE);
        LONG_COLUMN_NAME_MAP.put(Integer.valueOf(192), "reserved");
    }

    private PduPersister(Context context) {
        this.mContext = context;
        this.mContentResolver = context.getContentResolver();
        this.mDrmManagerClient = new DrmManagerClient(context);
        this.mTelephonyManager = (TelephonyManager) context.getSystemService("phone");
    }

    public static PduPersister getPduPersister(Context context) {
        if (sPersister == null) {
            sPersister = new PduPersister(context);
        } else if (!context.equals(sPersister.mContext)) {
            sPersister.release();
            sPersister = new PduPersister(context);
        }
        return sPersister;
    }

    private void setEncodedStringValueToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        String s = c.getString(columnIndex);
        if (s != null && s.length() > 0) {
            headers.setEncodedStringValue(new EncodedStringValue(c.getInt(((Integer) CHARSET_COLUMN_INDEX_MAP.get(Integer.valueOf(mapColumn))).intValue()), getBytes(s)), mapColumn);
        }
    }

    private void setTextStringToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        String s = c.getString(columnIndex);
        if (s != null) {
            headers.setTextString(getBytes(s), mapColumn);
        }
    }

    private void setOctetToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) throws InvalidHeaderValueException {
        if (!c.isNull(columnIndex)) {
            headers.setOctet(c.getInt(columnIndex), mapColumn);
        }
    }

    private void setLongToHeaders(Cursor c, int columnIndex, PduHeaders headers, int mapColumn) {
        if (!c.isNull(columnIndex)) {
            headers.setLongInteger(c.getLong(columnIndex), mapColumn);
        }
    }

    private Integer getIntegerFromPartColumn(Cursor c, int columnIndex) {
        if (c.isNull(columnIndex)) {
            return null;
        }
        return Integer.valueOf(c.getInt(columnIndex));
    }

    private byte[] getByteArrayFromPartColumn(Cursor c, int columnIndex) {
        if (c.isNull(columnIndex)) {
            return null;
        }
        return getBytes(c.getString(columnIndex));
    }

    private PduPart[] loadParts(long msgId) throws MmsException {
        return loadParts(msgId, false);
    }

    private PduPart[] loadParts(long msgId, boolean bSpam) throws MmsException {
        Cursor c;
        if (bSpam) {
            c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://spammms/" + msgId + "/spampart"), PART_PROJECTION, null, null, null);
        } else {
            c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/part"), PART_PROJECTION, null, null, null);
        }
        if (c != null) {
            if (c.getCount() != 0) {
                PduPart[] parts = new PduPart[c.getCount()];
                int partIdx = 0;
                while (c.moveToNext()) {
                    PduPart part = new PduPart();
                    Integer charset = getIntegerFromPartColumn(c, 1);
                    if (charset != null) {
                        part.setCharset(charset.intValue());
                    }
                    byte[] contentDisposition = getByteArrayFromPartColumn(c, 2);
                    if (contentDisposition != null) {
                        part.setContentDisposition(contentDisposition);
                    }
                    byte[] contentId = getByteArrayFromPartColumn(c, 3);
                    if (contentId != null) {
                        part.setContentId(contentId);
                    }
                    byte[] contentLocation = getByteArrayFromPartColumn(c, 4);
                    if (contentLocation != null) {
                        part.setContentLocation(contentLocation);
                    }
                    byte[] contentType = getByteArrayFromPartColumn(c, 5);
                    if (contentType != null) {
                        Uri partURI;
                        part.setContentType(contentType);
                        byte[] fileName = getByteArrayFromPartColumn(c, 6);
                        if (fileName != null) {
                            part.setFilename(fileName);
                        }
                        byte[] name = getByteArrayFromPartColumn(c, 7);
                        if (name != null) {
                            part.setName(name);
                        }
                        long partId = c.getLong(0);
                        if (bSpam) {
                            partURI = Uri.parse("content://spammms/spampart/" + partId);
                        } else {
                            partURI = Uri.parse("content://mms/part/" + partId);
                        }
                        part.setDataUri(partURI);
                        String type = toIsoString(contentType);
                        if (!(ContentType.isImageType(type) || ContentType.isAudioType(type) || ContentType.isVideoType(type))) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            InputStream is = null;
                            if (ContentType.TEXT_PLAIN.equals(type) || ContentType.APP_SMIL.equals(type) || ContentType.TEXT_HTML.equals(type)) {
                                String text = c.getString(8);
                                if (text == null) {
                                    text = "";
                                }
                                byte[] blob = new EncodedStringValue(text).getTextString();
                                baos.write(blob, 0, blob.length);
                            } else {
                                try {
                                    is = this.mContentResolver.openInputStream(partURI);
                                    if (is == null) {
                                        throw new MmsException("Failed to load part data, return null.");
                                    }
                                    byte[] buffer = new byte[256];
                                    for (int len = is.read(buffer); len >= 0; len = is.read(buffer)) {
                                        baos.write(buffer, 0, len);
                                    }
                                    if (is != null) {
                                        try {
                                            is.close();
                                        } catch (Throwable e) {
                                            Log.e(TAG, "Failed to close stream", e);
                                        } catch (Throwable th) {
                                            if (c != null) {
                                                c.close();
                                            }
                                        }
                                    }
                                } catch (Throwable e2) {
                                    Log.e(TAG, "Failed to load part data", e2);
                                    c.close();
                                    throw new MmsException(e2);
                                } catch (Throwable th2) {
                                    if (is != null) {
                                        try {
                                            is.close();
                                        } catch (Throwable e22) {
                                            Log.e(TAG, "Failed to close stream", e22);
                                        }
                                    }
                                }
                            }
                            part.setData(baos.toByteArray());
                        }
                        int partIdx2 = partIdx + 1;
                        parts[partIdx] = part;
                        partIdx = partIdx2;
                    } else {
                        throw new MmsException("Content-Type must be set.");
                    }
                }
                if (c != null) {
                    c.close();
                }
                return parts;
            }
        }
        if (c == null) {
            return null;
        }
        c.close();
        return null;
    }

    private void loadAddress(long msgId, PduHeaders headers) {
        loadAddress(msgId, headers, false);
    }

    private void loadAddress(long msgId, PduHeaders headers, boolean bSpam) {
        Cursor c;
        if (bSpam) {
            c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://spammms/" + msgId + "/spamaddr"), new String[]{"address", Addr.CHARSET, "type"}, null, null, null);
        } else {
            c = SqliteWrapper.query(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), new String[]{"address", Addr.CHARSET, "type"}, null, null, null);
        }
        if (c != null) {
            while (c.moveToNext()) {
                try {
                    String addr = c.getString(0);
                    if (!TextUtils.isEmpty(addr)) {
                        int addrType = c.getInt(2);
                        switch (addrType) {
                            case 129:
                            case 130:
                            case 151:
                                headers.appendEncodedStringValue(new EncodedStringValue(c.getInt(1), getBytes(addr)), addrType);
                                break;
                            case 137:
                                headers.setEncodedStringValue(new EncodedStringValue(c.getInt(1), getBytes(addr)), addrType);
                                break;
                            default:
                                Log.e(TAG, "Unknown address type: " + addrType);
                                break;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public com.google.android.mms.pdu.GenericPdu load(android.net.Uri r37) throws com.google.android.mms.MmsException {
        /*
        r36 = this;
        r30 = 0;
        r15 = 0;
        r24 = 0;
        r34 = -1;
        r7 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0056 }
        monitor-enter(r7);	 Catch:{ all -> 0x0056 }
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0053 }
        r0 = r37;
        r6 = r6.isUpdating(r0);	 Catch:{ all -> 0x0053 }
        if (r6 == 0) goto L_0x009e;
    L_0x0014:
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ InterruptedException -> 0x0048 }
        r6.wait();	 Catch:{ InterruptedException -> 0x0048 }
    L_0x0019:
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0053 }
        r0 = r37;
        r6 = r6.get(r0);	 Catch:{ all -> 0x0053 }
        r0 = r6;
        r0 = (com.google.android.mms.util.PduCacheEntry) r0;	 Catch:{ all -> 0x0053 }
        r15 = r0;
        if (r15 == 0) goto L_0x009e;
    L_0x0027:
        r6 = r15.getPdu();	 Catch:{ all -> 0x0053 }
        monitor-exit(r7);	 Catch:{ all -> 0x0053 }
        r7 = PDU_CACHE_INSTANCE;
        monitor-enter(r7);
        if (r30 == 0) goto L_0x008f;
    L_0x0031:
        r8 = $assertionsDisabled;	 Catch:{ all -> 0x0045 }
        if (r8 != 0) goto L_0x0077;
    L_0x0035:
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0045 }
        r0 = r37;
        r8 = r8.get(r0);	 Catch:{ all -> 0x0045 }
        if (r8 == 0) goto L_0x0077;
    L_0x003f:
        r6 = new java.lang.AssertionError;	 Catch:{ all -> 0x0045 }
        r6.<init>();	 Catch:{ all -> 0x0045 }
        throw r6;	 Catch:{ all -> 0x0045 }
    L_0x0045:
        r6 = move-exception;
    L_0x0046:
        monitor-exit(r7);	 Catch:{ all -> 0x0045 }
        throw r6;
    L_0x0048:
        r17 = move-exception;
        r6 = "PduPersister";
        r8 = "load: ";
        r0 = r17;
        android.util.Log.e(r6, r8, r0);	 Catch:{ all -> 0x0053 }
        goto L_0x0019;
    L_0x0053:
        r6 = move-exception;
    L_0x0054:
        monitor-exit(r7);	 Catch:{ all -> 0x0053 }
        throw r6;	 Catch:{ all -> 0x0056 }
    L_0x0056:
        r6 = move-exception;
        r16 = r15;
    L_0x0059:
        r7 = PDU_CACHE_INSTANCE;
        monitor-enter(r7);
        if (r30 == 0) goto L_0x0346;
    L_0x005e:
        r8 = $assertionsDisabled;	 Catch:{ all -> 0x0072 }
        if (r8 != 0) goto L_0x0315;
    L_0x0062:
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0072 }
        r0 = r37;
        r8 = r8.get(r0);	 Catch:{ all -> 0x0072 }
        if (r8 == 0) goto L_0x0315;
    L_0x006c:
        r6 = new java.lang.AssertionError;	 Catch:{ all -> 0x0072 }
        r6.<init>();	 Catch:{ all -> 0x0072 }
        throw r6;	 Catch:{ all -> 0x0072 }
    L_0x0072:
        r6 = move-exception;
        r15 = r16;
    L_0x0075:
        monitor-exit(r7);	 Catch:{ all -> 0x0336 }
        throw r6;
    L_0x0077:
        r16 = new com.google.android.mms.util.PduCacheEntry;	 Catch:{ all -> 0x0045 }
        r0 = r16;
        r1 = r30;
        r2 = r24;
        r3 = r34;
        r0.<init>(r1, r2, r3);	 Catch:{ all -> 0x0045 }
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0341 }
        r0 = r37;
        r1 = r16;
        r8.put(r0, r1);	 Catch:{ all -> 0x0341 }
        r15 = r16;
    L_0x008f:
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0045 }
        r9 = 0;
        r0 = r37;
        r8.setUpdating(r0, r9);	 Catch:{ all -> 0x0045 }
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0045 }
        r8.notifyAll();	 Catch:{ all -> 0x0045 }
        monitor-exit(r7);	 Catch:{ all -> 0x0045 }
    L_0x009d:
        return r6;
    L_0x009e:
        r16 = r15;
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x033c }
        r8 = 1;
        r0 = r37;
        r6.setUpdating(r0, r8);	 Catch:{ all -> 0x033c }
        monitor-exit(r7);	 Catch:{ all -> 0x033c }
        r0 = r36;
        r6 = r0.mContext;	 Catch:{ all -> 0x00f6 }
        r0 = r36;
        r7 = r0.mContentResolver;	 Catch:{ all -> 0x00f6 }
        r9 = PDU_PROJECTION;	 Catch:{ all -> 0x00f6 }
        r10 = 0;
        r11 = 0;
        r12 = 0;
        r8 = r37;
        r14 = com.google.android.mms.util.SqliteWrapper.query(r6, r7, r8, r9, r10, r11, r12);	 Catch:{ all -> 0x00f6 }
        r19 = new com.google.android.mms.pdu.PduHeaders;	 Catch:{ all -> 0x00f6 }
        r19.<init>();	 Catch:{ all -> 0x00f6 }
        r26 = android.content.ContentUris.parseId(r37);	 Catch:{ all -> 0x00f6 }
        if (r14 == 0) goto L_0x00d4;
    L_0x00c7:
        r6 = r14.getCount();	 Catch:{ all -> 0x00ef }
        r7 = 1;
        if (r6 != r7) goto L_0x00d4;
    L_0x00ce:
        r6 = r14.moveToFirst();	 Catch:{ all -> 0x00ef }
        if (r6 != 0) goto L_0x00f9;
    L_0x00d4:
        r6 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00ef }
        r7 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00ef }
        r7.<init>();	 Catch:{ all -> 0x00ef }
        r8 = "Bad uri: ";
        r7 = r7.append(r8);	 Catch:{ all -> 0x00ef }
        r0 = r37;
        r7 = r7.append(r0);	 Catch:{ all -> 0x00ef }
        r7 = r7.toString();	 Catch:{ all -> 0x00ef }
        r6.<init>(r7);	 Catch:{ all -> 0x00ef }
        throw r6;	 Catch:{ all -> 0x00ef }
    L_0x00ef:
        r6 = move-exception;
        if (r14 == 0) goto L_0x00f5;
    L_0x00f2:
        r14.close();	 Catch:{ all -> 0x00f6 }
    L_0x00f5:
        throw r6;	 Catch:{ all -> 0x00f6 }
    L_0x00f6:
        r6 = move-exception;
        goto L_0x0059;
    L_0x00f9:
        r6 = 1;
        r24 = r14.getInt(r6);	 Catch:{ all -> 0x00ef }
        r6 = 2;
        r34 = r14.getLong(r6);	 Catch:{ all -> 0x00ef }
        r6 = ENCODED_STRING_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x00ef }
        r32 = r6.entrySet();	 Catch:{ all -> 0x00ef }
        r21 = r32.iterator();	 Catch:{ all -> 0x00ef }
    L_0x010d:
        r6 = r21.hasNext();	 Catch:{ all -> 0x00ef }
        if (r6 == 0) goto L_0x0135;
    L_0x0113:
        r18 = r21.next();	 Catch:{ all -> 0x00ef }
        r18 = (java.util.Map.Entry) r18;	 Catch:{ all -> 0x00ef }
        r6 = r18.getValue();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r7 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r6 = r18.getKey();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r6 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r0 = r36;
        r1 = r19;
        r0.setEncodedStringValueToHeaders(r14, r7, r1, r6);	 Catch:{ all -> 0x00ef }
        goto L_0x010d;
    L_0x0135:
        r6 = TEXT_STRING_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x00ef }
        r32 = r6.entrySet();	 Catch:{ all -> 0x00ef }
        r21 = r32.iterator();	 Catch:{ all -> 0x00ef }
    L_0x013f:
        r6 = r21.hasNext();	 Catch:{ all -> 0x00ef }
        if (r6 == 0) goto L_0x0167;
    L_0x0145:
        r18 = r21.next();	 Catch:{ all -> 0x00ef }
        r18 = (java.util.Map.Entry) r18;	 Catch:{ all -> 0x00ef }
        r6 = r18.getValue();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r7 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r6 = r18.getKey();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r6 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r0 = r36;
        r1 = r19;
        r0.setTextStringToHeaders(r14, r7, r1, r6);	 Catch:{ all -> 0x00ef }
        goto L_0x013f;
    L_0x0167:
        r6 = OCTET_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x00ef }
        r32 = r6.entrySet();	 Catch:{ all -> 0x00ef }
        r21 = r32.iterator();	 Catch:{ all -> 0x00ef }
    L_0x0171:
        r6 = r21.hasNext();	 Catch:{ all -> 0x00ef }
        if (r6 == 0) goto L_0x0199;
    L_0x0177:
        r18 = r21.next();	 Catch:{ all -> 0x00ef }
        r18 = (java.util.Map.Entry) r18;	 Catch:{ all -> 0x00ef }
        r6 = r18.getValue();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r7 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r6 = r18.getKey();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r6 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r0 = r36;
        r1 = r19;
        r0.setOctetToHeaders(r14, r7, r1, r6);	 Catch:{ all -> 0x00ef }
        goto L_0x0171;
    L_0x0199:
        r6 = LONG_COLUMN_INDEX_MAP;	 Catch:{ all -> 0x00ef }
        r32 = r6.entrySet();	 Catch:{ all -> 0x00ef }
        r21 = r32.iterator();	 Catch:{ all -> 0x00ef }
    L_0x01a3:
        r6 = r21.hasNext();	 Catch:{ all -> 0x00ef }
        if (r6 == 0) goto L_0x01cb;
    L_0x01a9:
        r18 = r21.next();	 Catch:{ all -> 0x00ef }
        r18 = (java.util.Map.Entry) r18;	 Catch:{ all -> 0x00ef }
        r6 = r18.getValue();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r7 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r6 = r18.getKey();	 Catch:{ all -> 0x00ef }
        r6 = (java.lang.Integer) r6;	 Catch:{ all -> 0x00ef }
        r6 = r6.intValue();	 Catch:{ all -> 0x00ef }
        r0 = r36;
        r1 = r19;
        r0.setLongToHeaders(r14, r7, r1, r6);	 Catch:{ all -> 0x00ef }
        goto L_0x01a3;
    L_0x01cb:
        if (r14 == 0) goto L_0x01d0;
    L_0x01cd:
        r14.close();	 Catch:{ all -> 0x00f6 }
    L_0x01d0:
        r6 = -1;
        r6 = (r26 > r6 ? 1 : (r26 == r6 ? 0 : -1));
        if (r6 != 0) goto L_0x01de;
    L_0x01d6:
        r6 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00f6 }
        r7 = "Error! ID of the message: -1.";
        r6.<init>(r7);	 Catch:{ all -> 0x00f6 }
        throw r6;	 Catch:{ all -> 0x00f6 }
    L_0x01de:
        r23 = r37.getAuthority();	 Catch:{ all -> 0x00f6 }
        r22 = 0;
        if (r23 == 0) goto L_0x01f2;
    L_0x01e6:
        r6 = "spammms";
        r0 = r23;
        r6 = r0.equals(r6);	 Catch:{ all -> 0x00f6 }
        if (r6 == 0) goto L_0x01f2;
    L_0x01f0:
        r22 = 1;
    L_0x01f2:
        r0 = r36;
        r1 = r26;
        r3 = r19;
        r4 = r22;
        r0.loadAddress(r1, r3, r4);	 Catch:{ all -> 0x00f6 }
        r6 = 140; // 0x8c float:1.96E-43 double:6.9E-322;
        r0 = r19;
        r25 = r0.getOctet(r6);	 Catch:{ all -> 0x00f6 }
        r13 = new com.google.android.mms.pdu.PduBody;	 Catch:{ all -> 0x00f6 }
        r13.<init>();	 Catch:{ all -> 0x00f6 }
        r6 = 132; // 0x84 float:1.85E-43 double:6.5E-322;
        r0 = r25;
        if (r0 == r6) goto L_0x0216;
    L_0x0210:
        r6 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r0 = r25;
        if (r0 != r6) goto L_0x0237;
    L_0x0216:
        r0 = r36;
        r1 = r26;
        r3 = r22;
        r28 = r0.loadParts(r1, r3);	 Catch:{ all -> 0x00f6 }
        if (r28 == 0) goto L_0x0237;
    L_0x0222:
        r0 = r28;
        r0 = r0.length;	 Catch:{ all -> 0x00f6 }
        r29 = r0;
        r20 = 0;
    L_0x0229:
        r0 = r20;
        r1 = r29;
        if (r0 >= r1) goto L_0x0237;
    L_0x022f:
        r6 = r28[r20];	 Catch:{ all -> 0x00f6 }
        r13.addPart(r6);	 Catch:{ all -> 0x00f6 }
        r20 = r20 + 1;
        goto L_0x0229;
    L_0x0237:
        switch(r25) {
            case 128: goto L_0x02a4;
            case 129: goto L_0x02d4;
            case 130: goto L_0x0257;
            case 131: goto L_0x02bc;
            case 132: goto L_0x0298;
            case 133: goto L_0x02b0;
            case 134: goto L_0x0280;
            case 135: goto L_0x02c8;
            case 136: goto L_0x028c;
            case 137: goto L_0x02d4;
            case 138: goto L_0x02d4;
            case 139: goto L_0x02d4;
            case 140: goto L_0x02d4;
            case 141: goto L_0x02d4;
            case 142: goto L_0x02d4;
            case 143: goto L_0x02d4;
            case 144: goto L_0x02d4;
            case 145: goto L_0x02d4;
            case 146: goto L_0x02d4;
            case 147: goto L_0x02d4;
            case 148: goto L_0x02d4;
            case 149: goto L_0x02d4;
            case 150: goto L_0x02d4;
            case 151: goto L_0x02d4;
            default: goto L_0x023a;
        };	 Catch:{ all -> 0x00f6 }
    L_0x023a:
        r6 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00f6 }
        r7 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00f6 }
        r7.<init>();	 Catch:{ all -> 0x00f6 }
        r8 = "Unrecognized PDU type: ";
        r7 = r7.append(r8);	 Catch:{ all -> 0x00f6 }
        r8 = java.lang.Integer.toHexString(r25);	 Catch:{ all -> 0x00f6 }
        r7 = r7.append(r8);	 Catch:{ all -> 0x00f6 }
        r7 = r7.toString();	 Catch:{ all -> 0x00f6 }
        r6.<init>(r7);	 Catch:{ all -> 0x00f6 }
        throw r6;	 Catch:{ all -> 0x00f6 }
    L_0x0257:
        r31 = new com.google.android.mms.pdu.NotificationInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
    L_0x0262:
        r7 = PDU_CACHE_INSTANCE;
        monitor-enter(r7);
        if (r30 == 0) goto L_0x0349;
    L_0x0267:
        r6 = $assertionsDisabled;	 Catch:{ all -> 0x027b }
        if (r6 != 0) goto L_0x02f1;
    L_0x026b:
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x027b }
        r0 = r37;
        r6 = r6.get(r0);	 Catch:{ all -> 0x027b }
        if (r6 == 0) goto L_0x02f1;
    L_0x0275:
        r6 = new java.lang.AssertionError;	 Catch:{ all -> 0x027b }
        r6.<init>();	 Catch:{ all -> 0x027b }
        throw r6;	 Catch:{ all -> 0x027b }
    L_0x027b:
        r6 = move-exception;
        r15 = r16;
    L_0x027e:
        monitor-exit(r7);	 Catch:{ all -> 0x0339 }
        throw r6;
    L_0x0280:
        r31 = new com.google.android.mms.pdu.DeliveryInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x028c:
        r31 = new com.google.android.mms.pdu.ReadOrigInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x0298:
        r31 = new com.google.android.mms.pdu.RetrieveConf;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1, r13);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x02a4:
        r31 = new com.google.android.mms.pdu.SendReq;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1, r13);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x02b0:
        r31 = new com.google.android.mms.pdu.AcknowledgeInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x02bc:
        r31 = new com.google.android.mms.pdu.NotifyRespInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x02c8:
        r31 = new com.google.android.mms.pdu.ReadRecInd;	 Catch:{ all -> 0x00f6 }
        r0 = r31;
        r1 = r19;
        r0.<init>(r1);	 Catch:{ all -> 0x00f6 }
        r30 = r31;
        goto L_0x0262;
    L_0x02d4:
        r6 = new com.google.android.mms.MmsException;	 Catch:{ all -> 0x00f6 }
        r7 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00f6 }
        r7.<init>();	 Catch:{ all -> 0x00f6 }
        r8 = "Unsupported PDU type: ";
        r7 = r7.append(r8);	 Catch:{ all -> 0x00f6 }
        r8 = java.lang.Integer.toHexString(r25);	 Catch:{ all -> 0x00f6 }
        r7 = r7.append(r8);	 Catch:{ all -> 0x00f6 }
        r7 = r7.toString();	 Catch:{ all -> 0x00f6 }
        r6.<init>(r7);	 Catch:{ all -> 0x00f6 }
        throw r6;	 Catch:{ all -> 0x00f6 }
    L_0x02f1:
        r15 = new com.google.android.mms.util.PduCacheEntry;	 Catch:{ all -> 0x027b }
        r0 = r30;
        r1 = r24;
        r2 = r34;
        r15.<init>(r0, r1, r2);	 Catch:{ all -> 0x027b }
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0339 }
        r0 = r37;
        r6.put(r0, r15);	 Catch:{ all -> 0x0339 }
    L_0x0303:
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0339 }
        r8 = 0;
        r0 = r37;
        r6.setUpdating(r0, r8);	 Catch:{ all -> 0x0339 }
        r6 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0339 }
        r6.notifyAll();	 Catch:{ all -> 0x0339 }
        monitor-exit(r7);	 Catch:{ all -> 0x0339 }
        r6 = r30;
        goto L_0x009d;
    L_0x0315:
        r15 = new com.google.android.mms.util.PduCacheEntry;	 Catch:{ all -> 0x0072 }
        r0 = r30;
        r1 = r24;
        r2 = r34;
        r15.<init>(r0, r1, r2);	 Catch:{ all -> 0x0072 }
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0336 }
        r0 = r37;
        r8.put(r0, r15);	 Catch:{ all -> 0x0336 }
    L_0x0327:
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0336 }
        r9 = 0;
        r0 = r37;
        r8.setUpdating(r0, r9);	 Catch:{ all -> 0x0336 }
        r8 = PDU_CACHE_INSTANCE;	 Catch:{ all -> 0x0336 }
        r8.notifyAll();	 Catch:{ all -> 0x0336 }
        monitor-exit(r7);	 Catch:{ all -> 0x0336 }
        throw r6;
    L_0x0336:
        r6 = move-exception;
        goto L_0x0075;
    L_0x0339:
        r6 = move-exception;
        goto L_0x027e;
    L_0x033c:
        r6 = move-exception;
        r15 = r16;
        goto L_0x0054;
    L_0x0341:
        r6 = move-exception;
        r15 = r16;
        goto L_0x0046;
    L_0x0346:
        r15 = r16;
        goto L_0x0327;
    L_0x0349:
        r15 = r16;
        goto L_0x0303;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.android.mms.pdu.PduPersister.load(android.net.Uri):com.google.android.mms.pdu.GenericPdu");
    }

    private void persistAddress(long msgId, int type, EncodedStringValue[] array) {
        persistAddress(msgId, type, array, false);
    }

    private void persistAddress(long msgId, int type, EncodedStringValue[] array, boolean bSpam) {
        ContentValues values = new ContentValues(3);
        for (EncodedStringValue addr : array) {
            Uri uri;
            values.clear();
            values.put("address", toIsoString(addr.getTextString()));
            values.put(Addr.CHARSET, Integer.valueOf(addr.getCharacterSet()));
            values.put("type", Integer.valueOf(type));
            if (bSpam) {
                uri = Uri.parse("content://spammms/" + msgId + "/spamaddr");
            } else {
                uri = Uri.parse("content://mms/" + msgId + "/addr");
            }
            SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
        }
    }

    private static String getPartContentType(PduPart part) {
        return part.getContentType() == null ? null : toIsoString(part.getContentType());
    }

    public Uri persistPart(PduPart part, long msgId, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        return persistPart(part, msgId, preOpenedFiles, false, false);
    }

    public Uri persistPart(PduPart part, long msgId, HashMap<Uri, InputStream> preOpenedFiles, boolean bSpam, boolean hasVendorDrmEngine) throws MmsException {
        Uri uri;
        if (bSpam) {
            uri = Uri.parse("content://spammms/" + msgId + "/spampart");
        } else {
            uri = Uri.parse("content://mms/" + msgId + "/part");
        }
        ContentValues values = new ContentValues(8);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Part.CHARSET, Integer.valueOf(charset));
        }
        String contentType = getPartContentType(part);
        if (contentType != null) {
            StringTokenizer st;
            if (ContentType.IMAGE_JPG.equals(contentType)) {
                contentType = ContentType.IMAGE_JPEG;
            }
            values.put(Part.CONTENT_TYPE, contentType);
            if (ContentType.APP_SMIL.equals(contentType)) {
                values.put(Part.SEQ, Integer.valueOf(-1));
            }
            if (part.getFilename() != null) {
                if (this.mCscFeature.getEnableStatus("CscFeature_Message_EnableOMA13NameEncoding", false)) {
                    values.put(Part.FILENAME, toIsoString(part.getFilename()));
                } else {
                    st = new StringTokenizer(new String(part.getFilename()), "\\/:*?\"<>|");
                    String fileName = "";
                    while (st.hasMoreTokens()) {
                        fileName = fileName + st.nextToken();
                    }
                    values.put(Part.FILENAME, fileName);
                }
            }
            if (part.getName() != null) {
                if (this.mCscFeature.getEnableStatus("CscFeature_Message_EnableOMA13NameEncoding", false)) {
                    values.put("name", toIsoString(part.getName()));
                } else {
                    st = new StringTokenizer(new String(part.getName()), "\\/:*?\"<>|");
                    String name = "";
                    while (st.hasMoreTokens()) {
                        name = name + st.nextToken();
                    }
                    values.put("name", toIsoString(name.getBytes()));
                }
            }
            if (part.getContentDisposition() != null) {
                values.put(Part.CONTENT_DISPOSITION, toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
            }
            Uri res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
            if (res == null) {
                throw new MmsException("Failed to persist part, return null.");
            }
            persistData(part, res, contentType, preOpenedFiles, bSpam, hasVendorDrmEngine);
            part.setDataUri(res);
            return res;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    private void persistData(PduPart part, Uri uri, String contentType, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        persistData(part, uri, contentType, preOpenedFiles, false, false);
    }

    private void persistData(PduPart part, Uri uri, String contentType, HashMap<Uri, InputStream> preOpenedFiles, boolean bSpam, boolean hasVendorDrmEngine) throws MmsException {
        OutputStream os = null;
        InputStream is = null;
        DrmConvertSession drmConvertSession = null;
        boolean isDrm = false;
        String path = null;
        if (preOpenedFiles == null) {
            Log.v(TAG, "preOpenedFiles is null");
        }
        File f;
        try {
            byte[] data = part.getData();
            if (ContentType.TEXT_PLAIN.equals(contentType) || ContentType.APP_SMIL.equals(contentType) || ContentType.TEXT_HTML.equals(contentType)) {
                ContentValues cv = new ContentValues();
                if (data == null) {
                    cv.put(Part.TEXT, "");
                } else if (part.getCharset() == 38) {
                    cv.put(Part.TEXT, new EncodedStringValue(part.getCharset(), data).getString());
                    cv.put(Part.CHARSET, Integer.valueOf(106));
                } else {
                    cv.put(Part.TEXT, new EncodedStringValue(data).getString());
                }
                if (this.mContentResolver.update(uri, cv, null, null) != 1) {
                    throw new MmsException("unable to update " + uri.toString());
                }
            }
            isDrm = DownloadDrmHelper.isDrmConvertNeeded(contentType);
            if (isDrm) {
                if (uri != null) {
                    try {
                        path = convertUriToPath(this.mContext, uri);
                        if (new File(path).length() > 0) {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "IOException while closing: " + os, e);
                                }
                            }
                            if (is != null) {
                                try {
                                    is.close();
                                } catch (IOException e2) {
                                    Log.e(TAG, "IOException while closing: " + is, e2);
                                }
                            }
                            if (!(drmConvertSession == null || path == null)) {
                                drmConvertSession.close(path);
                            }
                            if (isDrm) {
                                f = new File(path);
                                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f.getName()), new ContentValues(0), null, null);
                                return;
                            }
                            return;
                        }
                    } catch (Exception e3) {
                        Log.e(TAG, "Can't get file info for: " + part.getDataUri(), e3);
                    }
                }
                if (!hasVendorDrmEngine) {
                    drmConvertSession = DrmConvertSession.open(this.mContext, contentType);
                    if (drmConvertSession == null) {
                        throw new MmsException("Mimetype " + contentType + " can not be converted.");
                    }
                }
            }
            os = this.mContentResolver.openOutputStream(uri);
            if (os == null) {
                throw new MmsException("unable to open output stream " + uri.toString());
            } else if (data == null) {
                dataUri = part.getDataUri();
                if (dataUri != null && dataUri != uri) {
                    if (preOpenedFiles != null) {
                        if (preOpenedFiles.containsKey(dataUri)) {
                            is = (InputStream) preOpenedFiles.get(dataUri);
                        }
                    }
                    if (is == null) {
                        is = this.mContentResolver.openInputStream(dataUri);
                    }
                    byte[] buffer = new byte[SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT];
                    while (true) {
                        int len = is.read(buffer);
                        if (len == -1) {
                            break;
                        } else if (!isDrm || hasVendorDrmEngine) {
                            os.write(buffer, 0, len);
                        } else {
                            convertedData = drmConvertSession.convert(buffer, len);
                            if (convertedData != null) {
                                os.write(convertedData, 0, convertedData.length);
                            } else {
                                throw new MmsException("Error converting drm data.");
                            }
                        }
                    }
                }
                Log.w(TAG, "Can't find data for this part.");
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e22) {
                        Log.e(TAG, "IOException while closing: " + os, e22);
                    }
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e222) {
                        Log.e(TAG, "IOException while closing: " + is, e222);
                    }
                }
                if (!(drmConvertSession == null || path == null)) {
                    drmConvertSession.close(path);
                }
                if (isDrm) {
                    f = new File(path);
                    SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f.getName()), new ContentValues(0), null, null);
                    return;
                }
                return;
            } else if (!isDrm || hasVendorDrmEngine) {
                os.write(data);
            } else {
                dataUri = uri;
                convertedData = drmConvertSession.convert(data, data.length);
                if (convertedData != null) {
                    os.write(convertedData, 0, convertedData.length);
                } else {
                    throw new MmsException("Error converting drm data.");
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e2222) {
                    Log.e(TAG, "IOException while closing: " + os, e2222);
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e22222) {
                    Log.e(TAG, "IOException while closing: " + is, e22222);
                }
            }
            if (!(drmConvertSession == null || path == null)) {
                drmConvertSession.close(path);
            }
            if (isDrm) {
                f = new File(path);
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f.getName()), new ContentValues(0), null, null);
            }
        } catch (Throwable e4) {
            Log.e(TAG, "Failed to open Input/Output stream.", e4);
            throw new MmsException(e4);
        } catch (Throwable e42) {
            Log.e(TAG, "Failed to read/write data.", e42);
            throw new MmsException(e42);
        } catch (Throwable th) {
            Throwable th2 = th;
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e222222) {
                    Log.e(TAG, "IOException while closing: " + os, e222222);
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e2222222) {
                    Log.e(TAG, "IOException while closing: " + is, e2222222);
                }
            }
            if (!(drmConvertSession == null || path == null)) {
                drmConvertSession.close(path);
            }
            if (isDrm) {
                f = new File(path);
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/resetFilePerm/" + f.getName()), new ContentValues(0), null, null);
            }
        }
    }

    public static String convertUriToPath(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("") || scheme.equals("file")) {
            return uri.getPath();
        }
        if (scheme.equals("http")) {
            return uri.toString();
        }
        if (scheme.equals("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, new String[]{Part._DATA}, null, null, null);
                if (cursor == null || cursor.getCount() == 0 || !cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Given Uri could not be found in media store");
                }
                String path = cursor.getString(cursor.getColumnIndexOrThrow(Part._DATA));
                if (cursor == null) {
                    return path;
                }
                cursor.close();
                return path;
            } catch (SQLiteException e) {
                throw new IllegalArgumentException("Given Uri is not formatted in a way so that it can be found in media store.");
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            throw new IllegalArgumentException("Given Uri scheme is not supported");
        }
    }

    private void updateAddress(long msgId, int type, EncodedStringValue[] array) {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + msgId + "/addr"), "type=" + type, null);
        if (array != null) {
            persistAddress(msgId, type, array);
        }
    }

    public void updateHeaders(Uri uri, SendReq sendReq) {
        updateHeaders(uri, sendReq, 0);
    }

    public void updateHeaders(Uri uri, SendReq sendReq, int simSlot) {
        synchronized (PDU_CACHE_INSTANCE) {
            if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                try {
                    PDU_CACHE_INSTANCE.wait();
                } catch (Throwable e) {
                    Log.e(TAG, "updateHeaders: ", e);
                }
            }
        }
        PDU_CACHE_INSTANCE.purge(uri);
        ContentValues values = new ContentValues(12);
        byte[] contentType = sendReq.getContentType();
        if (contentType != null) {
            values.put(BaseMmsColumns.CONTENT_TYPE, toIsoString(contentType));
        }
        long date = sendReq.getDate();
        if (date != -1) {
            values.put("date", Long.valueOf(date));
        }
        int deliveryReport = sendReq.getDeliveryReport();
        if (deliveryReport != 0) {
            values.put(BaseMmsColumns.DELIVERY_REPORT, Integer.valueOf(deliveryReport));
        }
        long deliveryTime = sendReq.getDeliveryTime();
        if (deliveryTime != -1) {
            values.put(BaseMmsColumns.DELIVERY_TIME, Long.valueOf(deliveryTime));
        }
        long expiry = sendReq.getExpiry();
        if (expiry != -1) {
            values.put(BaseMmsColumns.EXPIRY, Long.valueOf(expiry));
        }
        byte[] msgClass = sendReq.getMessageClass();
        if (msgClass != null) {
            values.put(BaseMmsColumns.MESSAGE_CLASS, toIsoString(msgClass));
        }
        int priority = sendReq.getPriority();
        if (priority != 0) {
            values.put("pri", Integer.valueOf(priority));
        }
        int readReport = sendReq.getReadReport();
        if (readReport != 0) {
            values.put(BaseMmsColumns.READ_REPORT, Integer.valueOf(readReport));
        }
        byte[] transId = sendReq.getTransactionId();
        if (transId != null) {
            values.put(BaseMmsColumns.TRANSACTION_ID, toIsoString(transId));
        }
        EncodedStringValue subject = sendReq.getSubject();
        if (subject != null) {
            values.put(BaseMmsColumns.SUBJECT, toIsoString(subject.getTextString()));
            values.put(BaseMmsColumns.SUBJECT_CHARSET, Integer.valueOf(subject.getCharacterSet()));
        } else {
            values.put(BaseMmsColumns.SUBJECT, "");
        }
        long messageSize = sendReq.getMessageSize();
        if (messageSize > 0) {
            values.put(BaseMmsColumns.MESSAGE_SIZE, Long.valueOf(messageSize));
        }
        PduHeaders headers = sendReq.getPduHeaders();
        HashSet<String> recipients = new HashSet();
        for (int addrType : ADDRESS_FIELDS) {
            EncodedStringValue[] array = null;
            if (addrType == 137) {
                if (headers.getEncodedStringValue(addrType) != null) {
                    array = new EncodedStringValue[]{headers.getEncodedStringValue(addrType)};
                }
            } else {
                array = headers.getEncodedStringValues(addrType);
            }
            if (array != null) {
                updateAddress(ContentUris.parseId(uri), addrType, array);
                if (this.mCscFeature.getString("CscFeature_Message_ConfigOpGroupMsg").equals("VZW")) {
                    if (addrType == 151 || addrType == 130 || addrType == 129) {
                        for (EncodedStringValue v : array) {
                            if (v != null) {
                                recipients.add(v.getString());
                            }
                        }
                    }
                } else if (addrType == 151) {
                    for (EncodedStringValue v2 : array) {
                        if (v2 != null) {
                            recipients.add(v2.getString());
                        }
                    }
                }
            }
        }
        if (!recipients.isEmpty()) {
            values.put("thread_id", Long.valueOf(Threads.getOrCreateThreadId(this.mContext, (Set) recipients, simSlot)));
        }
        String imsi = null;
        long[] mSubID = SubscriptionManager.getSubId(simSlot);
        if (mSubID != null && mSubID.length > 0) {
            imsi = this.mTelephonyManager.getSubscriberId(mSubID[0]);
        }
        if (imsi != null) {
            values.put("sim_imsi", imsi);
        }
        values.put("sim_slot", Integer.valueOf(simSlot));
        long reserved = sendReq.getReserved();
        if (reserved != -1) {
            values.put("reserved", Long.valueOf(reserved));
        }
        SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
    }

    private void updatePart(Uri uri, PduPart part, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        ContentValues values = new ContentValues(7);
        int charset = part.getCharset();
        if (charset != 0) {
            values.put(Part.CHARSET, Integer.valueOf(charset));
        }
        if (part.getContentType() != null) {
            String contentType = toIsoString(part.getContentType());
            values.put(Part.CONTENT_TYPE, contentType);
            if (part.getFilename() != null) {
                values.put(Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getName() != null) {
                values.put("name", new String(part.getName()));
            }
            if (part.getContentDisposition() != null) {
                values.put(Part.CONTENT_DISPOSITION, toIsoString(part.getContentDisposition()));
            }
            if (part.getContentId() != null) {
                values.put("cid", toIsoString(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Part.CONTENT_LOCATION, toIsoString(part.getContentLocation()));
            }
            SqliteWrapper.update(this.mContext, this.mContentResolver, uri, values, null, null);
            if (part.getData() != null || uri != part.getDataUri()) {
                persistData(part, uri, contentType, preOpenedFiles);
                return;
            }
            return;
        }
        throw new MmsException("MIME type of the part must be set.");
    }

    public void updateParts(Uri uri, PduBody body, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        PduPart pduPart;
        try {
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "updateParts: ", e);
                    }
                    PduCacheEntry cacheEntry = (PduCacheEntry) PDU_CACHE_INSTANCE.get(uri);
                    if (cacheEntry != null) {
                        ((MultimediaMessagePdu) cacheEntry.getPdu()).setBody(body);
                    }
                }
                PDU_CACHE_INSTANCE.setUpdating(uri, true);
            }
            ArrayList<PduPart> toBeCreated = new ArrayList();
            HashMap<Uri, PduPart> toBeUpdated = new HashMap();
            int partsNum = body.getPartsNum();
            StringBuilder filter = new StringBuilder().append('(');
            for (int i = 0; i < partsNum; i++) {
                PduPart part = body.getPart(i);
                Uri partUri = part.getDataUri();
                if (partUri == null || TextUtils.isEmpty(partUri.getAuthority()) || !partUri.getAuthority().startsWith("mms")) {
                    toBeCreated.add(part);
                } else {
                    toBeUpdated.put(partUri, part);
                    if (filter.length() > 1) {
                        filter.append(" AND ");
                    }
                    filter.append("_id");
                    filter.append("!=");
                    DatabaseUtils.appendEscapedSQLString(filter, partUri.getLastPathSegment());
                }
            }
            filter.append(')');
            long msgId = ContentUris.parseId(uri);
            pduPart = this.mContext;
            SqliteWrapper.delete(pduPart, this.mContentResolver, Uri.parse(Mms.CONTENT_URI + "/" + msgId + "/part"), filter.length() > 2 ? filter.toString() : null, null);
            Iterator i$ = toBeCreated.iterator();
            while (i$.hasNext()) {
                persistPart((PduPart) i$.next(), msgId, preOpenedFiles);
            }
            for (Entry<Uri, PduPart> e2 : toBeUpdated.entrySet()) {
                pduPart = (PduPart) e2.getValue();
                updatePart((Uri) e2.getKey(), pduPart, preOpenedFiles);
            }
            PDU_CACHE_INSTANCE.setUpdating(uri, false);
            PDU_CACHE_INSTANCE.notifyAll();
        } finally {
            pduPart = PDU_CACHE_INSTANCE;
            synchronized (pduPart) {
                PDU_CACHE_INSTANCE.setUpdating(uri, false);
                PDU_CACHE_INSTANCE.notifyAll();
            }
        }
    }

    public Uri persist(GenericPdu pdu, Uri uri, int reqAppId, int reqMsgId) throws MmsException {
        return persist(pdu, 0, uri, reqAppId, reqMsgId, null);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri, int reqAppId, int reqMsgId) throws MmsException {
        return persist(pdu, simSlot, uri, reqAppId, reqMsgId, null);
    }

    public Uri persist(GenericPdu pdu, Uri uri, int reqAppId, int reqMsgId, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        return persist(pdu, 0, uri, reqAppId, reqMsgId, (HashMap) preOpenedFiles);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri, int reqAppId, int reqMsgId, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        if (uri == null) {
            throw new MmsException("Uri may not be null.");
        } else if (((Integer) MESSAGE_BOX_MAP.get(uri)) == null) {
            throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
        } else {
            EncodedStringValue[] array;
            PDU_CACHE_INSTANCE.purge(uri);
            PduHeaders header = pdu.getPduHeaders();
            ContentValues values = new ContentValues();
            for (Entry<Integer, String> e : ENCODED_STRING_COLUMN_NAME_MAP.entrySet()) {
                int field = ((Integer) e.getKey()).intValue();
                EncodedStringValue encodedString = header.getEncodedStringValue(field);
                if (encodedString != null) {
                    String charsetColumn = (String) CHARSET_COLUMN_NAME_MAP.get(Integer.valueOf(field));
                    values.put((String) e.getValue(), toIsoString(encodedString.getTextString()));
                    values.put(charsetColumn, Integer.valueOf(encodedString.getCharacterSet()));
                }
            }
            for (Entry<Integer, String> e2 : TEXT_STRING_COLUMN_NAME_MAP.entrySet()) {
                byte[] text = header.getTextString(((Integer) e2.getKey()).intValue());
                if (text != null) {
                    values.put((String) e2.getValue(), toIsoString(text));
                }
            }
            for (Entry<Integer, String> e22 : OCTET_COLUMN_NAME_MAP.entrySet()) {
                int b = header.getOctet(((Integer) e22.getKey()).intValue());
                if (b != 0) {
                    values.put((String) e22.getValue(), Integer.valueOf(b));
                }
            }
            for (Entry<Integer, String> e222 : LONG_COLUMN_NAME_MAP.entrySet()) {
                long l = header.getLongInteger(((Integer) e222.getKey()).intValue());
                if (l != -1) {
                    values.put((String) e222.getValue(), Long.valueOf(l));
                }
            }
            HashMap<Integer, EncodedStringValue[]> addressMap = new HashMap(ADDRESS_FIELDS.length);
            for (int addrType : ADDRESS_FIELDS) {
                array = null;
                if (addrType == 137) {
                    if (header.getEncodedStringValue(addrType) != null) {
                        array = new EncodedStringValue[]{header.getEncodedStringValue(addrType)};
                    }
                } else {
                    array = header.getEncodedStringValues(addrType);
                }
                addressMap.put(Integer.valueOf(addrType), array);
            }
            HashSet<String> recipients = new HashSet();
            long threadId = DUMMY_THREAD_ID;
            int msgType = pdu.getMessageType();
            String myNumber = TelephonyManager.getDefault().getLine1Number();
            if (msgType == 130 || msgType == 132 || msgType == 128) {
                array = null;
                if (this.mCscFeature.getString("CscFeature_Message_ConfigOpGroupMsg").equals("VZW") && myNumber != null) {
                    switch (msgType) {
                        case 128:
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(151));
                            if (array != null) {
                                for (EncodedStringValue v : array) {
                                    if (v != null) {
                                        recipients.add(v.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(130));
                            if (array != null) {
                                for (EncodedStringValue v2 : array) {
                                    if (v2 != null) {
                                        recipients.add(v2.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(129));
                            if (array != null) {
                                for (EncodedStringValue v22 : array) {
                                    if (v22 != null) {
                                        recipients.add(v22.getString());
                                    }
                                }
                                break;
                            }
                            break;
                        case 130:
                        case 132:
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(137));
                            if (array != null) {
                                for (EncodedStringValue v222 : array) {
                                    if (v222 != null) {
                                        recipients.add(v222.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(151));
                            if (array != null) {
                                for (EncodedStringValue v2222 : array) {
                                    Log.secD("MmsDebug", " Compare against To" + v2222.getString());
                                    if (v2222 != null) {
                                        if (myNumber.compareTo(v2222.getString()) != 0) {
                                            recipients.add(v2222.getString());
                                        }
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(130));
                            if (array != null) {
                                for (EncodedStringValue v22222 : array) {
                                    Log.secD("MmsDebug", " Compare against Cc" + v22222.getString());
                                    if (v22222 != null) {
                                        if (myNumber.compareTo(v22222.getString()) != 0) {
                                            recipients.add(v22222.getString());
                                        }
                                    }
                                }
                                break;
                            }
                            break;
                    }
                }
                switch (msgType) {
                    case 128:
                        array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(151));
                        break;
                    case 130:
                    case 132:
                        array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(137));
                        break;
                    default:
                        break;
                }
                if (array != null) {
                    for (EncodedStringValue v222222 : array) {
                        if (v222222 != null) {
                            recipients.add(v222222.getString());
                        }
                    }
                }
                threadId = Threads.getOrCreateThreadId(this.mContext, (Set) recipients);
            }
            values.put("thread_id", Long.valueOf(threadId));
            String imsi = this.mTelephonyManager.getSubscriberId(SubscriptionManager.getSubId(simSlot)[0]);
            if (imsi != null) {
                values.put("sim_imsi", imsi);
            }
            values.put("sim_slot", Integer.valueOf(simSlot));
            long dummyId = System.nanoTime();
            if (pdu instanceof MultimediaMessagePdu) {
                PduBody body = ((MultimediaMessagePdu) pdu).getBody();
                if (body != null) {
                    int partsNum = body.getPartsNum();
                    for (int i = 0; i < partsNum; i++) {
                        persistPart(body.getPart(i), dummyId, preOpenedFiles);
                    }
                }
            }
            if (reqAppId > 0) {
                values.put("app_id", Integer.valueOf(reqAppId));
                values.put("msg_id", Integer.valueOf(reqMsgId));
            }
            Uri res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
            if (res == null) {
                throw new MmsException("persist() failed: return null.");
            }
            long msgId = ContentUris.parseId(res);
            values = new ContentValues(1);
            values.put(Part.MSG_ID, Long.valueOf(msgId));
            SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + dummyId + "/part"), values, null, null);
            res = Uri.parse(uri + "/" + msgId);
            for (int addrType2 : ADDRESS_FIELDS) {
                array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(addrType2));
                if (array != null) {
                    persistAddress(msgId, addrType2, array);
                }
            }
            return res;
        }
    }

    public Uri persist(GenericPdu pdu, Uri uri) throws MmsException {
        return persist(pdu, 0, uri, true, false, null, false, false);
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean bSpam) throws MmsException {
        return persist(pdu, 0, uri, true, false, null, bSpam, true);
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        return persist(pdu, 0, uri, createThreadId, groupMmsEnabled, preOpenedFiles, false, false);
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles, boolean bSpam) throws MmsException {
        return persist(pdu, 0, uri, createThreadId, groupMmsEnabled, preOpenedFiles, bSpam, true);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles) throws MmsException {
        return persist(pdu, simSlot, uri, createThreadId, groupMmsEnabled, preOpenedFiles, false, false);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri) throws MmsException {
        return persist(pdu, simSlot, uri, true, false, null, false, true);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri, boolean bSpam) throws MmsException {
        return persist(pdu, simSlot, uri, true, false, null, bSpam, true);
    }

    public Uri persist(GenericPdu pdu, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles, boolean bSpam, boolean hasVendorDrmEngine) throws MmsException {
        return persist(pdu, 0, uri, createThreadId, groupMmsEnabled, preOpenedFiles, bSpam, hasVendorDrmEngine);
    }

    public Uri persist(GenericPdu pdu, int simSlot, Uri uri, boolean createThreadId, boolean groupMmsEnabled, HashMap<Uri, InputStream> preOpenedFiles, boolean bSpam, boolean hasVendorDrmEngine) throws MmsException {
        if (uri == null) {
            throw new MmsException("Uri may not be null.");
        }
        long msgId = -1;
        try {
            msgId = ContentUris.parseId(uri);
        } catch (NumberFormatException e) {
        }
        boolean existingUri = msgId != -1;
        if (existingUri || MESSAGE_BOX_MAP.get(uri) != null) {
            EncodedStringValue[] array;
            Uri res;
            synchronized (PDU_CACHE_INSTANCE) {
                if (PDU_CACHE_INSTANCE.isUpdating(uri)) {
                    try {
                        PDU_CACHE_INSTANCE.wait();
                    } catch (Throwable e2) {
                        Log.e(TAG, "persist1: ", e2);
                    }
                }
            }
            PDU_CACHE_INSTANCE.purge(uri);
            PduHeaders header = pdu.getPduHeaders();
            ContentValues values = new ContentValues();
            for (Entry<Integer, String> e3 : ENCODED_STRING_COLUMN_NAME_MAP.entrySet()) {
                int field = ((Integer) e3.getKey()).intValue();
                EncodedStringValue encodedString = header.getEncodedStringValue(field);
                if (encodedString != null) {
                    String charsetColumn = (String) CHARSET_COLUMN_NAME_MAP.get(Integer.valueOf(field));
                    values.put((String) e3.getValue(), toIsoString(encodedString.getTextString()));
                    values.put(charsetColumn, Integer.valueOf(encodedString.getCharacterSet()));
                }
            }
            for (Entry<Integer, String> e32 : TEXT_STRING_COLUMN_NAME_MAP.entrySet()) {
                byte[] text = header.getTextString(((Integer) e32.getKey()).intValue());
                if (text != null) {
                    values.put((String) e32.getValue(), toIsoString(text));
                }
            }
            for (Entry<Integer, String> e322 : OCTET_COLUMN_NAME_MAP.entrySet()) {
                int b = header.getOctet(((Integer) e322.getKey()).intValue());
                if (b != 0) {
                    values.put((String) e322.getValue(), Integer.valueOf(b));
                }
            }
            for (Entry<Integer, String> e3222 : LONG_COLUMN_NAME_MAP.entrySet()) {
                long l = header.getLongInteger(((Integer) e3222.getKey()).intValue());
                if (l != -1) {
                    values.put((String) e3222.getValue(), Long.valueOf(l));
                }
            }
            HashMap<Integer, EncodedStringValue[]> hashMap = new HashMap(ADDRESS_FIELDS.length);
            for (int addrType : ADDRESS_FIELDS) {
                Object array2 = null;
                if (addrType == 137) {
                    if (header.getEncodedStringValue(addrType) != null) {
                        array2 = new EncodedStringValue[]{header.getEncodedStringValue(addrType)};
                    }
                } else {
                    array2 = header.getEncodedStringValues(addrType);
                }
                hashMap.put(Integer.valueOf(addrType), array2);
            }
            HashSet<String> recipients = new HashSet();
            long threadId = DUMMY_THREAD_ID;
            int msgType = pdu.getMessageType();
            String myNumber = TelephonyManager.getDefault().getLine1Number();
            if (msgType == 130 || msgType == 132 || msgType == 128) {
                if (this.mCscFeature.getString("CscFeature_Message_ConfigOpGroupMsg").equals("VZW") && myNumber != null) {
                    switch (msgType) {
                        case 128:
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(151));
                            if (array != null) {
                                for (EncodedStringValue v : array) {
                                    if (v != null) {
                                        recipients.add(v.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(130));
                            if (array != null) {
                                for (EncodedStringValue v2 : array) {
                                    if (v2 != null) {
                                        recipients.add(v2.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(129));
                            if (array != null) {
                                for (EncodedStringValue v22 : array) {
                                    if (v22 != null) {
                                        recipients.add(v22.getString());
                                    }
                                }
                                break;
                            }
                            break;
                        case 130:
                        case 132:
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(137));
                            if (array != null) {
                                for (EncodedStringValue v222 : array) {
                                    if (v222 != null) {
                                        recipients.add(v222.getString());
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(151));
                            if (array != null) {
                                for (EncodedStringValue v2222 : array) {
                                    Log.secD("MmsDebug", " Compare against To" + v2222.getString());
                                    if (v2222 != null) {
                                        if (myNumber.compareTo(v2222.getString()) != 0) {
                                            recipients.add(v2222.getString());
                                        }
                                    }
                                }
                            }
                            array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(130));
                            if (array != null) {
                                for (EncodedStringValue v22222 : array) {
                                    Log.secD("MmsDebug", " Compare against Cc" + v22222.getString());
                                    if (v22222 != null) {
                                        if (myNumber.compareTo(v22222.getString()) != 0) {
                                            recipients.add(v22222.getString());
                                        }
                                    }
                                }
                                break;
                            }
                            break;
                    }
                }
                switch (msgType) {
                    case 128:
                        loadRecipients(151, recipients, hashMap, false);
                        break;
                    case 130:
                    case 132:
                        loadRecipients(137, recipients, hashMap, false);
                        if (groupMmsEnabled) {
                            loadRecipients(151, recipients, hashMap, true);
                            loadRecipients(130, recipients, hashMap, true);
                            break;
                        }
                        break;
                    default:
                        break;
                }
                if (createThreadId) {
                    threadId = Threads.getOrCreateThreadId(this.mContext, (Set) recipients);
                }
            }
            if (!bSpam) {
                values.put("thread_id", Long.valueOf(threadId));
            }
            String imsi = null;
            long[] mSubID = SubscriptionManager.getSubId(simSlot);
            if (mSubID != null && mSubID.length > 0) {
                imsi = this.mTelephonyManager.getSubscriberId(mSubID[0]);
            }
            if (imsi != null) {
                values.put("sim_imsi", imsi);
            }
            values.put("sim_slot", Integer.valueOf(simSlot));
            long dummyId = System.currentTimeMillis();
            boolean textOnly = true;
            int messageSize = 0;
            if (pdu instanceof MultimediaMessagePdu) {
                PduBody body = ((MultimediaMessagePdu) pdu).getBody();
                if (body != null) {
                    int partsNum = body.getPartsNum();
                    if (partsNum > 2) {
                        textOnly = false;
                    }
                    for (int i = 0; i < partsNum; i++) {
                        PduPart part = body.getPart(i);
                        messageSize += part.getDataLength();
                        persistPart(part, dummyId, preOpenedFiles, bSpam, hasVendorDrmEngine);
                        String contentType = getPartContentType(part);
                        if (!(contentType == null || ContentType.APP_SMIL.equals(contentType) || ContentType.TEXT_PLAIN.equals(contentType))) {
                            textOnly = false;
                        }
                    }
                }
            }
            values.put(BaseMmsColumns.TEXT_ONLY, Integer.valueOf(textOnly ? 1 : 0));
            if (values.getAsInteger(BaseMmsColumns.MESSAGE_SIZE) == null) {
                values.put(BaseMmsColumns.MESSAGE_SIZE, Integer.valueOf(messageSize));
            }
            if (existingUri) {
                res = uri;
                SqliteWrapper.update(this.mContext, this.mContentResolver, res, values, null, null);
            } else {
                res = SqliteWrapper.insert(this.mContext, this.mContentResolver, uri, values);
                if (res == null) {
                    throw new MmsException("persist() failed: return null.");
                }
                msgId = ContentUris.parseId(res);
            }
            msgId = ContentUris.parseId(res);
            values = new ContentValues(1);
            values.put(Part.MSG_ID, Long.valueOf(msgId));
            if (bSpam) {
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://spammms/" + dummyId + "/spampart"), values, null, null);
            } else {
                SqliteWrapper.update(this.mContext, this.mContentResolver, Uri.parse("content://mms/" + dummyId + "/part"), values, null, null);
            }
            if (!existingUri) {
                res = Uri.parse(uri + "/" + msgId);
            }
            for (int addrType2 : ADDRESS_FIELDS) {
                array = (EncodedStringValue[]) hashMap.get(Integer.valueOf(addrType2));
                if (array != null) {
                    persistAddress(msgId, addrType2, array, bSpam);
                }
            }
            return res;
        }
        throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
    }

    private void loadRecipients(int addressType, HashSet<String> recipients, HashMap<Integer, EncodedStringValue[]> addressMap, boolean excludeMyNumber) {
        EncodedStringValue[] array = (EncodedStringValue[]) addressMap.get(Integer.valueOf(addressType));
        if (array != null) {
            String myNumber = excludeMyNumber ? this.mTelephonyManager.getLine1Number() : null;
            for (EncodedStringValue v : array) {
                if (v != null) {
                    String number = v.getString();
                    if ((myNumber == null || !PhoneNumberUtils.compare(number, myNumber)) && !recipients.contains(number)) {
                        recipients.add(number);
                    }
                }
            }
        }
    }

    public Uri move(Uri from, Uri to) throws MmsException {
        long msgId = ContentUris.parseId(from);
        if (msgId == -1) {
            throw new MmsException("Error! ID of the message: -1.");
        }
        Integer msgBox = (Integer) MESSAGE_BOX_MAP.get(to);
        if (msgBox == null) {
            throw new MmsException("Bad destination, must be one of content://mms/inbox, content://mms/sent, content://mms/drafts, content://mms/outbox, content://mms/temp.");
        }
        ContentValues values = new ContentValues(1);
        values.put(BaseMmsColumns.MESSAGE_BOX, msgBox);
        SqliteWrapper.update(this.mContext, this.mContentResolver, from, values, null, null);
        return ContentUris.withAppendedId(to, msgId);
    }

    public static String toIsoString(byte[] bytes) {
        try {
            return new String(bytes, CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return "";
        }
    }

    public static byte[] getBytes(String data) {
        try {
            return data.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "ISO_8859_1 must be supported!", e);
            return new byte[0];
        }
    }

    public void release() {
        SqliteWrapper.delete(this.mContext, this.mContentResolver, Uri.parse(TEMPORARY_DRM_OBJECT_URI), null, null);
    }

    public Cursor getPendingMessages(long dueTime) {
        Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        String[] selectionArgs = new String[]{String.valueOf(10), String.valueOf(dueTime)};
        return SqliteWrapper.query(this.mContext, this.mContentResolver, uriBuilder.build(), null, "err_type < ? AND due_time <= ?", selectionArgs, PendingMessages.DUE_TIME);
    }

    public Cursor getPendingMessages(int simSlot, long dueTime) {
        Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        String[] selectionArgs = new String[]{String.valueOf(10), String.valueOf(dueTime), String.valueOf(simSlot)};
        return SqliteWrapper.query(this.mContext, this.mContentResolver, uriBuilder.build(), null, "err_type < ? AND due_time <= ? AND sim_slot2 = ?", selectionArgs, PendingMessages.DUE_TIME);
    }
}
