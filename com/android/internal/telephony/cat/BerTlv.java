package com.android.internal.telephony.cat;

import java.util.List;

class BerTlv {
    public static final int BER_EVENT_DOWNLOAD_TAG = 214;
    public static final int BER_MENU_SELECTION_TAG = 211;
    public static final int BER_PROACTIVE_COMMAND_TAG = 208;
    public static final int BER_SMS_PP_DATA_DOWNLOAD_TAG = 209;
    public static final int BER_UNKNOWN_TAG = 0;
    private List<ComprehensionTlv> mCompTlvs = null;
    private boolean mLengthValid = true;
    private int mTag = 0;

    private BerTlv(int tag, List<ComprehensionTlv> ctlvs, boolean lengthValid) {
        this.mTag = tag;
        this.mCompTlvs = ctlvs;
        this.mLengthValid = lengthValid;
    }

    public List<ComprehensionTlv> getComprehensionTlvs() {
        return this.mCompTlvs;
    }

    public int getTag() {
        return this.mTag;
    }

    public boolean isLengthValid() {
        return this.mLengthValid;
    }

    public static BerTlv decode(byte[] data) throws ResultException {
        ResultException e;
        int endIndex = data.length;
        int length = 0;
        boolean isLengthValid = true;
        int curIndex = 0 + 1;
        int curIndex2;
        try {
            int tag = data[0] & 255;
            if (tag == BER_PROACTIVE_COMMAND_TAG) {
                curIndex2 = curIndex + 1;
                try {
                    int temp = data[curIndex] & 255;
                    if (temp < 128) {
                        length = temp;
                    } else if (temp == 129) {
                        curIndex = curIndex2 + 1;
                        temp = data[curIndex2] & 255;
                        if (temp < 128) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "length < 0x80 length=" + Integer.toHexString(0) + " curIndex=" + curIndex + " endIndex=" + endIndex);
                        }
                        length = temp;
                        curIndex2 = curIndex;
                    } else {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Expected first byte to be length or a length tag and < 0x81 byte= " + Integer.toHexString(temp) + " curIndex=" + curIndex2 + " endIndex=" + endIndex);
                    }
                } catch (IndexOutOfBoundsException e2) {
                    throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING, "IndexOutOfBoundsException  curIndex=" + curIndex2 + " endIndex=" + endIndex);
                } catch (ResultException e3) {
                    e = e3;
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, e.explanation());
                }
            } else if (ComprehensionTlvTag.COMMAND_DETAILS.value() == (tag & -129)) {
                tag = 0;
                curIndex2 = 0;
            } else {
                curIndex2 = curIndex;
            }
            if (endIndex - curIndex2 < length) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Command had extra data endIndex=" + endIndex + " curIndex=" + curIndex2 + " length=" + length);
            }
            List<ComprehensionTlv> ctlvs = ComprehensionTlv.decodeMany(data, curIndex2);
            if (tag == BER_PROACTIVE_COMMAND_TAG) {
                int totalLength = 0;
                for (ComprehensionTlv item : ctlvs) {
                    int itemLength = item.getLength();
                    if (itemLength >= 128 && itemLength <= 255) {
                        totalLength += itemLength + 3;
                    } else if (itemLength < 0 || itemLength >= 128) {
                        isLengthValid = false;
                        break;
                    } else {
                        totalLength += itemLength + 2;
                    }
                }
                if (length != totalLength) {
                    isLengthValid = false;
                }
            }
            return new BerTlv(tag, ctlvs, isLengthValid);
        } catch (IndexOutOfBoundsException e4) {
            curIndex2 = curIndex;
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING, "IndexOutOfBoundsException  curIndex=" + curIndex2 + " endIndex=" + endIndex);
        } catch (ResultException e5) {
            e = e5;
            curIndex2 = curIndex;
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, e.explanation());
        }
    }
}
