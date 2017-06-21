package com.rntemplate;

import android.app.Application;
import android.content.Context;
import android.support.annotation.Nullable;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.connection.FileDownloadUrlConnection;
import com.liulishuo.filedownloader.services.DownloadMgrInitialParams;
import com.rntemplate.constants.FileConstant;

import java.io.File;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;

public class MainApplication extends Application implements ReactApplication {

    public static Context CONTEXT;
    private static MainApplication instance;
    private ReactInstanceManager mReactInstanceManager;
    private ReactApplicationContext mReactApplicationContext;
    public static MainApplication getInstance() {
        return instance;
    }

    private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {

        @Nullable
        @Override
        protected String getJSBundleFile() {
            File file = new File(FileConstant.JS_BUNDLE_LOCAL_PATH);
            if (file != null && file.exists()) {
                return FileConstant.JS_BUNDLE_LOCAL_PATH;
            } else {
                return super.getJSBundleFile();
            }
            // return null;
        }

        @Override
        public boolean getUseDeveloperSupport() {
            return false;
        }

        @Override
        protected List<ReactPackage> getPackages() {
            return Arrays.<ReactPackage>asList(
                    new MainReactPackage()
            );
        }
    };

    @Override
    public ReactNativeHost getReactNativeHost() {
        return mReactNativeHost;
    }

    public ReactApplicationContext getReactApplicationContext() {
        return mReactApplicationContext;
    }

    public ReactInstanceManager getReactInstanceManager() {
        return mReactInstanceManager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CONTEXT = this;
        mReactInstanceManager = mReactNativeHost.getReactInstanceManager();
        mReactApplicationContext = (ReactApplicationContext) mReactInstanceManager.getCurrentReactContext();
        SoLoader.init(this, /* native exopackage */ false);

        FileDownloader.init(getApplicationContext(), new DownloadMgrInitialParams.InitCustomMaker()
                .connectionCreator(new FileDownloadUrlConnection
                        .Creator(new FileDownloadUrlConnection.Configuration()
                        .connectTimeout(15_000) // set connection timeout.
                        .readTimeout(15_000) // set read timeout.
                        .proxy(Proxy.NO_PROXY) // set proxy
                )));
    }

    public String getAppPackageName() {
        return this.getPackageName();
    }
}
