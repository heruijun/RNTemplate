package com.mgzf.nativelikeruntime.download;

import com.mgzf.nativelikeruntime.debug.KCLog;

/**
 * Created by heruijun on 2017/6/21.
 */

class KCDownloadProgressUpdater extends Thread {

    private boolean running = true;
    private long downloadedByteSampleArr[];
    private int slotIndex;

    KCDownloadTask mDownloadTask;

    public KCDownloadProgressUpdater(KCDownloadTask aDownloadTask) {
        mDownloadTask = aDownloadTask;

        downloadedByteSampleArr = new long[getMaxDownloadedByteArrIndex() + 1];
    }

    private KCDownloadConfig downloadConfig() {
        return mDownloadTask.mDownloadConfig;
    }

    private long downloadedBytes() {
        return mDownloadTask.mDownloadedBytes;
    }

    private KCDownloadListener downloadListener() {
        return mDownloadTask.mNotifier;
    }

    private long fileLength() {
        return mDownloadTask.mFileLength;
    }


    public int getMaxDownloadedByteArrIndex() {
        return downloadConfig().getDownloadSpeedSamplingTimeSpan() / downloadConfig().getUpdateProgressInterval() - 1;
    }

    void stopLoop() {
        running = false;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(MIN_PRIORITY);

        long startTimestamp = System.currentTimeMillis();
        while (running) {
            try {
                Thread.sleep(downloadConfig().getUpdateProgressInterval());
                // we are not ready to propagate the progress, cause we currently haven't
                // downloaded any bytes, the mDownloadedBytes instance variable is not yet
                // initialized, which is done in DownloadWorker.initConfig()
                if (downloadedBytes() <= 0)
                    continue;

                int speed;
                if (slotIndex == getMaxDownloadedByteArrIndex()) {
                    long totalRead = 0;
                    synchronized (this) {
                        for (int i = 0; i < getMaxDownloadedByteArrIndex(); ++i) {
                            totalRead += downloadedByteSampleArr[i];
                            downloadedByteSampleArr[i] = downloadedByteSampleArr[i + 1];
                        }
                        totalRead += downloadedByteSampleArr[getMaxDownloadedByteArrIndex()];
                        downloadedByteSampleArr[getMaxDownloadedByteArrIndex()] = 0;
                    }

                    speed = (int) (totalRead * 1000 / downloadConfig().getDownloadSpeedSamplingTimeSpan());
                } else {
                    long totalRead = 0;
                    for (int i = 0; i <= slotIndex; ++i) {
                        totalRead += downloadedByteSampleArr[i];
                    }

                    long tsDelta = System.currentTimeMillis() - startTimestamp;
                    speed = (int) (totalRead * 1000 / tsDelta);

                    if (tsDelta > downloadConfig().getDownloadSpeedSamplingTimeSpan()) {
                        slotIndex = getMaxDownloadedByteArrIndex();
                    } else {
                        slotIndex = (int) (tsDelta / downloadConfig().getUpdateProgressInterval());
                    }
                }

                try {
                    downloadListener().onProgressUpdate(downloadedBytes(), fileLength(), speed);
                } catch (Exception e) {
                    if (KCLog.DEBUG)
                        KCLog.e(e);
                }
            } catch (Exception e) {
            }
        }
    }

    synchronized void onProgressUpdate(int bytes) {
        downloadedByteSampleArr[slotIndex] += bytes;
    }
}
