package com.rntemplate.download;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCDownloadConfig {

    /**
     * tast config, config file
     */
    // 3 longs, '[CONFIG_META][FILE_LENGTH][CONFIG_LAST_CHUNK_OFFSET_INDEX]'
    protected final static int CONFIG_HEADER_SIZE = 3; // 3 longs
    protected final static int CONFIG_FILE_LENGTH_INDEX = 1; // the second long
    protected final static int CONFIG_LAST_CHUNK_OFFSET_INDEX = 2; // the third long
    protected final static int CONFIG_BYTE_OFFSET_SLOT_COUNT = 2; // 2 longs to keep start & end offsets of the download progress for each thread

    protected final static int CONFIG_META_DOWNLOAD_TIME_INDEX = 0; // start from byte 0, I use 2 bytes to keep the total time spent downloading the
    // task
    protected final static int CONFIG_META_THREAD_COUNT_INDEX = 7; // I use the seventh byte to keep the total thread count

    /**
     * task config
     */
    private final static int THREAD_COUNT_PER_TASK = 3;
    private int mThreadCountPerTask = THREAD_COUNT_PER_TASK;

    /**
     * worker config
     */
    protected final static int INITIAL_REQUEST_CHUNK_SIZE = 100 * 1024;
    protected final static int REQUEST_CHUNK_SIZE = 300 * 1024;
    protected final static int BUFFER_SIZE = 8192 * 4;

    protected final static int MAX_RETRY_COUNT = 10;
    protected final static int MAX_RETRY_WAIT_TIME = 24000;
    protected final static int INITIAL_RETRY_WAIT_TIME = 3000;

    /**
     * download progress updater config
     */
    private final static int UPDATE_PROGRESS_INTERVAL = 1000;
    private int mUpdateProgressInterval = UPDATE_PROGRESS_INTERVAL;

    private final static int DOWNLOAD_SPEED_SAMPLING_TIME_SPAN = 8000;
    private int mDownloadSpeedSamplingTimeSpan = DOWNLOAD_SPEED_SAMPLING_TIME_SPAN;

//	protected final static int MAX_DOWNLOADED_BYTE_ARR_INDEX = DOWNLOAD_SPEED_SAMPLING_TIME_SPAN / UPDATE_PROGRESS_INTERVAL - 1;


    /**
     * @param aCount: set the thread count， default value is {@value #THREAD_COUNT_PER_TASK}
     */
    public void setThreadCountPerTask(int aCount) {
        if (aCount > 0)
            mThreadCountPerTask = aCount;
    }

    /**
     * default value is {@value #THREAD_COUNT_PER_TASK}
     *
     * @return int
     * return thread count
     */
    public int getThreadCountPerTask() {
        return mThreadCountPerTask;
    }

    /**
     * @param aInterval set the Progress Interval
     */
    public void setUpdateProgressInterval(int aInterval) {
        if (aInterval > 0)
            mUpdateProgressInterval = aInterval;
    }

    /**
     * get the progress update interval default is {@value #UPDATE_PROGRESS_INTERVAL}
     *
     * @return the progress update interval
     */
    public int getUpdateProgressInterval() {
        return mUpdateProgressInterval;
    }

    /**
     * set the timespan of download speed sampling
     *
     * @param aTimeSpan time span
     */
    public void setDownloadSpeedSamplingTimeSpan(int aTimeSpan) {
        if (aTimeSpan > 0)
            mDownloadSpeedSamplingTimeSpan = aTimeSpan;
    }

    /**
     * get the timespan of download speed sampling;
     * defaut is {@value #DOWNLOAD_SPEED_SAMPLING_TIME_SPAN}
     *
     * @return the timespan
     */
    public int getDownloadSpeedSamplingTimeSpan() {
        return mDownloadSpeedSamplingTimeSpan;
    }


}
