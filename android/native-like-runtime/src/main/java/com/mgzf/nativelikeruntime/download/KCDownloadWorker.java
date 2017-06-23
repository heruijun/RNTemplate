package com.mgzf.nativelikeruntime.download;

import android.os.SystemClock;

import com.mgzf.nativelikeruntime.debug.KCLog;
import com.mgzf.nativelikeruntime.io.KCUtilIO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by heruijun on 2017/6/21.
 */

class KCDownloadWorker {


    private int mRetryWaitMilliseconds = KCDownloadConfig.INITIAL_RETRY_WAIT_TIME;
    private int mCurRetryCount;

    protected HttpURLConnection mHttpConn;

    private long mStartByteOffset;
    private long mEndByteOffset;
    private int mThreadIndex;

    private boolean mInitConfig;
    private RandomAccessFile mDestRandomAccessFile;

    private int mCurSerialNo;

    private boolean mPartialContentExpected;

    KCDownloadTask mDownloadTask;

    protected KCDownloadWorker(KCDownloadTask aDownloadTask, int threadIndex, int serialNo) throws Exception {
        mDownloadTask = aDownloadTask;
        mThreadIndex = threadIndex;
        mCurSerialNo = serialNo;

        // we once used multithread to download the current task, so we should
        // continue using multithread and expect partial content
        mPartialContentExpected = mDownloadTask.getThreadCount() > 1;
    }

    public boolean isStopped() {
        return mDownloadTask.mTaskStoppedStateList.get(mCurSerialNo);
    }

    protected void initRequest(final boolean init, final boolean useResumable) {
        if (isStopped())
            return;

        mDownloadTask.mDownloadEngine.getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        while (true) {
                            if (KCLog.DEBUG)
                                KCLog.d(">>>>DT download retry: " + mCurRetryCount + ", url: " + mDownloadTask.mUrl.toString());
                            if (!executeRequest(useResumable)) {
                                mDownloadTask.mFileLength = mDownloadTask.mConfigHeaderBuffer.get(KCDownloadConfig.CONFIG_FILE_LENGTH_INDEX);
                                mDownloadTask.mDownloadedBytes = mDownloadTask.mFileLength;
                                mDownloadTask.onComplete(true);
                                break;
                            } else {
                                int statusCode = mHttpConn.getResponseCode();
                                if (statusCode == HttpURLConnection.HTTP_MOVED_TEMP || statusCode == HttpURLConnection.HTTP_MOVED_PERM) {
                                    mDownloadTask.mUrl = new URL(mHttpConn.getHeaderField("Location"));
                                    if (KCLog.DEBUG)
                                        KCLog.d(">>>>DT redirecting to: " + mDownloadTask.mUrl.toString());
                                } else {
                                    if (KCLog.DEBUG)
                                        KCLog.d(">>>>DT status: " + statusCode + ", expect_partial_content: " + mPartialContentExpected);
                                    if (statusCode != HttpURLConnection.HTTP_PARTIAL) {
                                        // we are expecting partial content, while the status code is not 206
                                        // so we should retry the connection with the original url
                                        if (mPartialContentExpected) {
                                            mDownloadTask.mUrl = mDownloadTask.mOrigUrl;
                                            throw new KCWrongStatusCodeException();
                                        }

                                        // the status code is 200, while the Content-Length is 0, this is not 'scientific'
                                        // so again, we should retry the connection with the original url if we haven't yet
                                        // reach MAX_RETRY_COUNT
                                        if (statusCode == HttpURLConnection.HTTP_OK && !initContentLength()) {
                                            // if Content-Length is absent or equal to 0
                                            // we should retry the original URL
                                            mDownloadTask.mUrl = mDownloadTask.mOrigUrl;
                                            if (retry()) {
                                                continue;
                                            } else {
                                                // we reach MAX_RETRY_COUNT
                                                mDownloadTask.reportError(KCDownloadWorker.this, new KCZeroContentLengthException());
                                                return;
                                            }
                                        }
                                    }

                                    if (KCLog.DEBUG)
                                        KCLog.d(">>>>DT thread: " + Thread.currentThread().getName() + ", final url: " + mDownloadTask.mUrl.toString());
                                    break;
                                }
                            }
                        }

                        handleResponse();
                        break;
                    } catch (KCUnexpectedStatusCodeException e) {
                        mDownloadTask.reportError(KCDownloadWorker.this, e);
                        break;
                    } catch (Throwable e) {

                        if (e instanceof IOException) {
                            String msg = e.getMessage();
                            if (msg != null) {
                                msg = msg.toLowerCase(Locale.getDefault());
                                // oh shit, no free disk space is left
                                if (msg.contains("enospc") || msg.contains("no space")) {
                                    mDownloadTask.reportError(KCDownloadWorker.this, e);
                                    break;
                                }
                            }
                        }
                        if (e instanceof KCWrongStatusCodeException) {
                            // WrongStatusCodeException, which means the remote server initially
                            // told us that it supports resumable download, while later it returned
                            // 200 instead of 206, so we will retry at most MAX_RETRY_COUNT times
                            // and see if the remote server will return correct status code
                            mPartialContentExpected = true;
                        }

                        if (!mDownloadTask.mAborted) {
                            // if the task is NOT manually aborted
                            if (!retry()) {
                                // if we still do NOT reach MAX_RETRY_COUNT

                                if (e instanceof KCWrongStatusCodeException) {
                                    mDownloadTask.reportError(KCDownloadWorker.this, e);
                                } else {
                                    Exception reachMaxRetryException = new KCReachMaxRetryException(e);
                                    mDownloadTask.reportError(KCDownloadWorker.this, reachMaxRetryException);
                                }
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }

                if (KCLog.DEBUG)
                    KCLog.d(">>>> delete FD: " + (mDestRandomAccessFile != null) + ", " + Thread.currentThread().getName());
                // ensures that the file descriptor is correctly closed.
                KCUtilIO.closeSilently(mDestRandomAccessFile);
            }

            private void handleResponse() throws Exception {
                int statusCode = mHttpConn.getResponseCode();
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    // oh no, cannot use multithread
                    readFullContent();
                } else if (statusCode == HttpURLConnection.HTTP_PARTIAL) {
                    // cool, use multithread
                    handlePartialContent(init && !mInitConfig);
                } else if (statusCode >= 400 && statusCode < 500) {
                    if (KCLog.DEBUG)
                        KCLog.d("unexpected status code, URL: " + mDownloadTask.mUrl.toString());
                    // client error? probably because of bad URL
                    throw new KCUnexpectedStatusCodeException();
                } else if (statusCode != 416) { // REQUESTED_RANGE_NOT_SATISFIABLE = 416
                    // this rarely happens, but according to the RFC, we should assume it would happen
                    throw new IOException(statusCode + " " + mHttpConn.getResponseMessage());
                }
            }

            private boolean retry() {
                // if the request is not manually aborted by the user, it is most probably
                // because of network error, so we have to retry~
                if (++mCurRetryCount <= KCDownloadConfig.MAX_RETRY_COUNT) {
                    mRetryWaitMilliseconds = Math.min(KCDownloadConfig.MAX_RETRY_WAIT_TIME, mRetryWaitMilliseconds * 2);
                    SystemClock.sleep(mRetryWaitMilliseconds);
                    // luohh add for test
                    // SystemClock.sleep(2 * 1000);
                    return true;
                }

                return false;
            }
        });
    }

    private boolean executeRequest(final boolean useResumable) throws IOException {
        if (useResumable) {
            if (mDownloadTask.mFileLength > 0 && mStartByteOffset >= mDownloadTask.mFileLength)
                return false;
            mStartByteOffset = mDownloadTask.getStartOffset(mThreadIndex);
            mEndByteOffset = mDownloadTask.getEndOffset(mThreadIndex);
            // correct the startByteOffset if it was accidentally set to a number
            // larger than the endByteOffset(currently I have no idea what would cause this)
            if (mStartByteOffset > mEndByteOffset) {
                mStartByteOffset = mEndByteOffset - KCDownloadConfig.REQUEST_CHUNK_SIZE;
                if (mStartByteOffset < 0)
                    mStartByteOffset = 0;
            }

            if (mEndByteOffset == 0) {
                // mEndByteOffset is 0, which means that this is the first request we ever make for the current DownloadTask
                mEndByteOffset = KCDownloadConfig.INITIAL_REQUEST_CHUNK_SIZE;
            } else if (mStartByteOffset == mEndByteOffset) {
                // if the chunk for the current thread was *just* finished, we will try
                // to request a new chunk, if it does not succeed, it means we already
                // reach the end of the file, and we will keep going and see if the next
                // thread has already finished its last chunk (the 'else' part)
                if (mDownloadTask.requestNextChunk(mThreadIndex)) {
                    mStartByteOffset = mDownloadTask.getStartOffset(mThreadIndex);
                    mEndByteOffset = mDownloadTask.getEndOffset(mThreadIndex);
                } else {
                    // the current thread already finishes its share of chunks and we reach the end of the file
                    // so we shift to the next thread, see the comment in startWorkers()
                    ++mThreadIndex;
                    if (mThreadIndex >= mDownloadTask.getThreadCount())
                        return false;

                    return executeRequest(true);
                }
            }

            if (mDestRandomAccessFile == null)
                mDestRandomAccessFile = new RandomAccessFile(mDownloadTask.mDestFile, "rw");

            mDestRandomAccessFile.seek(mStartByteOffset);

            mHttpConn = openConnection(mDownloadTask.mUrl);
            mHttpConn.addRequestProperty("Range", "bytes=" + mStartByteOffset + "-" + (mEndByteOffset - 1));
        } else {
            mHttpConn = openConnection(mDownloadTask.mUrl);
        }
        // we are not expecting gzip. just give the raw bytes.
        mHttpConn.setRequestProperty("Accept-Encoding", "identity");
        mHttpConn.setConnectTimeout(15000);
        mHttpConn.setReadTimeout(15000);

        // luohh add for test
        // mHttpConn.setConnectTimeout(2 * 1000);
        // mHttpConn.setReadTimeout(2 * 1000);

        mHttpConn.setRequestMethod("GET");
        return true;
    }


    /**
     * Create an {@link HttpURLConnection} for the specified {@code url}.
     */
    protected HttpURLConnection createConnection(URL url) throws IOException {
        //		return (HttpURLConnection) url.openConnection();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return connection;
    }

    /**
     * Opens an {@link HttpURLConnection} with parameters.
     *
     * @param url
     * @return an open connection
     * @throws IOException
     */
    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection connection = createConnection(url);

//        // Workaround for the M release HttpURLConnection not observing the
//        // HttpURLConnection.setFollowRedirects() property.
//        // https://code.google.com/p/android/issues/detail?id=194495
//        //connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());
//        connection.setInstanceFollowRedirects(request.getFollowRedirects());
//        int timeoutMs = request.getTimeoutMs();
//        connection.setConnectTimeout(timeoutMs);
//        connection.setReadTimeout(timeoutMs);b
//        connection.setUseCaches(false);
//        connection.setDoInput(true);

        // use caller-provided custom SslSocketFactory, if any, for HTTPS
        if ("https".equals(url.getProtocol()) && mDownloadTask.mSslSocketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(mDownloadTask.mSslSocketFactory);
        }

        return connection;
    }

    private void handlePartialContent(boolean init) throws Exception {
        if (init) {
            mPartialContentExpected = true;

            readContentLength();

            if (mDownloadTask.mFileLength > 0 && mDownloadTask.mDownloadedBytes <= mDownloadTask.mFileLength) {
                int threadCount = initConfig();
                if (threadCount > 0) {
                    if (mDownloadTask.mNotifier != null)
                        mDownloadTask.mNotifier.onReceiveFileLength(mDownloadTask.mDownloadedBytes, mDownloadTask.mFileLength);
                    startWorkers(threadCount);
                } else {
                    KCLog.e("handlePartialContent:threadCount=0");
                    readFullContent();
                    return;
                }
            } else {
                // not possible to reach here according to RFC-2616(http://tools.ietf.org/html/rfc2616#page-122)
                // because if the status code is 206(SC_PARTIAL_CONTENT), Content-Range will not be absent,
                // thus mFileLength is not possible to be less than or equal to 0
                readFullContent();
                return;
            }
        }

        if (isStopped())
            return;

        readPartialContent();
    }

    private void readContentLength() {
        try {
            String contentRange = mHttpConn.getHeaderField("Content-Range");
            mDownloadTask.mFileLength = Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1));
        } catch (Exception e) {
            if (KCLog.DEBUG)
                KCLog.e(e);
        }
    }

    private int initConfig() {
        int threadCount = mDownloadTask.getThreadCount();
        long savedFileLength = mDownloadTask.mConfigHeaderBuffer.get(KCDownloadConfig.CONFIG_FILE_LENGTH_INDEX);

        if (threadCount == 0 || (savedFileLength != 0 && savedFileLength != mDownloadTask.mFileLength)) {
            threadCount = mDownloadTask.mDownloadConfig.getThreadCountPerTask();
            while (threadCount > 1 && mDownloadTask.mFileLength / threadCount < KCDownloadConfig.INITIAL_REQUEST_CHUNK_SIZE) {
                // if the file is too small, we don't need that many threads
                --threadCount;
            }

            // the preset mEndByteOffset might be bigger than the actual Content-Length(if the file is smaller than INITIAL_REQUEST_CHUNK_SIZE)
            if (mEndByteOffset > mDownloadTask.mFileLength)
                mEndByteOffset = mDownloadTask.mFileLength;
            mDownloadTask.setEndOffset(mThreadIndex, mEndByteOffset);
            // update mLastChunkEndOffset(the currently biggest offset we reached.)
            mDownloadTask.mLastChunkEndOffset = mEndByteOffset;
            mDownloadTask.mConfigHeaderBuffer.put(KCDownloadConfig.CONFIG_LAST_CHUNK_OFFSET_INDEX, mDownloadTask.mLastChunkEndOffset);

            // persis fileLength and threadCount in the config file
            mDownloadTask.mConfigHeaderBuffer.put(KCDownloadConfig.CONFIG_FILE_LENGTH_INDEX, mDownloadTask.mFileLength);
            mDownloadTask.setThreadCount(threadCount);
        } else {
            // init current download offset
            mDownloadTask.mLastChunkEndOffset = mDownloadTask.mConfigHeaderBuffer.get(KCDownloadConfig.CONFIG_LAST_CHUNK_OFFSET_INDEX);
            mDownloadTask.mDownloadedBytes = mDownloadTask.mLastChunkEndOffset;
            // the following LOOP ensures that downloadedBytes is accurate!
            for (int i = mThreadIndex; i < threadCount; ++i) {
                // why we minus mDownloadedBytes this way? see 'requestNextChunk()'
                mDownloadTask.mDownloadedBytes -= (mDownloadTask.getEndOffset(i) - mDownloadTask.getStartOffset(i));
            }

            if (mDownloadTask.mDownloadedBytes >= mDownloadTask.mFileLength) {
                threadCount = 0;
            }
        }

        mInitConfig = true;
        return threadCount;
    }

    private void startWorkers(int threadCount) throws Exception {
        // The reason that we add up 'mThreadIndex' for 'i' is because we almost finish downloading
        // the entire file, the thread represented by 'mThreadIndex' cannot request more chunks to download
        // so we will not need to start a worker for it, and this is why there is a '++mThreadIndex' in executeRequest()

        // The reason 'i' starts from '1' is that the current tbread is already running, which should
        // not be counted in this loop
        for (int i = mThreadIndex + 1; i < threadCount; ++i) {
            long startOffset = mDownloadTask.getStartOffset(i);
            long endOffset = mDownloadTask.getEndOffset(i);
            if (startOffset < endOffset || (startOffset == endOffset && mDownloadTask.requestNextChunk(i))) {
                KCDownloadWorker worker = new KCDownloadWorker(mDownloadTask, i, mCurSerialNo);
                mDownloadTask.mWorkerList.add(worker);
                worker.initRequest(false, true);
            } else {
                break;
            }
        }
    }

    private void readPartialContent() throws Exception {
        mCurRetryCount = 0;
        mRetryWaitMilliseconds = KCDownloadConfig.INITIAL_RETRY_WAIT_TIME;

        InputStream is = null;

        byte[] buffer = mDownloadTask.getBuffer();
        while (true) {
            int blockSize = 0;
            try {
                checkStatusCodeForPartialContent();
                is = mHttpConn.getInputStream();

                int len = 1;
                while (len > 0) {
                    len = is.read(buffer, blockSize, KCDownloadConfig.BUFFER_SIZE - blockSize);
                    if (len > 0) {
                        blockSize += len;
                        // ensures the buffer is filled to avoid making IO busy
                        if (blockSize < KCDownloadConfig.BUFFER_SIZE)
                            continue;
                    }

                    mDestRandomAccessFile.write(buffer, 0, blockSize);

                    syncReadBytes(blockSize);

                    updateDownloadedBytes(blockSize);
                    // propagate the download progress
                    if (mDownloadTask.mDownloadProgressUpdater != null)
                        mDownloadTask.mDownloadProgressUpdater.onProgressUpdate(blockSize);
                    blockSize = 0;
                }
            } catch (Exception e) {
                if (blockSize > 0) {
                    mDestRandomAccessFile.write(buffer, 0, blockSize);
                    syncReadBytes(blockSize);

                    updateDownloadedBytes(blockSize);
                    if (mDownloadTask.mDownloadProgressUpdater != null)
                        mDownloadTask.mDownloadProgressUpdater.onProgressUpdate(blockSize);
                }

                throw e;
            } finally {
                KCUtilIO.closeSilently(is);
                mHttpConn.disconnect();
            }

            // already downloaded the last chunk or the task was stopped
            boolean downloadedLastChunk = mStartByteOffset >= mEndByteOffset && !mDownloadTask.requestNextChunk(mThreadIndex);
            if (downloadedLastChunk || isStopped()) {
                if (downloadedLastChunk)
                    mDownloadTask.onComplete(false);
                break;
            } else {
                boolean isExecute = executeRequest(true);
                if (!isExecute) {
                    mDownloadTask.onComplete(true);
                    break;
                }

            }
        }
    }

    private void checkStatusCodeForPartialContent() throws IOException {
        long ts = System.currentTimeMillis();
        if (KCLog.DEBUG)
            KCLog.d(">>>>DT HTTP REQUEST starts" + Thread.currentThread().getName());

        if (mHttpConn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
            if (KCLog.DEBUG)
                KCLog.d(">>>> checkStatusCodeForPartialContent: " + Thread.currentThread().getName() + ", wrong status code: " + mHttpConn.getResponseCode());
            throw new KCWrongStatusCodeException();
        }
        if (KCLog.DEBUG)
            KCLog.d(">>>>DT HTTP REQUEST ends: " + (System.currentTimeMillis() - ts) + ", " + Thread.currentThread().getName());
    }

    // persist the downloaded byte count in the config file
    private void syncReadBytes(int total) throws IOException {
        mStartByteOffset += total;
        mDownloadTask.setStartOffset(mThreadIndex, mStartByteOffset);
    }

    private void updateDownloadedBytes(long bytes) {
        mDownloadTask.mDownloadedBytes += bytes;
    }

    // for single thread download
    private void readFullContent() throws IOException {
        mCurRetryCount = 0;
        mRetryWaitMilliseconds = KCDownloadConfig.INITIAL_RETRY_WAIT_TIME;

        if (mDownloadTask.mNotifier != null)
            mDownloadTask.mNotifier.onReceiveFileLength(0, mDownloadTask.mFileLength);
        mDownloadTask.mDownloadedBytes = 0;

        FileOutputStream fos = new FileOutputStream(mDownloadTask.mDestFile);

        InputStream is = null;

        byte[] buffer = mDownloadTask.getBuffer();
        int blockSize = 0;
        try {
            is = mHttpConn.getInputStream();

            int len = 1;
            while (len > 0) {
                len = is.read(buffer, blockSize, KCDownloadConfig.BUFFER_SIZE - blockSize);
                if (len > 0) {
                    blockSize += len;
                    if (blockSize < KCDownloadConfig.BUFFER_SIZE)
                        continue;
                }

                fos.write(buffer, 0, blockSize);

                updateDownloadedBytes(blockSize);
                if (mDownloadTask.mDownloadProgressUpdater != null)
                    mDownloadTask.mDownloadProgressUpdater.onProgressUpdate(blockSize);
                blockSize = 0;
            }

            mDownloadTask.onComplete(true);
        } catch (Exception e) {
            if (blockSize > 0) {
                fos.write(buffer, 0, blockSize);

                updateDownloadedBytes(blockSize);
                if (mDownloadTask.mDownloadProgressUpdater != null)
                    mDownloadTask.mDownloadProgressUpdater.onProgressUpdate(blockSize);
            }
        } finally {
            KCUtilIO.closeSilently(is);
            KCUtilIO.closeSilently(fos);
            mHttpConn.disconnect();
        }
    }

    private boolean initContentLength() {
        mDownloadTask.mFileLength = mHttpConn.getContentLength();

        return mDownloadTask.mFileLength > 0;
    }


}
