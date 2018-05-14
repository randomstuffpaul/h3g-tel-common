package com.itsoninc.android;

import android.util.Log;
import dalvik.system.BaseDexClassLoader;

public class ItsOnOemApi extends ItsOnOemApiBase {
    private static ItsOnOemApi instance = null;

    private ItsOnOemApi() {
        if (loadOemImpl()) {
            Log.i("ItsOnAPI", "ItsOn is enabled");
            return;
        }
        Log.i("ItsOnAPI", "ItsOn is not enabled");
        this.mIfImpl = new APIFailOpen();
    }

    public static synchronized ItsOnOemApi getInstance() {
        ItsOnOemApi itsOnOemApi;
        synchronized (ItsOnOemApi.class) {
            if (instance == null) {
                instance = new ItsOnOemApi();
            }
            itsOnOemApi = instance;
        }
        return itsOnOemApi;
    }

    boolean loadOemImpl() {
        try {
            this.mIfImpl = (ItsOnOemInterface) new BaseDexClassLoader(ItsOnOemApiBase.getJarFilePath(), null, null, getClass().getClassLoader()).loadClass("com.itsoninc.android.impl.ItsOnOem").newInstance();
            if (this.context != null) {
                Log.d("ItsOnAPI", "Setting context");
                this.mIfImpl.setContext(this.context);
            }
            if (this.frameworkIf != null) {
                Log.d("ItsOnAPI", "Setting framework " + this.frameworkIf);
                this.mIfImpl.setFrameworkInterface(this.frameworkIf);
            }
            return true;
        } catch (ClassNotFoundException e) {
            Log.d("ItsOnAPI", "Unable to load ItsOnOemApi implementation: " + e.toString());
            return false;
        } catch (Throwable e2) {
            Log.e("ItsOnAPI", "Unable to load ItsOnOemApi implementation", e2);
            return false;
        }
    }
}
