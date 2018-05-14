package android.provider;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.net.Uri.Builder;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Patterns;
import com.android.internal.telephony.SmsApplication;
import com.google.android.mms.pdu.CharacterSets;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Telephony {
    private static final String TAG = "Telephony";

    public interface BaseMmsColumns extends BaseColumns {
        @Deprecated
        public static final String ADAPTATION_ALLOWED = "adp_a";
        @Deprecated
        public static final String APPLIC_ID = "apl_id";
        @Deprecated
        public static final String AUX_APPLIC_ID = "aux_apl_id";
        public static final String CALLBACKTEXT = "callbacktext";
        public static final String CALLBACK_NUMBER = "callback";
        @Deprecated
        public static final String CANCEL_ID = "cl_id";
        @Deprecated
        public static final String CANCEL_STATUS = "cl_st";
        public static final String CONTENT_CLASS = "ct_cls";
        public static final String CONTENT_LOCATION = "ct_l";
        public static final String CONTENT_TYPE = "ct_t";
        public static final String CREATOR = "creator";
        public static final String DATE = "date";
        public static final String DATE_SENT = "date_sent";
        public static final String DELIVERY_REPORT = "d_rpt";
        public static final String DELIVERY_TIME = "d_tm";
        @Deprecated
        public static final String DELIVERY_TIME_TOKEN = "d_tm_tok";
        @Deprecated
        public static final String DISTRIBUTION_INDICATOR = "d_ind";
        @Deprecated
        public static final String DRM_CONTENT = "drm_c";
        @Deprecated
        public static final String ELEMENT_DESCRIPTOR = "e_des";
        public static final String EXPIRY = "exp";
        @Deprecated
        public static final String LIMIT = "limit";
        public static final String LOCKED = "locked";
        @Deprecated
        public static final String MBOX_QUOTAS = "mb_qt";
        @Deprecated
        public static final String MBOX_QUOTAS_TOKEN = "mb_qt_tok";
        @Deprecated
        public static final String MBOX_TOTALS = "mb_t";
        @Deprecated
        public static final String MBOX_TOTALS_TOKEN = "mb_t_tok";
        public static final String MESSAGE_BOX = "msg_box";
        public static final int MESSAGE_BOX_ALL = 0;
        public static final int MESSAGE_BOX_DRAFTS = 3;
        public static final int MESSAGE_BOX_FAILED = 5;
        public static final int MESSAGE_BOX_INBOX = 1;
        public static final int MESSAGE_BOX_OUTBOX = 4;
        public static final int MESSAGE_BOX_SENT = 2;
        public static final String MESSAGE_CLASS = "m_cls";
        @Deprecated
        public static final String MESSAGE_COUNT = "m_cnt";
        public static final String MESSAGE_ID = "m_id";
        public static final String MESSAGE_SIZE = "m_size";
        public static final String MESSAGE_TYPE = "m_type";
        public static final String MMS_VERSION = "v";
        @Deprecated
        public static final String MM_FLAGS = "mm_flg";
        @Deprecated
        public static final String MM_FLAGS_TOKEN = "mm_flg_tok";
        @Deprecated
        public static final String MM_STATE = "mm_st";
        @Deprecated
        public static final String PREVIOUSLY_SENT_BY = "p_s_by";
        @Deprecated
        public static final String PREVIOUSLY_SENT_DATE = "p_s_d";
        public static final String PRIORITY = "pri";
        @Deprecated
        public static final String QUOTAS = "qt";
        public static final String READ = "read";
        public static final String READ_REPORT = "rr";
        public static final String READ_STATUS = "read_status";
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE = "r_r_mod";
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE_TEXT = "r_r_mod_txt";
        @Deprecated
        public static final String REPLACE_ID = "repl_id";
        @Deprecated
        public static final String REPLY_APPLIC_ID = "r_apl_id";
        @Deprecated
        public static final String REPLY_CHARGING = "r_chg";
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE = "r_chg_dl";
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE_TOKEN = "r_chg_dl_tok";
        @Deprecated
        public static final String REPLY_CHARGING_ID = "r_chg_id";
        @Deprecated
        public static final String REPLY_CHARGING_SIZE = "r_chg_sz";
        public static final String REPORT_ALLOWED = "rpt_a";
        public static final String RESERVED = "reserved";
        public static final String RESPONSE_STATUS = "resp_st";
        public static final String RESPONSE_TEXT = "resp_txt";
        public static final String RETRIEVE_STATUS = "retr_st";
        public static final String RETRIEVE_TEXT = "retr_txt";
        public static final String RETRIEVE_TEXT_CHARSET = "retr_txt_cs";
        public static final String SEEN = "seen";
        @Deprecated
        public static final String SENDER_VISIBILITY = "s_vis";
        public static final String SIGNATURE = "signature";
        public static final String SIGNATURETEXT = "signaturetext";
        public static final String SIM_IMSI = "sim_imsi";
        public static final String SIM_SLOT = "sim_slot";
        @Deprecated
        public static final String START = "start";
        public static final String STATUS = "st";
        @Deprecated
        public static final String STATUS_TEXT = "st_txt";
        @Deprecated
        public static final String STORE = "store";
        @Deprecated
        public static final String STORED = "stored";
        @Deprecated
        public static final String STORE_STATUS = "store_st";
        @Deprecated
        public static final String STORE_STATUS_TEXT = "store_st_txt";
        public static final String SUBJECT = "sub";
        public static final String SUBJECT_CHARSET = "sub_cs";
        public static final String SUB_ID = "sub_id";
        public static final String TEXT_ONLY = "text_only";
        public static final String THREAD_ID = "thread_id";
        @Deprecated
        public static final String TOTALS = "totals";
        public static final String TRANSACTION_ID = "tr_id";
    }

    public interface CanonicalAddressesColumns extends BaseColumns {
        public static final String ADDRESS = "address";
    }

    public static final class Carriers implements BaseColumns {
        public static final String APN = "apn";
        public static final String AUTH_TYPE = "authtype";
        public static final String BEARER = "bearer";
        public static final String CARRIER_ENABLED = "carrier_enabled";
        public static final String CARRIER_ENABLED_ROAMING = "carrier_enabled_roaming";
        public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
        public static final String CURRENT = "current";
        public static final String DATABEARER = "databearer";
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final String MAX_CONNS = "max_conns";
        public static final String MAX_CONNS_TIME = "max_conns_time";
        public static final String MCC = "mcc";
        public static final String MMSC = "mmsc";
        public static final String MMSPORT = "mmsport";
        public static final String MMSPROXY = "mmsproxy";
        public static final String MNC = "mnc";
        public static final String MODEM_COGNITIVE = "modem_cognitive";
        public static final String MTU = "mtu";
        public static final String MVNO_MATCH_DATA = "mvno_match_data";
        public static final String MVNO_TYPE = "mvno_type";
        public static final String NAME = "name";
        public static final String NETWORK_FLAG = "network_flag";
        public static final String NUMERIC = "numeric";
        public static final String PASSWORD = "password";
        public static final String PORT = "port";
        public static final String PPP_DIGIT = "ppp_digit";
        public static final String PRESET_FLAG = "preset_flag";
        public static final String PROFILE_ID = "profile_id";
        public static final String PROTOCOL = "protocol";
        public static final String PROXY = "proxy";
        public static final String ROAMING_PROTOCOL = "roaming_protocol";
        public static final String SERVER = "server";
        public static final String SUB_ID = "sub_id";
        public static final String TYPE = "type";
        public static final String USER = "user";
        public static final String WAIT_TIME = "wait_time";

        private Carriers() {
        }
    }

    public static final class CellBroadcasts implements BaseColumns {
        public static final String CID = "cid";
        public static final String CMAS_CATEGORY = "cmas_category";
        public static final String CMAS_CERTAINTY = "cmas_certainty";
        public static final String CMAS_MESSAGE_CLASS = "cmas_message_class";
        public static final String CMAS_RESPONSE_TYPE = "cmas_response_type";
        public static final String CMAS_SEVERITY = "cmas_severity";
        public static final String CMAS_URGENCY = "cmas_urgency";
        public static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts");
        public static final String DEFAULT_SORT_ORDER = "date DESC";
        public static final String DELIVERY_TIME = "date";
        public static final String ETWS_WARNING_TYPE = "etws_warning_type";
        public static final String GEOGRAPHICAL_SCOPE = "geo_scope";
        public static final String LAC = "lac";
        public static final String LANGUAGE_CODE = "language";
        public static final String MESSAGE_BODY = "body";
        public static final String MESSAGE_FORMAT = "format";
        public static final String MESSAGE_PRIORITY = "priority";
        public static final String MESSAGE_READ = "read";
        public static final String PLMN = "plmn";
        public static final String[] QUERY_COLUMNS = new String[]{"_id", GEOGRAPHICAL_SCOPE, PLMN, LAC, "cid", SERIAL_NUMBER, SERVICE_CATEGORY, LANGUAGE_CODE, "body", "date", "read", MESSAGE_FORMAT, MESSAGE_PRIORITY, ETWS_WARNING_TYPE, CMAS_MESSAGE_CLASS, CMAS_CATEGORY, CMAS_RESPONSE_TYPE, CMAS_SEVERITY, CMAS_URGENCY, CMAS_CERTAINTY};
        public static final String SERIAL_NUMBER = "serial_number";
        public static final String SERVICE_CATEGORY = "service_category";
        public static final String V1_MESSAGE_CODE = "message_code";
        public static final String V1_MESSAGE_IDENTIFIER = "message_id";

        private CellBroadcasts() {
        }
    }

    public static final class Mms implements BaseMmsColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://mms");
        public static final String DEFAULT_SORT_ORDER = "date DESC";
        public static final Pattern NAME_ADDR_EMAIL_PATTERN = Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");
        public static final Uri REPORT_REQUEST_URI = Uri.withAppendedPath(CONTENT_URI, "report-request");
        public static final Uri REPORT_STATUS_URI = Uri.withAppendedPath(CONTENT_URI, "report-status");

        public static final class Addr implements BaseColumns {
            public static final String ADDRESS = "address";
            public static final String CHARSET = "charset";
            public static final String CONTACT_ID = "contact_id";
            public static final String MSG_ID = "msg_id";
            public static final String TYPE = "type";

            private Addr() {
            }
        }

        public static final class Draft implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/drafts");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Draft() {
            }
        }

        public static final class Inbox implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Inbox() {
            }
        }

        public static final class Intents {
            public static final String CONTENT_CHANGED_ACTION = "android.intent.action.CONTENT_CHANGED";
            public static final String DELETED_CONTENTS = "deleted_contents";
            public static final String EXTRA_MMS_CONTENT_URI = "android.provider.Telephony.extra.MMS_CONTENT_URI";
            public static final String EXTRA_MMS_LOCATION_URL = "android.provider.Telephony.extra.MMS_LOCATION_URL";
            public static final String MMS_DOWNLOAD_ACTION = "android.provider.Telephony.MMS_DOWNLOAD";
            public static final String MMS_SEND_ACTION = "android.provider.Telephony.MMS_SEND";

            private Intents() {
            }
        }

        public static final class Outbox implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Outbox() {
            }
        }

        public static final class Part implements BaseColumns {
            public static final String CHARSET = "chset";
            public static final String CONTENT_DISPOSITION = "cd";
            public static final String CONTENT_ID = "cid";
            public static final String CONTENT_LOCATION = "cl";
            public static final String CONTENT_TYPE = "ct";
            public static final String CT_START = "ctt_s";
            public static final String CT_TYPE = "ctt_t";
            public static final String FILENAME = "fn";
            public static final String MSG_ID = "mid";
            public static final String NAME = "name";
            public static final String SEQ = "seq";
            public static final String TEXT = "text";
            public static final String _DATA = "_data";

            private Part() {
            }
        }

        public static final class Rate {
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "rate");
            public static final String SENT_TIME = "sent_time";

            private Rate() {
            }
        }

        public static final class Sent implements BaseMmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://mms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Sent() {
            }
        }

        private Mms() {
        }

        public static Cursor query(ContentResolver cr, String[] projection) {
            return cr.query(CONTENT_URI, projection, null, null, "date DESC");
        }

        public static Cursor query(ContentResolver cr, String[] projection, String where, String orderBy) {
            return cr.query(CONTENT_URI, projection, where, null, orderBy == null ? "date DESC" : orderBy);
        }

        public static String extractAddrSpec(String address) {
            Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);
            if (match.matches()) {
                return match.group(2);
            }
            return address;
        }

        public static boolean isEmailAddress(String address) {
            if (TextUtils.isEmpty(address)) {
                return false;
            }
            return Patterns.EMAIL_ADDRESS.matcher(extractAddrSpec(address)).matches();
        }

        public static boolean isPhoneNumber(String number) {
            if (TextUtils.isEmpty(number)) {
                return false;
            }
            return Patterns.PHONE.matcher(number).matches();
        }
    }

    public static final class MmsSms implements BaseColumns {
        public static final Uri CONTENT_CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations");
        public static final Uri CONTENT_DRAFT_URI = Uri.parse("content://mms-sms/draft");
        public static final Uri CONTENT_FILTER_BYPHONE_URI = Uri.parse("content://mms-sms/messages/byphone");
        public static final Uri CONTENT_LOCKED_URI = Uri.parse("content://mms-sms/locked");
        public static final Uri CONTENT_UNDELIVERED_URI = Uri.parse("content://mms-sms/undelivered");
        public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");
        public static final int ERR_TYPE_GENERIC = 1;
        public static final int ERR_TYPE_GENERIC_PERMANENT = 10;
        public static final int ERR_TYPE_MMS_PROTO_PERMANENT = 12;
        public static final int ERR_TYPE_MMS_PROTO_TRANSIENT = 3;
        public static final int ERR_TYPE_SMS_PROTO_PERMANENT = 11;
        public static final int ERR_TYPE_SMS_PROTO_TRANSIENT = 2;
        public static final int ERR_TYPE_TRANSPORT_FAILURE = 4;
        public static final int MMS_PROTO = 1;
        public static final int NO_ERROR = 0;
        public static final Uri SEARCH_URI = Uri.parse("content://mms-sms/search");
        public static final int SMS_PROTO = 0;
        public static final String TYPE_DISCRIMINATOR_COLUMN = "transport_type";

        public static final class PendingMessages implements BaseColumns {
            public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "pending");
            public static final String DUE_TIME = "due_time";
            public static final String ERROR_CODE = "err_code";
            public static final String ERROR_TYPE = "err_type";
            public static final String LAST_TRY = "last_try";
            public static final String MSG_ID = "msg_id";
            public static final String MSG_TYPE = "msg_type";
            public static final String PROTO_TYPE = "proto_type";
            public static final String RETRY_INDEX = "retry_index";
            public static final String SIM_SLOT = "sim_slot2";
            public static final String SUB_ID = "pending_sub_id";

            private PendingMessages() {
            }
        }

        public static final class WordsTable {
            public static final String ID = "_id";
            public static final String INDEXED_TEXT = "index_text";
            public static final String SOURCE_ROW_ID = "source_id";
            public static final String TABLE_ID = "table_to_use";

            private WordsTable() {
            }
        }

        private MmsSms() {
        }
    }

    public interface TextBasedSmsColumns {
        public static final String ADDRESS = "address";
        public static final String BODY = "body";
        public static final String CALLBACK = "callback";
        public static final String CREATOR = "creator";
        public static final String DATE = "date";
        public static final String DATE_SENT = "date_sent";
        public static final String ERROR_CODE = "error_code";
        public static final String LOCKED = "locked";
        public static final int MESSAGE_TYPE_ALL = 0;
        public static final int MESSAGE_TYPE_DRAFT = 3;
        public static final int MESSAGE_TYPE_FAILED = 5;
        public static final int MESSAGE_TYPE_INBOX = 1;
        public static final int MESSAGE_TYPE_OUTBOX = 4;
        public static final int MESSAGE_TYPE_QUEUED = 6;
        public static final int MESSAGE_TYPE_SENT = 2;
        public static final String MTU = "mtu";
        public static final String PERSON = "person";
        public static final String PRIORITY = "pri";
        public static final String PROTOCOL = "protocol";
        public static final String READ = "read";
        public static final String REPLY_PATH_PRESENT = "reply_path_present";
        public static final String RESERVED = "reserved";
        public static final String SEEN = "seen";
        public static final String SERVICE_CENTER = "service_center";
        public static final String SIGNATURE = "signature";
        public static final String SIGNATURETEXT = "signaturetext";
        public static final String SIM_IMSI = "sim_imsi";
        public static final String SIM_SLOT = "sim_slot";
        public static final String STATUS = "status";
        public static final int STATUS_COMPLETE = 0;
        public static final int STATUS_EXPIRED = 70;
        public static final int STATUS_FAILED = 64;
        public static final int STATUS_NONE = -1;
        public static final int STATUS_PENDING = 32;
        public static final String SUBJECT = "subject";
        public static final String SUB_ID = "sub_id";
        public static final String THREAD_ID = "thread_id";
        public static final String TYPE = "type";
    }

    public static final class Sms implements BaseColumns, TextBasedSmsColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://sms");
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        public static final class Conversations implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/conversations");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
            public static final String MESSAGE_COUNT = "msg_count";
            public static final String SNIPPET = "snippet";

            private Conversations() {
            }
        }

        public static final class Draft implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/draft");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Draft() {
            }

            public static Uri addMessage(ContentResolver resolver, String address, String body, String subject, Long date) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, CONTENT_URI, address, body, subject, date, true, false);
            }

            public static Uri addMessage(long subId, ContentResolver resolver, String address, String body, String subject, Long date) {
                return Sms.addMessageToUri(subId, resolver, CONTENT_URI, address, body, subject, date, true, false);
            }
        }

        public static final class Inbox implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Inbox() {
            }

            public static Uri addMessage(ContentResolver resolver, String address, String body, String subject, Long date, boolean read) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, CONTENT_URI, address, body, subject, date, read, false);
            }

            public static Uri addMessage(long subId, ContentResolver resolver, String address, String body, String subject, Long date, boolean read) {
                return Sms.addMessageToUri(subId, resolver, CONTENT_URI, address, body, subject, date, read, false);
            }
        }

        public static final class Intents {
            public static final String ACTION_CHANGE_DEFAULT = "android.provider.Telephony.ACTION_CHANGE_DEFAULT";
            public static final String ACTION_KTLBS_DATA_SMS_RECEIVED = "com.kt.location.action.KTLBS_DATA_SMS_RECEIVED";
            public static final String CB_RECEIVED_ACTION = "android.provider.Telephony.CB_RECEIVED";
            public static final String CB_SETTINGS_AVAILABLE_ACTION = "android.provider.Telephony.CB_SETTINGS_AVAILABLE";
            public static final String DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED";
            public static final String DEVICE_READY_ACTION = "android.provider.Telephony.DEVICE_READY";
            public static final String DIRECTED_SMS_RECEIVED_ACTION = "verizon.intent.action.DIRECTED_SMS_RECEIVED";
            public static final String EMERGENCY_CDMA_MESSAGE_RECEIVED_ACTION = "android.provider.Telephony.EMERGENCY_CDMA_MESSAGE_RECEIVED";
            public static final String EXTRA_LMS_TOKEN_CTC = "lms_token_ctc";
            public static final String EXTRA_PACKAGE_NAME = "package";
            public static final String GET_CB_ERR_RECEIVED_ACTION = "android.provider.Telephony.GET_CB_ERR_RECEIVED";
            public static final String GET_SMSC_ACTION = "android.provider.Telephony.GET_SMSC";
            public static final String LGU_APM_RECEIVED_ACTION = "android.lgt.action.APM_SMS_RECEIVED";
            public static final String LGU_FOTA_RECEIVED_ACTION = "android.intent.action.PUSH_CONFIRM";
            public static final String LMS_FIRST_DISPLAY_TIMEOUT_CTC_ACTION = "android.provider.Telephony.LMS_FIRST_DISPLAY_TIMEOUT_CTC";
            public static final String LMS_MAXIMAL_CONNECTION_TIMEOUT_CTC_ACTION = "android.provider.Telephony.LMS_MAXIMAL_CONNECTION_TIMEOUT_CTC";
            public static final String NSRISMS_RECEIVED_ACTION = "android.provider.Telephony.SECURITY_SMS_RECEIVED";
            public static final int RESULT_SMS_DSAC_FAIL = 7;
            public static final int RESULT_SMS_DUPLICATE = 8;
            public static final int RESULT_SMS_DUPLICATED = 5;
            public static final int RESULT_SMS_GENERIC_ERROR = 2;
            public static final int RESULT_SMS_HANDLED = 1;
            public static final int RESULT_SMS_MDM_DISCARDED = 10;
            public static final int RESULT_SMS_OUT_OF_MEMORY = 3;
            public static final int RESULT_SMS_SEGMENT = 9;
            public static final int RESULT_SMS_UNSUPPORTED = 4;
            public static final String SET_CB_ERR_RECEIVED_ACTION = "android.provider.Telephony.SET_CB_ERR_RECEIVED";
            public static final String SHOW_DATA_SMS_RECEIVED_ACTION = "com.kt.show.manger.action.SHOW_DATA_SMS_RECEIVED";
            public static final String SIM_FULL_ACTION = "android.provider.Telephony.SIM_FULL";
            public static final String SMS_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_CB_RECEIVED";
            public static final String SMS_CB_RECEIVED_WIFI_ACTION = "android.provider.Telephony.SMS_CB_WIFI_RECEIVED";
            public static final String SMS_DELIVER_ACTION = "android.provider.Telephony.SMS_DELIVER";
            public static final String SMS_EMERGENCY_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED";
            public static final String SMS_FILTER_ACTION = "android.provider.Telephony.SMS_FILTER";
            public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
            public static final String SMS_REJECTED_ACTION = "android.provider.Telephony.SMS_REJECTED";
            public static final String SMS_SEND_ACTION = "android.provider.Telephony.SMS_SEND";
            public static final String SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION = "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED";
            public static final String WAP_PUSH_DELIVER_ACTION = "android.provider.Telephony.WAP_PUSH_DELIVER";
            public static final String WAP_PUSH_DM_NOTI_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_DM_NOTI_RECEIVED";
            public static final String WAP_PUSH_DS_NOTI_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_DS_NOTI_RECEIVED";
            public static final String WAP_PUSH_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_RECEIVED";

            private Intents() {
            }

            public static SmsMessage[] getMessagesFromIntent(Intent intent) {
                Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
                String format = intent.getStringExtra(CellBroadcasts.MESSAGE_FORMAT);
                long subId = intent.getLongExtra("subscription", SubscriptionManager.getDefaultSmsSubId());
                Rlog.v(Telephony.TAG, " getMessagesFromIntent sub_id : " + subId);
                int pduCount = messages.length;
                byte[] shiftBytes = new byte[2];
                boolean isShiftBytes = false;
                int startBodyOffset = 0;
                SmsMessage[] msgs = new SmsMessage[pduCount];
                int i = 0;
                while (i < pduCount) {
                    byte[] pdu = (byte[]) messages[i];
                    if (pdu != null && pdu.length > 0) {
                        msgs[i] = SmsMessage.createFromPdu(pdu, format);
                        if (isShiftBytes) {
                            msgs[i].mWrappedSmsMessage.replaceMessageBody(combineFourBytes(msgs[i].mWrappedSmsMessage.getUserData(), shiftBytes, startBodyOffset) + msgs[i].mWrappedSmsMessage.getMessageBody().substring(1));
                            isShiftBytes = false;
                        }
                        if (!(msgs[i] == null || msgs[i].mWrappedSmsMessage == null || !msgs[i].mWrappedSmsMessage.getIsFourBytesUnicode())) {
                            Rlog.e(Telephony.TAG, "Detect multibyte unicode at the end of page");
                            byte[] lastbyte = msgs[i].mWrappedSmsMessage.getLastByte();
                            shiftBytes[0] = lastbyte[0];
                            shiftBytes[1] = lastbyte[1];
                            startBodyOffset = msgs[i].mWrappedSmsMessage.getBodyOffset();
                            String messageBody = msgs[i].mWrappedSmsMessage.getMessageBody();
                            Rlog.d(Telephony.TAG, "messageBody Length : " + messageBody.length());
                            msgs[i].mWrappedSmsMessage.replaceMessageBody(messageBody.substring(0, messageBody.length() - 1));
                            isShiftBytes = true;
                        }
                    }
                    if (msgs[i] != null) {
                        msgs[i].setSubId(subId);
                    }
                    i++;
                }
                return msgs;
            }

            private static String combineFourBytes(byte[] firstbyte, byte[] lastbyte, int startBodyOffset) {
                Rlog.d(Telephony.TAG, "combineFourBytes : " + (lastbyte[0] & 255) + " " + (lastbyte[1] & 255) + " " + (firstbyte[0] & 255) + " " + (firstbyte[1] & 255) + " offset : " + startBodyOffset);
                try {
                    return new String(new byte[]{(byte) (lastbyte[0] & 255), (byte) (lastbyte[1] & 255), (byte) (firstbyte[startBodyOffset] & 255), (byte) (firstbyte[startBodyOffset + 1] & 255)}, CharacterSets.MIMENAME_UTF_16);
                } catch (UnsupportedEncodingException ex) {
                    String ret = "";
                    Rlog.e(Telephony.TAG, "implausible UnsupportedEncodingException", ex);
                    return ret;
                }
            }
        }

        public static final class Outbox implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Outbox() {
            }

            public static Uri addMessage(ContentResolver resolver, String address, String body, String subject, Long date, boolean deliveryReport, long threadId) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, CONTENT_URI, address, body, subject, date, true, deliveryReport, threadId);
            }

            public static Uri addMessage(long subId, ContentResolver resolver, String address, String body, String subject, Long date, boolean deliveryReport, long threadId) {
                return Sms.addMessageToUri(subId, resolver, CONTENT_URI, address, body, subject, date, true, deliveryReport, threadId);
            }
        }

        public static final class Sent implements BaseColumns, TextBasedSmsColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://sms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            private Sent() {
            }

            public static Uri addMessage(ContentResolver resolver, String address, String body, String subject, Long date) {
                return Sms.addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, CONTENT_URI, address, body, subject, date, true, false);
            }

            public static Uri addMessage(long subId, ContentResolver resolver, String address, String body, String subject, Long date) {
                return Sms.addMessageToUri(subId, resolver, CONTENT_URI, address, body, subject, date, true, false);
            }
        }

        private Sms() {
        }

        public static String getDefaultSmsPackage(Context context) {
            ComponentName component = SmsApplication.getDefaultSmsApplication(context, false);
            if (component != null) {
                return component.getPackageName();
            }
            return null;
        }

        public static Cursor query(ContentResolver cr, String[] projection) {
            return cr.query(CONTENT_URI, projection, null, null, "date DESC");
        }

        public static Cursor query(ContentResolver cr, String[] projection, String where, String orderBy) {
            return cr.query(CONTENT_URI, projection, where, null, orderBy == null ? "date DESC" : orderBy);
        }

        public static Uri addMessageToUri(ContentResolver resolver, Uri uri, String address, String body, String subject, Long date, boolean read, boolean deliveryReport) {
            return addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, uri, address, body, subject, date, read, deliveryReport, -1);
        }

        public static Uri addMessageToUri(long subId, ContentResolver resolver, Uri uri, String address, String body, String subject, Long date, boolean read, boolean deliveryReport) {
            return addMessageToUri(subId, resolver, uri, address, body, subject, date, read, deliveryReport, -1);
        }

        public static Uri addMessageToUri(ContentResolver resolver, Uri uri, String address, String body, String subject, Long date, boolean read, boolean deliveryReport, long threadId) {
            return addMessageToUri(SubscriptionManager.getDefaultSmsSubId(), resolver, uri, address, body, subject, date, read, deliveryReport, threadId);
        }

        public static Uri addMessageToUri(long subId, ContentResolver resolver, Uri uri, String address, String body, String subject, Long date, boolean read, boolean deliveryReport, long threadId) {
            ContentValues values = new ContentValues(8);
            Rlog.v(Telephony.TAG, "Telephony addMessageToUri sub id: " + subId);
            values.put("sub_id", Long.valueOf(subId));
            values.put("address", address);
            if (date != null) {
                values.put("date", date);
            }
            values.put("read", read ? Integer.valueOf(1) : Integer.valueOf(0));
            values.put(TextBasedSmsColumns.SUBJECT, subject);
            values.put("body", body);
            if (deliveryReport) {
                values.put(TextBasedSmsColumns.STATUS, Integer.valueOf(32));
            }
            if (threadId != -1) {
                values.put("thread_id", Long.valueOf(threadId));
            }
            return resolver.insert(uri, values);
        }

        public static Uri addMessageToUri(ContentResolver resolver, Uri uri, String address, String body, String subject, String imsi, int mSimSlot, Long date, boolean read, boolean deliveryReport, long threadId) {
            ContentValues values = new ContentValues(7);
            values.put("address", address);
            if (date != null) {
                values.put("date", date);
            }
            values.put("read", read ? Integer.valueOf(1) : Integer.valueOf(0));
            values.put(TextBasedSmsColumns.SUBJECT, subject);
            values.put("body", body);
            if (imsi != null) {
                values.put("sim_imsi", imsi);
            }
            values.put("sim_slot", Integer.valueOf(mSimSlot));
            if (deliveryReport) {
                values.put(TextBasedSmsColumns.STATUS, Integer.valueOf(32));
            }
            if (threadId != -1) {
                values.put("thread_id", Long.valueOf(threadId));
            }
            return resolver.insert(uri, values);
        }

        public static boolean moveMessageToFolder(Context context, Uri uri, int folder, int error) {
            if (uri == null) {
                return false;
            }
            boolean z;
            boolean markAsUnread = false;
            boolean markAsRead = false;
            switch (folder) {
                case 1:
                case 3:
                    break;
                case 2:
                case 4:
                    markAsRead = true;
                    break;
                case 5:
                case 6:
                    markAsUnread = true;
                    break;
                default:
                    return false;
            }
            ContentValues values = new ContentValues(3);
            values.put("type", Integer.valueOf(folder));
            if (markAsUnread) {
                values.put("read", Integer.valueOf(0));
            } else if (markAsRead) {
                values.put("read", Integer.valueOf(1));
            }
            values.put(TextBasedSmsColumns.ERROR_CODE, Integer.valueOf(error));
            if (1 == SqliteWrapper.update(context, context.getContentResolver(), uri, values, null, null)) {
                z = true;
            } else {
                z = false;
            }
            return z;
        }

        public static boolean isOutgoingFolder(int messageType) {
            return messageType == 5 || messageType == 4 || messageType == 2 || messageType == 6;
        }
    }

    public interface ThreadsColumns extends BaseColumns {
        public static final String ALERT_EXPIRED = "alert_expired";
        public static final String ARCHIVED = "archived";
        public static final String DATE = "date";
        public static final String ERROR = "error";
        public static final String GROUP_SNIPPET = "group_snippet";
        public static final String HAS_ATTACHMENT = "has_attachment";
        public static final String MESSAGE_COUNT = "message_count";
        public static final String MESSAGE_TYPE = "message_type";
        public static final String READ = "read";
        public static final String RECIPIENT_IDS = "recipient_ids";
        public static final String REPLY_ALL_STATUS = "reply_all";
        public static final String SNIPPET = "snippet";
        public static final String SNIPPET_CHARSET = "snippet_cs";
        public static final int THREAD_TYPE_ALL = 0;
        public static final int THREAD_TYPE_DRAFT = 1;
        public static final int THREAD_TYPE_FAILED = 3;
        public static final int THREAD_TYPE_RECEIVE = 5;
        public static final int THREAD_TYPE_SENDING = 2;
        public static final int THREAD_TYPE_SENT = 4;
        public static final String TYPE = "type";
        public static final String UNREAD_COUNT = "unread_count";
    }

    public static final class Threads implements ThreadsColumns {
        public static final int ALERTS_ALL_ONE_THREAD = 110;
        public static final int ALERT_AMBER_THREAD = 103;
        public static final int ALERT_EXTREME_THREAD = 101;
        public static final int ALERT_PRESIDENTIAL_THREAD = 100;
        public static final int ALERT_SEVERE_THREAD = 102;
        public static final int ALERT_TEST_MESSAGE_THREAD = 104;
        public static final int BROADCAST_THREAD = 1;
        public static final int COMMON_THREAD = 0;
        public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "conversations");
        private static final String[] ID_PROJECTION = new String[]{"_id"};
        public static final Uri OBSOLETE_THREADS_URI = Uri.withAppendedPath(CONTENT_URI, "obsolete");
        private static final long TEMP_RECIPIENT = 9223372036854775806L;
        public static final long TEMP_THREAD_ID = 9223372036854775806L;
        private static final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/threadID");

        private Threads() {
        }

        public static long getOrCreateThreadId(Context context, String recipient) {
            Set recipients = new HashSet();
            recipients.add(recipient);
            return getOrCreateThreadId(context, recipients);
        }

        public static long getOrCreateThreadId(Context context, String recipient, int simSlot) {
            Set recipients = new HashSet();
            recipients.add(recipient);
            return getOrCreateThreadId(context, recipients, simSlot);
        }

        private static boolean isTempRecipient(Set<String> recipients) {
            if (recipients.size() == 1) {
                Iterator i$ = recipients.iterator();
                if (i$.hasNext()) {
                    return ((String) i$.next()).equals(Long.toString(9223372036854775806L));
                }
            }
            return false;
        }

        public static long getOrCreateThreadId(Context context, Set<String> recipients) {
            return getOrCreateThreadId(context, recipients, true, 0);
        }

        public static long getOrCreateThreadId(Context context, Set<String> recipients, int simSlot) {
            return getOrCreateThreadId(context, recipients, true, simSlot);
        }

        public static long getOrCreateThreadId(Context context, Set<String> recipients, boolean createThread) {
            return getOrCreateThreadId(context, recipients, createThread, 0);
        }

        public static long getOrCreateThreadId(Context context, Set<String> recipients, boolean createThread, int simSlot) {
            Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();
            if (isTempRecipient(recipients)) {
                return 9223372036854775806L;
            }
            for (String recipient : recipients) {
                String recipient2;
                if (Mms.isEmailAddress(recipient2)) {
                    recipient2 = Mms.extractAddrSpec(recipient2);
                }
                uriBuilder.appendQueryParameter("recipient", recipient2);
            }
            uriBuilder.appendQueryParameter("createthread", String.valueOf(createThread));
            uriBuilder.appendQueryParameter("sim_slot", String.valueOf(simSlot));
            Context context2 = context;
            Cursor cursor = SqliteWrapper.query(context2, context.getContentResolver(), uriBuilder.build(), ID_PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        long j = cursor.getLong(0);
                        return j;
                    }
                    Rlog.e(Telephony.TAG, "getOrCreateThreadId returned no rows!");
                    cursor.close();
                } finally {
                    cursor.close();
                }
            }
            if (!createThread) {
                return -1;
            }
            throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
        }
    }

    private Telephony() {
    }
}
