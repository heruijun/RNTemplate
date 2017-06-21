package com.rntemplate.deploy;

import com.rntemplate.KerNet;
import com.rntemplate.constants.FileConstant;
import com.rntemplate.debug.KCLog;
import com.rntemplate.download.KCDownloadListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCDeployInstall {

    KCDeploy mKCDeploy;

    KCDeployInstall(KCDeploy deploy) {
        mKCDeploy = deploy;
    }

    public void installRN() {
        // 检查更新并安装
        downLoadBundle();
    }

    /**
     * 下载最新Bundle
     */
    private void downLoadBundle() {
        try {
            final File rnFile = new File(FileConstant.JS_PATCH_LOCAL_PATH);

            KCDownloadListener downloadListener = new KCDownloadListener() {
                @Override
                public void onPrepare() {
                    // 如果有之前下载过的文件则删除
                    if (rnFile.exists())
                        rnFile.delete();
                }

                @Override
                public void onReceiveFileLength(long downloadedBytes, long fileLength) {
                }

                @Override
                public void onProgressUpdate(long downloadedBytes, long fileLength, int speed) {
                }

                @Override
                public void onComplete(long downloadedBytes, long fileLength, int totalTimeInSeconds) {
                    // 解压新bundle文件
                    File targetFile = new File(FileConstant.JS_PATCH_LOCAL_FOLDER);
                    mKCDeploy.deploy(rnFile, targetFile);
                }

                @Override
                public void onError(long downloadedBytes, Throwable e) {

                }
            };

            KerNet.defaultDownloadEngine().startDownload(FileConstant.JS_BUNDLE_REMOTE_URL, rnFile.getAbsolutePath(), downloadListener, true, false);

        } catch (FileNotFoundException e) {
            KCLog.e(e);
        } catch (URISyntaxException e) {
            KCLog.e(e);
        } catch (Exception e) {
            KCLog.e(e);
        }
    }

}
