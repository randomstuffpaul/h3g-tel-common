package com.android.internal.telephony;

import android.net.Uri;
import android.os.SystemProperties;
import com.android.internal.telephony.Call.CallType;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CallDetails {
    public static final int CALL_DOMAIN_AUTOMATIC = 3;
    public static final int CALL_DOMAIN_CS = 1;
    public static final int CALL_DOMAIN_NOT_SET = 4;
    public static final int CALL_DOMAIN_PS = 2;
    public static final int CALL_DOMAIN_UNKNOWN = 0;
    public static final int CALL_TYPE_UNKNOWN = 10;
    public static final int CALL_TYPE_VOICE = 0;
    public static final int CALL_TYPE_VS_RX = 2;
    public static final int CALL_TYPE_VS_TX = 1;
    public static final int CALL_TYPE_VT = 3;
    public static final boolean SHIP_BUILD = "true".equals(SystemProperties.get("ro.product_ship", "false"));
    public int call_domain;
    public boolean call_isMpty;
    public int call_type;
    private Map<String, String> mExtras;

    public CallDetails() {
        this.call_isMpty = false;
        this.call_type = 0;
        this.call_domain = 4;
        this.mExtras = new HashMap();
    }

    public CallDetails(int callType, int callDomain, String[] extraparams) {
        this.call_isMpty = false;
        this.call_type = callType;
        this.call_domain = callDomain;
        this.mExtras = getMapFromExtras(extraparams);
    }

    public CallDetails(CallType callType) {
        this.call_isMpty = false;
        this.mExtras = new HashMap();
        if (callType == CallType.CS_CALL_VOICE) {
            this.call_domain = 1;
            this.call_type = 0;
        } else if (callType == CallType.CS_CALL_VIDEO) {
            this.call_domain = 1;
            this.call_type = 3;
        } else if (callType == CallType.IMS_CALL_VOICE) {
            this.call_domain = 2;
            this.call_type = 0;
        } else if (callType == CallType.IMS_CALL_HDVIDEO) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "hd");
        } else if (callType == CallType.IMS_CALL_QCIFVIDEO) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "qcif");
        } else if (callType == CallType.IMS_CALL_QVGAVIDEO) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "qvga");
        } else if (callType == CallType.IMS_CALL_VIDEO_SHARE_TX) {
            this.call_domain = 2;
            this.call_type = 1;
        } else if (callType == CallType.IMS_CALL_VIDEO_SHARE_RX) {
            this.call_domain = 2;
            this.call_type = 2;
        } else if (callType == CallType.IMS_CALL_HDVIDEO_LAND) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "hd_land");
        } else if (callType == CallType.IMS_CALL_HD720VIDEO) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "hd720");
        } else if (callType == CallType.IMS_CALL_CIFVIDEO) {
            this.call_domain = 2;
            this.call_type = 3;
            this.mExtras.put("resolution", "cif");
        } else {
            this.call_domain = 4;
            this.call_type = 0;
        }
    }

    public CallDetails(CallDetails srcCall) {
        if (srcCall != null) {
            this.call_isMpty = srcCall.call_isMpty;
            this.call_type = srcCall.call_type;
            this.call_domain = srcCall.call_domain;
            this.mExtras = srcCall.mExtras;
            return;
        }
        this.call_isMpty = false;
        this.call_type = 0;
        this.call_domain = 4;
        this.mExtras = new HashMap();
    }

    public void setExtras(String[] extraparams) {
        this.mExtras = getMapFromExtras(extraparams);
    }

    public void setExtraValue(String key, String value) {
        this.mExtras.put(key, value);
    }

    public String getExtraValue(String key) {
        return (String) this.mExtras.get(key);
    }

    public String[] getExtraStrings() {
        return getExtrasFromMap(this.mExtras);
    }

    public static String[] getExtrasFromMap(Map<String, String> newExtras) {
        if (newExtras == null) {
            return null;
        }
        String[] extras = new String[newExtras.size()];
        if (extras != null) {
            for (Entry<String, String> entry : newExtras.entrySet()) {
                extras[0] = "" + ((String) entry.getKey()) + "=" + ((String) entry.getValue());
            }
        }
        return extras;
    }

    public static Map<String, String> getMapFromExtras(String[] extras) {
        return getMapFromExtras(extras, false);
    }

    public static Map<String, String> getMapFromExtras(String[] extras, boolean needDecode) {
        HashMap<String, String> map = new HashMap();
        if (extras != null) {
            for (String s : extras) {
                int sep_index = s.indexOf(61);
                if (sep_index >= 0) {
                    String key = s.substring(0, sep_index);
                    String value = sep_index + 1 < s.length() ? s.substring(sep_index + 1) : "";
                    if (needDecode) {
                        value = Uri.decode(value);
                    }
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    public void setExtrasFromMap(Map<String, String> newExtras) {
        if (newExtras != null) {
            this.mExtras = newExtras;
        }
    }

    public String getCsvFromExtras() {
        StringBuilder sb = new StringBuilder();
        if (this.mExtras.isEmpty()) {
            return "";
        }
        for (Entry<String, String> entry : this.mExtras.entrySet()) {
            sb.append(((String) entry.getKey()) + "=" + Uri.encode((String) entry.getValue()) + "|");
        }
        return sb.substring(0, sb.length() - 1);
    }

    public void setExtrasFromCsv(String newExtras) {
        this.mExtras = getMapFromExtras(newExtras.split("\\|"), true);
    }

    public void setIsMpty(boolean isMpty) {
        this.call_isMpty = isMpty;
    }

    public CallType toCallType() {
        boolean isConferenceCall = this.mExtras.containsKey("participants");
        String resolution = (String) this.mExtras.get("resolution");
        if (this.call_domain == 2) {
            if (this.call_type == 0) {
                if (isConferenceCall) {
                    return CallType.IMS_CALL_CONFERENCE;
                }
                return CallType.IMS_CALL_VOICE;
            } else if (this.call_type == 3) {
                if (isConferenceCall) {
                    return CallType.IMS_CALL_CONFERENCE;
                }
                if ("qcif".equals(resolution)) {
                    return CallType.IMS_CALL_QCIFVIDEO;
                }
                if ("qvga".equals(resolution)) {
                    return CallType.IMS_CALL_QVGAVIDEO;
                }
                if ("hd_land".equals(resolution)) {
                    return CallType.IMS_CALL_HDVIDEO_LAND;
                }
                if ("hd720".equals(resolution)) {
                    return CallType.IMS_CALL_HD720VIDEO;
                }
                if ("cif".equals(resolution)) {
                    return CallType.IMS_CALL_CIFVIDEO;
                }
                return CallType.IMS_CALL_HDVIDEO;
            } else if (this.call_type == 1) {
                return CallType.IMS_CALL_VIDEO_SHARE_TX;
            } else {
                if (this.call_type == 2) {
                    return CallType.IMS_CALL_VIDEO_SHARE_RX;
                }
                return CallType.NO_CALL;
            }
        } else if (this.call_domain != 1) {
            return CallType.NO_CALL;
        } else {
            if (this.call_type == 0) {
                return CallType.CS_CALL_VOICE;
            }
            if (this.call_type == 3) {
                return CallType.CS_CALL_VIDEO;
            }
            return CallType.NO_CALL;
        }
    }

    public String toString() {
        if (SHIP_BUILD) {
            return "type " + this.call_type + " domain " + this.call_domain + " isMpty " + this.call_isMpty + " extras : xxxxxxxxxx";
        }
        return "type " + this.call_type + " domain " + this.call_domain + " isMpty " + this.call_isMpty + " " + getCsvFromExtras();
    }

    public boolean isChanged(CallDetails details) {
        boolean changed = false;
        if (details == null || details == this) {
            return false;
        }
        if (this.call_type != details.call_type || this.call_domain != details.call_domain) {
            return true;
        }
        if (!this.mExtras.equals(details.mExtras)) {
            changed = true;
        }
        return changed;
    }
}
