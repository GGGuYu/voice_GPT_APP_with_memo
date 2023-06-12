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
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.core.content.ContextCompat;

// 本样例展示在线实时语音识别使用方法
// Android SDK 详细说明：https://help.aliyun.com/document_detail/174190.html
public class SpeechTranscriberActivity extends Activity implements INativeNuiCallback{
    private static final String TAG = "SpeechTranscriberActivity";

    NativeNui nui_instance = new NativeNui();
    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 *16000/1000; //20ms audio for 16k/16bit/mono
    public final static int SAMPLE_RATE = 16000;
    private AudioRecord mAudioRecorder ;

    private Button startButton;
    private Button cancelButton;

    private AtomicBoolean vadMode = new AtomicBoolean(false);

    private Switch mVadSwitch;

    private TextView asrView;
    private TextView kwsView;

    private HandlerThread mHanderThread;

    private boolean mInit = false;
    private Handler mHandler;
    private MediaPlayer mp;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
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
        ToastText("内部SDK版本号:" + version);

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
        nui_instance.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void initUIWidgets() {
        asrView = (TextView)findViewById(R.id.textView);
        kwsView = (TextView)findViewById(R.id.kws_text);

        startButton = (Button)findViewById(R.id.button_start);
        cancelButton = (Button)findViewById(R.id.button_cancel);

        mVadSwitch = (Switch)findViewById(R.id.vad_switch);

        mVadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Log.i(TAG, "vad mode onCheckedChanged b=" + b);
                vadMode.set(b);
            }
        });


        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "start!!!");

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(asrView, "");
                showText(kwsView, "");
                startDialog();
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
                        long ret = nui_instance.stopDialog();
                        Log.i(TAG, "cancel dialog " + ret + " end");
                    }
                });

            }
        });
    }

    private void doInit() {
        showText(asrView, "");
        showText(kwsView, "");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        //获取工作路径
        String assets_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + assets_path);


        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
        //录音麦克风如何选择, 可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, WAVE_FRAM_SIZE * 4);

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        int ret = nui_instance.initialize(this, genInitParams(assets_path, debug_path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
        }

        //设置相关识别参数，具体参考API文档
        nui_instance.setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();
            nls_config.put("enable_intermediate_result", true);
//            参数可根据实际业务进行配置
//            接口说明可见https://help.aliyun.com/document_detail/173528.html
//            查看 2.开始识别
//            nls_config.put("enable_punctuation_prediction", true);
//            nls_config.put("enable_inverse_text_normalization", true);
//            nls_config.put("max_sentence_silence", 800);
//            nls_config.put("enable_words", false);
//            nls_config.put("sample_rate", 16000);
//            nls_config.put("sr_format", "opus");
            JSONObject tmp = new JSONObject();
            tmp.put("nls_config", nls_config);
            tmp.put("service_type", Constants.kServiceTypeSpeechTranscriber); // 必填
//            如果有HttpDns则可进行设置
//            tmp.put("direct_ip", Utils.getDirectIp());
            params = tmp.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void startDialog() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T,
                        genDialogParams());
                Log.i(TAG, "start done with " + ret);
            }
        });
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

            //Auth实现访问阿里云鉴权SDK动态获取token
            //JSONObject object = Auth.getAliYunTicket();
            JSONObject object = new JSONObject();

            //token 24小时过期，因此需要通过阿里云SDK来进行更新
            //接口说明可见: https://help.aliyun.com/document_detail/173528.html
            //查看 1. 鉴权和初始化
            object.put("app_key", ""); // 必填
            object.put("token", ""); // 必填
            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1");
            object.put("device_id", Utils.getDeviceId()); // 必填
            object.put("workspace", workpath); // 必填
            object.put("debug_path", debugpath);

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            // 这里只能选择FullMix和FullCloud
            object.put("service_mode", Constants.ModeFullCloud); // 必填

            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }

    private String genDialogParams() {
        String params = "";
        try {
            JSONObject dialog_param = new JSONObject();
            //运行过程中可以在startDialog时更新参数，尤其是更新过期token
//            dialog_param.put("app_key", "");
//            dialog_param.put("token", "");
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "dialog params: " + params);
        return params;
    }

    private boolean checkNotInitToast() {
        if (!mInit) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SpeechTranscriberActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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
                    if (who == kwsView) {
                        who.setText("激活词");
                    } else {
                        who.setText("识别文本");
                    }
                } else {
                    who.setText(text);
                }
            }
        });
    }

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SpeechTranscriberActivity.this, str, Toast.LENGTH_LONG).show();
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
    public void onNuiEventCallback(Constants.NuiEvent event, final int resultCode, final int arg2, KwsResult kwsResult,
                                   AsrResult asrResult) {
        Log.i(TAG, "event=" + event);
        if (event == Constants.NuiEvent.EVENT_TRANSCRIBER_COMPLETE) {
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT || event == Constants.NuiEvent.EVENT_SENTENCE_END) {
            showText(asrView, asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SpeechTranscriberActivity.this, "ERROR with " + resultCode,
                            Toast.LENGTH_LONG).show();
                }
            });

            showText(kwsView, "");
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        }
    }

    //当调用NativeNui的start后，会一定时间反复回调该接口，底层会提供buffer并告知这次需要数据的长度
    //返回值告知底层读了多少数据，应该尽量保证return的长度等于需要的长度，如果返回<=0，则表示出错
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

    //当录音状态发送变化的时候调用
    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
        Log.i(TAG, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
            Log.i(TAG, "audio recorder start");
            mAudioRecorder.startRecording();
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            mAudioRecorder.release();
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            mAudioRecorder.stop();
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {
//        Log.i(TAG, "onNuiAudioRMSChanged vol " + val);
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
        Log.i(TAG, "onNuiVprEventCallback event " + event);
    }
}



