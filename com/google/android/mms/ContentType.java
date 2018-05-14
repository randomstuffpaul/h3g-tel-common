package com.google.android.mms;

import com.google.android.mms.pdu.CharacterSets;
import java.util.ArrayList;

public class ContentType {
    public static final String APP_DRM_CONTENT = "application/vnd.oma.drm.content";
    public static final String APP_DRM_MESSAGE = "application/vnd.oma.drm.message";
    public static final String APP_DRM_RIGHTS_WBXML = "application/vnd.oma.drm.rights+wbxml";
    public static final String APP_OGG = "application/ogg";
    public static final String APP_SMIL = "application/smil";
    public static final String APP_WAP_XHTML = "application/vnd.wap.xhtml+xml";
    public static final String APP_XHTML = "application/xhtml+xml";
    public static final String AUDIO_3GPP = "audio/3gpp";
    public static final String AUDIO_AAC = "audio/aac";
    public static final String AUDIO_AAC_ADTS = "audio/aac-adts";
    public static final String AUDIO_AAC_MP4 = "audio/aac_mp4";
    public static final String AUDIO_AMR = "audio/amr";
    public static final String AUDIO_AMR_WB = "audio/amr-wb";
    public static final String AUDIO_ASF = "audio/x-ms-asf";
    public static final String AUDIO_EVRC = "audio/evrc";
    public static final String AUDIO_FLAC = "audio/flac";
    public static final String AUDIO_IMELODY = "audio/imelody";
    public static final String AUDIO_M4A = "audio/m4a";
    public static final String AUDIO_MID = "audio/mid";
    public static final String AUDIO_MIDI = "audio/midi";
    public static final String AUDIO_MMF = "application/vnd.smaf";
    public static final String AUDIO_MP3 = "audio/mp3";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_MP4A_LATM = "audio/mp4a-latm";
    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_MPEG3 = "audio/mpeg3";
    public static final String AUDIO_MPG = "audio/mpg";
    public static final String AUDIO_MXMF = "audio/mobile-xmf";
    public static final String AUDIO_OGG = "audio/ogg";
    public static final String AUDIO_QCELP = "audio/qcelp";
    public static final String AUDIO_QCELP_VND = "audio/vnd.qcelp";
    public static final String AUDIO_QCP = "audio/qcp";
    public static final String AUDIO_SP_MIDI = "audio/sp-midi";
    public static final String AUDIO_TEXT_X_IMY = "text/x-imelody";
    public static final String AUDIO_TEXT_X_IMY_C = "text/x-iMelody";
    public static final String AUDIO_UNSPECIFIED = "audio/*";
    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_WAVE = "audio/wave";
    public static final String AUDIO_WMA = "audio/x-ms-wma";
    public static final String AUDIO_XMF = "audio/xmf";
    public static final String AUDIO_X_AAC = "audio/x-aac";
    public static final String AUDIO_X_FLAC = "application/x-flac";
    public static final String AUDIO_X_MID = "audio/x-mid";
    public static final String AUDIO_X_MIDI = "audio/x-midi";
    public static final String AUDIO_X_MP3 = "audio/x-mp3";
    public static final String AUDIO_X_MPEG = "audio/x-mpeg";
    public static final String AUDIO_X_MPEG3 = "audio/x-mpeg3";
    public static final String AUDIO_X_MPG = "audio/x-mpg";
    public static final String AUDIO_X_WAV = "audio/x-wav";
    public static final String AUDIO_X_WAVE = "audio/x-wave";
    public static final String AUDIO_X_XMF = "audio/x-xmf";
    public static final String IMAGE_BMP = "image/bmp";
    public static final String IMAGE_GIF = "image/gif";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_JPG = "image/jpg";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_UNSPECIFIED = "image/*";
    public static final String IMAGE_WBMP = "image/vnd.wap.wbmp";
    public static final String IMAGE_XBMP = "image/x-bmp";
    public static final String IMAGE_X_MS_BMP = "image/x-ms-bmp";
    public static final String MMS_GENERIC = "application/vnd.wap.mms-generic";
    public static final String MMS_MESSAGE = "application/vnd.wap.mms-message";
    public static final String MULTIPART_ALTERNATIVE = "application/vnd.wap.multipart.alternative";
    public static final String MULTIPART_MIXED = "application/vnd.wap.multipart.mixed";
    public static final String MULTIPART_RELATED = "application/vnd.wap.multipart.related";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_VCALENDAR = "text/x-vCalendar";
    public static final String TEXT_VCARD = "text/x-vCard";
    public static final String TEXT_VNOTE = "text/x-vNote";
    public static final String TEXT_VTASK = "text/x-vtodo";
    public static final String VIDEO_3G2 = "video/3gpp2";
    public static final String VIDEO_3GP = "video/3gp";
    public static final String VIDEO_3GPP = "video/3gpp";
    public static final String VIDEO_ASF = "video/x-ms-asf";
    public static final String VIDEO_AVI = "video/avi";
    public static final String VIDEO_DIVX = "video/divx";
    public static final String VIDEO_H263 = "video/h263";
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String VIDEO_MP4V_ES = "video/mp4v-es";
    public static final String VIDEO_MPEG = "video/mpeg";
    public static final String VIDEO_UNSPECIFIED = "video/*";
    public static final String VIDEO_WMV = "video/x-ms-wmv";
    private static final ArrayList<String> sIsAudioTypes = new ArrayList();
    private static final ArrayList<String> sIsImageTypes = new ArrayList();
    private static final ArrayList<String> sIsVideoTypes = new ArrayList();
    private static final ArrayList<String> sSupportedAudioTypes = new ArrayList();
    private static final ArrayList<String> sSupportedContentTypes = new ArrayList();
    private static final ArrayList<String> sSupportedImageTypes = new ArrayList();
    private static final ArrayList<String> sSupportedVideoTypes = new ArrayList();

    static {
        sSupportedContentTypes.add(TEXT_PLAIN);
        sSupportedContentTypes.add(TEXT_HTML);
        sSupportedContentTypes.add(TEXT_VCALENDAR);
        sSupportedContentTypes.add(TEXT_VCARD);
        sSupportedContentTypes.add(TEXT_VNOTE);
        sSupportedContentTypes.add(TEXT_VTASK);
        sSupportedContentTypes.add(IMAGE_JPEG);
        sSupportedContentTypes.add(IMAGE_GIF);
        sSupportedContentTypes.add(IMAGE_WBMP);
        sSupportedContentTypes.add(IMAGE_PNG);
        sSupportedContentTypes.add(IMAGE_JPG);
        sSupportedContentTypes.add(IMAGE_X_MS_BMP);
        sSupportedContentTypes.add(IMAGE_BMP);
        sSupportedContentTypes.add(IMAGE_XBMP);
        sSupportedContentTypes.add(AUDIO_AAC);
        sSupportedContentTypes.add(AUDIO_AAC_MP4);
        sSupportedContentTypes.add(AUDIO_QCELP);
        sSupportedContentTypes.add(AUDIO_EVRC);
        sSupportedContentTypes.add(AUDIO_X_AAC);
        sSupportedContentTypes.add(AUDIO_AAC_ADTS);
        sSupportedContentTypes.add(AUDIO_AMR);
        sSupportedContentTypes.add(AUDIO_IMELODY);
        sSupportedContentTypes.add(AUDIO_TEXT_X_IMY);
        sSupportedContentTypes.add(AUDIO_TEXT_X_IMY_C);
        sSupportedContentTypes.add(AUDIO_MID);
        sSupportedContentTypes.add(AUDIO_MIDI);
        sSupportedContentTypes.add(AUDIO_SP_MIDI);
        sSupportedContentTypes.add(AUDIO_MP3);
        sSupportedContentTypes.add(AUDIO_MP4);
        sSupportedContentTypes.add(AUDIO_MP4A_LATM);
        sSupportedContentTypes.add(AUDIO_MPEG3);
        sSupportedContentTypes.add(AUDIO_MPEG);
        sSupportedContentTypes.add(AUDIO_MPG);
        sSupportedContentTypes.add(AUDIO_X_MID);
        sSupportedContentTypes.add(AUDIO_X_MIDI);
        sSupportedContentTypes.add(AUDIO_X_MP3);
        sSupportedContentTypes.add(AUDIO_X_MPEG3);
        sSupportedContentTypes.add(AUDIO_X_MPEG);
        sSupportedContentTypes.add(AUDIO_X_MPG);
        sSupportedContentTypes.add(AUDIO_X_WAV);
        sSupportedContentTypes.add(AUDIO_3GPP);
        sSupportedContentTypes.add(AUDIO_OGG);
        sSupportedContentTypes.add(APP_OGG);
        sSupportedContentTypes.add(AUDIO_WAV);
        sSupportedContentTypes.add(AUDIO_WAVE);
        sSupportedContentTypes.add(AUDIO_X_WAV);
        sSupportedContentTypes.add(AUDIO_X_WAVE);
        sSupportedContentTypes.add(AUDIO_FLAC);
        sSupportedContentTypes.add(AUDIO_X_FLAC);
        sSupportedContentTypes.add(AUDIO_WMA);
        sSupportedContentTypes.add(AUDIO_M4A);
        sSupportedContentTypes.add(AUDIO_MXMF);
        sSupportedContentTypes.add(AUDIO_XMF);
        sSupportedContentTypes.add(AUDIO_X_XMF);
        sSupportedContentTypes.add(AUDIO_ASF);
        sSupportedContentTypes.add(AUDIO_AMR_WB);
        sSupportedContentTypes.add(AUDIO_QCELP);
        sSupportedContentTypes.add(AUDIO_QCELP_VND);
        sSupportedContentTypes.add(AUDIO_QCP);
        sSupportedContentTypes.add(AUDIO_EVRC);
        sSupportedContentTypes.add(VIDEO_3GPP);
        sSupportedContentTypes.add(VIDEO_3G2);
        sSupportedContentTypes.add(VIDEO_H263);
        sSupportedContentTypes.add(VIDEO_MP4);
        sSupportedContentTypes.add(VIDEO_MP4V_ES);
        sSupportedContentTypes.add(VIDEO_3GP);
        sSupportedContentTypes.add(VIDEO_AVI);
        sSupportedContentTypes.add(VIDEO_WMV);
        sSupportedContentTypes.add(VIDEO_ASF);
        sSupportedContentTypes.add(VIDEO_DIVX);
        sSupportedContentTypes.add(VIDEO_MPEG);
        sSupportedContentTypes.add(APP_SMIL);
        sSupportedContentTypes.add(APP_WAP_XHTML);
        sSupportedContentTypes.add(APP_XHTML);
        sSupportedContentTypes.add(APP_DRM_CONTENT);
        sSupportedContentTypes.add("application/vnd.oma.drm.message");
        sSupportedContentTypes.add("application/vnd.oma.drm.rights+wbxml");
        sSupportedImageTypes.add(IMAGE_JPEG);
        sSupportedImageTypes.add(IMAGE_GIF);
        sSupportedImageTypes.add(IMAGE_WBMP);
        sSupportedImageTypes.add(IMAGE_PNG);
        sSupportedImageTypes.add(IMAGE_JPG);
        sSupportedImageTypes.add(IMAGE_BMP);
        sSupportedImageTypes.add(IMAGE_XBMP);
        sSupportedImageTypes.add(IMAGE_X_MS_BMP);
        sSupportedAudioTypes.add(AUDIO_AAC);
        sSupportedAudioTypes.add(AUDIO_AAC_MP4);
        sSupportedAudioTypes.add(AUDIO_QCELP);
        sSupportedAudioTypes.add(AUDIO_EVRC);
        sSupportedAudioTypes.add(AUDIO_X_AAC);
        sSupportedAudioTypes.add(AUDIO_AAC_ADTS);
        sSupportedAudioTypes.add(AUDIO_AMR);
        sSupportedAudioTypes.add(AUDIO_IMELODY);
        sSupportedAudioTypes.add(AUDIO_TEXT_X_IMY);
        sSupportedAudioTypes.add(AUDIO_TEXT_X_IMY_C);
        sSupportedAudioTypes.add(AUDIO_MID);
        sSupportedAudioTypes.add(AUDIO_MIDI);
        sSupportedAudioTypes.add(AUDIO_SP_MIDI);
        sSupportedAudioTypes.add(AUDIO_MP3);
        sSupportedAudioTypes.add(AUDIO_MPEG3);
        sSupportedAudioTypes.add(AUDIO_MPEG);
        sSupportedAudioTypes.add(AUDIO_MPG);
        sSupportedAudioTypes.add(AUDIO_MP4);
        sSupportedAudioTypes.add(AUDIO_MP4A_LATM);
        sSupportedAudioTypes.add(AUDIO_X_MID);
        sSupportedAudioTypes.add(AUDIO_X_MIDI);
        sSupportedAudioTypes.add(AUDIO_X_MP3);
        sSupportedAudioTypes.add(AUDIO_X_MPEG3);
        sSupportedAudioTypes.add(AUDIO_X_MPEG);
        sSupportedAudioTypes.add(AUDIO_X_MPG);
        sSupportedAudioTypes.add(AUDIO_X_WAV);
        sSupportedAudioTypes.add(AUDIO_3GPP);
        sSupportedAudioTypes.add(AUDIO_OGG);
        sSupportedAudioTypes.add(APP_OGG);
        sSupportedAudioTypes.add(AUDIO_WAV);
        sSupportedAudioTypes.add(AUDIO_WAVE);
        sSupportedAudioTypes.add(AUDIO_X_WAV);
        sSupportedAudioTypes.add(AUDIO_X_WAVE);
        sSupportedAudioTypes.add(AUDIO_FLAC);
        sSupportedAudioTypes.add(AUDIO_X_FLAC);
        sSupportedAudioTypes.add(AUDIO_WMA);
        sSupportedAudioTypes.add(AUDIO_M4A);
        sSupportedAudioTypes.add(AUDIO_MXMF);
        sSupportedAudioTypes.add(AUDIO_XMF);
        sSupportedAudioTypes.add(AUDIO_X_XMF);
        sSupportedAudioTypes.add(AUDIO_ASF);
        sSupportedAudioTypes.add(AUDIO_AMR_WB);
        sSupportedAudioTypes.add(AUDIO_QCELP);
        sSupportedAudioTypes.add(AUDIO_QCELP_VND);
        sSupportedAudioTypes.add(AUDIO_QCP);
        sSupportedAudioTypes.add(AUDIO_EVRC);
        sSupportedVideoTypes.add(VIDEO_3GPP);
        sSupportedVideoTypes.add(VIDEO_3G2);
        sSupportedVideoTypes.add(VIDEO_H263);
        sSupportedVideoTypes.add(VIDEO_MP4);
        sSupportedVideoTypes.add(VIDEO_MP4V_ES);
        sSupportedVideoTypes.add(VIDEO_3GP);
        sSupportedVideoTypes.add(VIDEO_AVI);
        sSupportedVideoTypes.add(VIDEO_WMV);
        sSupportedVideoTypes.add(VIDEO_ASF);
        sSupportedVideoTypes.add(VIDEO_DIVX);
        sIsImageTypes.add(IMAGE_JPEG);
        sIsImageTypes.add(IMAGE_JPG);
        sIsImageTypes.add(IMAGE_GIF);
        sIsImageTypes.add(IMAGE_WBMP);
        sIsImageTypes.add(IMAGE_PNG);
        sIsImageTypes.add(IMAGE_BMP);
        sIsImageTypes.add(IMAGE_XBMP);
        sIsImageTypes.add(IMAGE_X_MS_BMP);
        sIsAudioTypes.add(AUDIO_AAC);
        sIsAudioTypes.add(AUDIO_X_AAC);
        sIsAudioTypes.add(AUDIO_AAC_ADTS);
        sIsAudioTypes.add(AUDIO_AMR);
        sIsAudioTypes.add(AUDIO_IMELODY);
        sIsAudioTypes.add(AUDIO_TEXT_X_IMY);
        sIsAudioTypes.add(AUDIO_TEXT_X_IMY_C);
        sIsAudioTypes.add(AUDIO_MID);
        sIsAudioTypes.add(AUDIO_MIDI);
        sIsAudioTypes.add(AUDIO_SP_MIDI);
        sIsAudioTypes.add(AUDIO_MP3);
        sIsAudioTypes.add(AUDIO_MPEG3);
        sIsAudioTypes.add(AUDIO_MPEG);
        sIsAudioTypes.add(AUDIO_MPG);
        sIsAudioTypes.add(AUDIO_MP4);
        sIsAudioTypes.add(AUDIO_MP4A_LATM);
        sIsAudioTypes.add(AUDIO_X_MID);
        sIsAudioTypes.add(AUDIO_X_MIDI);
        sIsAudioTypes.add(AUDIO_X_MP3);
        sIsAudioTypes.add(AUDIO_X_MPEG3);
        sIsAudioTypes.add(AUDIO_X_MPEG);
        sIsAudioTypes.add(AUDIO_X_MPG);
        sIsAudioTypes.add(AUDIO_3GPP);
        sIsAudioTypes.add(AUDIO_OGG);
        sIsAudioTypes.add(APP_OGG);
        sIsAudioTypes.add(AUDIO_MMF);
        sIsAudioTypes.add(AUDIO_WAV);
        sIsAudioTypes.add(AUDIO_WAVE);
        sIsAudioTypes.add(AUDIO_X_WAV);
        sIsAudioTypes.add(AUDIO_X_WAVE);
        sIsAudioTypes.add(AUDIO_FLAC);
        sIsAudioTypes.add(AUDIO_X_FLAC);
        sIsAudioTypes.add(AUDIO_WMA);
        sIsAudioTypes.add(AUDIO_M4A);
        sIsAudioTypes.add(AUDIO_MXMF);
        sIsAudioTypes.add(AUDIO_XMF);
        sIsAudioTypes.add(AUDIO_X_XMF);
        sIsAudioTypes.add(AUDIO_ASF);
        sIsAudioTypes.add(AUDIO_AMR_WB);
        sIsAudioTypes.add(AUDIO_QCELP);
        sIsAudioTypes.add(AUDIO_QCELP_VND);
        sIsAudioTypes.add(AUDIO_QCP);
        sIsAudioTypes.add(AUDIO_EVRC);
        sIsVideoTypes.add(VIDEO_3GPP);
        sIsVideoTypes.add(VIDEO_3G2);
        sIsVideoTypes.add(VIDEO_H263);
        sIsVideoTypes.add(VIDEO_MP4);
        sIsVideoTypes.add(VIDEO_MP4V_ES);
        sIsVideoTypes.add(VIDEO_3GP);
        sIsVideoTypes.add(VIDEO_AVI);
        sIsVideoTypes.add(VIDEO_WMV);
        sIsVideoTypes.add(VIDEO_ASF);
        sIsVideoTypes.add(VIDEO_DIVX);
    }

    private ContentType() {
    }

    public static boolean isSupportedType(String contentType) {
        return contentType != null && sSupportedContentTypes.contains(contentType);
    }

    public static boolean isSupportedImageType(String contentType) {
        return isImageType(contentType) && isSupportedType(contentType);
    }

    public static boolean isSupportedAudioType(String contentType) {
        return isAudioType(contentType) && isSupportedType(contentType);
    }

    public static boolean isSupportedVideoType(String contentType) {
        return isVideoType(contentType) && isSupportedType(contentType);
    }

    public static boolean isTextType(String contentType) {
        return contentType != null && contentType.startsWith("text/");
    }

    public static boolean isImageType(String contentType) {
        return contentType != null && (contentType.startsWith("image/") || sSupportedImageTypes.contains(contentType));
    }

    public static boolean isAudioType(String contentType) {
        return contentType != null && (contentType.startsWith("audio/") || sSupportedAudioTypes.contains(contentType));
    }

    public static boolean isVideoType(String contentType) {
        return contentType != null && (contentType.startsWith("video/") || sSupportedVideoTypes.contains(contentType));
    }

    public static boolean isDrmType(String contentType) {
        return contentType != null && (contentType.equals(APP_DRM_CONTENT) || contentType.equals("application/vnd.oma.drm.message") || contentType.equals("application/vnd.oma.drm.rights+wbxml"));
    }

    public static boolean isUnspecified(String contentType) {
        return contentType != null && contentType.endsWith(CharacterSets.MIMENAME_ANY_CHARSET);
    }

    public static ArrayList<String> getImageTypes() {
        return (ArrayList) sSupportedImageTypes.clone();
    }

    public static ArrayList<String> getAudioTypes() {
        return (ArrayList) sSupportedAudioTypes.clone();
    }

    public static ArrayList<String> getVideoTypes() {
        return (ArrayList) sSupportedVideoTypes.clone();
    }

    public static ArrayList<String> getSupportedTypes() {
        return (ArrayList) sSupportedContentTypes.clone();
    }
}
