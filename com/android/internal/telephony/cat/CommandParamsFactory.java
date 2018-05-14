package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import com.sec.android.app.CscFeature;
import java.util.Iterator;
import java.util.List;

class CommandParamsFactory extends Handler {
    static final int DTTZ_SETTING = 3;
    static final int LANGUAGE_SETTING = 4;
    static final int LOAD_MULTI_ICONS = 2;
    static final int LOAD_NO_ICON = 0;
    static final int LOAD_SINGLE_ICON = 1;
    static final int MSG_ID_LOAD_ICON_DONE = 1;
    static final int REFRESH_NAA_APPLICATION_RESET_3G = 5;
    static final int REFRESH_NAA_FILE_CHANGE = 1;
    static final int REFRESH_NAA_INIT = 3;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE = 2;
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE = 0;
    static final int REFRESH_NAA_ROAMING_RESET_3G = 7;
    static final int REFRESH_NAA_SESSION_RESET_3G = 6;
    static final int REFRESH_UICC_RESET = 4;
    static final int SETUP_CALL_CONFIRM_TIMEOUT = 40;
    private static CommandParamsFactory sInstance = null;
    String[] disabledCmdList = null;
    String disabledProactiveCmd;
    private RilMessageDecoder mCaller = null;
    private CommandParams mCmdParams = null;
    private int mIconLoadState = 0;
    private IconLoader mIconLoader;
    private int mSlotId;

    static /* synthetic */ class C00561 {
        static final /* synthetic */ int[] f8xca33cf42 = new int[CommandType.values().length];

        static {
            try {
                f8xca33cf42[CommandType.SET_UP_MENU.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f8xca33cf42[CommandType.SELECT_ITEM.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f8xca33cf42[CommandType.DISPLAY_TEXT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f8xca33cf42[CommandType.SET_UP_IDLE_MODE_TEXT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                f8xca33cf42[CommandType.GET_INKEY.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                f8xca33cf42[CommandType.GET_INPUT.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                f8xca33cf42[CommandType.SEND_DTMF.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                f8xca33cf42[CommandType.SEND_SMS.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                f8xca33cf42[CommandType.SEND_SS.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                f8xca33cf42[CommandType.SEND_USSD.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                f8xca33cf42[CommandType.GET_CHANNEL_STATUS.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                f8xca33cf42[CommandType.SET_UP_CALL.ordinal()] = 12;
            } catch (NoSuchFieldError e12) {
            }
            try {
                f8xca33cf42[CommandType.REFRESH.ordinal()] = 13;
            } catch (NoSuchFieldError e13) {
            }
            try {
                f8xca33cf42[CommandType.LAUNCH_BROWSER.ordinal()] = 14;
            } catch (NoSuchFieldError e14) {
            }
            try {
                f8xca33cf42[CommandType.PLAY_TONE.ordinal()] = 15;
            } catch (NoSuchFieldError e15) {
            }
            try {
                f8xca33cf42[CommandType.SET_UP_EVENT_LIST.ordinal()] = 16;
            } catch (NoSuchFieldError e16) {
            }
            try {
                f8xca33cf42[CommandType.PROVIDE_LOCAL_INFORMATION.ordinal()] = 17;
            } catch (NoSuchFieldError e17) {
            }
            try {
                f8xca33cf42[CommandType.LANGUAGE_NOTIFICATION.ordinal()] = 18;
            } catch (NoSuchFieldError e18) {
            }
            try {
                f8xca33cf42[CommandType.OPEN_CHANNEL.ordinal()] = 19;
            } catch (NoSuchFieldError e19) {
            }
            try {
                f8xca33cf42[CommandType.CLOSE_CHANNEL.ordinal()] = 20;
            } catch (NoSuchFieldError e20) {
            }
            try {
                f8xca33cf42[CommandType.RECEIVE_DATA.ordinal()] = 21;
            } catch (NoSuchFieldError e21) {
            }
            try {
                f8xca33cf42[CommandType.SEND_DATA.ordinal()] = 22;
            } catch (NoSuchFieldError e22) {
            }
            try {
                f8xca33cf42[CommandType.ACTIVATE.ordinal()] = 23;
            } catch (NoSuchFieldError e23) {
            }
        }
    }

    static synchronized CommandParamsFactory getInstance(RilMessageDecoder caller, IccFileHandler fh) {
        CommandParamsFactory commandParamsFactory;
        synchronized (CommandParamsFactory.class) {
            if (sInstance != null) {
                commandParamsFactory = sInstance;
            } else if (fh != null) {
                commandParamsFactory = new CommandParamsFactory(caller, fh);
            } else {
                commandParamsFactory = null;
            }
        }
        return commandParamsFactory;
    }

    private CommandParamsFactory(RilMessageDecoder caller, IccFileHandler fh) {
        this.mCaller = caller;
        this.mIconLoader = IconLoader.getInstance(this, fh);
        this.disabledProactiveCmd = CscFeature.getInstance().getString("CscFeature_RIL_DisableSimToolKitCmds");
        this.disabledCmdList = this.disabledProactiveCmd.split(",");
        this.mSlotId = fh.getPhoneId();
    }

    private CommandDetails processCommandDetails(List<ComprehensionTlv> ctlvs) {
        CommandDetails cmdDet = null;
        if (ctlvs != null) {
            ComprehensionTlv ctlvCmdDet = searchForTag(ComprehensionTlvTag.COMMAND_DETAILS, ctlvs);
            if (ctlvCmdDet != null) {
                try {
                    cmdDet = ValueParser.retrieveCommandDetails(ctlvCmdDet);
                } catch (ResultException e) {
                    CatLog.m0d((Object) this, "processCommandDetails: Failed to procees command details e=" + e);
                }
            }
        }
        return cmdDet;
    }

    void make(BerTlv berTlv) {
        if (berTlv != null) {
            this.mCmdParams = null;
            this.mIconLoadState = 0;
            if (berTlv.getTag() != BerTlv.BER_PROACTIVE_COMMAND_TAG) {
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
            boolean cmdPending = false;
            List<ComprehensionTlv> ctlvs = berTlv.getComprehensionTlvs();
            CommandDetails cmdDet = processCommandDetails(ctlvs);
            if (cmdDet == null) {
                sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                return;
            }
            CommandType cmdType = CommandType.fromInt(cmdDet.typeOfCommand);
            if (cmdType == null) {
                this.mCmdParams = new CommandParams(cmdDet);
                sendCmdParams(ResultCode.BEYOND_TERMINAL_CAPABILITY);
            } else if (berTlv.isLengthValid()) {
                try {
                    switch (C00561.f8xca33cf42[cmdType.ordinal()]) {
                        case 1:
                            cmdPending = processSelectItem(cmdDet, ctlvs);
                            break;
                        case 2:
                            cmdPending = processSelectItem(cmdDet, ctlvs);
                            break;
                        case 3:
                            cmdPending = processDisplayText(cmdDet, ctlvs);
                            break;
                        case 4:
                            cmdPending = processSetUpIdleModeText(cmdDet, ctlvs);
                            break;
                        case 5:
                            cmdPending = processGetInkey(cmdDet, ctlvs);
                            break;
                        case 6:
                            cmdPending = processGetInput(cmdDet, ctlvs);
                            break;
                        case 7:
                            cmdPending = processSendDTMF(cmdDet, ctlvs);
                            break;
                        case 8:
                            cmdPending = processSMSCommand(cmdDet, ctlvs);
                            break;
                        case 9:
                            cmdPending = processSendSS(cmdDet, ctlvs);
                            break;
                        case 10:
                            cmdPending = processSendUSSD(cmdDet, ctlvs);
                            break;
                        case 11:
                            cmdPending = processGetChannelStatus(cmdDet, ctlvs);
                            break;
                        case 12:
                            cmdPending = processSetupCall(cmdDet, ctlvs);
                            break;
                        case 13:
                            processRefresh(cmdDet, ctlvs);
                            cmdPending = false;
                            break;
                        case 14:
                            cmdPending = processLaunchBrowser(cmdDet, ctlvs);
                            break;
                        case 15:
                            cmdPending = processPlayTone(cmdDet, ctlvs);
                            break;
                        case 16:
                            cmdPending = processSetUpEventList(cmdDet, ctlvs);
                            break;
                        case 17:
                            cmdPending = processProvideLocalInfo(cmdDet, ctlvs);
                            break;
                        case 18:
                            cmdPending = processLanguageNotification(cmdDet, ctlvs);
                            break;
                        case 19:
                            cmdPending = processOpenChannel(cmdDet, ctlvs);
                            break;
                        case 20:
                            cmdPending = processCloseChannel(cmdDet, ctlvs);
                            break;
                        case 21:
                            cmdPending = processReceiveData(cmdDet, ctlvs);
                            break;
                        case 22:
                            cmdPending = processSendData(cmdDet, ctlvs);
                            break;
                        case 23:
                            this.mCmdParams = new CommandParams(cmdDet);
                            break;
                        default:
                            this.mCmdParams = new CommandParams(cmdDet);
                            sendCmdParams(ResultCode.CMD_TYPE_NOT_UNDERSTOOD);
                            return;
                    }
                    if (!cmdPending) {
                        sendCmdParams(ResultCode.OK);
                    }
                } catch (ResultException e) {
                    CatLog.m0d((Object) this, "make: caught ResultException e=" + e);
                    this.mCmdParams = new CommandParams(cmdDet);
                    sendCmdParams(e.result());
                }
            } else {
                this.mCmdParams = new CommandParams(cmdDet);
                sendCmdParams(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                sendCmdParams(setIcons(msg.obj));
                return;
            default:
                return;
        }
    }

    private ResultCode setIcons(Object data) {
        if (data == null) {
            return ResultCode.OK;
        }
        switch (this.mIconLoadState) {
            case 1:
                this.mCmdParams.setIcon((Bitmap) data);
                break;
            case 2:
                for (Bitmap icon : (Bitmap[]) data) {
                    this.mCmdParams.setIcon(icon);
                }
                break;
        }
        return ResultCode.OK;
    }

    private void sendCmdParams(ResultCode resCode) {
        this.mCaller.sendMsgParamsDecoded(resCode, this.mCmdParams);
    }

    private ComprehensionTlv searchForTag(ComprehensionTlvTag tag, List<ComprehensionTlv> ctlvs) {
        return searchForNextTag(tag, ctlvs.iterator());
    }

    private ComprehensionTlv searchForNextTag(ComprehensionTlvTag tag, Iterator<ComprehensionTlv> iter) {
        int tagValue = tag.value();
        while (iter.hasNext()) {
            ComprehensionTlv ctlv = (ComprehensionTlv) iter.next();
            if (ctlv.getTag() == tagValue) {
                return ctlv;
            }
        }
        return null;
    }

    private ComprehensionTlv searchForDupTag(ComprehensionTlvTag tag, List<ComprehensionTlv> ctlvs) {
        int tagValue = tag.value();
        int i = 0;
        for (ComprehensionTlv ctlv : ctlvs) {
            if (ctlv.getTag() == tagValue) {
                i++;
                if (i > 1) {
                    return ctlv;
                }
            }
        }
        return null;
    }

    private boolean processDisplayText(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process DisplayText");
        TextMessage textMsg = new TextMessage();
        boolean hasIcon = false;
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveTextString(ctlv);
        } else if (2 == CatService.getPackageType(this.mSlotId)) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        if (textMsg.text == null) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
        boolean z;
        if (searchForTag(ComprehensionTlvTag.IMMEDIATE_RESPONSE, ctlvs) != null) {
            textMsg.responseNeeded = false;
        }
        if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
            hasIcon = true;
        }
        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
        if (ctlv != null) {
            textMsg.duration = ValueParser.retrieveDuration(ctlv);
        }
        textMsg.isHighPriority = (cmdDet.commandQualifier & 1) != 0;
        if ((cmdDet.commandQualifier & 128) != 0) {
            z = true;
        } else {
            z = false;
        }
        textMsg.userClear = z;
        if (!textMsg.responseNeeded && textMsg.userClear && textMsg.duration == null) {
            textMsg.duration = new Duration(6000, TimeUnit.SECOND);
            CatLog.m0d((Object) this, "display forever");
        }
        if ("SPR-CDMA".equals("") && textMsg.duration == null && !textMsg.userClear) {
            textMsg.duration = new Duration(1440, TimeUnit.MINUTE);
            textMsg.responseNeeded = false;
            textMsg.userClear = true;
            CatLog.m0d((Object) this, "SPR display text for 24 Hrs");
        }
        ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
        if (ctlv != null) {
            textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
        }
        this.mCmdParams = new DisplayTextParams(cmdDet, textMsg, hasIcon);
        if (null == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processSetUpIdleModeText(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SetUpIdleModeText");
        TextMessage textMsg = new TextMessage();
        boolean hasIcon = false;
        if (!isDisabledCmd("SetupIdleModeText")) {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
            if (ctlv != null) {
                textMsg.text = ValueParser.retrieveTextString(ctlv);
            }
            if (textMsg.text == null) {
                CatLog.m0d((Object) this, "null");
                if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            } else if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                hasIcon = true;
            }
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
        }
        this.mCmdParams = new DisplayTextParams(cmdDet, textMsg, hasIcon);
        if (isDisabledCmd("SetupIdleModeText") || null == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processGetInkey(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process GetInkey");
        Input input = new Input();
        IconId iconId = null;
        boolean hasIcon = false;
        if (!isDisabledCmd("GetInkey")) {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
            if (ctlv != null) {
                input.text = ValueParser.retrieveTextString(ctlv);
                if (input.text == null) {
                    throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
                }
                boolean z;
                ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
                if (ctlv != null) {
                    iconId = ValueParser.retrieveIconId(ctlv);
                    hasIcon = true;
                }
                ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
                if (ctlv != null) {
                    input.duration = ValueParser.retrieveDuration(ctlv);
                }
                input.minLen = 1;
                input.maxLen = 1;
                if ((cmdDet.commandQualifier & 1) == 0) {
                    z = true;
                } else {
                    z = false;
                }
                input.digitOnly = z;
                if ((cmdDet.commandQualifier & 2) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                input.ucs2 = z;
                if ((cmdDet.commandQualifier & 4) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                input.yesNo = z;
                if ((cmdDet.commandQualifier & 128) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                input.helpAvailable = z;
                input.echo = true;
                ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
                if (ctlv != null) {
                    input.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
                }
            } else {
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            }
        }
        this.mCmdParams = new GetInputParams(cmdDet, input, hasIcon);
        if (isDisabledCmd("GetInkey") || iconId == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processGetInput(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process GetInput");
        Input input = new Input();
        boolean hasIcon = false;
        if (!isDisabledCmd("GetInput")) {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
            if (ctlv != null) {
                input.text = ValueParser.retrieveTextString(ctlv);
                if (input.text == null) {
                    input.text = "";
                }
                ctlv = searchForTag(ComprehensionTlvTag.RESPONSE_LENGTH, ctlvs);
                if (ctlv != null) {
                    try {
                        byte[] rawValue = ctlv.getRawValue();
                        int valueIndex = ctlv.getValueIndex();
                        input.minLen = rawValue[valueIndex] & 255;
                        input.maxLen = rawValue[valueIndex + 1] & 255;
                        if (input.minLen > input.maxLen) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                        }
                        boolean z;
                        ctlv = searchForTag(ComprehensionTlvTag.DEFAULT_TEXT, ctlvs);
                        if (ctlv != null) {
                            input.defaultText = ValueParser.retrieveTextString(ctlv);
                        }
                        ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
                        if (ctlv != null) {
                            input.duration = ValueParser.retrieveDuration(ctlv);
                        }
                        if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                            hasIcon = true;
                        }
                        if ((cmdDet.commandQualifier & 1) == 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        input.digitOnly = z;
                        if ((cmdDet.commandQualifier & 2) != 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        input.ucs2 = z;
                        if ((cmdDet.commandQualifier & 4) == 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        input.echo = z;
                        if ((cmdDet.commandQualifier & 8) != 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        input.packed = z;
                        if ((cmdDet.commandQualifier & 128) != 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        input.helpAvailable = z;
                        if (2 == CatService.getPackageType(this.mSlotId)) {
                            if (input.ucs2 && input.maxLen > 1) {
                                input.maxLen /= 2;
                                if (input.maxLen > 70) {
                                    input.maxLen = 70;
                                }
                            }
                        } else if (input.ucs2 && input.maxLen > 70) {
                            input.maxLen = 70;
                        }
                        ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
                        if (ctlv != null) {
                            input.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                    }
                }
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            }
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        this.mCmdParams = new GetInputParams(cmdDet, input, hasIcon);
        if (isDisabledCmd("GetInput") || null == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processRefresh(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process Refresh");
        TextMessage textMsg = new TextMessage();
        switch (cmdDet.commandQualifier) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
                CatLog.m0d((Object) this, "Inside REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE case");
                textMsg.text = "default refresh...";
                textMsg.responseNeeded = false;
                break;
            case 4:
                CatLog.m0d((Object) this, "Inside REFRESH_UICC_RESET case");
                textMsg.text = "default reset...";
                textMsg.responseNeeded = false;
                break;
        }
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
        if (ctlv != null) {
            textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
        }
        this.mCmdParams = new DisplayTextParams(cmdDet, textMsg);
        return false;
    }

    private boolean processSelectItem(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        boolean z;
        CatLog.m0d((Object) this, "process SelectItem");
        Menu menu = new Menu();
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        boolean hasIcon = false;
        if (!isDisabledCmd("SelectItem")) {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
            if (ctlv != null) {
                menu.title = ValueParser.retrieveAlphaId(ctlv);
            }
            boolean is_first = true;
            while (true) {
                ctlv = searchForNextTag(ComprehensionTlvTag.ITEM, iter);
                if (!is_first || ctlv == null || ctlv.getLength() != 0) {
                    if (ctlv == null) {
                        break;
                    }
                    menu.items.add(ValueParser.retrieveItem(ctlv));
                    is_first = false;
                } else if (searchForNextTag(ComprehensionTlvTag.ITEM, iter) != null) {
                    break;
                } else {
                    menu.items.add(null);
                    is_first = false;
                }
            }
            if (2 == CatService.getPackageType(this.mSlotId) && (menu.items.size() == 0 || (menu.items.size() > 1 && menu.items.get(0) == null))) {
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            } else if (menu.items.size() == 0 || (menu.items.size() > 1 && menu.items.get(0) == null)) {
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            } else {
                boolean presentTypeSpecified;
                ctlv = searchForTag(ComprehensionTlvTag.ITEM_ID, ctlvs);
                if (ctlv != null) {
                    menu.defaultItem = ValueParser.retrieveItemId(ctlv) - 1;
                }
                if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                    hasIcon = true;
                }
                if ((cmdDet.commandQualifier & 1) != 0) {
                    presentTypeSpecified = true;
                } else {
                    presentTypeSpecified = false;
                }
                if (presentTypeSpecified) {
                    if ((cmdDet.commandQualifier & 2) == 0) {
                        menu.presentationType = PresentationType.DATA_VALUES;
                    } else {
                        menu.presentationType = PresentationType.NAVIGATION_OPTIONS;
                    }
                }
                if ((cmdDet.commandQualifier & 4) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                menu.softKeyPreferred = z;
                if ((cmdDet.commandQualifier & 128) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                menu.helpAvailable = z;
                ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
                if (ctlv != null) {
                    menu.titleAttrs = ValueParser.retrieveTextAttribute(ctlv);
                }
            }
        }
        if (null != null) {
            z = true;
        } else {
            z = false;
        }
        this.mCmdParams = new SelectItemParams(cmdDet, menu, z, hasIcon);
        if (isDisabledCmd("SelectItem")) {
            return false;
        }
        switch (this.mIconLoadState) {
            case 0:
                return false;
            case 1:
                if (null != null) {
                    this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
                    break;
                }
                break;
            case 2:
                if (null != null) {
                    int[] recordNumbers = null.recordNumbers;
                    if (null != null) {
                        recordNumbers = new int[(null.recordNumbers.length + 1)];
                        recordNumbers[0] = null.recordNumber;
                        System.arraycopy(null.recordNumbers, 0, recordNumbers, 1, null.recordNumbers.length);
                    }
                    this.mIconLoader.loadIcons(recordNumbers, obtainMessage(1));
                    break;
                }
                break;
        }
        return true;
    }

    private boolean processEventNotify(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process EventNotify");
        TextMessage textMsg = new TextMessage();
        boolean hasIcon = false;
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
            if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                hasIcon = true;
            }
            textMsg.responseNeeded = false;
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new DisplayTextParams(cmdDet, textMsg, hasIcon);
            if (null == null) {
                return false;
            }
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processLaunchBrowser(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process LaunchBrowser");
        TextMessage confirmMsg = new TextMessage();
        String url = null;
        String gatewayProxy = null;
        boolean hasIcon = false;
        if (isDisabledCmd("LaunchBrowser")) {
        }
        if (CscFeature.getInstance().getEnableStatus("CscFeature_RIL_EnableLaunchBrowser")) {
            LaunchBrowserMode mode;
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.URL, ctlvs);
            if (ctlv != null) {
                try {
                    byte[] rawValue = ctlv.getRawValue();
                    int valueIndex = ctlv.getValueIndex();
                    int valueLen = ctlv.getLength();
                    if (valueLen > 0) {
                        url = GsmAlphabet.gsm8BitUnpackedToString(rawValue, valueIndex, valueLen);
                    } else {
                        url = null;
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
            if (ctlv != null) {
                gatewayProxy = ValueParser.retrieveTextString(ctlv);
                CatLog.m0d((Object) this, "proxy = " + gatewayProxy);
            }
            confirmMsg.text = ValueParser.retrieveAlphaId(searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs));
            if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                hasIcon = true;
            }
            switch (cmdDet.commandQualifier) {
                case 2:
                    mode = LaunchBrowserMode.USE_EXISTING_BROWSER;
                    break;
                case 3:
                    mode = LaunchBrowserMode.LAUNCH_NEW_BROWSER;
                    break;
                default:
                    mode = LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED;
                    break;
            }
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                confirmMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new LaunchBrowserParams(cmdDet, confirmMsg, url, mode, gatewayProxy, hasIcon);
            if (null == null) {
                return false;
            }
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
            return true;
        }
        CatLog.m0d((Object) this, "BEYOND_TERMINAL_CAPABILITY for processLaunchBrowser");
        throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
    }

    private boolean processPlayTone(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process PlayTone");
        Tone tone = null;
        TextMessage textMsg = new TextMessage();
        Duration duration = null;
        boolean hasIcon = false;
        if (isDisabledCmd("PlayTone")) {
            this.mCmdParams = new PlayToneParams(cmdDet, textMsg, null, null, false, false);
        } else {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.TONE, ctlvs);
            if (ctlv != null && ctlv.getLength() > 0) {
                try {
                    tone = Tone.fromInt(ctlv.getRawValue()[ctlv.getValueIndex()]);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
            if (ctlv != null) {
                textMsg.text = ValueParser.retrieveAlphaId(ctlv);
            }
            ctlv = searchForTag(ComprehensionTlvTag.DURATION, ctlvs);
            if (ctlv != null) {
                duration = ValueParser.retrieveDuration(ctlv);
            }
            if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                hasIcon = true;
            }
            boolean vibrate = (cmdDet.commandQualifier & 1) != 0;
            textMsg.responseNeeded = false;
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new PlayToneParams(cmdDet, textMsg, tone, duration, vibrate, hasIcon);
        }
        if (isDisabledCmd("PlayTone") || null == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processSetupCall(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SetupCall");
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        TextMessage confirmMsg = new TextMessage();
        TextMessage callMsg = new TextMessage();
        String address = null;
        boolean hasIcon = false;
        if (!isDisabledCmd("SetupCall")) {
            confirmMsg.duration = new Duration(40, TimeUnit.SECOND);
            ComprehensionTlv ctlv = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, iter);
            if (2 == CatService.getPackageType(this.mSlotId)) {
                CatLog.m0d((Object) this, "iter.hasNext() : " + iter.hasNext());
                if (!iter.hasNext()) {
                    confirmMsg.text = null;
                    iter = ctlvs.iterator();
                } else if (ctlv != null) {
                    confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
                }
                iter = ctlvs.iterator();
            } else if (ctlv != null) {
                confirmMsg.text = ValueParser.retrieveAlphaId(ctlv);
            }
            if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
                hasIcon = true;
            }
            ctlv = searchForNextTag(ComprehensionTlvTag.ADDRESS, iter);
            if (ctlv != null) {
                address = ValueParser.retrieveAddress(ctlv);
            } else {
                callMsg.text = confirmMsg.text;
                confirmMsg.text = null;
                ctlv = searchForTag(ComprehensionTlvTag.ADDRESS, ctlvs);
                if (ctlv != null) {
                    address = ValueParser.retrieveAddress(ctlv);
                }
            }
            CatLog.m0d((Object) this, "processSetupCall address is : " + address);
            if (callMsg.text == null) {
                ctlv = searchForNextTag(ComprehensionTlvTag.ALPHA_ID, iter);
                if (ctlv != null) {
                    callMsg.text = ValueParser.retrieveAlphaId(ctlv);
                }
            }
            if (callMsg.text == null) {
                callMsg.text = confirmMsg.text;
            }
            if (confirmMsg.text == null) {
                CatLog.m0d((Object) this, "processSetupCall confirmMsg.text is null ");
            } else {
                CatLog.m0d((Object) this, "processSetupCall confirmMsg.text is : " + confirmMsg.text);
            }
            if ("false".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true")) || 2 == CatService.getPackageType(this.mSlotId)) {
                if (searchForTag(ComprehensionTlvTag.SUBADDRESS, ctlvs) != null) {
                    throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
                } else if (searchForTag(ComprehensionTlvTag.DURATION, ctlvs) != null && cmdDet.commandQualifier == 1) {
                    throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
                }
            }
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                if (confirmMsg.text != null) {
                    confirmMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
                    ctlv = searchForNextTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, iter);
                    if (ctlv != null) {
                        callMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
                    }
                } else {
                    callMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
                }
            }
        }
        this.mCmdParams = new CallSetupParams(cmdDet, confirmMsg, callMsg, address, hasIcon);
        if (isDisabledCmd("SetupCall") || (null == null && null == null)) {
            return false;
        }
        this.mIconLoadState = 2;
        int[] recordNumbers = new int[2];
        recordNumbers[0] = null != null ? null.recordNumber : -1;
        recordNumbers[1] = null != null ? null.recordNumber : -1;
        this.mIconLoader.loadIcons(recordNumbers, obtainMessage(1));
        return true;
    }

    private boolean processProvideLocalInfo(CommandDetails cmdDet, List<ComprehensionTlv> list) throws ResultException {
        CatLog.m0d((Object) this, "process ProvideLocalInfo");
        switch (cmdDet.commandQualifier) {
            case 3:
                CatLog.m0d((Object) this, "PLI [DTTZ_SETTING]");
                this.mCmdParams = new CommandParams(cmdDet);
                break;
            case 4:
                CatLog.m0d((Object) this, "PLI [LANGUAGE_SETTING]");
                this.mCmdParams = new CommandParams(cmdDet);
                break;
            default:
                CatLog.m0d((Object) this, "PLI[" + cmdDet.commandQualifier + "] Command Not Supported");
                this.mCmdParams = new CommandParams(cmdDet);
                throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }
        return false;
    }

    private boolean processBIPClient(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CommandType commandType = CommandType.fromInt(cmdDet.typeOfCommand);
        if (commandType != null) {
            CatLog.m0d((Object) this, "process " + commandType.name());
        }
        TextMessage textMsg = new TextMessage();
        IconId iconId = null;
        boolean has_alpha_id = false;
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
            CatLog.m0d((Object) this, "alpha TLV text=" + textMsg.text);
            has_alpha_id = true;
        }
        ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            iconId = ValueParser.retrieveIconId(ctlv);
            textMsg.iconSelfExplanatory = iconId.selfExplanatory;
        }
        textMsg.responseNeeded = false;
        this.mCmdParams = new BIPClientParams(cmdDet, textMsg, has_alpha_id);
        if (iconId == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
        return true;
    }

    public void dispose() {
        this.mIconLoader.dispose();
        this.mIconLoader = null;
        this.mCmdParams = null;
        this.mCaller = null;
        sInstance = null;
    }

    private boolean isDisabledCmd(String cmd) {
        if (this.disabledCmdList.length <= 0) {
            return false;
        }
        for (Object equals : this.disabledCmdList) {
            if (cmd.equals(equals)) {
                return true;
            }
        }
        return false;
    }

    private boolean processSetUpEventList(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SetUpEventList");
        if (isDisabledCmd("SetupEventList")) {
            this.mCmdParams = new SetupEventListParams(cmdDet, 1, new int[]{254});
        } else {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.EVENT_LIST, ctlvs);
            if (ctlv != null) {
                try {
                    byte[] rawValue = ctlv.getRawValue();
                    int valueIndex = ctlv.getValueIndex();
                    int valueLen = ctlv.getLength();
                    if (valueLen != 0) {
                        int[] events = new int[valueLen];
                        for (int i = 0; i < valueLen; i++) {
                            events[i] = rawValue[valueIndex + i];
                            if ((events[i] & 255) == 8) {
                                CatLog.m0d((Object) this, "BEYOND_TERMINAL_CAPABILITY for BROWSER_TERMINATION_EVENT");
                                throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
                            }
                        }
                        this.mCmdParams = new SetupEventListParams(cmdDet, valueLen, events);
                    } else {
                        this.mCmdParams = new SetupEventListParams(cmdDet, 1, new int[]{255});
                    }
                } catch (IndexOutOfBoundsException e) {
                }
            }
        }
        return false;
    }

    private boolean processSendSS(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SendSS");
        TextMessage textMsg = new TextMessage();
        IconId iconId = null;
        boolean hasIcon = false;
        if (isDisabledCmd("SendSs")) {
            throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }
        textMsg.text = ValueParser.retrieveAlphaId(searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs));
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            textMsg.iconSelfExplanatory = ValueParser.retrieveIconId(ctlv).selfExplanatory;
            if (textMsg.iconSelfExplanatory || !(textMsg.text == null || "Default Message".equals(textMsg.text))) {
                iconId = null;
                hasIcon = true;
            } else {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        textMsg.responseNeeded = false;
        ctlv = searchForTag(ComprehensionTlvTag.SS_STRING, ctlvs);
        if (ctlv != null) {
            String ssString = ValueParser.retrieveSSstring(ctlv);
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new SendSSParams(cmdDet, textMsg, ssString, hasIcon);
            if (iconId == null) {
                return false;
            }
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processSendUSSD(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SendUSSD");
        TextMessage textMsg = new TextMessage();
        IconId iconId = null;
        boolean hasIcon = false;
        if (isDisabledCmd("SendUssd")) {
            throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }
        textMsg.text = ValueParser.retrieveAlphaId(searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs));
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs);
        if (ctlv != null) {
            textMsg.iconSelfExplanatory = ValueParser.retrieveIconId(ctlv).selfExplanatory;
            if (textMsg.iconSelfExplanatory || !("Default Message".equals(textMsg.text) || textMsg.text == null)) {
                iconId = null;
                hasIcon = true;
            } else {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
        }
        textMsg.responseNeeded = false;
        ctlv = searchForTag(ComprehensionTlvTag.USSD_STRING, ctlvs);
        if (ctlv != null) {
            byte[] ussdString = ValueParser.retrieveUSSDstring(ctlv);
            if (ussdString == null) {
                ussdString = new byte[0];
            }
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new SendUSSDParams(cmdDet, textMsg, ussdString, hasIcon);
            if (iconId == null) {
                return false;
            }
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(iconId.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processSendDTMF(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SendDTMF");
        TextMessage textMsg = new TextMessage();
        boolean hasIcon = false;
        if (isDisabledCmd("SendDtmf")) {
            throw new ResultException(ResultCode.BEYOND_TERMINAL_CAPABILITY);
        }
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        if (ctlv != null) {
            textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        } else {
            textMsg.text = "Default Message";
        }
        if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
            hasIcon = true;
        }
        textMsg.responseNeeded = false;
        ctlv = searchForTag(ComprehensionTlvTag.DTMF_STRING, ctlvs);
        if (ctlv != null) {
            byte[] dtmfString = ValueParser.retrieveDTMFstring(ctlv);
            ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
            if (ctlv != null) {
                textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
            }
            this.mCmdParams = new SendDTMFParams(cmdDet, textMsg, dtmfString, hasIcon);
            if (null == null) {
                return false;
            }
            this.mIconLoadState = 1;
            this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
            return true;
        }
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processSMSCommand(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process SMS Command");
        TextMessage textMsg = new TextMessage();
        boolean ispackin_required = false;
        String Smscaddress = null;
        String Pdu = null;
        boolean hasIcon = false;
        String Format = SmsMessage.FORMAT_3GPP;
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
        textMsg.text = ValueParser.retrieveAlphaId(ctlv);
        if (2 == CatService.getPackageType(this.mSlotId) && ctlv == null) {
            textMsg.text = "null alphaId, default sending...";
            CatLog.m0d((Object) this, "Catservice stksending default : " + textMsg.text);
        }
        if (searchForTag(ComprehensionTlvTag.ICON_ID, ctlvs) != null) {
            hasIcon = true;
        }
        textMsg.responseNeeded = false;
        if ("false".equals(getSystemProperty("ro.ril.stk_qmi_ril", "true"))) {
            ctlv = searchForTag(ComprehensionTlvTag.ADDRESS, ctlvs);
            if (ctlv != null) {
                Smscaddress = ValueParser.retrieveSMSCaddress(ctlv);
                CatLog.m0d((Object) this, "The Smsc address is " + Smscaddress);
            }
            if ((cmdDet.commandQualifier & 255) == 1) {
                ispackin_required = true;
            }
            if ((cmdDet.commandQualifier & 255) == 0) {
                ispackin_required = false;
            }
            if (2 == CatService.getPackageType(this.mSlotId)) {
                Format = SmsMessage.FORMAT_3GPP2;
                ctlv = searchForTag(ComprehensionTlvTag.SMS_TPDU_CDMA, ctlvs);
                if (ctlv != null) {
                    Pdu = ValueParser.retrieveSMSTPDU_CDMA(ctlv, ispackin_required);
                    CatLog.m0d((Object) this, "The SMS tpdu is " + Pdu);
                } else {
                    CatLog.m0d((Object) this, "SMS tpdu ctlv == null");
                    throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
                }
            }
            ctlv = searchForTag(ComprehensionTlvTag.SMS_TPDU, ctlvs);
            if (ctlv != null) {
                Pdu = ValueParser.retrieveSMSTPDU(ctlv, ispackin_required);
                CatLog.m0d((Object) this, "The SMS tpdu is " + Pdu);
            } else {
                ctlv = searchForTag(ComprehensionTlvTag.SMS_TPDU_CDMA, ctlvs);
                if (ctlv != null) {
                    Pdu = ValueParser.retrieveSMSTPDU_CDMA_Common(ctlv, ispackin_required);
                    Format = SmsMessage.FORMAT_3GPP2;
                    CatLog.m0d((Object) this, "The SMS(3GPP2) tpdu is " + Pdu);
                } else {
                    throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
                }
            }
        }
        ctlv = searchForTag(ComprehensionTlvTag.TEXT_ATTRIBUTE, ctlvs);
        if (ctlv != null) {
            textMsg.textAttributes = ValueParser.retrieveTextAttribute(ctlv);
        }
        this.mCmdParams = new SendSMSParams(cmdDet, textMsg, Smscaddress, Pdu, Format, hasIcon);
        if (null == null) {
            return false;
        }
        this.mIconLoadState = 1;
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        this.mIconLoader.loadIcon(null.recordNumber, obtainMessage(1));
        return true;
    }

    private boolean processLanguageNotification(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        String targetLanguage;
        boolean initialLanguage;
        CatLog.m0d((Object) this, "process Language noti Command");
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.LANGUAGE, ctlvs);
        if (ctlv != null) {
            targetLanguage = ValueParser.retrieveLanguage(ctlv);
        } else {
            targetLanguage = null;
        }
        CatLog.m0d((Object) this, "targetLanguage = " + targetLanguage);
        if (cmdDet.commandQualifier == 0) {
            initialLanguage = true;
        } else {
            initialLanguage = false;
        }
        this.mCmdParams = new LanguageNotificationParams(cmdDet, targetLanguage, initialLanguage);
        return false;
    }

    private boolean processOpenChannel(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        boolean checkBearerDescriptionNull;
        CatLog.m0d((Object) this, "process Open Channel Command");
        BearerDescription bearerDesc = new BearerDescription();
        TextMessage textMsg = new TextMessage();
        TextMessage textMsgUser = new TextMessage();
        TextMessage textMsgPassword = new TextMessage();
        TransportLevel transportLevel = new TransportLevel();
        DataDestinationAddress dataDestAdd = new DataDestinationAddress();
        String networkAccessName = null;
        BearerMode bearerMode = new BearerMode();
        boolean checkTransportLevelNull = false;
        Iterator<ComprehensionTlv> iter = ctlvs.iterator();
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.BEARER_DESCRIPTION, ctlvs);
        if (ctlv != null) {
            bearerDesc = ValueParser.retrieveBearerDescription(ctlv);
            checkBearerDescriptionNull = false;
            if (bearerDesc.bearerGPRS != null) {
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.bearerType = " + bearerDesc.bearerType);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.precedenceClass = " + bearerDesc.bearerGPRS.precedenceClass);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.delayClass = " + bearerDesc.bearerGPRS.delayClass);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.reliabilityClass = " + bearerDesc.bearerGPRS.reliabilityClass);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.peakThroughputClass = " + bearerDesc.bearerGPRS.peakThroughputClass);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.meanThroughputClass = " + bearerDesc.bearerGPRS.meanThroughputClass);
                CatLog.m0d((Object) this, "bearerDesc.bearerGPRS.packetDataProtocolType = " + bearerDesc.bearerGPRS.packetDataProtocolType);
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                ctlv = searchForTag(ComprehensionTlvTag.NETWORK_ACCESS_NAME, ctlvs);
                if (ctlv != null) {
                    networkAccessName = ValueParser.retrieveNetworkAccessName(ctlv);
                    CatLog.m0d((Object) this, "networkAccessName = " + networkAccessName);
                } else {
                    CatLog.m0d((Object) this, "Warning: network access name ctlv is null");
                }
            } else if (bearerDesc.bearerCSD != null) {
                CatLog.m0d((Object) this, "bearerDesc.bearerCSD.bearerType = " + bearerDesc.bearerType);
                CatLog.m0d((Object) this, "bearerDesc.bearerCSD.bearerCSD.dataRate = " + bearerDesc.bearerCSD.dataRate);
                CatLog.m0d((Object) this, "bearerDesc.bearerCSD.bearerService = " + bearerDesc.bearerCSD.bearerService);
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                ctlv = searchForTag(ComprehensionTlvTag.ADDRESS, ctlvs);
                if (ctlv != null) {
                    networkAccessName = ValueParser.retrieveNetworkAccessName(ctlv);
                    CatLog.m0d((Object) this, "networkAccessName = " + networkAccessName);
                } else {
                    CatLog.m0d((Object) this, "Exception: network access name ctlv is null");
                }
            } else if (bearerDesc.bearerDefault) {
                CatLog.m0d((Object) this, "bearerDesc.bearerDefault = " + bearerDesc.bearerDefault);
            } else if (bearerDesc.bearerType == (byte) 11) {
                CatLog.m0d((Object) this, "bearerDesc.bearerType = BEARER_E_UTRAN");
            } else if (bearerDesc.bearerType == (byte) 8) {
                CatLog.m0d((Object) this, "bearerDesc.bearerType = BEARER_CDMA");
            } else {
                CatLog.m0d((Object) this, "Warning: Bearer description not identified");
                checkBearerDescriptionNull = true;
            }
        } else {
            CatLog.m0d((Object) this, "Warning: bearer description ctlv is null");
            checkBearerDescriptionNull = true;
        }
        CatLog.m0d((Object) this, "Moving onto the next TAG");
        ctlv = searchForNextTag(ComprehensionTlvTag.TRANSPORT_LEVEL, iter);
        if (ctlv != null) {
            transportLevel = ValueParser.retrieveTransportLevel(ctlv);
            CatLog.m0d((Object) this, "transportLevel.transportProtocol = " + transportLevel.transportProtocol);
            CatLog.m0d((Object) this, "transportLevel.portNumber = " + transportLevel.portNumber);
            switch (transportLevel.transportProtocol) {
                case (byte) 1:
                case (byte) 2:
                    checkTransportLevelNull = false;
                    if (checkBearerDescriptionNull) {
                        CatLog.m0d((Object) this, "Exception: Bearer Description ctlv is null");
                        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
                    }
                    break;
                case (byte) 3:
                case (byte) 4:
                case (byte) 5:
                    CatLog.m0d((Object) this, "Transport Protocol Match Found");
                    checkTransportLevelNull = false;
                    break;
            }
        }
        checkTransportLevelNull = true;
        CatLog.m0d((Object) this, "Warning: Transport Level ctlv is null");
        if (checkTransportLevelNull && checkBearerDescriptionNull) {
            CatLog.m0d((Object) this, "Exception: Both Bearer Description and Transport Level ctlv are null");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        if (!(checkTransportLevelNull || checkBearerDescriptionNull)) {
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            ctlv = searchForNextTag(ComprehensionTlvTag.DATA_DESTINATION_ADDRESS, iter);
            if (ctlv != null) {
                dataDestAdd = ValueParser.retrieveDataDestinationAddress(ctlv);
                if (dataDestAdd != null) {
                    CatLog.m0d((Object) this, "dataDestAdd.addressType = " + dataDestAdd.addressType);
                    CatLog.m0d((Object) this, "dataDestAdd.address = " + IccUtils.bytesToHexString(dataDestAdd.address));
                } else {
                    CatLog.m0d((Object) this, "Data Destination Address is null. Supply Dynamic IP");
                }
            } else {
                CatLog.m0d((Object) this, "Warning: data Destination Address ctlv is null");
            }
        }
        CatLog.m0d((Object) this, "Moving onto the next TAG");
        ctlv = searchForTag(ComprehensionTlvTag.BUFFER_SIZE, ctlvs);
        if (ctlv != null) {
            int bufferSize = ValueParser.retrieveBufferSize(ctlv);
            CatLog.m0d((Object) this, "bufferSize = " + bufferSize);
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            if (!checkBearerDescriptionNull) {
                ctlv = searchForTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
                if (ctlv != null) {
                    textMsgUser.text = ValueParser.retrieveTextString(ctlv);
                    CatLog.m0d((Object) this, "User Name = " + textMsgUser.text);
                } else {
                    CatLog.m0d((Object) this, "Exception: user name (text string) ctlv is null");
                }
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                ctlv = searchForDupTag(ComprehensionTlvTag.TEXT_STRING, ctlvs);
                if (ctlv != null) {
                    textMsgPassword.text = ValueParser.retrieveTextString(ctlv);
                    CatLog.m0d((Object) this, "Password = " + textMsgPassword.text);
                } else {
                    CatLog.m0d((Object) this, "Exception: user password (text string) ctlv is null");
                }
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                bearerMode.isOnDemand = (cmdDet.commandQualifier & 1) == 0;
                bearerMode.isAutoReconnect = (cmdDet.commandQualifier & 2) == 2;
                bearerMode.isBackgroundMode = (cmdDet.commandQualifier & 4) == 4;
                CatLog.m0d((Object) this, "bearerMode.isOnDemand = " + bearerMode.isOnDemand);
                CatLog.m0d((Object) this, "bearerMode.isAutoReconnect = " + bearerMode.isAutoReconnect);
                CatLog.m0d((Object) this, "bearerMode.isBackgroundMode = " + bearerMode.isBackgroundMode);
            }
            ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
            if (ctlv != null) {
                textMsg.text = ValueParser.retrieveAlphaId(ctlv);
                if (textMsg.text == null) {
                    textMsg.text = new String("");
                }
            } else {
                textMsg.text = null;
            }
            CatLog.m0d((Object) this, "Alpha ID " + textMsg.text);
            if (isDisabledCmd("OpenChannel")) {
                this.mCmdParams = new OpenChannelParams(cmdDet, bufferSize, transportLevel, dataDestAdd, null);
            } else if (checkBearerDescriptionNull) {
                this.mCmdParams = new OpenChannelParams(cmdDet, bufferSize, transportLevel, dataDestAdd, textMsg);
            } else {
                this.mCmdParams = new OpenChannelParams(cmdDet, bearerDesc, bufferSize, transportLevel, dataDestAdd, networkAccessName, bearerMode, textMsg, textMsgUser, textMsgPassword);
            }
            return false;
        }
        CatLog.m0d((Object) this, "Exception: buffer size ctlv is null");
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processCloseChannel(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process Close Channel Command");
        TextMessage textMsg = new TextMessage();
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            CloseChannelMode closeChannelMode;
            int channelId = ValueParser.retrieveDeviceIdentities(ctlv).destinationId;
            CatLog.m0d((Object) this, "channelId = " + channelId);
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            switch (cmdDet.commandQualifier & 1) {
                case 1:
                    closeChannelMode = CloseChannelMode.CLOSE_TCP_AND_TCP_IN_LISTEN_STATE;
                    break;
                default:
                    closeChannelMode = CloseChannelMode.CLOSE_TCP_AND_TCP_IN_CLOSED_STATE;
                    break;
            }
            CatLog.m0d((Object) this, "CloseChannelMode = " + closeChannelMode);
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
            if (ctlv != null) {
                textMsg.text = ValueParser.retrieveAlphaId(ctlv);
            } else {
                textMsg.text = null;
            }
            CatLog.m0d((Object) this, "Alpha ID " + textMsg.text);
            if (isDisabledCmd("CloseChannel")) {
                this.mCmdParams = new CloseChannelParams(cmdDet, channelId, closeChannelMode, null);
            } else {
                this.mCmdParams = new CloseChannelParams(cmdDet, channelId, closeChannelMode, textMsg);
            }
            return false;
        }
        CatLog.m0d((Object) this, "Exception: channel id (devId) ctlv is null");
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processReceiveData(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process Receive Data Command");
        TextMessage textMsg = new TextMessage();
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            int channelId = ValueParser.retrieveDeviceIdentities(ctlv).destinationId;
            CatLog.m0d((Object) this, "channelId = " + channelId);
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA_LENGTH, ctlvs);
            if (ctlv != null) {
                byte channelDataLength = ValueParser.retrieveChannelDataLength(ctlv);
                CatLog.m0d((Object) this, "channelDataLength = " + (channelDataLength & 255));
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
                if (isDisabledCmd("ReceiveData") || ctlv == null) {
                    textMsg.text = null;
                } else {
                    textMsg.text = ValueParser.retrieveAlphaId(ctlv);
                }
                CatLog.m0d((Object) this, "Alpha ID = " + textMsg.text);
                this.mCmdParams = new ReceiveDataParams(cmdDet, channelId, channelDataLength, textMsg);
                return false;
            }
            CatLog.m0d((Object) this, "Exception: channel data length ctlv is null");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        CatLog.m0d((Object) this, "Exception: channel data length ctlv is null");
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processSendData(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        boolean sendImmediate = true;
        CatLog.m0d((Object) this, "process Send Data Command");
        TextMessage textMsg = new TextMessage();
        ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
        if (ctlv != null) {
            int channelId = ValueParser.retrieveDeviceIdentities(ctlv).destinationId;
            CatLog.m0d((Object) this, "channelId = " + channelId);
            CatLog.m0d((Object) this, "Moving onto the next TAG");
            ctlv = searchForTag(ComprehensionTlvTag.CHANNEL_DATA, ctlvs);
            if (ctlv != null) {
                byte[] channelData = ValueParser.retrieveChannelData(ctlv);
                CatLog.m0d((Object) this, "channelData = " + IccUtils.bytesToHexString(channelData));
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                if ((cmdDet.commandQualifier & 1) != 1) {
                    sendImmediate = false;
                }
                CatLog.m0d((Object) this, "SendDataImmediately  = " + sendImmediate);
                CatLog.m0d((Object) this, "Moving onto the next TAG");
                ctlv = searchForTag(ComprehensionTlvTag.ALPHA_ID, ctlvs);
                if (isDisabledCmd("SendData") || ctlv == null) {
                    textMsg.text = null;
                } else {
                    textMsg.text = ValueParser.retrieveAlphaId(ctlv);
                }
                CatLog.m0d((Object) this, "Alpha ID = " + textMsg.text);
                this.mCmdParams = new SendDataParams(cmdDet, channelId, channelData, sendImmediate, textMsg);
                return false;
            }
            CatLog.m0d((Object) this, "Exception: channel data ctlv is null");
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
        CatLog.m0d((Object) this, "Exception: channel id(devId) ctlv is null");
        throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
    }

    private boolean processGetChannelStatus(CommandDetails cmdDet, List<ComprehensionTlv> ctlvs) throws ResultException {
        CatLog.m0d((Object) this, "process Get Channel Status Command");
        int channelId = 0;
        if (!isDisabledCmd("GetChannelStatus")) {
            ComprehensionTlv ctlv = searchForTag(ComprehensionTlvTag.DEVICE_IDENTITIES, ctlvs);
            if (ctlv != null) {
                channelId = ValueParser.retrieveDeviceIdentities(ctlv).destinationId;
                CatLog.m0d((Object) this, "channelId = " + channelId);
            } else {
                CatLog.m0d((Object) this, "Exception:channel id ctlv is null");
                throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
            }
        }
        this.mCmdParams = new GetChannelDataParams(cmdDet, channelId);
        return false;
    }

    private String getSystemProperty(String key, String defValue) {
        return TelephonyManager.getTelephonyProperty(key, SubscriptionController.getInstance().getSubId(this.mSlotId)[0], defValue);
    }
}
