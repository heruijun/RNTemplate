package com.mgzf.nativelikeruntime.download;

import com.mgzf.nativelikeruntime.debug.KCLog;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCDownloadEngine {
    private ExecutorService mThreadService;

    /**
     * Staging area for Download task that already have a duplicate request in flight.
     * <p>
     * <ul>
     * <li>containsKey(key) indicates that there is a request in flight for the given key.</li>
     * <li>get(key) returns waiting requests for the given key. The in flight request is <em>not</em> contained in that list. Is null if no
     * requests are staged.</li>
     * </ul>
     */
    private final Map<String, Queue<KCDownloadTask>> mWaitingRequests = new HashMap<String, Queue<KCDownloadTask>>();
    private final Map<String, KCDownloadTask> mCurrentRequests = new HashMap<>();


    // threads used to concurrently initiate requests
    public KCDownloadEngine(String aUserAgent, final int aMaxConn) {
        mThreadService = Executors.newCachedThreadPool();
        initHttpConnectionProperties(aUserAgent, aMaxConn);
    }

    private void initHttpConnectionProperties(String aUserAgent, final int aMaxConn) {
        System.setProperty("http.keepAlive", "true"); // enabling connection pooling
        System.setProperty("http.maxConnections", String.valueOf(aMaxConn));
        System.setProperty("http.agent", aUserAgent);
        HttpURLConnection.setFollowRedirects(false);
    }

    private KCDownloadTask createDownloadTask(String aUrl, String aDestFilePath, final KCDownloadListener aListener, final boolean aUseResumable, final boolean aNeedProgress)
            throws FileNotFoundException, URISyntaxException {
        URL urlObj = null;
        try {
            urlObj = new URL(aUrl);
        } catch (MalformedURLException e) {
            // KCLog.e(e);
        }

        if (urlObj == null)
            return null;

        final KCDownloadTask dt = new KCDownloadTask(this, urlObj, aDestFilePath, aListener);

        // so dt is useless in that case, the reason I design the interface like this is that sometimes I don't need
        // a KCDownloadTask to use its public methods(stop, resume, cancel)
        return dt;
    }

    public KCDownloadTask startDownload(String aUrl, String aDestFilePath, KCDownloadListener aListener, boolean aUseResumable, boolean aNeedProgress) throws FileNotFoundException,
            URISyntaxException {
        KCDownloadTask downloadTask = createDownloadTask(aUrl, aDestFilePath, aListener, aUseResumable, aNeedProgress);
        startDownload(downloadTask, aUseResumable, aNeedProgress);
        return downloadTask;
    }

    public void startDownload(final KCDownloadTask aDownloadTask, final boolean aUseResumable, final boolean aNeedProgress) {
        synchronized (mCurrentRequests) {
            String key = aDownloadTask.getCacheKey();
            if (!mCurrentRequests.containsKey(key)) {
                mCurrentRequests.put(key, aDownloadTask);

                getExecutorService().execute(new Runnable() {
                    @Override
                    public void run() {
                        aDownloadTask.runTask(aUseResumable, aNeedProgress);
                    }
                });
            }
        }

        addWaiting(aDownloadTask);
    }


    public interface KCDownloadFilter {
        public boolean apply(KCDownloadTask aDownloadTask);
    }

    /**
     * Cancels all requests in this queue for which the given filter applies.
     *
     * @param filter The filtering function to use
     */
    public void cancelAll(KCDownloadFilter filter) {
        synchronized (mCurrentRequests) {
            Collection<KCDownloadTask> downloadTasks = mCurrentRequests.values();
            for (KCDownloadTask task : downloadTasks) {
                if (filter.apply(task)) {
                    task.cancel();
                }
            }
        }
    }

    private void addWaiting(KCDownloadTask aDownloadTask) {
        // Insert request into stage if there's already a request with the same cache key in flight.
        synchronized (mWaitingRequests) {
            String cacheKey = aDownloadTask.getCacheKey();
            if (mWaitingRequests.containsKey(cacheKey)) {
                // There is already a request in flight. Queue up.
                Queue<KCDownloadTask> stagedRequests = mWaitingRequests.get(cacheKey);
                if (stagedRequests == null) {
                    stagedRequests = new LinkedList<KCDownloadTask>();
                }
                stagedRequests.add(aDownloadTask);
                mWaitingRequests.put(cacheKey, stagedRequests);
                if (KCLog.DEBUG) {
                    KCLog.v("Download task for key=%s is in flight, putting on hold.", cacheKey);
                }
            } else {
                // Insert 'null' queue for this cacheKey, indicating there is now a request in flight.
                mWaitingRequests.put(cacheKey, null);
            }
        }
    }

    protected void removeWaitingRequest(KCDownloadTask aDownloadTask) {
        synchronized (mWaitingRequests) {
            String cacheKey = aDownloadTask.getCacheKey();
            Queue<KCDownloadTask> waitingRequests = mWaitingRequests.remove(cacheKey);
            if (waitingRequests != null) {
                if (KCLog.DEBUG) {
                    KCLog.v("Releasing %d waiting requests for cacheKey=%s.", waitingRequests.size(), cacheKey);
                }
            }
        }
    }

    //No matter success or failure
    // called in reportError, onComplete, cancel
    protected void finish(KCDownloadTask aDownloadTask) {
        // Remove from the map of requests currently being processed.
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(aDownloadTask.getCacheKey());
        }
        removeWaitingRequest(aDownloadTask);

        if (KCLog.DEBUG)
            KCLog.e("finsh DownloadTask");
    }

    protected ExecutorService getExecutorService() {
        if (mThreadService == null)
            mThreadService = Executors.newCachedThreadPool();
        return mThreadService;
    }

    public void shutdown() {
        if (mThreadService != null) {
            mThreadService.shutdown();
            mThreadService = null;
        }
    }


}
