package mit.alibaba.nuidemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeSpeechRecognizerCallback;
import com.alibaba.idst.nui.NativeNui;
import com.alibaba.idst.nui.NuiSpeechRecognizer;

import androidx.core.content.ContextCompat;

// 此为内部代码，请查看 SpeechRecognizerActivity.java
public class SpeechRecognizerNewActivity extends Activity implements INativeSpeechRecognizerCallback,
                                                                    IMainRecorderCallback{
    private static final String TAG = "SpeechRecognizerNewActivity";

    //新版本实时转写接口，独立于NativeNui
    NuiSpeechRecognizer speech_recognizer = new NuiSpeechRecognizer();

    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 *16000/1000; //20ms audio for 16k/16bit/mono
    public final static int SAMPLE_RATE = 16000;

    //Recorder
    private AudioRecord mAudioRecorder;

    //Recording Manager if push audio manually
    private MainRecorder mRecorder = null;

    private Button startButton;
    private Button cancelButton;

    private TextView asrView;

    private HandlerThread mHanderThread;

    private boolean mInit = false;
    private Handler mHandler;
    private MediaPlayer mp;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private final boolean audio_update_manually = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_demo);

        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.i(TAG, "copy assets failed");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查该权限是否已经获取
            int i = ContextCompat.checkSelfPermission(this, permissions[0]);
            // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
            if (i != PackageManager.PERMISSION_GRANTED) {
                // 如果没有授予该权限，就去提示用户请求
                this.requestPermissions(permissions, 321);
            }
            while (true) {
                i = ContextCompat.checkSelfPermission(this, permissions[0]);
                // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
                if (i == PackageManager.PERMISSION_GRANTED)
                    break;
            }
        }

        String version = NativeNui.GetInstance().GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SpeechRecognizerNewActivity.this, version_text, Toast.LENGTH_LONG).show();
            }
        });

        initUIWidgets();

        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());
    }
    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
        doInit ();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
        speech_recognizer.release();
        if (audio_update_manually) {
            mRecorder.release();
            mRecorder = null;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void initUIWidgets() {
        asrView = (TextView)findViewById(R.id.textView);

        startButton = (Button)findViewById(R.id.button_start);
        cancelButton = (Button)findViewById(R.id.button_cancel);

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(asrView, "");
                String para = "";
                speech_recognizer.start(para);
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel");
                if (!checkNotInitToast()) {
                    return;
                }

                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        long ret = speech_recognizer.stop();
                        Log.i(TAG, "cancel dialog " + ret + " end");
                    }
                });

            }
        });
    }

    private void doInit() {
        showText(asrView, "");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        //这里获得当前nuisdk.aar中assets路径
        String assets_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + assets_path);

        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
        //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
        mAudioRecorder = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                WAVE_FRAM_SIZE * 4);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        if (mRecorder == null) {
            mRecorder = new MainRecorder();
            mRecorder.prepare(null, this, this);
        }

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        int ret = speech_recognizer.initialize(this, genInitParams(assets_path,debug_path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "speech_recognizer initialize result = " + ret);
        if (ret == 0) {
            mInit = true;
        } else if (ret == -9999) {
            Log.e(TAG, "load jni library failed");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                Toast.makeText(SpeechRecognizerNewActivity.this, "this activity is unsupported.", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        //设置相关识别参数，具体参考API文档
        speech_recognizer.setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();
            nls_config.put("enable_intermediate_result", true);
//            参数可根据实际业务进行配置
//            接口说明可见: https://help.aliyun.com/document_detail/173298.html
//            nls_config.put("enable_punctuation_prediction", true);
//            nls_config.put("enable_inverse_text_normalization", true);
//            nls_config.put("max_sentence_silence", 800);
//            nls_config.put("enable_words", false);
//            nls_config.put("sample_rate", 16000);
//            nls_config.put("sr_format", "opus");
            JSONObject tmp = new JSONObject();
            tmp.put("nls_config", nls_config);
//            如果有HttpDns则可进行设置
//            tmp.put("direct_ip", Utils.getDirectIp());
            params = tmp.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try{
            //获取token方式一般有两种：

            //方法1：
            //参考Auth类的实现在端上访问阿里云Token服务获取SDK进行获取
            //JSONObject object = Auth.getAliYunTicket();

            //方法2：（推荐做法）
            //在您的服务端进行token管理，此处获取服务端的token进行语音服务访问


            //请输入您申请的id与token，否则无法使用语音服务，获取方式请参考阿里云官网文档：
            //https://help.aliyun.com/document_detail/72153.html?spm=a2c4g.11186623.6.555.59bd69bb6tkTSc
            JSONObject object = new JSONObject();
            object.put("app_key", "");
            object.put("token", "");
            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1");
            object.put("device_id", Utils.getDeviceId());
//            object.put("dic","test_dic");
//            object.put("diu", Utils.getDeviceId());
//            object.put("diu2", "test_diu2");
//            object.put("div", "test_div");
//            object.put("tid", "test_tid");
//            object.put("keep_alive", "10000");
//            object.put("dip", "16100");
            object.put("workspace", workpath);
            object.put("debug_path",debugpath);
            object.put("audio_update_manually", audio_update_manually ? "true" : "false");

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeAsrCloud);
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }


    private boolean checkNotInitToast() {
        if (!mInit) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SpeechRecognizerNewActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
                }
            });
            return false;
        } else {
            return true;
        }
    }

    private void setButtonState(final Button btn, final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "setBtn state " + btn.getText() + " state=" + state);
                btn.setEnabled(state);
            }
        });
    }

    private void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "showText text=" + text);
                if (TextUtils.isEmpty(text)) {
                    Log.w(TAG, "asr text is empty");
                    who.setText("识别文本");
                } else {
                    who.setText(text);
                }
            }
        });
    }

    private void appendText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "append text=" + text);
                if (TextUtils.isEmpty(text)) {
                    return;
                } else {
                    String orign = who.getText().toString();
                    who.setText(orign + "\n---\n" + text);
                }
            }
        });
    }

    //当回调事件发生时调用
    @Override
    public void onSpeechRecognizerEventCallback(NuiSpeechRecognizer.SpeechRecognizerEvent event, String result, final int error_code) {
        Log.i(TAG, "event=" + event);
        if (event == NuiSpeechRecognizer.SpeechRecognizerEvent.SR_EVENT_ASR_RESULT) {
            showText(asrView, result);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == NuiSpeechRecognizer.SpeechRecognizerEvent.SR_EVENT_ASR_PARTIAL_RESULT) {
            showText(asrView, result);
        } else if (event == NuiSpeechRecognizer.SpeechRecognizerEvent.SR_EVENT_ASR_ERROR
                || event == NuiSpeechRecognizer.SpeechRecognizerEvent.SR_EVENT_MIC_ERROR) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SpeechRecognizerNewActivity.this, "ERROR with " + error_code,
                            Toast.LENGTH_LONG).show();
                }
            });

            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        }
    }

    @Override
    public void onSpeechRecognizerEventTrackerCallback(String track_info) {
        ;
    }

    @Override
    public int onNuiNeedAudioData(byte[] buffer, int len) {
        int ret = 0;
        if (mAudioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "audio recorder not init");
            return -1;
        }
        ret = mAudioRecorder.read(buffer, 0, len);
        return ret;
    }

    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
        Log.i(TAG, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
            Log.i(TAG, "audio recorder start");
            if (audio_update_manually) {
                mRecorder.resume();
            } else {
                mAudioRecorder.startRecording();
            }
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            if (audio_update_manually) {
                mRecorder.pause();
            } else {
                mAudioRecorder.release();
            }
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            if (audio_update_manually) {
                mRecorder.pause();
            } else {
                mAudioRecorder.stop();
            }
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {

    }

    @Override
    public int onRecorderData(byte[] data, int len, boolean first_pack) {
        if (audio_update_manually) {
            return speech_recognizer.updateAudio(data, len);
        } else {
            return 0;
        }
    }
}



