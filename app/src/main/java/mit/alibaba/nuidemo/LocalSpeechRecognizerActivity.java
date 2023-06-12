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

// 本样例展示离线语音识别使用方法
// 此为内部代码，我们不对外直接提供具有离线语音识别能力的SDK和Demo，如有业务需求，请联系我们的产品或者商务的同事。
public class LocalSpeechRecognizerActivity extends Activity implements INativeNuiCallback,IMainRecorderCallback{
    private static final String TAG = "LocalSpeechRecognizerActivity";

    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 *16000/1000; //20ms audio for 16k/16bit/mono
    public final static int SAMPLE_RATE = 16000;
    private AudioRecord mAudioRecorder ;
    boolean manually_update_audio = true;
    private MainRecorder mRecorder = null;

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
                Toast.makeText(LocalSpeechRecognizerActivity.this, version_text, Toast.LENGTH_LONG).show();
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
        NativeNui.GetInstance().release();
        if (manually_update_audio) {
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
                        long ret = NativeNui.GetInstance().stopDialog();
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
        String asset_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + asset_path);


        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        if (manually_update_audio && mRecorder == null) {
            mRecorder = new MainRecorder();
            mRecorder.prepare(null, this, this);
        } else {
            //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
            //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
            mAudioRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    WAVE_FRAM_SIZE * 4);
        }

        //这里主动调用完成SDK配置文件的拷贝
        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.i(TAG, "copy assets failed");
            return;
        }
        //初始化SDK
        int ret = NativeNui.GetInstance().initialize(
                this, genInitParams(asset_path, debug_path),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        }

        //设置相关识别参数，具体参考API文档
        NativeNui.GetInstance().setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("service_type", Constants.kServiceTypeSpeechTranscriber);
            params = parameters.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void startDialog() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int ret = NativeNui.GetInstance().startDialog(Constants.VadMode.TYPE_P2T,
                        genDialogParams());
                Log.i(TAG, "start done with " + ret);
            }
        });
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try{
            JSONObject object = new JSONObject();
            object.put("url","dummy");

            // 如果使用本地识别，相应的信息用于鉴权
            // ak_idak_secretapp_key如何获得,请查看https://help.aliyun.com/document_detail/72138.html
            // 涉及账户安全,以下账号信息请妥善保存,不推荐明文存储。
            // 可将以下账号信息加密后存在服务器,待需要传入SDK时在解密;或在本地加密后存储(不推荐,仍然有风险)。
            object.put("app_key", "");
            object.put("ak_id", "");
            object.put("ak_secret", "");

            object.put("sdk_code", "nui_sdk_inc");
            object.put("device_id", Utils.getDeviceId());
            object.put("workspace", workpath);
            object.put("debug_path", debugpath);
            if (manually_update_audio) {
                object.put("audio_update_manually", "true");
            } else {
                object.put("audio_update_manually","false");
            }

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeAsrLocal);
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "InsideUserContext:" + str);
        return str;
    }

    private String genDialogParams() {
        String params = "";
        try {
            JSONObject dialog_param = new JSONObject();
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
                    Toast.makeText(LocalSpeechRecognizerActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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
        if (event == Constants.NuiEvent.EVENT_ASR_RESULT) {
            showText(asrView, asrResult.asrResult);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT) {
            showText(asrView, asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(LocalSpeechRecognizerActivity.this, "ERROR with " + resultCode,
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
            if (manually_update_audio) {
                mRecorder.resume();
            } else {
                mAudioRecorder.startRecording();
            }
            Log.i(TAG, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
            Log.i(TAG, "audio recorder close");
            if (manually_update_audio) {
                mRecorder.pause();
            } else {
                mAudioRecorder.release();
            }
        } else if (state == Constants.AudioState.STATE_PAUSE) {
            Log.i(TAG, "audio recorder pause");
            if (manually_update_audio) {
                mRecorder.pause();
            } else {
                mAudioRecorder.stop();
            }
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {
        Log.i(TAG, "onNuiAudioRMSChanged vol " + val);
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
        Log.i(TAG, "onNuiVprEventCallback event " + event);
    }

    @Override
    public int onRecorderData(byte[] data, int len, boolean first_pack) {
        return NativeNui.GetInstance().updateAudio(data, len, first_pack);
    }
}


