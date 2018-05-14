package com.android.internal.telephony;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.secutil.Log;
import java.util.List;

public enum AppDirectedSMS {
    INSTANCE;
    
    private static final String BUA_APP_PREFIX = "BUA-ADS";
    private static final String BUA_SMS_PREFIX = "//F1";
    private static final String COLON = ":";
    private static final String DIRECTED_SMS_ACTION = "verizon.intent.action.DIRECTED_SMS_RECEIVED";
    private static final String DIRECTED_SMS_META_DATA_NAME = "com.verizon.directedAppSMS";
    private static final String DIRECTED_SMS_PERMISSION_NAME = "com.verizon.permissions.appdirectedsms";
    private static final String TAG = "AppDirectedSMS";
    private static final String VZW_SMS_PREFIX = "//VZW";
    private Context mcontext;

    public static class AppMessageInfo {
        private static final int APPDIR_SMS_NO_COMPONENT = 2;
        private static final int APPDIR_SMS_TRUE = 1;
        private int mAppDirSmsStatus;
        private String mAppPrefix;
        private ComponentName mComponentname;
        private String mParameter;

        public AppMessageInfo() {
            this.mAppDirSmsStatus = 0;
            this.mComponentname = null;
            this.mParameter = null;
            this.mAppPrefix = null;
        }

        public boolean getappdirsmsstatus() {
            return this.mAppDirSmsStatus == 1 || this.mAppDirSmsStatus == 2;
        }

        public void setSuccesfulResult(ComponentName componentname, String parameter, String appPrefix) {
            this.mComponentname = componentname;
            this.mAppDirSmsStatus = 1;
            this.mParameter = parameter;
            this.mAppPrefix = appPrefix;
        }

        public void setNoRegisteredComponent() {
            this.mAppDirSmsStatus = 2;
        }

        public ComponentName getcomponentnameDirectedSms() {
            return this.mComponentname;
        }

        public String getappMsgBody() {
            return this.mParameter;
        }

        public String getAppPrefix() {
            return this.mAppPrefix;
        }

        public boolean checkifcomponentpresent() {
            return this.mComponentname != null;
        }
    }

    private static class MatchInfo {
        public String mAppPrefix;
        public ComponentName mComponentName;
        public String mParameter;

        private MatchInfo() {
            this.mComponentName = null;
            this.mAppPrefix = null;
            this.mParameter = null;
        }
    }

    public AppMessageInfo checkIfAppDirSMS(Context context, String message) {
        this.mcontext = context;
        AppMessageInfo msgInfo = new AppMessageInfo();
        if (message.startsWith(BUA_SMS_PREFIX)) {
            Log.secI(TAG, "checkIfAppDirSMS| BUA Message");
            setResult(message, msgInfo, BUA_APP_PREFIX);
        } else {
            Log.secI(TAG, "checkIfAppDirSMS| Not BUA");
            setBestMatchResult(message, msgInfo);
        }
        return msgInfo;
    }

    private void setResult(String parameter, AppMessageInfo msgInfo, String appPrefix) {
        ComponentName componentName = findAppDirectedSMSPackageWithPrefix(appPrefix);
        if (componentName != null) {
            msgInfo.setSuccesfulResult(componentName, parameter, appPrefix);
            return;
        }
        msgInfo.setNoRegisteredComponent();
        Log.secI(TAG, "setResult| no component");
    }

    private void setBestMatchResult(String message, AppMessageInfo msgInfo) {
        MatchInfo matchInfo = findAppDirectedSMSPackage(message);
        if (matchInfo != null && matchInfo.mComponentName != null) {
            msgInfo.setSuccesfulResult(matchInfo.mComponentName, matchInfo.mParameter, matchInfo.mAppPrefix);
        } else if (message.startsWith(VZW_SMS_PREFIX)) {
            msgInfo.setNoRegisteredComponent();
            Log.secI(TAG, "setBestMatchResult| no component");
        }
    }

    private ComponentName findAppDirectedSMSPackageWithPrefix(String appPrefix) {
        NameNotFoundException e;
        ComponentName componentName = null;
        Intent resolveIntent = new Intent();
        resolveIntent.setAction("verizon.intent.action.DIRECTED_SMS_RECEIVED");
        PackageManager pm = this.mcontext.getPackageManager();
        List<ResolveInfo> queryResults = pm.queryBroadcastReceivers(resolveIntent, 0);
        if (queryResults == null || queryResults.size() <= 0) {
            Log.secI(TAG, "queryResult is null or size is zero");
            return null;
        }
        for (int i = 0; i < queryResults.size(); i++) {
            ResolveInfo ri = (ResolveInfo) queryResults.get(i);
            String packageName = ri.activityInfo.packageName;
            if (isVzwApnPermissionGranted(ri.activityInfo.packageName)) {
                try {
                    ComponentName componentName2 = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                    try {
                        if (matchesMetaData(pm.getReceiverInfo(componentName2, 128).metaData, appPrefix)) {
                            Log.secD(TAG, "findAppDirectedSMSPackageWithPrefix| package is found from receiver!!![package]=" + ri.activityInfo.packageName + " " + " [receiver]=" + ri.activityInfo.name);
                            componentName = componentName2;
                            return componentName2;
                        }
                        componentName = componentName2;
                        try {
                            if (matchesMetaData(pm.getApplicationInfo(packageName, 128).metaData, appPrefix)) {
                                Log.secD(TAG, "findAppDirectedSMSPackageWithPrefix| package is found from application !!! [package]=" + ri.activityInfo.packageName + " " + " [receiver]=" + ri.activityInfo.name);
                                return componentName;
                            }
                        } catch (NameNotFoundException e2) {
                            e2.printStackTrace();
                            Log.secE(TAG, "findAppDirectedSMSPackageWithPrefix| ApplicationInfo NameNotFoundException");
                        }
                    } catch (NameNotFoundException e3) {
                        e2 = e3;
                        componentName = componentName2;
                        e2.printStackTrace();
                        Log.secE(TAG, "findAppDirectedSMSPackageWithPrefix| ReceiverInfo NameNotFoundException");
                        if (matchesMetaData(pm.getApplicationInfo(packageName, 128).metaData, appPrefix)) {
                            Log.secD(TAG, "findAppDirectedSMSPackageWithPrefix| package is found from application !!! [package]=" + ri.activityInfo.packageName + " " + " [receiver]=" + ri.activityInfo.name);
                            return componentName;
                        }
                    }
                } catch (NameNotFoundException e4) {
                    e2 = e4;
                    e2.printStackTrace();
                    Log.secE(TAG, "findAppDirectedSMSPackageWithPrefix| ReceiverInfo NameNotFoundException");
                    if (matchesMetaData(pm.getApplicationInfo(packageName, 128).metaData, appPrefix)) {
                    } else {
                        Log.secD(TAG, "findAppDirectedSMSPackageWithPrefix| package is found from application !!! [package]=" + ri.activityInfo.packageName + " " + " [receiver]=" + ri.activityInfo.name);
                        return componentName;
                    }
                }
            }
        }
        return null;
    }

    private MatchInfo findAppDirectedSMSPackage(String message) {
        String appMessage;
        Intent resolveIntent = new Intent();
        resolveIntent.setAction("verizon.intent.action.DIRECTED_SMS_RECEIVED");
        if (message.startsWith(VZW_SMS_PREFIX)) {
            appMessage = message.substring(VZW_SMS_PREFIX.length(), message.length());
        } else {
            appMessage = message;
        }
        PackageManager pm = this.mcontext.getPackageManager();
        List<ResolveInfo> queryResults = pm.queryBroadcastReceivers(resolveIntent, 0);
        if (queryResults == null || queryResults.size() <= 0) {
            Log.secI(TAG, "findAppDirectedSMSPackage| queryResult is null or size is zero");
            return null;
        }
        MatchInfo matchInfo = new MatchInfo();
        for (int i = 0; i < queryResults.size(); i++) {
            ResolveInfo ri = (ResolveInfo) queryResults.get(i);
            String packageName = ri.activityInfo.packageName;
            if (isVzwApnPermissionGranted(ri.activityInfo.packageName)) {
                ComponentName componentName = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                try {
                    startsWithMetaData(pm.getReceiverInfo(componentName, 128).metaData, appMessage, componentName, matchInfo);
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                    Log.secE(TAG, "findAppDirectedSMSPackage| ReceiverInfo NameNotFoundException");
                }
                try {
                    startsWithMetaData(pm.getApplicationInfo(packageName, 128).metaData, appMessage, componentName, matchInfo);
                } catch (NameNotFoundException e2) {
                    e2.printStackTrace();
                    Log.secE(TAG, "findAppDirectedSMSPackage| ApplicationInfo NameNotFoundException");
                }
            }
        }
        return matchInfo;
    }

    private boolean isVzwApnPermissionGranted(String packageName) {
        PackageManager mPackageManager = this.mcontext.getPackageManager();
        try {
            PackageInfo callingPackageInfo = mPackageManager.getPackageInfo(packageName, 64);
            if ((callingPackageInfo.applicationInfo.flags & 1) != 0) {
                return true;
            }
            try {
                PackageInfo permissionPackageInfo = mPackageManager.getPackageInfo(DIRECTED_SMS_PERMISSION_NAME, 64);
                Signature[] callingPackageSignatures = callingPackageInfo.signatures;
                if (callingPackageSignatures != null) {
                    Signature[] permissionPackageSignatures = permissionPackageInfo.signatures;
                    for (Signature equals : callingPackageSignatures) {
                        for (Object equals2 : permissionPackageSignatures) {
                            if (equals.equals(equals2)) {
                                Log.secI(TAG, "isVzwApnPermissionGranted Signature of the application matched with verizon provided signatures");
                                return true;
                            }
                        }
                    }
                }
                Log.secI(TAG, "isVzwApnPermissionGranted app with no correct signature");
                return false;
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                Log.secE(TAG, "isVzwApnPermissionGranted permission package NameNotFoundException");
                return false;
            }
        } catch (NameNotFoundException e2) {
            e2.printStackTrace();
            Log.secE(TAG, "isVzwApnPermissionGranted calling package NameNotFoundException");
            return false;
        }
    }

    private boolean isPackageVzwSmsAuthorized(String packageName) {
        boolean z = false;
        Log.secI(TAG, "isPackageVzwSmsAuthorized" + packageName);
        if (!TextUtils.isEmpty(packageName)) {
            z = false;
            Cursor cursor = null;
            try {
                cursor = this.mcontext.getContentResolver().query(Uri.parse("content://com.verizon.vzwavs.provider/apis"), null, packageName, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    z = cursor.getString(0).contains("VZWSMS");
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
            Log.secI(TAG, "isPackageVzwSmsAuthorized result" + z);
        }
        return z;
    }

    private void startsWithMetaData(Bundle bundle, String appMessage, ComponentName component, MatchInfo matchInfo) {
        if (bundle != null) {
            String metaData = bundle.getString(DIRECTED_SMS_META_DATA_NAME);
            if (metaData != null && appMessage.startsWith(metaData)) {
                String parameter = appMessage.substring(metaData.length(), appMessage.length());
                if (matchInfo.mAppPrefix == null || metaData.length() > matchInfo.mAppPrefix.length()) {
                    if (parameter != null && parameter.length() >= 1 && parameter.startsWith(COLON)) {
                        parameter = parameter.substring(1, parameter.length());
                    }
                    matchInfo.mParameter = parameter;
                    matchInfo.mComponentName = component;
                    matchInfo.mAppPrefix = metaData;
                    Log.secD(TAG, "startsWithMetaData| match found [componentName]=" + matchInfo.mComponentName.getClassName() + " [parameter]=" + matchInfo.mParameter + " [appPrefix]=" + matchInfo.mAppPrefix);
                }
            }
        }
    }

    private boolean matchesMetaData(Bundle bundle, String appPrefix) {
        if (bundle == null) {
            return false;
        }
        return appPrefix.equals(bundle.getString(DIRECTED_SMS_META_DATA_NAME));
    }
}
