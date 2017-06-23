package com.mgzf.nativelikeruntime.deploy;

import android.content.Context;

import com.mgzf.nativelikeruntime.io.KCZip;

import java.io.File;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCDeploy {

    protected Context mContext;
    protected KCDeployFlow mDeployFlow;

    protected KCDeploy(Context context) {
        this(context, null);
    }

    protected KCDeploy(Context context, KCDeployFlow deployFlow) {
        mContext = context;
        if (deployFlow != null)
            mDeployFlow = deployFlow;
    }

    protected boolean deploy(File zipFile, File targetFile) {
        try {
            KCZip.unZipToDir(zipFile, targetFile);
            mDeployFlow.onComplete();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            // del file
            // error callback
        } finally {
            // del file
        }
        return false;
    }

}