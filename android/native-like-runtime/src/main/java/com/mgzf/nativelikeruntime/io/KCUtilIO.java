package com.mgzf.nativelikeruntime.io;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.mgzf.nativelikeruntime.debug.KCLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by heruijun on 2017/6/21.
 */

public class KCUtilIO {
    /**
     * {@value}
     */
    public static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32 KB
    /**
     * {@value}
     */
    public static final int DEFAULT_TOTAL_SIZE = 500 * 1024; // 500 Kb
    /**
     * {@value}
     */
    public static final int CONTINUE_LOADING_PERCENTAGE = 75;

    private KCUtilIO() {
    }

    /**
     * Copies stream, fires progress events by listener, can be interrupted by listener. Uses buffer size = {@value #DEFAULT_BUFFER_SIZE} bytes.
     *
     * @param is       Input stream
     * @param os       Output stream
     * @param listener null-ok; Listener of copying progress and controller of copying interrupting
     * @return <b>true</b> - if stream copied successfully; <b>false</b> - if copying was interrupted by listener
     * @throws IOException throws a IOException
     */
    public static boolean copyStream(InputStream is, OutputStream os, KCCopyListener listener) throws IOException {
        return copyStream(is, os, listener, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copies stream, fires progress events by listener, can be interrupted by listener.
     *
     * @param is         Input stream
     * @param os         Output stream
     * @param listener   null-ok; Listener of copying progress and controller of copying interrupting
     * @param bufferSize Buffer size for copying, also represents a step for firing progress listener callback, i.e. progress event will be fired after every
     *                   copied <b>bufferSize</b> bytes
     * @return <b>true</b> - if stream copied successfully; <b>false</b> - if copying was interrupted by listener
     * @throws IOException throws a IOException
     */
    public static boolean copyStream(InputStream is, OutputStream os, KCCopyListener listener, int bufferSize) throws IOException {
        return copyStream(null, is, os, listener, bufferSize);
    }


    /**
     * Copies stream, fires progress events by listener, can be interrupted by listener.
     *
     * @param aPool         ByteArrayPool
     * @param aInputStream  Input stream
     * @param aOutputStream Output stream
     * @param listener      null-ok; Listener of copying progress and controller of copying interrupting
     * @param bufferSize    Buffer size for copying, also represents a step for firing progress listener callback, i.e. progress event will be fired after every
     *                      copied <b>bufferSize</b> bytes
     * @return <b>true</b> - if stream copied successfully; <b>false</b> - if copying was interrupted by listener
     * @throws IOException throws a IOException
     */
    public static boolean copyStream(KCByteArrayPool aPool, InputStream aInputStream, OutputStream aOutputStream, KCCopyListener listener, int bufferSize) throws IOException {
        byte[] buffer = null;

        try {
            if (aInputStream == null || aOutputStream == null) {
                throw new IOException();
            }

            if (aPool != null)
                buffer = aPool.getBuf(bufferSize);
            else
                buffer = new byte[bufferSize];

            int current = 0;
            int total = aInputStream.available();
            if (total <= 0) {
                total = DEFAULT_TOTAL_SIZE;
            }
            int count;
            //check before
            if (shouldStopLoading(listener, current, total, buffer))
                return false;
            while ((count = aInputStream.read(buffer, 0, bufferSize)) != -1) {
                aOutputStream.write(buffer, 0, count);
                current += count;
                if (shouldStopLoading(listener, current, total, buffer))
                    return false;
            }
            aOutputStream.flush();
            return true;
        } finally {
            if (aPool != null)
                aPool.returnBuf(buffer);
        }

    }


    private static boolean shouldStopLoading(KCCopyListener listener, int current, int total, byte[] aBytes) {
        if (listener != null) {
            boolean shouldContinue = listener.onBytesCopied(current, total, aBytes);
            if (!shouldContinue) {
                if (100 * current / total < CONTINUE_LOADING_PERCENTAGE) {
                    return true; // if loaded more than 75% then continue loading anyway
                }
            }
        }
        return false;
    }

    /**
     * Reads all data from stream and close it silently
     *
     * @param is Input stream
     */
    public static void readAndCloseStream(InputStream is) {
        final byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
        try {
            while (is.read(bytes, 0, DEFAULT_BUFFER_SIZE) != -1) {
            }
        } catch (IOException e) {
            // Do nothing
        } finally {
            closeSilently(is);
        }
    }

    public static void closeSilently(Closeable aCloseable) {
        try {
            if (aCloseable != null)
                aCloseable.close();
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Listener and controller for copy process
     */
    public static interface KCCopyListener {
        /**
         * @param aCurrent Loaded bytes
         * @param aTotal   Total bytes for loading
         * @param aBytes   bytes
         * @return <b>true</b> - if copying should be continued; <b>false</b> - if copying should be interrupted
         */
        boolean onBytesCopied(int aCurrent, int aTotal, byte[] aBytes);
    }

    public static Bitmap InputStream2Bitmap(InputStream is) {
        return BitmapFactory.decodeStream(is);
    }

    public static InputStream Bitmap2InputStream(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        return is;
    }

    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            copyStream(input, output, null);
            return output.toByteArray();
        } finally {
            output.close();
        }

    }

    public static byte[] inputStreamToBytes(KCByteArrayPool aPool, InputStream aInputStream, int aContentLength) throws IOException {
        return inputStreamToBytes(aPool, aInputStream, aContentLength, null);
    }

    public static byte[] inputStreamToBytes(KCByteArrayPool aPool, InputStream aInputStream, int aContentLength, KCCopyListener aCopyListener) throws IOException {
        KCPoolingByteArrayOutputStream bytes = new KCPoolingByteArrayOutputStream(aPool, aContentLength);
        try {
            copyStream(aPool, aInputStream, bytes, aCopyListener, 4096);
            return bytes.toByteArray();
        } finally {
            try {
                // Close the InputStream and release the resources.
                aInputStream.close();
            } catch (IOException e) {
                // This can happen if there was an exception above that left the entity in
                // an invalid state.
                KCLog.v("Error occured when calling inputStreamToBytes");
            }
            bytes.close();
        }
    }
}
