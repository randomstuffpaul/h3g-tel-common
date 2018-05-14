package com.android.internal.telephony.cat;

import com.android.internal.telephony.uicc.IccUtils;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/* compiled from: ResponseData */
class ProvideLocalInfoLangSetting extends ResponseData {
    private byte[] langType = new byte[2];
    private byte tag = (byte) -83;
    private byte tagLen = (byte) 2;

    public ProvideLocalInfoLangSetting(String langName) {
        CatLog.m0d((Object) this, "Inside ProvideLocalinfolangSetting method, lenguage : " + langName);
        String[] langString = Locale.getISOLanguages();
        for (int i = 0; i < langString.length; i++) {
            if (langName.equals(langString[i])) {
                CatLog.m0d((Object) this, "Value of langString : " + langString[i]);
                this.langType = langString[i].getBytes();
                CatLog.m0d((Object) this, "Value of langtype byte" + IccUtils.bytesToHexString(this.langType));
                return;
            }
        }
    }

    public void format(ByteArrayOutputStream buf) {
        buf.write(this.tag);
        buf.write(this.tagLen);
        for (int i = 0; i < 2; i++) {
            buf.write(this.langType[i]);
        }
    }
}
