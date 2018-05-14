package android.telephony.gsm;

public class CbConfig {
    public boolean bCBEnabled;
    public short[] msgIDs;
    public int msgIdCount;
    public char msgIdMaxCount;
    public char selectedId;

    public String toString() {
        return super.toString() + "CB ENABLED: " + this.bCBEnabled + "selectedId" + this.selectedId + " msgIdMaxCount:" + this.msgIdMaxCount + "msgIdCount" + this.msgIdCount;
    }
}
