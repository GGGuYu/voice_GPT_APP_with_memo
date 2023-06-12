/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package mit.alibaba.nuidemo;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;

import android.util.Log;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.NativeNui;

// 本样例展示在线语音合成使用方法
// Android SDK 详细说明：https://help.aliyun.com/document_detail/174481.html
public class TtsBasicActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "TtsBasicActivity";

    NativeNui nui_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    final static String CN_PREVIEW ="语音合成服务，通过先进的深度学习技术，将文本转换成自然流畅的语音。目前有多种音色可供选择，并提供调节语速、语调、音量等功能。适用于智能客服、语音交互、文学有声阅读和无障碍播报等场景。";
    // 以下为552字符长度的示例，用于测试长文本语音合成
    //final static String CN_PREVIEW ="近年来，随着端到端语音识别的流行，基于Transformer结构的语音识别系统逐渐成为了主流。然而，由于Transformer是一种自回归模型，需要逐个生成目标文字，计算复杂度随着目标文字数量线性增加，限制了其在工业生产中的应用。针对Transoformer模型自回归生成文字的低计算效率缺陷，学术界提出了非自回归模型来并行的输出目标文字。根据生成目标文字时，迭代轮数，非自回归模型分为：多轮迭代式与单轮迭代非自回归模型。其中实用的是基于单轮迭代的非自回归模型。对于单轮非自回归模型，现有工作往往聚焦于如何更加准确的预测目标文字个数，如CTC-enhanced采用CTC预测输出文字个数，尽管如此，考虑到现实应用中，语速、口音、静音以及噪声等因素的影响，如何准确的预测目标文字个数以及抽取目标文字对应的声学隐变量仍然是一个比较大的挑战；另外一方面，我们通过对比自回归模型与单轮非自回归模型在工业大数据上的错误类型（如下图所示，AR与vanilla NAR），发现，相比于自回归模型，非自回归模型，在预测目标文字个数方面差距较小，但是替换错误显著的增加，我们认为这是由于单轮非自回归模型中条件独立假设导致的语义信息丢失。于此同时，目前非自回归模型主要停留在学术验证阶段，还没有工业大数据上的相关实验与结论。";
    final String Tag = this.getClass().getName();
    int i=1;
    private Button ttsStartBtn, ttsQuitBtn, ttsCancelBtn, ttsPauseBtn, ttsResumeBtn, ttsClearTextBtn;
    private EditText ttsEditView;
    String asset_path;
    Toast mToast;
    private OutputStream output_file = null;
    private boolean b_savewav = false;
    //  AudioPlayer默认采样率是16000
    private AudioPlayer mAudioTrack =  new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "start play");
        }
        @Override
        public void playOver() {
            Log.i(TAG, "play over");
        }
    });
    boolean initialized =  false;
    private String ttsText = new String();
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_basic);
        ttsEditView = (EditText) findViewById(R.id.tts_content);
        ttsStartBtn = (Button)findViewById(R.id.tts_start_btn);
        ttsCancelBtn = (Button)findViewById(R.id.tts_cancel_btn);
        ttsPauseBtn = (Button)findViewById(R.id.tts_pause_btn);
        ttsResumeBtn = (Button)findViewById(R.id.tts_resume_btn);
        ttsQuitBtn = (Button)findViewById(R.id.tts_quit_btn);
        ttsClearTextBtn = (Button)findViewById(R.id.tts_clear_btn);

        ttsEditView.setText(CN_PREVIEW);
        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsText = ttsEditView.getText().toString();
                if (TextUtils.isEmpty(ttsText)) {
                    Log.e(TAG, "tts empty");
                    return;
                }
                if (!initialized) {
                    Log.i(TAG, "init tts");
                    Initialize(asset_path);
                }

                Log.i(TAG, "start play tts");
                // 支持一次性合成300字符以内的文字，其中1个汉字、1个英文字母或1个标点均算作1个字符，
                // 超过300个字符的内容将会截断。所以请确保传入的text小于300字符(不包含ssml格式)。
                // 长短文本语音合成收费不同，须另外开通长文本语音服务，请注意。
                // 不需要长文本语音合成功能则无需考虑以下操作。
                int charNum = nui_tts_instance.getUtf8CharsNum(ttsText);
                Log.i(TAG, "chars:" + charNum + " of text:" + ttsText);
                if (charNum > 300) {
                    Log.w(TAG, "text exceed 300 chars.");
                    // 超过300字符设置成 长文本语音合成 模式
                    nui_tts_instance.setparamTts("tts_version", "1");
                } else {
                    // 未超过300字符设置成 短文本语音合成 模式
                    nui_tts_instance.setparamTts("tts_version", "0");
                }

                // 每个instance一个task，若想同时处理多个task，请启动多instance
                nui_tts_instance.startTts("1", "", ttsText);
            }
        });
        ttsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts release");
                mAudioTrack.stop();
                nui_tts_instance.tts_release();
                initialized = false;
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "cancel tts");
                nui_tts_instance.cancelTts("");
                mAudioTrack.stop();
            }
        });

        ttsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "pause tts");
                nui_tts_instance.pauseTts();
                mAudioTrack.pause();
            }
        });
        ttsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "resume tts");
                nui_tts_instance.resumeTts();
                mAudioTrack.play();
            }
        });
        ttsClearTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsEditView.setText("");
            }
        });

        // 这里获得当前nuisdk.aar中assets路径
        String path = CommonUtils.getModelPath(this);
        Log.i(TAG, "workpath = " + path);
        asset_path = path;

        //这里主动调用完成SDK配置文件的拷贝
        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.i(TAG, "copy assets failed");
            return;
        }

        String version = nui_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        ToastText("内部SDK版本号: " + version);

        if (Constants.NuiResultCode.SUCCESS == Initialize(path)) {
            initialized = true;
        } else {
            Log.e(TAG, "init failed");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        nui_tts_instance.tts_release();
        initialized = false;
    }

    @Override
    public void onClick(View v) {
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }
    private int Initialize(String path) {
        int ret = nui_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "tts event:" + event + " task id " + task_id + " ret " + ret_code);
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    mAudioTrack.play();
                    Log.i(TAG, "start play");
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "play end");

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    // 调试使用, 若希望存下音频文件, 如下
                    if (b_savewav) {
                        try {
                            output_file.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (event == TtsEvent.TTS_EVENT_PAUSE) {
                    mAudioTrack.pause();
                    Log.i(TAG, "play pause");
                } else if (event == TtsEvent.TTS_EVENT_RESUME) {
                    mAudioTrack.play();
                } else if (event == TtsEvent.TTS_EVENT_ERROR) {
                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    String error_msg =  nui_tts_instance.getparamTts("error_msg");
                    Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                    ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                    ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                }
            }
            @Override
            public void onTtsDataCallback(String info, int info_len, byte[] data) {
                if (info.length() > 0) {
                     Log.i(TAG, "info: " + info);
                }
                if (data.length > 0) {
                    mAudioTrack.setAudioData(data);
                    Log.i(TAG, "write:" + data.length);
                    if (b_savewav) {
                        try {
                            output_file.write(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            @Override
            public void onTtsVolCallback(int vol) {
                Log.i(TAG, "tts vol " + vol);
            }
        }, genTicket(path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
            Log.i(TAG, "create failed");
        }

        // 在线语音合成发音人可以参考阿里云官网
        // https://help.aliyun.com/document_detail/84435.html
        nui_tts_instance.setparamTts("font_name", "zhida");

        // 详细参数可见: https://help.aliyun.com/document_detail/173642.html
        nui_tts_instance.setparamTts("sample_rate", "16000");
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(16000);

        nui_tts_instance.setparamTts("enable_subtitle", "1");
        // 调整语速
        nui_tts_instance.setparamTts("speed_level", "1");
        // 调整音调
        nui_tts_instance.setparamTts("pitch_level", "-2");
        // 调整音量
        nui_tts_instance.setparamTts("volume", "10");

        if (b_savewav) {
            try {
                output_file = new FileOutputStream("/sdcard/mit/tmp/test.pcm");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }
    private String genTicket(String workpath) {
        String str = "";
        try {
            //获取token方式一般有两种：

            //方法1：
            //参考Auth类的实现在端上访问阿里云Token服务获取SDK进行获取
            //JSONObject object = Auth.getAliYunTicket();

            //方法2：（推荐做法）
            //在您的服务端进行token管理，此处获取服务端的token进行语音服务访问


            //请输入您申请的id与token，否则无法使用语音服务，获取方式请参考阿里云官网文档：
            //https://help.aliyun.com/document_detail/72153.html?spm=a2c4g.11186623.6.555.59bd69bb6tkTSc
            JSONObject object = new JSONObject();
            object.put("app_key", "cSXMJ4Q6bHWoZBw5"); // 必填
            object.put("token", "0c3c1e9e003d4582b608d9b80d786f63"); // 必填

            object.put("device_id", Utils.getDeviceId()); // 必填, 可填入用户账户相关id
            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"); // 默认
            object.put("workspace", workpath); // 必填, 且需要有读写权限

            // 设置为在线合成
            //  Local = 0,
            //  Mix = 1,  // init local and cloud
            //  Cloud = 2,
            object.put("mode_type", "2");
            str = object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "UserContext:" + str);
        return str;
    }

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TtsBasicActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }
}
