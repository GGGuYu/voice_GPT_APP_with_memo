package mit.alibaba.nuidemo;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MainRecorder implements Runnable{
    private static final String TAG = "MainRecorder";

    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 * 16000/1000;
    public final static int SAMPLE_RATE = 16000;
    private AudioRecord mAudioRecorder ;
    private Thread mThread;

    private ReentrantLock reentrantLock;
    private Condition condition;
    private FileOutputStream fout;
    private InputStream mDebugFinput;
    private Context context;

    private boolean running = true;
    private boolean finish = false;

    private AtomicBoolean mMonkeyMode = new AtomicBoolean(false);

    private byte[] buffer;

    private IMainRecorderCallback cb = null;

    private int audioRead(byte[] buffer, int size) {
        if (mMonkeyMode.get()) {
            int ret = -1;
//            if (mDebugFinput != null) {
//                try {
//                    ret = mDebugFinput.read(buffer, 0, size);
//                    if (ret == -1) {
//                        Log.i(TAG, "read file done reload");
//                        mDebugFinput.close();
//                        mDebugFinput = context.getResources().openRawResource(R.raw.monkey1);
//                        ret = mDebugFinput.read(buffer, 0, size);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            try {
//                Thread.sleep(80);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            return ret;
        } else {
            return mAudioRecorder.read(buffer, 0, size);
        }
    }
    @Override
    public void run() {
        while (!finish) {
            reentrantLock.lock();
            try {
                while (!running) {
                    condition.await();
                    Log.i(TAG, "recorder resume");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                reentrantLock.unlock();
            }
            if (finish) {
                break;
            }

            if (0 > audioRead(buffer, WAVE_FRAM_SIZE * 4)) {
                Log.i(TAG, "read error");
            } else {
                if (cb == null) {
                    Log.i(TAG, "no callback for recorder, exit ...");
                    throw new RuntimeException("no callback for recorder");

                }
                int ret = 0;
                cb.onRecorderData(buffer, WAVE_FRAM_SIZE * 4, false);
                if (ret != 0) {
                    Log.e(TAG, "update audio failed");
                }

                if (fout != null) {
                    try {
                        fout.write(buffer, 0, WAVE_FRAM_SIZE * 4);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                Thread.sleep(10);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "recorder thread finish");
    }

    public synchronized void prepare(String path, Context context, IMainRecorderCallback callback) {
        this.context = context;
        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
        //录音麦克风如何选择, 可查看
        //   https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                WAVE_FRAM_SIZE * 4);
        reentrantLock = new ReentrantLock();
        condition = reentrantLock.newCondition();
        buffer = new byte[WAVE_FRAM_SIZE * 4];
        cb = callback;
        if (!TextUtils.isEmpty(path)) {
            try {
                fout = new FileOutputStream(path);
                Log.i(TAG, "open debug file " + path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        mThread = new Thread(this);
        running = false;
        finish = false;
        mThread.start();
    }

    public synchronized void resume() {
        mAudioRecorder.startRecording();
        reentrantLock.lock();
        try {
            running = true;
            condition.signalAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            reentrantLock.unlock();
        }
    }

    public synchronized void pause() {
        reentrantLock.lock();
        try {
            running = false;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            reentrantLock.unlock();
        }
        mAudioRecorder.stop();
    }

    public synchronized void setDebugMode(boolean enable) {
//        if (enable) {
//            if (mDebugFinput != null) {
//                try {
//                    mDebugFinput.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            mDebugFinput = context.getResources().openRawResource(R.raw.monkey1);
//        } else {
//            if (mDebugFinput != null) {
//                try {
//                    mDebugFinput.close();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        mMonkeyMode.set(enable);
    }

    public synchronized void release() {
        if (running) {
            finish = true;
        } else {
            reentrantLock.lock();
            finish = true;
            running = true;
            try {
                condition.signalAll();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                reentrantLock.unlock();
            }
        }

        Log.i(TAG, "wait recorder exit");
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "recorder exit done");

        if (fout != null) {
            try {
                fout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mAudioRecorder.release();
    }
}
