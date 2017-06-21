package com.rntemplate.download;

/**
 * Created by heruijun on 2017/6/21.
 * This class implements multithread download
 * <p>
 * == Format of the .cfg file
 * <p>
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | A |         |B|       C       |       D       |       E       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |       F       |       G       |       H       |       I       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |       J       |      ...      |      ...      |      ...      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * A(2 bytes): stores the total time spent downloading the file
 * B(1 byte): stores the number of threads used to download the file
 * C(8 bytes): stores file length(acquired from HTTP header Content-Length)
 * D(8 bytes): stores the end offset of the last requested chunk
 * E(8 bytes): stores the start offset for thread 1
 * F(8 bytes): stores the end offset for thread 1
 * G(8 bytes): stores the start offset for thread 2
 * H(8 bytes): stores the end offset for thread 2
 * I(8 bytes): stores the start offset for thread 3
 * J(8 bytes): stores the end offset for thread 3
 */

import com.rntemplate.debug.KCLog;
import com.rntemplate.io.KCUtilIO;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;


public class KCDownloadTask {
    protected KCDownloadEngine mDownloadEngine;
    protected URL mOrigUrl;
    protected URL mUrl;
    protected KCDownloadListener mNotifier;
    protected KCDownloadProgressUpdater mDownloadProgressUpdater;

    protected KCDownloadConfig mDownloadConfig = new KCDownloadConfig();

    protected File mDestFile;
    private File mConfigFile;
    private FileChannel mConfigFileChannel;
    protected LongBuffer mConfigHeaderBuffer;
    private ByteBuffer mConfigMetaBuffer; // the first 8 bytes of the config file
    protected long mLastChunkEndOffset;

    protected long mFileLength = -1;
    protected long mDownloadedBytes;


    protected List<KCDownloadWorker> mWorkerList = new ArrayList<KCDownloadWorker>();
    protected List<Boolean> mTaskStoppedStateList = new ArrayList<Boolean>(1);
    private volatile int mGlobalTaskRunSerialNo = -1;
    private final Object mControlTaskLock = new Object();

    // buffer objects(byte[]) that can be reused respectively by each thread
    private static ThreadLocal<byte[]> mBufferPool = new ThreadLocal<byte[]>();

    protected boolean mAborted = false;
    private boolean mPreparingToRun = true;
    private boolean mRunning;
    // set to true if the caller calls stop() before the task is started.
    private boolean mEarlyStopped;

    private long mStartDownloadTimestamp;

    protected SSLSocketFactory mSslSocketFactory;


    public KCDownloadTask(KCDownloadEngine aEngine, URL aUrl, String aDestFilePath) throws FileNotFoundException {
        this(aEngine, aUrl, aDestFilePath, null, null);
    }

    public KCDownloadTask(KCDownloadEngine aDownloadEngine, URL aUrl, String aDestFilePath, KCDownloadListener aNotifier) throws FileNotFoundException {
        this(aDownloadEngine, aUrl, aDestFilePath, aNotifier, null);
    }


    public KCDownloadTask(KCDownloadEngine aDownloadEngine, URL aUrl, String aDestFilePath, KCDownloadListener aNotifier, KCDownloadConfig aDownloadConfig) throws FileNotFoundException {
        mDownloadEngine = aDownloadEngine;
        mOrigUrl = mUrl = aUrl; // keep the original url
        mDestFile = new File(aDestFilePath);
        mConfigFile = new File(mDestFile.getAbsolutePath() + ".cfg");
        mNotifier = aNotifier;
        if (aDownloadConfig == null)
            mDownloadConfig = new KCDownloadConfig();
        else
            mDownloadConfig = aDownloadConfig;

        setSSLSocketFactory(null);
    }


    /**
     * Sets the SSL socket factory for this instance.
     *
     * @param aSF
     *            the SSL socket factory to be used by this instance.
     * @throws IllegalArgumentException
     *             if the specified socket factory is {@code null}.
     */
    public void setSSLSocketFactory(SSLSocketFactory aSF) {
        if (aSF == null) {
            mSslSocketFactory = KCSSLManager.setCertificates().mSSLSocketFactory;
        } else {
            mSslSocketFactory = aSF;
        }
    }

    /**
     * Returns the SSL socket factory used by this instance.
     *
     * @return the SSL socket factory used by this instance.
     */
    public SSLSocketFactory getSSLSocketFactory() {
        return mSslSocketFactory;
    }


    //is Download task identity
    public String getCacheKey() {
        return mOrigUrl.toString();
    }

    public KCDownloadListener getDownloadNotifier() {
        return mNotifier;
    }

    public void runTask(final boolean useResumable, final boolean needProgress) {
        // synchrnozized to ensure mGlobalTaskRunSerialNo is accessed atomically
        synchronized (mControlTaskLock) {
            if (mEarlyStopped) {
                mEarlyStopped = false;
                return;
            }

            KCDownloadWorker worker = null;
            try {
                if (mNotifier != null)
                    mNotifier.onPrepare();

                if (!mDestFile.exists() || !mConfigFile.exists()) {
                    File parentFile = mDestFile.getParentFile();
                    if (parentFile != null && !parentFile.exists()) {
                        parentFile.mkdirs();
                    }

                    mDestFile.delete();
                    mConfigFile.delete();
                }


                // if resumable download is required, a config file should be created to keep the configurations
                if (useResumable) {
                    initConfigFileBuffer();
                    verifyLastChunkEndOffset();
                }

                mTaskStoppedStateList.add(++mGlobalTaskRunSerialNo, Boolean.FALSE);
                worker = new KCDownloadWorker(this, 0, mGlobalTaskRunSerialNo);
                mWorkerList.add(worker);
                worker.initRequest(true, useResumable);

                mStartDownloadTimestamp = System.currentTimeMillis();

                // is a download progress meter needed?
                if (needProgress) {
                    mDownloadProgressUpdater = new KCDownloadProgressUpdater(this);
                    if (mNotifier != null)
                        mDownloadProgressUpdater.start();
                }

                mPreparingToRun = false;
                mRunning = true;
            } catch (Exception e) {
                if (KCLog.DEBUG)
                    KCLog.e(e);
                reportError(worker, e);
            }
        }
    }


    @SuppressWarnings("resource")
    private void initConfigFileBuffer() throws IOException {
        mConfigFileChannel = new RandomAccessFile(mConfigFile, "rw").getChannel();
        // the length of the config file is fixed
        int configFileSize = (Long.SIZE / 8 * KCDownloadConfig.CONFIG_HEADER_SIZE) + (mDownloadConfig.getThreadCountPerTask() * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT * (Long.SIZE / 8));
        mConfigHeaderBuffer = mConfigFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, configFileSize).asLongBuffer();
        mConfigMetaBuffer = mConfigFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, Long.SIZE / 8);
    }

    private void verifyLastChunkEndOffset() {
        long lastChunkEndOffset = mConfigHeaderBuffer.get(KCDownloadConfig.CONFIG_LAST_CHUNK_OFFSET_INDEX);
        if (lastChunkEndOffset > 0) {
            long maxChunkEndOffset = 0;
            int threadCount = (mConfigMetaBuffer.get(KCDownloadConfig.CONFIG_META_THREAD_COUNT_INDEX) & 0xff);
            for (int i = 0; i < threadCount; ++i) {
                long chunkEndOffset = getEndOffset(i);
                if (maxChunkEndOffset < chunkEndOffset)
                    maxChunkEndOffset = chunkEndOffset;
            }

            if (maxChunkEndOffset != lastChunkEndOffset)
                mConfigHeaderBuffer.put(KCDownloadConfig.CONFIG_LAST_CHUNK_OFFSET_INDEX, maxChunkEndOffset);
        }
    }

    private boolean mDone;

    protected synchronized void onComplete(boolean force) {
        if (!force && (mDone || mDownloadedBytes != mFileLength)) {
            if (KCLog.DEBUG)
                KCLog.e("will onComplet");
            return;
        }

        stopDownloadProgressAndSpeedUpdater();
        saveTotalDownloadTime();

        if (mDownloadEngine != null) {
            mDownloadEngine.finish(this);
        }

        KCUtilIO.closeSilently(mConfigFileChannel);
        if (mConfigFile != null && mConfigFile.exists())
            mConfigFile.delete();
        mDone = true;
        mRunning = false;

        if (mNotifier != null) {
            try {
                mNotifier.onComplete(mDownloadedBytes, mFileLength, getTotalDownloadTime());
            } catch (Exception e) {
                KCLog.e(e);
            }
        }
    }

    public void cancel() {
        stop();
        if (mDownloadEngine != null) {
            mDownloadEngine.finish(this);
        }
    }

    private void stop() {
        synchronized (mControlTaskLock) {
            if (mPreparingToRun) { // the task is not yet started
                // so we mark it as "early-stopped"
                mEarlyStopped = true;
                return;
            }

            if (!mRunning)
                return;

            saveTotalDownloadTime();

            stopDownloadProgressAndSpeedUpdater();

            mTaskStoppedStateList.set(mGlobalTaskRunSerialNo, Boolean.TRUE);

            mAborted = true;
            // disconnect all HTTP connections for the current task
            for (int i = 0; i < mWorkerList.size(); ++i) {
                HttpURLConnection conn = mWorkerList.get(i).mHttpConn;
                if (conn != null)
                    try {
                        conn.disconnect();
                    } catch (Exception e) {
                        // TODO: handle exception
                        KCLog.e(e);
                    }

            }

            KCUtilIO.closeSilently(mConfigFileChannel);

            mRunning = false;
        }
    }

    private void saveTotalDownloadTime() {
        if (mStartDownloadTimestamp > 0) {
            int downloadTimeSpan = (int) ((System.currentTimeMillis() - mStartDownloadTimestamp) / 1000);
            if (downloadTimeSpan > 0) {
                setTotalDownloadTime(getTotalDownloadTime() + downloadTimeSpan);
            }
            mStartDownloadTimestamp = 0;
        }
    }

    private void stopDownloadProgressAndSpeedUpdater() {
        if (mDownloadProgressUpdater != null) {
            mDownloadProgressUpdater.stopLoop();
            mDownloadProgressUpdater.interrupt();
            mDownloadProgressUpdater = null;
        }
    }

    public void resume(final boolean useResumable) {
        synchronized (mControlTaskLock) {
            if (mRunning)
                return;

            mAborted = false;
            mPreparingToRun = true;

            mDownloadedBytes = 0;
            mWorkerList.clear();
            mUrl = mOrigUrl;

            mDownloadEngine.getExecutorService().execute(new Runnable() {
                @Override
                public void run() {
                    runTask(useResumable, true);
                }
            });
        }
    }

    protected void reportError(KCDownloadWorker worker, Throwable e) {
        // we only need to stop the task ONCE, so we have to check if the task
        // is already stopped
        if (worker == null || !worker.isStopped()) {
            if (worker != null)
                stop();

            if (mNotifier != null)
                mNotifier.onError(mDownloadedBytes, e);

            if (mDownloadEngine != null) {
                mDownloadEngine.finish(this);
            }
        }
    }

    public boolean isStopped() {
        synchronized (mControlTaskLock) {
            return mTaskStoppedStateList.get(mGlobalTaskRunSerialNo);
        }
    }

    protected static byte[] getBuffer() {
        byte[] buffer = mBufferPool.get();
        if (buffer == null) {
            buffer = new byte[KCDownloadConfig.BUFFER_SIZE];
            mBufferPool.set(buffer);
        }
        return buffer;
    }

    /**
     * this method requests next chunk of bytes to download, it is synchronized because it will be called concurrently by multiple threads
     *
     * @return true if chunks of the file are not yet fully consumed
     **/
    protected synchronized boolean requestNextChunk(int threadIndex) {
        if (mLastChunkEndOffset < mFileLength) {
            int blockSize = (int) Math.min(mFileLength - mLastChunkEndOffset, KCDownloadConfig.REQUEST_CHUNK_SIZE);

            // index = 3 + threadIndex * 2
            // example:
            // if threadIndex = 1, index = 3 + 1 * 2, then the slot to store the
            // start offset of the chunk is 5, end offset of the chunk is 6
            int index = KCDownloadConfig.CONFIG_HEADER_SIZE + (threadIndex * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT);
            mConfigHeaderBuffer.put(index, mLastChunkEndOffset); // mutable startOffset, increased as downloaded bytes being increased
            mConfigHeaderBuffer.put(index + 1, mLastChunkEndOffset + blockSize); // endOffset

            // update mLastChunkEndOffset and persist it in the config file
            mLastChunkEndOffset += blockSize;
            mConfigHeaderBuffer.put(KCDownloadConfig.CONFIG_LAST_CHUNK_OFFSET_INDEX, mLastChunkEndOffset);

            return true;
        }
        return false;
    }

    // use 2 bytes to keep the time
    private void setTotalDownloadTime(int timeInSeconds) {
        if (mConfigMetaBuffer != null) {
            mConfigMetaBuffer.put(KCDownloadConfig.CONFIG_META_DOWNLOAD_TIME_INDEX, (byte) (timeInSeconds >> 8));
            mConfigMetaBuffer.put(KCDownloadConfig.CONFIG_META_DOWNLOAD_TIME_INDEX + 1, (byte) timeInSeconds);
        }
    }

    // use 2 bytes to keep the time
    private int getTotalDownloadTime() {
        if (mConfigMetaBuffer != null)
            return ((mConfigMetaBuffer.get(KCDownloadConfig.CONFIG_META_DOWNLOAD_TIME_INDEX) << 8) & 0xff00) | (mConfigMetaBuffer.get(KCDownloadConfig.CONFIG_META_DOWNLOAD_TIME_INDEX + 1) & 0xff);

        return -1;
    }

    protected void setThreadCount(int count) {
        if (mConfigMetaBuffer != null)
            mConfigMetaBuffer.put(KCDownloadConfig.CONFIG_META_THREAD_COUNT_INDEX, (byte) count);
    }

    public int getThreadCount() {
        int count = 1;
        if (mConfigMetaBuffer != null) {
            count = mConfigMetaBuffer.get(KCDownloadConfig.CONFIG_META_THREAD_COUNT_INDEX) & 0xff;
        }
        return count;
    }

    protected void setStartOffset(int threadIndex, long offset) {
        int index = KCDownloadConfig.CONFIG_HEADER_SIZE + (threadIndex * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT);
        mConfigHeaderBuffer.put(index, offset);
    }

    protected void setEndOffset(int threadIndex, long offset) {
        int index = KCDownloadConfig.CONFIG_HEADER_SIZE + (threadIndex * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT) + 1;
        mConfigHeaderBuffer.put(index, offset);
    }

    protected long getStartOffset(int threadIndex) {
        int index = KCDownloadConfig.CONFIG_HEADER_SIZE + (threadIndex * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT);
        return mConfigHeaderBuffer.get(index);
    }

    protected long getEndOffset(int threadIndex) {
        int index = KCDownloadConfig.CONFIG_HEADER_SIZE + (threadIndex * KCDownloadConfig.CONFIG_BYTE_OFFSET_SLOT_COUNT) + 1;
        return mConfigHeaderBuffer.get(index);
    }


}
