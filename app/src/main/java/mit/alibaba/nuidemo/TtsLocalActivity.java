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
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import java.io.FileInputStream;
import java.io.*;
import android.util.Log;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.NativeNui;
import java.util.List;
import java.util.ArrayList;
import android.widget.ArrayAdapter;

import androidx.core.content.ContextCompat;

import java.util.Map;
import java.util.HashMap;

// 本样例展示离线语音合成使用方法
// Android SDK 详细说明：https://help.aliyun.com/document_detail/204187.html
// Android SDK 接口说明：https://help.aliyun.com/document_detail/204185.html
public class TtsLocalActivity extends Activity implements View.OnClickListener, OnItemSelectedListener {
    private static final String TAG = "TtsLocalActivity";

    // 本地合成目前仅支持单路合成
    NativeNui nui_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    String mFontPath = "/sdcard/idst/res/";  // 语音包保存路径，任意配置
    String mDebugPath = "/sdcard/idst/debug/";  // demo音频文件存储路径，任意配置
    final String CN_PREVIEW = "本样例展示离线语音合成使用方法，1）设置鉴权信息：按照鉴权认证文档获取注册信息，并调用接口tts_initialize进行设置；2）配置语音包：将购买语音包推到目录" + mFontPath + "下，要确保SDK有读的权限；3）开始合成" ;
    String mAssetPath;   // SDK工作路径，该目录下含有配置文件及前端资源

    private OutputStream mOutputFile = null;
    private boolean mSaveWav = false;
    private Map<String, List<String>> paramMap =  new HashMap<>();
    private Button ttsStartBtn, ttsQuitBtn, ttsCancelBtn, ttsPauseBtn, ttsResumeBtn, ttsClearTextBtn;
    private Button ttsBatchProcess; // 提供批量合成示例
    private Spinner mPitchSpin, mFontSpin, mSpeedSpin, mVolumeSpin;
    private EditText mEditView;
    boolean mInitialized = false;
    private String mFontName = "aijia";
    private String mText = new String();
    private boolean mPlaying = false;  // 用于管控AudioPlaying的播放状态
    private String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
    // 播报模块示例：展示合成音频播放
    // AudioPlayer默认采样率是16000
    private AudioPlayer mAudioTrack = new AudioPlayer(new AudioPlayerCallback() {
        @Override
        public void playStart() {
            Log.i(TAG, "playStart");
        }
        @Override
        public void playOver() {
            Log.i(TAG, "playOver");
            mPlaying = false;
        }
    });

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_local);
        setView();
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

        // 检查该权限是否已经获取
        int i = ContextCompat.checkSelfPermission(this, permissions[1]);
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

        mEditView.setText(CN_PREVIEW);
        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mText = mEditView.getText().toString();
                if (TextUtils.isEmpty(mText)) {
                    Log.e(TAG, "text is empty");
                    ToastText("文本为空！");
                    return;
                }
                if (!mInitialized) {
                    Log.i(TAG, "tts-init");
                    Initialize(mAssetPath);
                }
                Log.i(TAG, "start tts-play");
                // 该接口是异步的
                // taskid（32位uui）可以自己设置；为空时，SDK内部会会自动产生
                // 每个instance一个task，若想同时处理多个task，请启动多instance
                int ret = nui_tts_instance.startTts("1", "", mText);
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    String errmsg =  nui_tts_instance.getparamTts("error_msg");
                    Log.e(TAG, "startTts failed. errmsg:" + errmsg);
                    ToastText(Utils.getMsgWithErrorCode(ret, "start"));
                } else {
                    Log.i(TAG, "start tts-play done");
                }
            }
        });
        ttsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-release");
                mAudioTrack.stop();
                int ret = nui_tts_instance.tts_release();
                if (ret != 0) {
                    Log.e(TAG, "tts_release failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "release"));
                }
                mInitialized = false;
                Log.i(TAG, "tts-release done");
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-cancel");
                int ret = nui_tts_instance.cancelTts("");
                if (ret != 0) {
                    Log.e(TAG, "cancelTts failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "cancel"));
                }
                Log.i(TAG, "tts-cancel done");
                mAudioTrack.stop();
            }
        });

        ttsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-pause");
                int ret = nui_tts_instance.pauseTts();
                if (ret != 0) {
                    Log.e(TAG, "pauseTts failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "pause"));
                }
                mAudioTrack.pause();
            }
        });
        ttsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "tts-resume");
                int ret = nui_tts_instance.resumeTts();
                if (ret != 0) {
                    Log.e(TAG, "resumeTts failed, error:" + ret);
                    ToastText(Utils.getMsgWithErrorCode(ret, "resume"));
                }
                mAudioTrack.play();
            }
        });
        ttsClearTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditView.setText("");
            }
        });
        ttsBatchProcess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        BatchProcess();
                    }
                });
                t.start();
            }
        });

        //设置默认目的地路径最下级目录名字
        // 比如设置当前 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        // 未调用此接口, 则默认为 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        CommonUtils.setTargetDataDir("asr_my");

        //这里获得当前nuisdk.aar中assets可以搬运的默认目的地路径,
        // 比如 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        String path = CommonUtils.getModelPath(this);
        Log.i(TAG, "workpath:" + path);
        mAssetPath = path;

        //这里主动调用完成SDK配置文件的拷贝拷贝目的地为CommonUtils.getModelPath(this);
        // 比如当前为 /data/user/0/mit.alibaba.nuidemo/files/asr_my
        if (CommonUtils.copyTtsAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.e(TAG, "copy assets failed");
            ToastText("从aar的assets拷贝资源文件失败, 请检查资源文件是否存在, 详情可通过日志确认。");
            return;
        }
        // 注意: 鉴权文件为 /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/ttset.bin
        // 切勿覆盖和删除asr_my内文件

        final String android_id = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i(TAG, "android_id:" + android_id);
        final String serialNum = Utils.getSerialNumber();
        Log.i(TAG, "serialNum:" + serialNum);

        String version = nui_tts_instance.GetVersion();
        Log.i(TAG, "current sdk version: " + version);
        ToastText("内部SDK版本号: " + version);

        int ret = Initialize(path);
        if (Constants.NuiResultCode.SUCCESS != ret) {
            String errmsg =  nui_tts_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        nui_tts_instance.tts_release();
        mInitialized = false;
    }

    @Override
    public void onClick(View v) {
    }

    private int Initialize(String path) {
        String initParams = genInitParams(path);
        if (initParams == null) {
            Log.e(TAG, "no valid authentication information was obtained");
            ToastText("鉴权信息无效, 请查看Demo和官网中对账号的说明。");
            return -1;
        }

        int ret = nui_tts_instance.tts_initialize(new INativeTtsCallback() {
            @Override
            public void onTtsEventCallback(INativeTtsCallback.TtsEvent event, String task_id, int ret_code) {
                Log.i(TAG, "event:" + event + " task id:" + task_id + " ret:" + ret_code);
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    Log.i(TAG, "eventStart");
                    mAudioTrack.play();
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END || event == TtsEvent.TTS_EVENT_CANCEL || event == TtsEvent.TTS_EVENT_ERROR) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
                    Log.i(TAG, "eventEnd");

                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    // 调试使用, 若希望存下音频文件, 如下
                    if (mSaveWav) {
                        try {
                            mOutputFile.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (event == TtsEvent.TTS_EVENT_ERROR) {
                        String error_msg =  nui_tts_instance.getparamTts("error_msg");
                        Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                        ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                        ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                    }
                } else if (event == TtsEvent.TTS_EVENT_PAUSE) {
                    mAudioTrack.pause();
                    Log.i(TAG, "eventPause");
                } else if (event == TtsEvent.TTS_EVENT_RESUME) {
                    mAudioTrack.play();
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
                    if (mSaveWav) {
                        try {
                            mOutputFile.write(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            @Override
            public void onTtsVolCallback(int volume) {
                Log.i(TAG, "volume " + volume);
            }
        }, initParams, Constants.LogLevel.LOG_LEVEL_VERBOSE, true);  // 注意初始化信息的完整性，通过genTicket生成

        if (Constants.NuiResultCode.SUCCESS == ret) {
            mInitialized = true;
        } else {
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg =  nui_tts_instance.getparamTts("error_msg");
            Log.e(TAG, "init failed. errmsg:" + errmsg);
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

        // 语音包和SDK是隔离的，需要先设置语音包
        // 如果切换发音人：SDK可使用语音包与鉴权账号相关，由购买时获得的语音包使用权限决定
        // 如已经购买aijia，按下边方式调用后，发音人将切为aijia
        // 语音包下载地址：https://help.aliyun.com/document_detail/204185.html?spm=a2c4g.11186623.6.628.3cde73409gZCmA
        // 语音包试听：https://www.aliyun.com/activity/intelligent/offline_tts?spm=5176.12061040.J_5715429470.2.42c33871uFGa5s
        // 特别说明：离线语音合成的发音人, 并不一定也存在于在线语音合成；同理, 在线语音合成的发音人, 并不一定也存在于离线语音合成

        // aar中的资源目录中自带了一个发音人aijia, /data/user/0/mit.alibaba.nuidemo/files/asr_my/tts/voices/aijia
        String fullName = mAssetPath + "/tts/voices/" + mFontName;
        if (!isExist(fullName)) {
            Log.e(TAG, fullName + " does not exist");
            ToastText("范例语音包文件:" + fullName + " 不存在, 尝试用Demo中设置的外部发音人.");

            // 这里假设用户将发音人文件放在 /sdcard/idst/res/aijia
            fullName = mFontPath + mFontName;
            if (!isExist(fullName)) {
                Log.e(TAG, fullName + " does not exist");
                ToastText("语音包文件:" + fullName + " 不存在, 请查看Demo中的说明, 确认路径是否正确.");
            }
        }

        // 切换发音人：一定要输入全路径名称
        Log.i(TAG, "use extend_font_name:" + fullName);
        ret = nui_tts_instance.setparamTts("extend_font_name", fullName);
        if (ret != Constants.NuiResultCode.SUCCESS) {
            Log.e(TAG, "setparamTts extend_font_name " + fullName + " failed, ret:" + ret);
            ToastText(Utils.getMsgWithErrorCode(ret, "init"));
            String errmsg =  nui_tts_instance.getparamTts("error_msg");
            ToastText("初始化失败, 错误码:" + ret + " 错误信息:" + errmsg);
            return ret;
        }

        UpdateAudioPlayerSampleRate();
        // 调整语速
//        nui_tts_instance.setparamTts("speed_level", "1");
        // 调整音调
//        nui_tts_instance.setparamTts("pitch_level", "0");
        // 调整音量
//        nui_tts_instance.setparamTts("volume", "1.0");

        if (mSaveWav) {
            try {
                mOutputFile = new FileOutputStream(mDebugPath + "/test.pcm");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public boolean isExist(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            Log.e(TAG, "打不开：" + filename);
            return false;
        } else {
            return true;
        }
    }

    private String genInitParams(String workpath) {
        File folder = new File(workpath);
        if (!folder.exists() && !folder.isDirectory()) {
            Log.e(TAG, "工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            ToastText("工作目录:" + workpath + " , 不存在, 会导致无法初始化");
            return null;
        }

        String str = "";
        try {
            // 需要特别注意：ak_id/ak_secret/app_key/sdk_code/device_id等参数必须传入SDK
            // 离线语音合成sdk_code取值：精品版为software_nls_tts_offline， 标准版为software_nls_tts_offline_standard
            // 鉴权信息获取参：https://help.aliyun.com/document_detail/69835.htm?spm=a2c4g.11186623.2.28.10b33069T2ydLk#topic-1917889
            JSONObject initObject = Auth.getTicket();
            String ak_secret = initObject.getString("ak_secret");
            if (ak_secret.equals("")) {
                // 如果接口没有设置鉴权信息，尝试从本地鉴权文件加载（方便测试人员多账号验证）
                initObject = null;
                // 假设本地有存了鉴权信息的文件, 注意账号安全
                String fileName = "/sdcard/idst/auth.txt";
                if (isExist(fileName)) {
                    initObject = Auth.getTicketFromJsonFile(fileName);
                }
                if (initObject == null) {
                    ToastText("无法获取有效鉴权信息，请检查账号信息ak_id和ak_secret. 或者将鉴权信息以json格式保存至本地文件(/sdcard/idst/auth.txt)");
                    return null;
                }
            }
            initObject.put("workspace", workpath); // 必填
            // 设置为离线合成
            initObject.put("mode_type", "0"); // 必填
            str = initObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 注意! str中包含ak_id ak_secret token app_key等敏感信息, 实际产品中请勿在Log中输出这类信息！
        Log.i(TAG, "ticket:" + str);
        return str;
    }

    @Override
    public void onItemSelected(AdapterView<?> view, View arg1, int arg2, long arg3) {
        Log.i(TAG, "idst demo");
        if (view == mFontSpin) {
            String fontName = mFontSpin.getSelectedItem().toString();
            String fullName = mFontPath + fontName;
            if (!isExist(fullName)) {
                Log.e(TAG, fullName + " does not exist");
                ToastText("文件:" + fullName + " 不存在, 请查看Demo中的说明, 确认路径是否正确.");
                return;
            }
            mFontName = fontName;
            // 切换发音人：一定要输入全路径名称
            nui_tts_instance.setparamTts("extend_font_name", fullName);
            UpdateAudioPlayerSampleRate();
        } else if (view == mSpeedSpin) {
            nui_tts_instance.setparamTts("speed_level", mSpeedSpin.getSelectedItem().toString());
        } else if(view == mPitchSpin) {
            nui_tts_instance.setparamTts("pitch_level", mPitchSpin.getSelectedItem().toString());
        } else if (view == mVolumeSpin) {
            String volume = mVolumeSpin.getSelectedItem().toString();
            nui_tts_instance.setparamTts("volume", volume);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    private void setView() {
        mEditView = (EditText) findViewById(R.id.tts_content);
        ttsStartBtn = (Button)findViewById(R.id.tts_start_btn);
        ttsCancelBtn = (Button)findViewById(R.id.tts_cancel_btn);
        ttsPauseBtn = (Button)findViewById(R.id.tts_pause_btn);
        ttsResumeBtn = (Button)findViewById(R.id.tts_resume_btn);
        ttsQuitBtn = (Button)findViewById(R.id.tts_quit_btn);
        ttsClearTextBtn = (Button)findViewById(R.id.tts_clear_btn);
        ttsBatchProcess = (Button)findViewById(R.id.tts_batch_process);
        mPitchSpin = (Spinner)findViewById(R.id.tts_set_pitch_spin);
        mPitchSpin.setOnItemSelectedListener(this);
        mFontSpin = (Spinner)findViewById(R.id.tts_set_font_spin);;
        mFontSpin.setOnItemSelectedListener(this);
        mSpeedSpin = (Spinner)findViewById(R.id.tts_set_speed_spin);
        mSpeedSpin.setOnItemSelectedListener(this);
        mVolumeSpin = (Spinner)findViewById(R.id.tts_set_volume_spin);
        mVolumeSpin.setOnItemSelectedListener(this);

        getFontList();
        getSpeedList();
        getPitchList();
        getVolumeList();
    }

    private List<String> GetFontListFromPath() {
        List<String> FontList = new ArrayList<>();
        File file = new File(mFontPath);
        File[] tempList = file.listFiles();
        if (tempList != null) {
            for (int i = 0; i < tempList.length; i++) {
                if (tempList[i].isFile()) {
                    String[] arr = tempList[i].toString().split("/");
                    String name = arr[arr.length-1];
                    FontList.add(name);
                }
            }
        }
        return FontList;
    }

    private void getFontList() {
        List<String> Font = GetFontListFromPath();
        ArrayAdapter<String> spinnerFont = new ArrayAdapter<String>(TtsLocalActivity.this, android.R.layout.simple_spinner_dropdown_item, Font);
        mFontSpin.setAdapter(spinnerFont);
        mFontSpin.setSelection(0);
        paramMap.put("fontname", Font);
    }

    private void getSpeedList() {
        List<String> speed = new ArrayList<>();
        speed.add("0.6");
        speed.add("1.0");
        speed.add("2.0");
        ArrayAdapter<String> spinnerSpeed = new ArrayAdapter<String>(TtsLocalActivity.this,
                android.R.layout.simple_spinner_dropdown_item, speed);
        mSpeedSpin.setAdapter(spinnerSpeed);
        mSpeedSpin.setSelection(1);
        paramMap.put("speed_level", speed);
    }

    private void getPitchList() {
        List<String> pitch = new ArrayList<>();
        pitch.add("-500");
        pitch.add("0");
        pitch.add("500");
        ArrayAdapter<String> spinnerPitch = new ArrayAdapter<String>(TtsLocalActivity.this, android.R.layout.simple_spinner_dropdown_item, pitch);
        mPitchSpin.setAdapter(spinnerPitch);
        mPitchSpin.setSelection(1);
        paramMap.put("pitch_level", pitch);
    }

    private void getVolumeList() {
        List<String> volume = new ArrayList<>();
        volume.add("0.6");
        volume.add("1.0");
        volume.add("2.0");
        ArrayAdapter<String> spinnerVolume = new ArrayAdapter<String>(TtsLocalActivity.this,
                android.R.layout.simple_spinner_dropdown_item, volume);
        mVolumeSpin.setAdapter(spinnerVolume);
        mVolumeSpin.setSelection(1);
        paramMap.put("volume", volume);
    }

    // 批量合成示例，仅参考
    public void BatchProcess() {
        FileInputStream fileInputStream = null;
        String textlist = mDebugPath + "tts.txt";
        if (!isExist(textlist)) {
            Log.e(TAG, textlist + "does not exist");
            ToastText("批量合成示例文件:" + textlist + " 不存在, 请查看Demo中的说明, 确认路径是否正确.");
            return;
        }
        Initialize(mAssetPath);
        String fullName = mFontPath + mFontName;
        // 切换发音人：一定要输入全路径名称
        nui_tts_instance.setparamTts("extend_font_name", fullName);
        UpdateAudioPlayerSampleRate();
        try {
            fileInputStream = new FileInputStream(textlist);
        } catch (FileNotFoundException e) {
            return;
        }
        InputStreamReader fis = new InputStreamReader(fileInputStream);
        BufferedReader isr = new BufferedReader(fis);
        String line = "";
        try {
            while ((line = isr.readLine()) != null && line.length() > 0 && mInitialized) {
                String text = line;
                String[] values = line.split("\t");
                if (values.length == 2) {
                    text = values[1];
                }
                ShowText(text);
                Log.w(TAG, "text:" + values[1]);
                int ret = nui_tts_instance.startTts("1", "", text);
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    Log.e(TAG, "BatchProcess: start failed, ret:" + ret);
                    continue;
                }
                mPlaying = true;
                // 等待，直到播放完毕（回调playOver）
                while (mPlaying) {
                    if (mInitialized == false) {
                        break;
                    }
                    Sleep(5);
                }
            }
        }  catch ( IOException e) {
            e.printStackTrace();
        }
        try {
            isr.close();
            fis.close();
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        nui_tts_instance.tts_release();
        mInitialized = false;
        Log.i(TAG, "monkeyTextList done");
    }

    private void ShowText(String content) {
        final String str = content;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEditView.setText(str);
            }
        });
        try {
            Thread.sleep(30);  // wait render
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void Sleep(int dur) {
        try {
            Thread.sleep(dur);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TtsLocalActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void UpdateAudioPlayerSampleRate() {
        // 获取当前模型采样率
        String samplerate_s = nui_tts_instance.getparamTts("model_sample_rate");
        if (samplerate_s != null) {
            mAudioTrack.setSampleRate(Integer.parseInt(samplerate_s));
        }
    }
}
