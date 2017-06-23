package com.mgzf.nativelikeruntime.download;

/**
 * Created by heruijun on 2017/6/21.
 */

public interface KCDownloadListener {

    void onPrepare();

    void onReceiveFileLength(long downloadedBytes, long fileLength);

    void onProgressUpdate(long downloadedBytes, long fileLength, int speed);

    void onComplete(long downloadedBytes, long fileLength, int totalTimeInSeconds);

    void onError(long downloadedBytes, Throwable e);

}
