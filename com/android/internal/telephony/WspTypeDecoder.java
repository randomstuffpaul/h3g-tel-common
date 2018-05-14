package com.android.internal.telephony;

import android.util.secutil.Log;
import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.CharacterSets;
import java.util.HashMap;

public class WspTypeDecoder {
    public static final String CONTENT_MIME_TYPE_B_DRM_RIGHTS_WBXML = "application/vnd.oma.drm.rights+wbxml";
    public static final String CONTENT_MIME_TYPE_B_DRM_RIGHTS_XML = "application/vnd.oma.drm.rights+xml";
    public static final String CONTENT_MIME_TYPE_B_DRM_ROAP_TRIGGER_WBXML = "application/vnd.oma.drm.roap-trigger+wbxml";
    public static final String CONTENT_MIME_TYPE_B_DRM_ROAP_TRIGGER_XML = "application/vnd.oma.drm.roap-trigger+xml";
    public static final String CONTENT_MIME_TYPE_B_EMN = "application/vnd.wap.emn+wbxml";
    public static final String CONTENT_MIME_TYPE_B_MMS = "application/vnd.wap.mms-message";
    public static final String CONTENT_MIME_TYPE_B_PUSH_CO = "application/vnd.wap.coc";
    public static final String CONTENT_MIME_TYPE_B_PUSH_DM_SYNC_WBXML = "application/vnd.syncml.dm+wbxml";
    public static final String CONTENT_MIME_TYPE_B_PUSH_DM_SYNC_XML = "application/vnd.syncml.dm+xml";
    public static final String CONTENT_MIME_TYPE_B_PUSH_DS_SYNCML_NOTI = "application/vnd.syncml.ds.notification";
    public static final String CONTENT_MIME_TYPE_B_PUSH_PROV_CONNECTIVITY = "application/vnd.wap.connectivity-wbxml";
    public static final String CONTENT_MIME_TYPE_B_PUSH_SI = "application/vnd.wap.sic";
    public static final String CONTENT_MIME_TYPE_B_PUSH_SL = "application/vnd.wap.slc";
    public static final String CONTENT_MIME_TYPE_B_PUSH_SYNCML_NOTI = "application/vnd.syncml.notification";
    public static final String CONTENT_MIME_TYPE_B_SUPL_INIT = "application/vnd.omaloc-supl-init";
    public static final String CONTENT_MIME_TYPE_B_VND_DOCOMO_PF = "application/vnd.docomo.pf";
    public static final String CONTENT_MIME_TYPE_B_VND_DOCOMO_SLC = "application/vnd.syncml+wbxml";
    public static final String CONTENT_TYPE_B_MMS = "application/vnd.wap.mms-message";
    public static final String CONTENT_TYPE_B_PUSH_CO = "application/vnd.wap.coc";
    public static final String CONTENT_TYPE_B_PUSH_SYNCML_NOTI = "application/vnd.syncml.notification";
    public static final int PARAMETER_ID_X_WAP_APPLICATION_ID = 47;
    public static final int PDU_TYPE_CONFIRMED_PUSH = 7;
    public static final int PDU_TYPE_PUSH = 6;
    private static final int Q_VALUE = 0;
    private static final int WAP_PDU_LENGTH_QUOTE = 31;
    private static final int WAP_PDU_SHORT_LENGTH_MAX = 30;
    private static final HashMap<Integer, String> WELL_KNOWN_MIME_TYPES = new HashMap();
    private static final HashMap<Integer, String> WELL_KNOWN_PARAMETERS = new HashMap();
    public static final int WSP_HEADER_ACCEPT = 0;
    public static final int WSP_HEADER_ACCEPT_APPLICATION = 50;
    public static final int WSP_HEADER_ACCEPT_CHARSET = 1;
    public static final int WSP_HEADER_ACCEPT_CHARSET2 = 59;
    public static final int WSP_HEADER_ACCEPT_ENCODING = 2;
    public static final int WSP_HEADER_ACCEPT_ENCODING2 = 60;
    public static final int WSP_HEADER_ACCEPT_LANGUAGE = 3;
    public static final int WSP_HEADER_ACCEPT_RANGES = 4;
    public static final int WSP_HEADER_AGE = 5;
    public static final int WSP_HEADER_ALLOW = 6;
    public static final int WSP_HEADER_AUTHORIZATION = 7;
    public static final int WSP_HEADER_BEARER_INDICATION = 51;
    public static final int WSP_HEADER_CACHE_CONTROL = 8;
    public static final int WSP_HEADER_CACHE_CONTROL2 = 61;
    public static final int WSP_HEADER_CACHE_CONTROL3 = 71;
    public static final int WSP_HEADER_CONNECTION = 9;
    public static final int WSP_HEADER_CONTENT_BASE = 10;
    public static final int WSP_HEADER_CONTENT_DISPOSITION = 46;
    public static final int WSP_HEADER_CONTENT_DISPOSITION2 = 69;
    public static final int WSP_HEADER_CONTENT_ENCODING = 11;
    public static final int WSP_HEADER_CONTENT_ID = 64;
    public static final int WSP_HEADER_CONTENT_LANGUAGE = 12;
    public static final int WSP_HEADER_CONTENT_LENGTH = 13;
    public static final int WSP_HEADER_CONTENT_LOCATION = 14;
    public static final int WSP_HEADER_CONTENT_MD5 = 15;
    public static final int WSP_HEADER_CONTENT_RANGE = 16;
    public static final int WSP_HEADER_CONTENT_RANGE2 = 62;
    public static final int WSP_HEADER_CONTENT_TYPE = 17;
    public static final int WSP_HEADER_COOKIE = 66;
    public static final int WSP_HEADER_DATE = 18;
    public static final int WSP_HEADER_ENCODING_VERSION = 67;
    public static final int WSP_HEADER_ETAG = 19;
    public static final int WSP_HEADER_EXPECT = 56;
    public static final int WSP_HEADER_EXPECT2 = 72;
    public static final int WSP_HEADER_EXPIRES = 20;
    public static final int WSP_HEADER_FROM = 21;
    public static final int WSP_HEADER_HOST = 22;
    public static final int WSP_HEADER_IF_MATCH = 24;
    public static final int WSP_HEADER_IF_MODIFIED_SINCE = 23;
    public static final int WSP_HEADER_IF_NONE_MATCH = 25;
    public static final int WSP_HEADER_IF_RANGE = 26;
    public static final int WSP_HEADER_IF_UNMODIFIED_SINCE = 27;
    public static final int WSP_HEADER_LAST_MODIFIED = 29;
    public static final int WSP_HEADER_LOCATION = 28;
    public static final int WSP_HEADER_MAX_FORWARDS = 30;
    public static final int WSP_HEADER_PRAGMA = 31;
    public static final int WSP_HEADER_PROFILE = 53;
    public static final int WSP_HEADER_PROFILE_DIFF = 54;
    public static final int WSP_HEADER_PROFILE_WARNING = 55;
    public static final int WSP_HEADER_PROFILE_WARNING2 = 68;
    public static final int WSP_HEADER_PROXY_AUTHENTICATE = 32;
    public static final int WSP_HEADER_PROXY_AUTHORIZATION = 33;
    public static final int WSP_HEADER_PUBLIC = 34;
    public static final int WSP_HEADER_PUSH_FLAG = 52;
    public static final int WSP_HEADER_RANGE = 35;
    public static final int WSP_HEADER_REFERER = 36;
    public static final int WSP_HEADER_RETRY_AFTER = 37;
    public static final int WSP_HEADER_SERVER = 38;
    public static final int WSP_HEADER_SET_COOKIE = 65;
    public static final int WSP_HEADER_TE = 57;
    public static final int WSP_HEADER_TRAILER = 58;
    public static final int WSP_HEADER_TRANSFER_ENCODING = 39;
    public static final int WSP_HEADER_UPGRADE = 40;
    public static final int WSP_HEADER_USER_AGENT = 41;
    public static final int WSP_HEADER_VARY = 42;
    public static final int WSP_HEADER_VIA = 43;
    public static final int WSP_HEADER_WARNING = 44;
    public static final int WSP_HEADER_WWW_AUTHENTICATE = 45;
    public static final int WSP_HEADER_X_WAP_APPLICATION_ID = 47;
    public static final int WSP_HEADER_X_WAP_CONTENT_URI = 48;
    public static final int WSP_HEADER_X_WAP_INITIATOR_URI = 49;
    public static final int WSP_HEADER_X_WAP_LOC_DELIVERY = 74;
    public static final int WSP_HEADER_X_WAP_LOC_INVOCATION = 73;
    public static final int WSP_HEADER_X_WAP_SECURITY = 70;
    public static final int WSP_HEADER_X_WAP_TOD = 63;
    public static final int X_WAP_APPLICATION_ID_X_OMA_DOCOMO_EMN_UA = 9;
    public static final int X_WAP_APPLICATION_ID_X_OMA_DOCOMO_SP_MAIL_UA = 36950;
    public static final int X_WAP_APPLICATION_ID_X_OMA_DOCOMO_STORAGESERVICE_UA = 36959;
    public static final int X_WAP_APPLICATION_ID_X_OMA_DOCOMO_SYNCML_DM = 7;
    public static final int X_WAP_APPLICATION_ID_X_OMA_DOCOMO_XMD_MAIL_UA = 36956;
    public static final int iCONTENT_TYPE_B_DRM_RIGHTS_WBXML = 75;
    public static final int iCONTENT_TYPE_B_DRM_RIGHTS_XML = 74;
    public static final int iCONTENT_TYPE_B_EMN = 778;
    public static final int iCONTENT_TYPE_B_MMS = 62;
    public static final int iCONTENT_TYPE_B_PUSH_CO = 50;
    public static final int iCONTENT_TYPE_B_PUSH_DM_SYNC_WBXML = 66;
    public static final int iCONTENT_TYPE_B_PUSH_DM_SYNC_XML = 67;
    public static final int iCONTENT_TYPE_B_PUSH_DS_SYNCML_NOTI = 78;
    public static final int iCONTENT_TYPE_B_PUSH_DS_SYNCML_NOTI_CE = 206;
    public static final int iCONTENT_TYPE_B_PUSH_PROV_CONNECTIVITY = 54;
    public static final int iCONTENT_TYPE_B_PUSH_SI = 46;
    public static final int iCONTENT_TYPE_B_PUSH_SL = 48;
    public static final int iCONTENT_TYPE_B_PUSH_SYNCML_NOTI = 68;
    public static final int iCONTENT_TYPE_B_ROAP_TRIGGER_WBXML = 790;
    public static final int iCONTENT_TYPE_B_SUPL_INIT = 786;
    public static final int iCONTENT_TYPE_B_VND_DOCOMO_PF = 784;
    public static final int iCONTENT_TYPE_B_VND_DOCOMO_SLC = 176;
    HashMap<String, String> mContentParameters;
    int mDataLength;
    String mStringValue;
    long mUnsigned32bit;
    byte[] mWspData;

    static {
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(0), "*/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(1), "text/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(2), ContentType.TEXT_HTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(3), ContentType.TEXT_PLAIN);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(4), "text/x-hdml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(5), "text/x-ttml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(6), ContentType.TEXT_VCALENDAR);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(7), ContentType.TEXT_VCARD);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(8), "text/vnd.wap.wml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(9), "text/vnd.wap.wmlscript");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(10), "text/vnd.wap.wta-event");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(11), "multipart/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(12), "multipart/mixed");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(13), "multipart/form-data");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(14), "multipart/byterantes");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(15), "multipart/alternative");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(16), "application/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(17), "application/java-vm");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(18), "application/x-www-form-urlencoded");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(19), "application/x-hdmlc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(20), "application/vnd.wap.wmlc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(21), "application/vnd.wap.wmlscriptc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(22), "application/vnd.wap.wta-eventc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(23), "application/vnd.wap.uaprof");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(24), "application/vnd.wap.wtls-ca-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(25), "application/vnd.wap.wtls-user-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(26), "application/x-x509-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(27), "application/x-x509-user-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(28), ContentType.IMAGE_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(29), ContentType.IMAGE_GIF);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(30), ContentType.IMAGE_JPEG);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(31), "image/tiff");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(32), ContentType.IMAGE_PNG);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(33), ContentType.IMAGE_WBMP);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(34), "application/vnd.wap.multipart.*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(35), ContentType.MULTIPART_MIXED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(36), "application/vnd.wap.multipart.form-data");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(37), "application/vnd.wap.multipart.byteranges");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(38), ContentType.MULTIPART_ALTERNATIVE);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(39), "application/xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(40), "text/xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(41), "application/vnd.wap.wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(42), "application/x-x968-cross-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(43), "application/x-x968-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(44), "application/x-x968-user-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(45), "text/vnd.wap.si");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(46), CONTENT_MIME_TYPE_B_PUSH_SI);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(47), "text/vnd.wap.sl");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(48), CONTENT_MIME_TYPE_B_PUSH_SL);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(49), "text/vnd.wap.co");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(50), "application/vnd.wap.coc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(51), ContentType.MULTIPART_RELATED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(52), "application/vnd.wap.sia");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(53), "text/vnd.wap.connectivity-xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(54), CONTENT_MIME_TYPE_B_PUSH_PROV_CONNECTIVITY);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(55), "application/pkcs7-mime");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(56), "application/vnd.wap.hashed-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(57), "application/vnd.wap.signed-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(58), "application/vnd.wap.cert-response");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(59), ContentType.APP_XHTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(60), "application/wml+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(61), "text/css");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(62), "application/vnd.wap.mms-message");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(63), "application/vnd.wap.rollover-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(64), "application/vnd.wap.locc+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(65), "application/vnd.wap.loc+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(66), CONTENT_MIME_TYPE_B_PUSH_DM_SYNC_WBXML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(67), CONTENT_MIME_TYPE_B_PUSH_DM_SYNC_XML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(68), "application/vnd.syncml.notification");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(69), ContentType.APP_WAP_XHTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(70), "application/vnd.wv.csp.cir");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(71), "application/vnd.oma.dd+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(72), "application/vnd.oma.drm.message");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(73), ContentType.APP_DRM_CONTENT);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(74), CONTENT_MIME_TYPE_B_DRM_RIGHTS_XML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(75), "application/vnd.oma.drm.rights+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(76), "application/vnd.wv.csp+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(77), "application/vnd.wv.csp+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(78), CONTENT_MIME_TYPE_B_PUSH_DS_SYNCML_NOTI);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(79), ContentType.AUDIO_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(80), ContentType.VIDEO_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(81), "application/vnd.oma.dd2+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(82), "application/mikey");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(83), "application/vnd.oma.dcd");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(84), "application/vnd.oma.dcdc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(513), "application/vnd.uplanet.cacheop-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(514), "application/vnd.uplanet.signal");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(515), "application/vnd.uplanet.alert-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(516), "application/vnd.uplanet.list-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(517), "application/vnd.uplanet.listcmd-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(518), "application/vnd.uplanet.channel-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(519), "application/vnd.uplanet.provisioning-status-uri");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(520), "x-wap.multipart/vnd.uplanet.header-set");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(521), "application/vnd.uplanet.bearer-choice-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(522), "application/vnd.phonecom.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(523), "application/vnd.nokia.syncset+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(524), "image/x-up-wpng");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(768), "application/iota.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(769), "application/iota.mmc-xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(770), "application/vnd.syncml+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(771), CONTENT_MIME_TYPE_B_VND_DOCOMO_SLC);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(772), "text/vnd.wap.emn+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(773), "text/calendar");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(774), "application/vnd.omads-email+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(775), "application/vnd.omads-file+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(776), "application/vnd.omads-folder+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(777), "text/directory;profile=vCard");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(iCONTENT_TYPE_B_EMN), CONTENT_MIME_TYPE_B_EMN);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(779), "application/vnd.nokia.ipdc-purchase-response");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(780), "application/vnd.motorola.screen3+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(781), "application/vnd.motorola.screen3+gzip");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(782), "application/vnd.cmcc.setting+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(783), "application/vnd.cmcc.bombing+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(iCONTENT_TYPE_B_VND_DOCOMO_PF), CONTENT_MIME_TYPE_B_VND_DOCOMO_PF);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(785), "application/vnd.docomo.ub");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(iCONTENT_TYPE_B_SUPL_INIT), CONTENT_MIME_TYPE_B_SUPL_INIT);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(787), "application/vnd.oma.group-usage-list+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(788), "application/oma-directory+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(789), "application/vnd.docomo.pf2");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(iCONTENT_TYPE_B_ROAP_TRIGGER_WBXML), CONTENT_MIME_TYPE_B_DRM_ROAP_TRIGGER_WBXML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(791), "application/vnd.sbm.mid2");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(792), "application/vnd.wmf.bootstrap");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(793), "application/vnc.cmcc.dcd+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(794), "application/vnd.sbm.cid");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(795), "application/vnd.oma.bcast.provisioningtrigger");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(0), "Q");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(1), "Charset");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(2), "Level");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(3), "Type");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(7), "Differences");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(8), "Padding");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(9), "Type");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(14), "Max-Age");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(16), "Secure");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(17), "SEC");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(18), "MAC");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(19), "Creation-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(20), "Modification-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(21), "Read-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(22), "Size");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(23), "Name");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(24), "Filename");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(25), "Start");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(26), "Start-info");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(27), "Comment");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(28), "Domain");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(29), "Path");
    }

    public WspTypeDecoder(byte[] pdu) {
        this.mWspData = pdu;
    }

    public boolean decodeTextString(int startIndex) {
        int index = startIndex;
        while (this.mWspData[index] != (byte) 0) {
            index++;
        }
        this.mDataLength = (index - startIndex) + 1;
        if (this.mWspData[startIndex] == Byte.MAX_VALUE) {
            this.mStringValue = new String(this.mWspData, startIndex + 1, this.mDataLength - 2);
        } else {
            this.mStringValue = new String(this.mWspData, startIndex, this.mDataLength - 1);
        }
        return true;
    }

    public boolean decodeTokenText(int startIndex) {
        int index = startIndex;
        while (this.mWspData[index] != (byte) 0) {
            index++;
        }
        this.mDataLength = (index - startIndex) + 1;
        this.mStringValue = new String(this.mWspData, startIndex, this.mDataLength - 1);
        return true;
    }

    public boolean decodeShortInteger(int startIndex) {
        if ((this.mWspData[startIndex] & 128) == 0) {
            return false;
        }
        this.mUnsigned32bit = (long) (this.mWspData[startIndex] & 127);
        this.mDataLength = 1;
        return true;
    }

    public boolean decodeLongInteger(int startIndex) {
        int lengthMultiOctet = this.mWspData[startIndex] & 255;
        if (lengthMultiOctet > 30) {
            return false;
        }
        this.mUnsigned32bit = 0;
        for (int i = 1; i <= lengthMultiOctet; i++) {
            this.mUnsigned32bit = (this.mUnsigned32bit << 8) | ((long) (this.mWspData[startIndex + i] & 255));
        }
        this.mDataLength = lengthMultiOctet + 1;
        return true;
    }

    public boolean decodeIntegerValue(int startIndex) {
        if (decodeShortInteger(startIndex)) {
            return true;
        }
        return decodeLongInteger(startIndex);
    }

    public boolean decodeUintvarInteger(int startIndex) {
        int index = startIndex;
        this.mUnsigned32bit = 0;
        while ((this.mWspData[index] & 128) != 0) {
            if (index - startIndex >= 4) {
                return false;
            }
            this.mUnsigned32bit = (this.mUnsigned32bit << 7) | ((long) (this.mWspData[index] & 127));
            index++;
        }
        this.mUnsigned32bit = (this.mUnsigned32bit << 7) | ((long) (this.mWspData[index] & 127));
        this.mDataLength = (index - startIndex) + 1;
        return true;
    }

    public boolean decodeValueLength(int startIndex) {
        if ((this.mWspData[startIndex] & 255) > 31) {
            return false;
        }
        if (this.mWspData[startIndex] < (byte) 31) {
            this.mUnsigned32bit = (long) this.mWspData[startIndex];
            this.mDataLength = 1;
            return true;
        }
        decodeUintvarInteger(startIndex + 1);
        this.mDataLength++;
        return true;
    }

    public boolean decodeExtensionMedia(int startIndex) {
        boolean rtrn = false;
        int index = startIndex;
        this.mDataLength = 0;
        this.mStringValue = null;
        int length = this.mWspData.length;
        if (index < length) {
            rtrn = true;
        }
        while (index < length && this.mWspData[index] != (byte) 0) {
            index++;
        }
        this.mDataLength = (index - startIndex) + 1;
        this.mStringValue = new String(this.mWspData, startIndex, this.mDataLength - 1);
        return rtrn;
    }

    public boolean decodeConstrainedEncoding(int startIndex) {
        if (!decodeShortInteger(startIndex)) {
            return decodeExtensionMedia(startIndex);
        }
        this.mStringValue = null;
        return true;
    }

    public boolean decodeContentType(int startIndex) {
        this.mContentParameters = new HashMap();
        try {
            if (decodeValueLength(startIndex)) {
                int headersLength = (int) this.mUnsigned32bit;
                int mediaPrefixLength = getDecodedDataLength();
                int readLength;
                long wellKnownValue;
                String mimeType;
                if (decodeIntegerValue(startIndex + mediaPrefixLength)) {
                    this.mDataLength += mediaPrefixLength;
                    readLength = this.mDataLength;
                    this.mStringValue = null;
                    expandWellKnownMimeType();
                    wellKnownValue = this.mUnsigned32bit;
                    mimeType = this.mStringValue;
                    if (!readContentParameters(this.mDataLength + startIndex, headersLength - (this.mDataLength - mediaPrefixLength), 0)) {
                        return false;
                    }
                    this.mDataLength += readLength;
                    this.mUnsigned32bit = wellKnownValue;
                    this.mStringValue = mimeType;
                    return true;
                }
                if (decodeExtensionMedia(startIndex + mediaPrefixLength)) {
                    this.mDataLength += mediaPrefixLength;
                    readLength = this.mDataLength;
                    expandWellKnownMimeType();
                    wellKnownValue = this.mUnsigned32bit;
                    mimeType = this.mStringValue;
                    if (readContentParameters(this.mDataLength + startIndex, headersLength - (this.mDataLength - mediaPrefixLength), 0)) {
                        this.mDataLength += readLength;
                        this.mUnsigned32bit = wellKnownValue;
                        this.mStringValue = mimeType;
                        return true;
                    }
                }
                return false;
            }
            boolean found = decodeConstrainedEncoding(startIndex);
            if (!found) {
                return found;
            }
            expandWellKnownMimeType();
            return found;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    private boolean readContentParameters(int startIndex, int leftToRead, int accumulator) {
        if (leftToRead > 0) {
            String param;
            int totalRead;
            String value;
            byte nextByte = this.mWspData[startIndex];
            if ((nextByte & 128) == 0 && nextByte > (byte) 31) {
                decodeTokenText(startIndex);
                param = this.mStringValue;
                totalRead = 0 + this.mDataLength;
            } else if (!decodeIntegerValue(startIndex)) {
                return false;
            } else {
                totalRead = 0 + this.mDataLength;
                int wellKnownParameterValue = (int) this.mUnsigned32bit;
                param = (String) WELL_KNOWN_PARAMETERS.get(Integer.valueOf(wellKnownParameterValue));
                if (param == null) {
                    param = "unassigned/0x" + Long.toHexString((long) wellKnownParameterValue);
                }
                if (wellKnownParameterValue == 0) {
                    if (!decodeUintvarInteger(startIndex + totalRead)) {
                        return false;
                    }
                    totalRead += this.mDataLength;
                    this.mContentParameters.put(param, String.valueOf(this.mUnsigned32bit));
                    return readContentParameters(startIndex + totalRead, leftToRead - totalRead, accumulator + totalRead);
                }
            }
            if (decodeNoValue(startIndex + totalRead)) {
                totalRead += this.mDataLength;
                value = null;
            } else if (decodeIntegerValue(startIndex + totalRead)) {
                totalRead += this.mDataLength;
                int intValue = (int) this.mUnsigned32bit;
                if (intValue == 0) {
                    value = "";
                } else {
                    value = String.valueOf(intValue);
                }
            } else {
                decodeTokenText(startIndex + totalRead);
                totalRead += this.mDataLength;
                value = this.mStringValue;
                if (value.startsWith("\"")) {
                    value = value.substring(1);
                }
            }
            this.mContentParameters.put(param, value);
            return readContentParameters(startIndex + totalRead, leftToRead - totalRead, accumulator + totalRead);
        }
        this.mDataLength = accumulator;
        return true;
    }

    private boolean decodeNoValue(int startIndex) {
        if (this.mWspData[startIndex] != (byte) 0) {
            return false;
        }
        this.mDataLength = 1;
        return true;
    }

    private void expandWellKnownMimeType() {
        if (this.mStringValue == null) {
            this.mStringValue = (String) WELL_KNOWN_MIME_TYPES.get(Integer.valueOf((int) this.mUnsigned32bit));
            return;
        }
        this.mUnsigned32bit = -1;
    }

    public boolean decodeContentLength(int startIndex) {
        return decodeIntegerValue(startIndex);
    }

    public boolean decodeContentLocation(int startIndex) {
        return decodeTextString(startIndex);
    }

    public boolean decodeXWapApplicationId(int startIndex) {
        if (!decodeIntegerValue(startIndex)) {
            return decodeTextString(startIndex);
        }
        this.mStringValue = null;
        return true;
    }

    public boolean seekXWapApplicationId(int startIndex, int endIndex) {
        int i = startIndex;
        i = startIndex;
        while (i <= endIndex) {
            try {
                if (decodeIntegerValue(i)) {
                    if (((int) getValue32()) == 47) {
                        this.mUnsigned32bit = (long) (i + 1);
                        return true;
                    }
                } else if (!decodeTextString(i)) {
                    return false;
                }
                i += getDecodedDataLength();
                if (i > endIndex) {
                    return false;
                }
                byte val = this.mWspData[i];
                if (val >= (byte) 0 && val <= (byte) 30) {
                    i += this.mWspData[i] + 1;
                } else if (val == (byte) 31) {
                    if (i + 1 >= endIndex) {
                        return false;
                    }
                    i++;
                    if (!decodeUintvarInteger(i)) {
                        return false;
                    }
                    i += getDecodedDataLength();
                } else if ((byte) 31 >= val || val > Byte.MAX_VALUE) {
                    i++;
                } else if (!decodeTextString(i)) {
                    return false;
                } else {
                    i += getDecodedDataLength();
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
        return false;
    }

    public boolean decodeXWapContentURI(int startIndex) {
        return decodeTextString(startIndex);
    }

    public boolean decodeXWapInitiatorURI(int startIndex) {
        return decodeTextString(startIndex);
    }

    public int getDecodedDataLength() {
        return this.mDataLength;
    }

    public long getValue32() {
        return this.mUnsigned32bit;
    }

    public String getValueString() {
        return this.mStringValue;
    }

    public HashMap<String, String> getContentParameters() {
        return this.mContentParameters;
    }

    public HashMap<?, ?> getHeaderMapfromSMSPushPDU(int generalHeaderStartIndex, int headerStartIndex, int headerLength) {
        HashMap ret = new HashMap();
        int pos = generalHeaderStartIndex;
        while (true) {
            String headerValue;
            if (decodeIntegerValue(pos)) {
                long headerName = getValue32();
                pos += getDecodedDataLength();
                Log.secD("WAP PUSH", "int header found pos=" + pos + " name=" + String.format("0x%02X", new Object[]{Long.valueOf(headerName)}));
                long headerValue2;
                long valueLength;
                byte[] data;
                switch ((int) headerName) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 17:
                    case 44:
                    case 55:
                    case 59:
                    case WSP_HEADER_ACCEPT_ENCODING2 /*60*/:
                    case 67:
                    case 68:
                        if (((headerName != 0 && headerName != 1 && headerName != 59 && headerName != 3) || this.mWspData[pos] != Byte.MIN_VALUE) && (headerName != 2 || this.mWspData[pos] != (byte) -125)) {
                            if (!decodeValueLength(pos)) {
                                if (!decodeShortInteger(pos)) {
                                    if (!decodeTextString(pos)) {
                                        Log.secD("WAP PUSH", "cannot decode header value pos=" + pos);
                                        break;
                                    }
                                    headerValue = getValueString();
                                    Log.secD("WAP PUSH", "     text value pos=" + pos + " value=" + headerValue);
                                    if (headerValue != null) {
                                        ret.put(Integer.valueOf((int) headerName), headerValue);
                                    }
                                    pos += getDecodedDataLength();
                                    break;
                                }
                                headerValue2 = getValue32();
                                Log.secD("WAP PUSH", "     int value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                                ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                                pos += getDecodedDataLength();
                                break;
                            }
                            valueLength = getValue32();
                            if (valueLength > 0) {
                                data = new byte[((int) valueLength)];
                                System.arraycopy(this.mWspData, pos, data, 0, (int) valueLength);
                                Log.secD("WAP PUSH", "     length value pos=" + pos + " value=" + data);
                                ret.put(Integer.valueOf((int) headerName), data);
                            }
                            pos = (int) (((long) pos) + (((long) getDecodedDataLength()) + valueLength));
                            break;
                        }
                        headerValue = CharacterSets.MIMENAME_ANY_CHARSET;
                        Log.secD("WAP PUSH", "     specific value pos=" + pos + " value=" + headerValue);
                        ret.put(Integer.valueOf((int) headerName), headerValue);
                        pos++;
                        break;
                        break;
                    case 4:
                    case 9:
                    case 11:
                    case 14:
                    case 19:
                    case 21:
                    case 22:
                    case 24:
                    case 25:
                    case WSP_HEADER_LOCATION /*28*/:
                    case 36:
                    case 38:
                    case 39:
                    case 40:
                    case 41:
                    case WSP_HEADER_VIA /*43*/:
                    case 48:
                    case 49:
                    case 53:
                    case 64:
                        if ((headerName != 4 || (this.mWspData[pos] != Byte.MIN_VALUE && this.mWspData[pos] != (byte) -127)) && ((headerName != 9 || this.mWspData[pos] != Byte.MIN_VALUE) && ((headerName != 11 || this.mWspData[pos] < Byte.MIN_VALUE || this.mWspData[pos] > (byte) -126) && (headerName != 39 || this.mWspData[pos] != Byte.MIN_VALUE)))) {
                            if (!decodeTextString(pos)) {
                                Log.secD("WAP PUSH", "cannot decode header value text pos=" + pos);
                                break;
                            }
                            headerValue = getValueString();
                            Log.secD("WAP PUSH", "     text value pos=" + pos + " value=" + headerValue);
                            if (headerValue != null) {
                                ret.put(Integer.valueOf((int) headerName), headerValue);
                            }
                            pos += getDecodedDataLength();
                            break;
                        }
                        headerValue2 = (long) this.mWspData[pos];
                        Log.secD("WAP PUSH", "     specific value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                        ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                        pos++;
                        break;
                        break;
                    case 5:
                    case 6:
                    case 13:
                    case 18:
                    case 20:
                    case 23:
                    case WSP_HEADER_IF_UNMODIFIED_SINCE /*27*/:
                    case WSP_HEADER_LAST_MODIFIED /*29*/:
                    case 30:
                    case 51:
                    case 52:
                    case 63:
                        if (!decodeIntegerValue(pos)) {
                            Log.secD("WAP PUSH", "cannot decode header value pos=" + pos);
                            break;
                        }
                        headerValue2 = getValue32();
                        Log.secD("WAP PUSH", "     int value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                        ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                        pos += getDecodedDataLength();
                        break;
                    case 7:
                    case 8:
                    case 15:
                    case 16:
                    case 31:
                    case 32:
                    case 33:
                    case 35:
                    case 37:
                    case WSP_HEADER_WWW_AUTHENTICATE /*45*/:
                    case 46:
                    case 54:
                    case 56:
                    case 57:
                    case WSP_HEADER_CACHE_CONTROL2 /*61*/:
                    case 62:
                    case WSP_HEADER_SET_COOKIE /*65*/:
                    case 66:
                    case WSP_HEADER_CONTENT_DISPOSITION2 /*69*/:
                    case 71:
                    case 72:
                    case 73:
                    case 74:
                        if (((headerName != 8 && headerName != 61 && headerName != 71) || this.mWspData[pos] < Byte.MIN_VALUE || this.mWspData[pos] > (byte) -117) && ((headerName != 31 || this.mWspData[pos] != Byte.MIN_VALUE) && (((headerName != 56 && headerName != 72) || this.mWspData[pos] != Byte.MIN_VALUE) && (headerName != 57 || this.mWspData[pos] != (byte) -127)))) {
                            if (!decodeValueLength(pos)) {
                                Log.secD("WAP PUSH", "cannot decode header value pos=" + pos);
                                break;
                            }
                            valueLength = getValue32();
                            if (valueLength > 0) {
                                data = new byte[((int) valueLength)];
                                System.arraycopy(this.mWspData, pos, data, 0, (int) valueLength);
                                Log.secD("WAP PUSH", "     length value pos=" + pos + " value=" + data);
                                ret.put(Integer.valueOf((int) headerName), data);
                            }
                            pos = (int) (((long) pos) + (((long) getDecodedDataLength()) + valueLength));
                            break;
                        }
                        headerValue2 = (long) this.mWspData[pos];
                        Log.secD("WAP PUSH", "     specific value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                        ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                        pos++;
                        break;
                        break;
                    case 12:
                    case 26:
                    case 34:
                    case 42:
                    case 47:
                    case 50:
                    case 58:
                        if ((headerName != 12 || this.mWspData[pos] != Byte.MIN_VALUE) && (headerName != 50 || this.mWspData[pos] != Byte.MIN_VALUE)) {
                            if (!decodeIntegerValue(pos)) {
                                if (!decodeTextString(pos)) {
                                    Log.secD("WAP PUSH", "cannot decode header value pos=" + pos);
                                    break;
                                }
                                headerValue = getValueString();
                                Log.secD("WAP PUSH", "     text value pos=" + pos + " value=" + headerValue);
                                if (headerValue != null) {
                                    ret.put(Integer.valueOf((int) headerName), headerValue);
                                }
                                pos += getDecodedDataLength();
                                break;
                            }
                            headerValue2 = getValue32();
                            Log.secD("WAP PUSH", "     int value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                            ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                            pos += getDecodedDataLength();
                            break;
                        }
                        headerValue = CharacterSets.MIMENAME_ANY_CHARSET;
                        Log.secD("WAP PUSH", "     specific value pos=" + pos + " value=" + headerValue);
                        ret.put(Integer.valueOf((int) headerName), headerValue);
                        pos++;
                        break;
                    case 70:
                        if (headerName != 70 || this.mWspData[pos] != Byte.MIN_VALUE) {
                            Log.secD("WAP PUSH", "cannot decode header value pos=" + pos);
                            break;
                        }
                        headerValue2 = (long) this.mWspData[pos];
                        Log.secD("WAP PUSH", "     specific value pos=" + pos + " value=" + String.format("0x%02X", new Object[]{Long.valueOf(headerValue2)}));
                        ret.put(Integer.valueOf((int) headerName), Long.valueOf(headerValue2));
                        pos++;
                        break;
                    default:
                        Log.secD("WAP PUSH", "Unknown header name");
                        break;
                }
                if (pos >= headerStartIndex + headerLength) {
                    Log.secD("WAP PUSH", "decoding Push PDU end. header start " + headerStartIndex + " length: " + headerLength + " pos: " + pos);
                }
            } else if (decodeTextString(pos)) {
                String headerName2 = getValueString();
                Log.secD("WAP PUSH", "text header found pos=" + pos + " name=" + headerName2);
                pos += getDecodedDataLength();
                if (decodeTextString(pos)) {
                    headerValue = getValueString();
                    Log.secD("WAP PUSH", "     text value pos=" + pos + " value=" + headerValue);
                    if (headerValue != null) {
                        ret.put(headerName2, headerValue);
                    }
                    pos += getDecodedDataLength();
                } else {
                    Log.secD("WAP PUSH", "cannot decode header value text pos=" + pos);
                }
            } else {
                Log.secD("WAP PUSH", "cannot decode header name text pos=" + pos);
            }
            return ret;
        }
    }
}
