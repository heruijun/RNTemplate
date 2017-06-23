package com.mgzf.nativelikeruntime.deploy;

import android.content.Context;

/**
 * Created by heruijun on 2017/6/21.
 */

public class NativeLikeAppManager {

    private KCDeploy mDeploy;
    private KCDeployInstall mDeployInstall;

    public NativeLikeAppManager(Context context, KCDeployFlow deployFlow) {
        setup(context, deployFlow);
    }

    private synchronized void setup(final Context context, KCDeployFlow deployFlow) {
        if (mDeploy == null) mDeploy = new KCDeploy(context, deployFlow);
        if (mDeployInstall == null) mDeployInstall = new KCDeployInstall(mDeploy);
        upgradeRNBundle();
    }

    /**
     * 通过服务端配置检测并下载Bundle，解压覆盖
     */
    public void upgradeRNBundle() {
        if (mDeployInstall != null) mDeployInstall.installRN();
    }

}
