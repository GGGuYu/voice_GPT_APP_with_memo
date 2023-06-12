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

import com.alibaba.fastjson.JSONArray;
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

// 本样例展示本地唤醒及在线一句话语音识别使用方法
// 语音唤醒定制化程度比较高，需要根据使用设备/应用场景先确定唤醒词，再采集数据进行模型训练。初期可以提供demo集成和体验，但性能无法保证。
// 我们不对外直接提供具有唤醒能力的SDK和Demo，如有业务需求，请联系我们的产品或者商务的同事。
public class WakeupAndSpeechRecognizerActivity extends Activity implements INativeNuiCallback{
    private static final String TAG = "WakeupAndSpeechRecognizerActivity";

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
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE};
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
            // 检查该权限是否已经获取
            i = ContextCompat.checkSelfPermission(this, permissions[1]);
            // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
            if (i != PackageManager.PERMISSION_GRANTED) {
                // 如果没有授予该权限，就去提示用户请求
                this.requestPermissions(permissions, 321);
            }
            while (true) {
                i = ContextCompat.checkSelfPermission(this, permissions[1]);
                // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
                if (i == PackageManager.PERMISSION_GRANTED)
                    break;
            }
        }

        String version = NativeNui.GetInstance().GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        final String version_text = "内部SDK版本号: " + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WakeupAndSpeechRecognizerActivity.this, version_text, Toast.LENGTH_LONG).show();
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
        doInit();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
        NativeNui.GetInstance().release();
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

        //主动调用将SDK中的相关配置加载到运行目录
        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.e(TAG, "copy assets failed");
            return;
        }

        //如果需要使用外置的唤醒模型，把文件放在assets目录，通过以下接口设置
//        CommonUtils.setExternalAssetFile(this, "pack_kws.bin");

        //这里获得当前nuisdk.aar中assets路径
        String asset_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + asset_path);

        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
        //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
        mAudioRecorder = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                WAVE_FRAM_SIZE * 4);

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        //由于唤醒功能为本地功能, 涉及鉴权, 故genInitParams中需要填写ak_id、ak_secret
        int ret = NativeNui.GetInstance().initialize(
                this, genInitParams(asset_path, debug_path),
                Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndSpeechRecognizerActivity.this, msg_text, Toast.LENGTH_LONG).show();
                }
            });
        }

        //设置相关识别参数，具体参考API文档
        NativeNui.GetInstance().setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();
            nls_config.put("enable_intermediate_result", true);
            //参数可根据实际业务进行配置
//            nls_config.put("enable_punctuation_prediction", true);
//            nls_config.put("enable_inverse_text_normalization", true);
//            nls_config.put("enable_voice_detection", true);
//            nls_config.put("customization_id", "test_id");
//            nls_config.put("vocabulary_id", "test_id");
//            nls_config.put("max_start_silence", 10000);
//            nls_config.put("max_end_silence", 800);
//            nls_config.put("sample_rate", 16000);
//            nls_config.put("sr_format", "opus");
            JSONObject parameters = new JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR);

            //可以通过以下方式设置自定义唤醒词进行体验，如果需要更好的唤醒效果请进行唤醒词定制
            //注意：动态唤醒词只有在设置了唤醒模型的前提下才可以使用
            JSONArray dynamic_wuw = new JSONArray();
            JSONObject wuw = new JSONObject();
            wuw.put("name", "小白小白");
            wuw.put("type", "main");
            dynamic_wuw.add(wuw);
            parameters.put("wuw", dynamic_wuw);

            //如果有HttpDns则可进行设置
//            parameters.put("direct_ip", Utils.getDirectIp());
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
                //唤醒后进行识别模式
                int ret = NativeNui.GetInstance().startDialog(Constants.VadMode.TYPE_KWS,
                        genDialogParams());
                Log.i(TAG, "start done with " + ret);
                if (ret != 0) {
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WakeupAndSpeechRecognizerActivity.this, msg_text, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private String genInitParams(String workpath, String debugpath) {
        String str = "";
        try{
//            JSONObject object = Auth.getTicket();
            JSONObject object = new JSONObject();

            //请使用您的阿里云账号与appkey进行访问, 以下介绍两种方案
            //方案一(有安全风险,不推荐):
            //  首先ak_id ak_secret app_key如何获得,请查看https://help.aliyun.com/document_detail/72138.html
            //  涉及账户安全,以下账号信息请妥善保存,不可明文存储。
            //  可将以下账号信息加密后存在服务器,待需要传入SDK时在解密;或在本地加密后存储(不推荐,仍然有风险)。
//            object.put("ak_id","xxxxxxx"); // 必填
//            object.put("ak_secret","xxxxxxx"); // 必填
//            object.put("app_key","xxxxxxx"); // 必填
            //方案二(推荐):
            //  首先ak_id ak_secret app_key如何获得,请查看https://help.aliyun.com/document_detail/72138.html
            //  然后请看https://help.aliyun.com/document_detail/466615.html
            //  使用其中方案二使用STS获取临时账号
            object.put("ak_id","STS.xxxxxxx"); // 必填
            object.put("ak_secret","xxxxxxx"); // 必填
            object.put("app_key","xxxxxxx"); // 必填
            object.put("sts_token","xxxxxxx"); // 必填

            // 特别说明: 鉴权所用的id是由以下device_id，与手机内部的一些唯一码进行组合加密生成的。
            //   更换手机或者更换device_id都会导致重新鉴权计费。
            //   此外, 以下device_id请设置有意义且具有唯一性的id, 比如用户账号(手机号、IMEI等),
            //   传入相同或随机变换的device_id会导致鉴权失败或重复收费。
            object.put("device_id", Utils.getDeviceId()); // 必填

            // 离线语音合成sdk_code取值：精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
            // 唤醒sdk_code取值：nls_asrttssdk_public_cn
            // 离线语音合成账户和sdk_code也可用于唤醒
            object.put("sdk_code", "nls_asrttssdk_public_cn"); // 必填

            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1");
            object.put("workspace", workpath); // 必填
            object.put("debug_path", debugpath);

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeFullMix);

            //如果使用外置唤醒资源，可以进行设置文件路径。通过upgrade_file参数传入唤醒模型文件的绝对路径。

            // 举例1：模型文件kws.bin可以放在assets，这里需要主动拷贝到App的data目录，并获得绝对路径。
            String kws_bin_name = "kws.bin";
            String kws_bin_dest_name = CommonUtils.getModelPath(this) + "/" + kws_bin_name;
            CommonUtils.copyAsset(this, kws_bin_name, kws_bin_dest_name);

            // 举例2：模型文件kws.bin放在/sdcard/目录，请确认读写权限
//            kws_bin_name = "kws.bin";
//            String kws_bin_dest_name = "/sdcard/" + kws_bin_name;

            object.put("upgrade_file", kws_bin_dest_name);
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
                    Toast.makeText(WakeupAndSpeechRecognizerActivity.this, "SDK未正确初始化.", Toast.LENGTH_LONG).show();
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
        if (event == Constants.NuiEvent.EVENT_WUW_TRUSTED) {
            showText(asrView, "");
            showText(kwsView, kwsResult.kws);
        } else if (event == Constants.NuiEvent.EVENT_ASR_RESULT) {
            showText(asrView, asrResult.asrResult);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT) {
            showText(asrView, asrResult.asrResult);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WakeupAndSpeechRecognizerActivity.this, "ERROR with " + resultCode,
                            Toast.LENGTH_SHORT).show();
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


