package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import com.android.internal.telephony.cat.AppInterface.CommandType;

class CommandParams {
    boolean hasIconTag = false;
    CommandDetails mCmdDet;

    CommandParams(CommandDetails cmdDet) {
        this.mCmdDet = cmdDet;
    }

    CommandType getCommandType() {
        return CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) {
        return true;
    }

    public String toString() {
        return this.mCmdDet.toString();
    }

    void setHasIconTag(boolean value) {
        this.hasIconTag = value;
    }
}
