package com.rntemplate;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadSampleListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.util.FileDownloadUtils;
import com.rntemplate.constants.FileConstant;
import com.rntemplate.hotreload.HotReload;
import com.rntemplate.hotreload.HotUpdate;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Created by heruijun on 2017/6/16.
 */

public class LauncherActivity extends AppCompatActivity {

    private int downloadId3;
    private String normalTaskFilePath;
    private ProgressBar mProgressBar;
    private TextView mSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mSpeed = (TextView) findViewById(R.id.speed_tv);
        normalTaskFilePath = FileConstant.JS_PATCH_LOCAL_PATH;
    }

    public void enter(View v) {
        Intent intent = new Intent(LauncherActivity.this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * 下载更新包
     *
     * @param v
     */
    public void load(View v) {
        checkVersion();
    }

    public void del(View v) {
        new File(normalTaskFilePath).delete();
        new File(FileDownloadUtils.getTempPath(normalTaskFilePath)).delete();
    }

    public void change(View v) {
        HotUpdate hotUpdate = new HotUpdate(FileConstant.JS_BUNDLE_LOCAL_PATH);
        hotUpdate.reloadBundle();
    }

    private void checkVersion() {
        Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show();
        downLoadBundle();
    }

    /**
     * 下载最新Bundle
     */
    private void downLoadBundle() {
        // 1.下载前检查SD卡是否存在更新包文件夹
        HotReload.checkPackage(getApplicationContext(), FileConstant.LOCAL_FOLDER);
        downloadId3 = createDownloadTask(3).start();
    }

    private BaseDownloadTask createDownloadTask(final int position) {
        final ViewHolder tag;
        final String url;
        boolean isDir = false;
        String path;

        url = FileConstant.JS_BUNDLE_REMOTE_URL;
        tag = new ViewHolder(new WeakReference<>(this), mProgressBar, null, mSpeed, 3);
        path = normalTaskFilePath;

        return FileDownloader.getImpl().create(url)
                .setPath(path, isDir)
                .setCallbackProgressTimes(300)
                .setMinIntervalUpdateSpeed(400)
                .setTag(tag)
                .setListener(new FileDownloadSampleListener() {

                    @Override
                    protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                        super.pending(task, soFarBytes, totalBytes);
                        ((ViewHolder) task.getTag()).updatePending(task);
                    }

                    @Override
                    protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                        super.progress(task, soFarBytes, totalBytes);
                        ((ViewHolder) task.getTag()).updateProgress(soFarBytes, totalBytes,
                                task.getSpeed());
                    }

                    @Override
                    protected void error(BaseDownloadTask task, Throwable e) {
                        super.error(task, e);
                        ((ViewHolder) task.getTag()).updateError(e, task.getSpeed());
                    }

                    @Override
                    protected void connected(BaseDownloadTask task, String etag, boolean isContinue, int soFarBytes, int totalBytes) {
                        super.connected(task, etag, isContinue, soFarBytes, totalBytes);
                        ((ViewHolder) task.getTag()).updateConnected(etag, task.getFilename());
                    }

                    @Override
                    protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                        super.paused(task, soFarBytes, totalBytes);
                        ((ViewHolder) task.getTag()).updatePaused(task.getSpeed());
                    }

                    @Override
                    protected void completed(BaseDownloadTask task) {
                        super.completed(task);
                        ((ViewHolder) task.getTag()).updateCompleted(task);
                        Log.e("complete", "开始解压");
                        // HotReload.handleZIP(MainApplication.CONTEXT);
                    }

                    @Override
                    protected void warn(BaseDownloadTask task) {
                        super.warn(task);
                        ((ViewHolder) task.getTag()).updateWarn();
                    }
                });
    }


    private static class ViewHolder {
        private ProgressBar pb;
        private TextView detailTv;
        private TextView speedTv;
        private int position;
        private TextView filenameTv;

        private WeakReference<LauncherActivity> weakReferenceContext;

        public ViewHolder(WeakReference<LauncherActivity> weakReferenceContext,
                          final ProgressBar pb, final TextView detailTv, final TextView speedTv,
                          final int position) {
            this.weakReferenceContext = weakReferenceContext;
            this.pb = pb;
            this.detailTv = detailTv;
            this.position = position;
            this.speedTv = speedTv;
        }

        public void setFilenameTv(TextView filenameTv) {
            this.filenameTv = filenameTv;
        }

        private void updateSpeed(int speed) {
            speedTv.setText(String.format("%dKB/s", speed));
        }

        public void updateProgress(final int sofar, final int total, final int speed) {
            if (total == -1) {
                // chunked transfer encoding data
                pb.setIndeterminate(true);
            } else {
                pb.setMax(total);
                pb.setProgress(sofar);
            }

            updateSpeed(speed);

            if (detailTv != null) {
                detailTv.setText(String.format("sofar: %d total: %d", sofar, total));
            }
        }

        public void updatePending(BaseDownloadTask task) {
            if (filenameTv != null) {
                filenameTv.setText(task.getFilename());
            }
        }

        public void updatePaused(final int speed) {
            toast(String.format("paused %d", position));
            updateSpeed(speed);
            pb.setIndeterminate(false);
        }

        public void updateConnected(String etag, String filename) {
            if (filenameTv != null) {
                filenameTv.setText(filename);
            }
        }

        public void updateWarn() {
            toast(String.format("warn %d", position));
            pb.setIndeterminate(false);
        }

        public void updateError(final Throwable ex, final int speed) {
            toast(String.format("error %d %s", position, ex));
            updateSpeed(speed);
            pb.setIndeterminate(false);
            ex.printStackTrace();
        }

        public void updateCompleted(final BaseDownloadTask task) {

            toast(String.format("completed %d %s", position, task.getTargetFilePath()));

            if (detailTv != null) {
                detailTv.setText(String.format("sofar: %d total: %d",
                        task.getSmallFileSoFarBytes(), task.getSmallFileTotalBytes()));
            }

            updateSpeed(task.getSpeed());
            pb.setIndeterminate(false);
            pb.setMax(task.getSmallFileTotalBytes());
            pb.setProgress(task.getSmallFileSoFarBytes());
        }

        private void toast(final String msg) {
            if (this.weakReferenceContext != null && this.weakReferenceContext.get() != null) {
                Toast.makeText(this.weakReferenceContext.get(), msg, Toast.LENGTH_SHORT).show();
            }
        }

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                System.exit(0);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

}
