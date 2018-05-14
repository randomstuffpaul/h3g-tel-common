package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import com.android.internal.telephony.cat.AppInterface.CommandType;

public class CatCmdMessage implements Parcelable {
    public static final Creator<CatCmdMessage> CREATOR = new C00501();
    private BrowserSettings mBrowserSettings = null;
    private CallSettings mCallSettings = null;
    CommandDetails mCmdDet;
    private int[] mEventList = null;
    private boolean mHasIcon = false;
    private boolean mInitialLanguage = false;
    private Input mInput;
    private String mLanguage = null;
    private Menu mMenu;
    private int mNumberOfEventList = 0;
    private TextMessage mTextMsg;
    private ToneSettings mToneSettings = null;

    static class C00501 implements Creator<CatCmdMessage> {
        C00501() {
        }

        public CatCmdMessage createFromParcel(Parcel in) {
            return new CatCmdMessage(in);
        }

        public CatCmdMessage[] newArray(int size) {
            return new CatCmdMessage[size];
        }
    }

    static /* synthetic */ class C00512 {
        static final /* synthetic */ int[] f6xca33cf42 = new int[CommandType.values().length];

        static {
            try {
                f6xca33cf42[CommandType.SET_UP_MENU.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f6xca33cf42[CommandType.SELECT_ITEM.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f6xca33cf42[CommandType.DISPLAY_TEXT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f6xca33cf42[CommandType.SET_UP_IDLE_MODE_TEXT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                f6xca33cf42[CommandType.SEND_DTMF.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                f6xca33cf42[CommandType.SEND_SMS.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                f6xca33cf42[CommandType.SEND_SS.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                f6xca33cf42[CommandType.SEND_USSD.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                f6xca33cf42[CommandType.REFRESH.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                f6xca33cf42[CommandType.GET_INPUT.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                f6xca33cf42[CommandType.GET_INKEY.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                f6xca33cf42[CommandType.LAUNCH_BROWSER.ordinal()] = 12;
            } catch (NoSuchFieldError e12) {
            }
            try {
                f6xca33cf42[CommandType.PLAY_TONE.ordinal()] = 13;
            } catch (NoSuchFieldError e13) {
            }
            try {
                f6xca33cf42[CommandType.SET_UP_CALL.ordinal()] = 14;
            } catch (NoSuchFieldError e14) {
            }
            try {
                f6xca33cf42[CommandType.LANGUAGE_NOTIFICATION.ordinal()] = 15;
            } catch (NoSuchFieldError e15) {
            }
            try {
                f6xca33cf42[CommandType.OPEN_CHANNEL.ordinal()] = 16;
            } catch (NoSuchFieldError e16) {
            }
            try {
                f6xca33cf42[CommandType.CLOSE_CHANNEL.ordinal()] = 17;
            } catch (NoSuchFieldError e17) {
            }
            try {
                f6xca33cf42[CommandType.RECEIVE_DATA.ordinal()] = 18;
            } catch (NoSuchFieldError e18) {
            }
            try {
                f6xca33cf42[CommandType.SEND_DATA.ordinal()] = 19;
            } catch (NoSuchFieldError e19) {
            }
            try {
                f6xca33cf42[CommandType.SET_UP_EVENT_LIST.ordinal()] = 20;
            } catch (NoSuchFieldError e20) {
            }
            try {
                f6xca33cf42[CommandType.PROVIDE_LOCAL_INFORMATION.ordinal()] = 21;
            } catch (NoSuchFieldError e21) {
            }
        }
    }

    public class BrowserSettings {
        public String gatewayProxy;
        public LaunchBrowserMode mode;
        public String url;
    }

    public class CallSettings {
        public String address;
        public TextMessage callMsg;
        public TextMessage confirmMsg;
    }

    CatCmdMessage(CommandParams cmdParams) {
        this.mCmdDet = cmdParams.mCmdDet;
        this.mHasIcon = cmdParams.hasIconTag;
        switch (C00512.f6xca33cf42[getCmdType().ordinal()]) {
            case 1:
            case 2:
                this.mMenu = ((SelectItemParams) cmdParams).mMenu;
                return;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                this.mTextMsg = ((DisplayTextParams) cmdParams).mTextMsg;
                return;
            case 10:
            case 11:
                this.mInput = ((GetInputParams) cmdParams).mInput;
                return;
            case 12:
                this.mTextMsg = ((LaunchBrowserParams) cmdParams).mConfirmMsg;
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = ((LaunchBrowserParams) cmdParams).mUrl;
                this.mBrowserSettings.mode = ((LaunchBrowserParams) cmdParams).mMode;
                this.mBrowserSettings.gatewayProxy = ((LaunchBrowserParams) cmdParams).mGatewayProxy;
                return;
            case 13:
                PlayToneParams params = (PlayToneParams) cmdParams;
                this.mToneSettings = params.mSettings;
                this.mTextMsg = params.mTextMsg;
                return;
            case 14:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = ((CallSetupParams) cmdParams).mConfirmMsg;
                this.mCallSettings.callMsg = ((CallSetupParams) cmdParams).mCallMsg;
                this.mCallSettings.address = ((CallSetupParams) cmdParams).address;
                return;
            case 15:
                this.mLanguage = ((LanguageNotificationParams) cmdParams).mLanguage;
                this.mInitialLanguage = ((LanguageNotificationParams) cmdParams).mInitialLanguage;
                return;
            case 16:
                this.mTextMsg = ((OpenChannelParams) cmdParams).mTextMessage;
                return;
            case 17:
                this.mTextMsg = ((CloseChannelParams) cmdParams).mTextMessage;
                return;
            case 18:
                this.mTextMsg = ((ReceiveDataParams) cmdParams).mTextMessage;
                return;
            case 19:
                this.mTextMsg = ((SendDataParams) cmdParams).mTextMessage;
                return;
            case 20:
                this.mNumberOfEventList = ((SetupEventListParams) cmdParams).numberOfEvents;
                this.mEventList = ((SetupEventListParams) cmdParams).events;
                return;
            default:
                return;
        }
    }

    public CatCmdMessage(Parcel in) {
        this.mCmdDet = (CommandDetails) in.readParcelable(null);
        this.mTextMsg = (TextMessage) in.readParcelable(null);
        this.mMenu = (Menu) in.readParcelable(null);
        this.mInput = (Input) in.readParcelable(null);
        switch (C00512.f6xca33cf42[getCmdType().ordinal()]) {
            case 12:
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = in.readString();
                this.mBrowserSettings.gatewayProxy = in.readString();
                this.mBrowserSettings.mode = LaunchBrowserMode.values()[in.readInt()];
                return;
            case 13:
                this.mToneSettings = (ToneSettings) in.readParcelable(null);
                return;
            case 14:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = (TextMessage) in.readParcelable(null);
                this.mCallSettings.callMsg = (TextMessage) in.readParcelable(null);
                return;
            case 15:
                this.mLanguage = in.readString();
                boolean[] tempBooleanArray = new boolean[1];
                in.readBooleanArray(tempBooleanArray);
                this.mInitialLanguage = tempBooleanArray[0];
                return;
            case 20:
                this.mNumberOfEventList = in.readInt();
                this.mEventList = new int[this.mNumberOfEventList];
                in.readIntArray(this.mEventList);
                return;
            default:
                return;
        }
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mCmdDet, 0);
        dest.writeParcelable(this.mTextMsg, 0);
        dest.writeParcelable(this.mMenu, 0);
        dest.writeParcelable(this.mInput, 0);
        switch (C00512.f6xca33cf42[getCmdType().ordinal()]) {
            case 12:
                dest.writeString(this.mBrowserSettings.url);
                dest.writeString(this.mBrowserSettings.gatewayProxy);
                dest.writeInt(this.mBrowserSettings.mode.ordinal());
                return;
            case 13:
                dest.writeParcelable(this.mToneSettings, 0);
                return;
            case 14:
                dest.writeParcelable(this.mCallSettings.confirmMsg, 0);
                dest.writeParcelable(this.mCallSettings.callMsg, 0);
                return;
            case 15:
                dest.writeString(this.mLanguage);
                dest.writeBooleanArray(new boolean[]{this.mInitialLanguage});
                return;
            case 20:
                dest.writeInt(this.mNumberOfEventList);
                dest.writeIntArray(this.mEventList);
                return;
            default:
                return;
        }
    }

    public int describeContents() {
        return 0;
    }

    public CommandType getCmdType() {
        return CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return this.mMenu;
    }

    public Input geInput() {
        return this.mInput;
    }

    public TextMessage geTextMessage() {
        return this.mTextMsg;
    }

    public BrowserSettings getBrowserSettings() {
        return this.mBrowserSettings;
    }

    public ToneSettings getToneSettings() {
        return this.mToneSettings;
    }

    public CallSettings getCallSettings() {
        return this.mCallSettings;
    }

    public int getNumberOfEventList() {
        return this.mNumberOfEventList;
    }

    public int[] getEventList() {
        return this.mEventList;
    }

    public String getLanguage() {
        return this.mLanguage;
    }

    public boolean getinitLanguage() {
        return this.mInitialLanguage;
    }

    public boolean getHasIcon() {
        return this.mHasIcon;
    }

    public boolean hasTextAttribute() {
        boolean z = false;
        switch (C00512.f6xca33cf42[getCmdType().ordinal()]) {
            case 1:
            case 2:
                if (this.mMenu == null || this.mMenu.titleAttrs == null) {
                    return false;
                }
                return true;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 12:
            case 13:
                if (this.mTextMsg == null || this.mTextMsg.textAttributes == null) {
                    return false;
                }
                return true;
            case 10:
            case 11:
                if (this.mInput == null || this.mInput.textAttributes == null) {
                    return false;
                }
                return true;
            case 14:
                if (!((this.mCallSettings.confirmMsg == null || this.mCallSettings.confirmMsg.textAttributes == null) && (this.mCallSettings.callMsg == null || this.mCallSettings.callMsg.textAttributes == null))) {
                    z = true;
                }
                return z;
            default:
                return false;
        }
    }
}
