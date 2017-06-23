package com.mgzf.nativelikeruntime;

import com.mgzf.nativelikeruntime.download.KCDownloadEngine;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KerNet {

    /**
     * Creates a default instace of KCDownloadEngine
     *
     * @param aUserAgent user agent
     * @param aMaxConn   max conn
     * @return A KCDownloadEngine
     */
    public static KCDownloadEngine newDownloadEngine(final String aUserAgent, final int aMaxConn) {
        return new KCDownloadEngine(aUserAgent, aMaxConn);
    }

    private static KCDownloadEngine defaultDownloadEngine = null;

    public static synchronized KCDownloadEngine defaultDownloadEngine() {
        if (defaultDownloadEngine == null)
            return defaultDownloadEngine = KerNet.newDownloadEngine("kernet", 5);
        else
            return defaultDownloadEngine;
    }

}
