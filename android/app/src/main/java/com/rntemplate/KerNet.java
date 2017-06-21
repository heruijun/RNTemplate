package com.rntemplate;

import android.content.Context;

import com.rntemplate.download.KCDownloadEngine;

import java.io.File;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KerNet {

    /**
     * Default on-disk cache directory.
     */
//    private static final String DEFAULT_CACHE_DIR = "kernet";
//
//
//    private static KCNetwork newNetwork(KCHttpStack aStack) {
//
//        if (aStack == null) {
//            aStack = new KCHttpStackDefault();
//        }
//
//        KCNetwork network = new KCNetworkBasic(aStack);
//        return network;
//    }
//
//    private static KCCache newCache(Context aContext) {
//        KCCache cache = null;
//        if (aContext != null) {
//            File cacheDir = new File(aContext.getCacheDir(), DEFAULT_CACHE_DIR);
//            cache = new KCCacheDisk(cacheDir);
//        }
//        return cache;
//    }
//
//    /**
//     * Creates a default instance of the worker pool and calls {@link KCRequestQueue#start()} on it.
//     *
//     * @param aContext A {@link Context} to use for creating the cache dir.
//     * @param aStack   An {@link KCHttpStack} to use for the network, or null for default.
//     * @return A started {@link KCRequestQueue} instance.
//     */
//    public static KCRequestQueue newRequestQueue(Context aContext, KCHttpStack aStack) {
//        KCRequestQueue queue = new KCRequestQueue(newCache(aContext), newNetwork(aStack));
//        queue.start();
//
//        return queue;
//    }
//
//    /**
//     * Creates a default instance of the worker pool and calls {@link KCRequestQueue#start()} on it.
//     *
//     * @param aContext A {@link Context} to use for creating the cache dir.
//     * @return A started {@link KCRequestQueue} instance.
//     */
//    public static KCRequestQueue newRequestQueue(Context aContext) {
//        return newRequestQueue(aContext, null);
//    }
//
//    /**
//     * Creates a default instance of KCRequestRunner and calls startAsyn on it
//     *
//     * @param aContext A {@link Context} to use for creating the cache dir.
//     *                 if null, not use cache, else use cache of Default on-disk cache directory
//     * @param aStack   An {@link KCHttpStack} to use for the network, or null for default.
//     * @return A Request Runner
//     */
//    public static KCRequestRunner newRequestRunner(Context aContext, KCHttpStack aStack) {
//        KCRequestRunner requestRunner = new KCRequestRunner(newCache(aContext), newNetwork(aStack));
//        return requestRunner;
//    }
//
//    /**
//     * Creates a default instance of KCRequestRunner and calls startAsyn on it
//     *
//     * @param aContext A {@link Context} to use for creating the cache dir.
//     *                 if null, not use cache, else use cache of Default on-disk cache directory
//     * @return A Request Runner
//     */
//    public static KCRequestRunner newRequestRunner(Context aContext) {
//        return newRequestRunner(aContext, null);
//    }

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
