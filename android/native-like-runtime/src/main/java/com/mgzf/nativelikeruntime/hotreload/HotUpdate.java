package com.mgzf.nativelikeruntime.hotreload;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.mgzf.nativelikeruntime.App;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by heruijun on 2017/6/20.
 */

public class HotUpdate {

    private ReactInstanceManager mReactInstanceManager;
    private ReactApplicationContext mReactApplicationContext;
    private String mLatestJSBundleFile;

    public HotUpdate(String latestJSBundleFile) {
        this.mReactInstanceManager = App.getInstance().getReactInstanceManager();
        this.mReactApplicationContext = App.getInstance().getReactApplicationContext();
        this.mLatestJSBundleFile = latestJSBundleFile;
    }

    private void setJSBundle() throws NoSuchFieldException, IllegalAccessException {
        try {
            Field bundleLoaderField = mReactInstanceManager.getClass().getDeclaredField("mBundleLoader");
            Class<?> jsBundleLoaderClass = Class.forName("com.facebook.react.cxxbridge.JSBundleLoader");
            Method createFileLoaderMethod = null;

            Method[] methods = jsBundleLoaderClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals("createFileLoader")) {
                    createFileLoaderMethod = method;
                    break;
                }
            }

            if (createFileLoaderMethod == null) {
                throw new NoSuchMethodException("Could not find a recognized 'createFileLoader' method");
            }

            int numParameters = createFileLoaderMethod.getGenericParameterTypes().length;
            Object latestJSBundleLoader;

            if (numParameters == 1) {
                latestJSBundleLoader = createFileLoaderMethod.invoke(jsBundleLoaderClass, mLatestJSBundleFile);
            } else {
                throw new NoSuchMethodException("Could not find a recognized 'createFileLoader' method");
            }

            bundleLoaderField.setAccessible(true);
            bundleLoaderField.set(mReactInstanceManager, latestJSBundleLoader);
        } catch (Exception e) {
            // 通常这里抛出异常，是RN版本问题
            Log.e("RN exception: ", e.getMessage());
        }
    }

    public void reloadBundle() {
        try {
            setJSBundle();
            final Method recreateMethod = mReactInstanceManager.getClass().getMethod("recreateReactContextInBackground");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        recreateMethod.invoke(mReactInstanceManager);
                    } catch (Exception e) {
                        loadBundleLegacy();
                    }
                }
            });
        } catch (Exception e) {
            loadBundleLegacy();
        }
    }

    private void loadBundleLegacy() {
        if (mReactApplicationContext != null) {
            final Activity currentActivity = mReactApplicationContext.getCurrentActivity();
            if (currentActivity == null) {
                return;
            }

            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentActivity.recreate();
                }
            });
        }
    }
}
