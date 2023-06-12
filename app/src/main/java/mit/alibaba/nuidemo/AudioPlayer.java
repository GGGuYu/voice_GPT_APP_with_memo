package mit.alibaba.nuidemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer {

    public enum PlayState {
        idle,
        playing,
        pause
    }

    private static String TAG = "AudioPlayer";

    private int SAMPLE_RATE = 16000;
    private boolean isFinishSend = false;
    private AudioPlayerCallback audioPlayerCallback;
    private LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue();
    private PlayState playState;
    private byte[] tempData;
    private Thread ttsPlayerThread;

    // 初始化播放器
    // 此处仅使用Android系统自带的AudioTrack进行音频播放Demo演示, 客户可根据自己需要替换播放器
    // 默认采样率为16000、单通道、16bit pcm格式
    private int iMinBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT) * 2;
    private AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            iMinBufSize, AudioTrack.MODE_STREAM);


    AudioPlayer(AudioPlayerCallback callback) {
        Log.i(TAG,"Audio Player init!");
        playState = PlayState.idle;
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack is uninited!! new again...");
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    iMinBufSize, AudioTrack.MODE_STREAM);
        }
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack new failed ...");
        }
        audioTrack.play();
        audioPlayerCallback = callback;

        ttsPlayerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (playState == PlayState.playing) {
                        if (audioQueue.size() == 0) {
                            if (isFinishSend) {
                                audioPlayerCallback.playOver();
                                isFinishSend = false;
                            } else {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            continue;
                        }
                        try {
                            tempData = audioQueue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        audioTrack.write(tempData, 0, tempData.length);
                    } else {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        ttsPlayerThread.start();
    }

    public void setAudioData(byte[] data) {
        audioQueue.offer(data);
        //非阻塞
    }

    public void isFinishSend(boolean isFinish) {
        isFinishSend = isFinish;
        Log.i(TAG,"TtsLocalActivity isFinishSend:" + isFinishSend);
    }

    public void play() {
        playState = PlayState.playing;
        Log.i(TAG,"TtsLocalActivity playState:" + playState);
        isFinishSend = false;
        if (audioTrack != null) {
            audioTrack.play();
        }
        audioPlayerCallback.playStart();
    }

    public void stop() {
        playState = PlayState.idle;
        Log.i(TAG,"stop-playState :" + playState);
        audioQueue.clear();
        if (audioTrack != null) {
            audioTrack.flush();
            audioTrack.pause();
            audioTrack.stop();
        }
    }

    public void pause() {
        playState = PlayState.pause;
        if (audioTrack != null) {
            audioTrack.pause();
        }
    }

    public void resume() {
        if (audioTrack != null) {
            audioTrack.play();
        }
        playState = PlayState.playing;
    }

    public void initAudioTrack(int samplerate) {
        // 初始化播放器
        // 此处仅使用Android系统自带的AudioTrack进行音频播放Demo演示, 客户可根据自己需要替换播放器
        // 默认采样率为16000、单通道、16bit pcm格式
        int iMinBufSize = AudioTrack.getMinBufferSize(samplerate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT) * 2;
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, samplerate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                iMinBufSize, AudioTrack.MODE_STREAM);
    }

    public void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            Log.i(TAG,"releaseAudioTrack audioTrack released");
        }
        audioTrack = null;
    }

    public void setSampleRate(int sampleRate) {
        if (SAMPLE_RATE != sampleRate) {
            releaseAudioTrack();
            initAudioTrack(sampleRate);
            SAMPLE_RATE = sampleRate;
        }
    }
}
