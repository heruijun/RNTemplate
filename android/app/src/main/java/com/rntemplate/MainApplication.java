package com.rntemplate;

import android.app.Application;
import android.support.annotation.Nullable;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import com.rntemplate.constants.FileConstant;
import com.rntemplate.debug.KCLog;
import com.rntemplate.deploy.KCDeployFlow;
import com.rntemplate.deploy.NativeLikeAppManager;
import com.rntemplate.hotreload.HotUpdate;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MainApplication extends Application implements ReactApplication {

    private static MainApplication instance;
    private ReactInstanceManager mReactInstanceManager;
    private ReactApplicationContext mReactApplicationContext;
    private NativeLikeAppManager mNativeLikeAppManager;

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
        mReactInstanceManager = mReactNativeHost.getReactInstanceManager();
        mReactApplicationContext = (ReactApplicationContext) mReactInstanceManager.getCurrentReactContext();

        // 检查RN更新资源
        checkBundleUpdate();

        SoLoader.init(this, /* native exopackage */ false);
    }

    private void checkBundleUpdate() {
        mNativeLikeAppManager = new NativeLikeAppManager(this, new KCDeployFlow() {
            @Override
            public void onComplete() {
                KCLog.e("下载完成", "download --> ok");
                // 通过反射刷新RN界面
//                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
                HotUpdate hotUpdate = new HotUpdate(FileConstant.JS_BUNDLE_LOCAL_PATH);
                hotUpdate.reloadBundle();
//                    }
//                }, 2000);
            }

            @Override
            public void onError() {

            }
        });
    }

    public String getAppPackageName() {
        return this.getPackageName();
    }
}
