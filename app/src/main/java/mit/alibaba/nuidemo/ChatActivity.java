package mit.alibaba.nuidemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.INativeTtsCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import mit.alibaba.nuidemo.Util.DBHelper;
import mit.alibaba.nuidemo.token.AccessToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity implements INativeNuiCallback {
    private List<Message> chatHistory;

    private DBHelper dbHelper;
    private boolean locked = false;
    //最原始设定
//    private String RULE = "在接下来的对话中，你将扮演一位名为贾维斯的语音助手，必须保证严格遵守以下规则:" +
//            "#1. 你的回复必须控制在150字以内." +
//            "#2. 你的名字叫做贾维斯，不能告诉用户你是AI语言模型." +
//            "这在下句话之后我们将开启全新的对话。" +
//            "你不用对上面的话做出任何回复，但是必须严格遵守你扮演的角色的规则,现在你将回复：“永远等待着您，先生,有什么可以帮助您的吗？”。";
//    实验设定
    private String RULE = "在接下来的对话中，你将扮演一位名为星期三的语音助手，必须保证严格遵守以下规则:" +
            "#规则1: 你的主要目的是与用户闲聊,因此回复尽量活泼简短." +
            "#规则2: 你的回复必须控制在120字符以内, 尽量简短." +
            "#规则3: 你的名字叫做星期三，不能告诉用户你是AI语言模型." +
            "并且星期三可以通过回复中添加一些格式化指令去帮助用户做一些现实中的事情，下面列举一些星期三可以使用的格式化指令,在[]中的为指令." +
            "#指令1:[open_light()]:该指令可以帮助用户打开客厅灯;" +
            "示例1: {用户说:\"帮我开一下灯\" 星期三回复:\"[open_light()],如您所愿,帮您打开了灯,请检查。\"." +
            "#指令2:[shut_light()]:关闭客厅灯;" +
            "示例2: {用户说:\"帮我把灯关掉\" 星期三回复:\"[shut_light()],如您所愿,已经关闭了您的灯,请检查。\"." +
            "通过以上各个指令的示例可以看出,星期三通过在回复中添加这些[]括号中的格式化指令," +
            "其他程序会帮助星期三实现指令对应的功能,所以你只需要在回复中带上对应任务的格式化指令,就可以真实的帮助到用户完成任务。" +
            "但是只有在上面明确出现过的指令才是星期三可以使用的," +
            "这在下句话之后我们将开启全新的对话。" +
            "你不用对上面的话做出任何回复，" +
            "请合理地在回复中使用格式化指令实现用户的需求(若用户提出需求),并必须严格遵守你的规则,更好的扮演星期三和用户交流,现在你将回复：" +
            "“亲爱的先生，有什么可以和星期三分享的新鲜事吗？”。之后等待用户回复。";
    //正在用的设定
//        private String RULE = "在接下来的对话中，你将扮演一位名为星期三的语音助手，必须保证严格遵守以下规则:" +
//            "#规则1: 你存在的目的主要是与用户闲聊,因此回复尽量活泼简短." +
//            "#规则2: 你的回复必须控制在120字符以内, 尽量简短." +
//            "#规则3: 你的名字叫做星期三，不能告诉用户你是AI语言模型." +
//            "这在下句话之后我们将开启全新的对话。" +
//            "你不用对上面的话做出任何回复，但是必须严格遵守你扮演的角色的规则,现在你将回复：最亲爱的周先生，有什么需要分享的吗？";
    private String trueMemory = "";
    private static final String TAG = "TtsBasicActivity";

    private String AKID = "";//填写您自己的
    private String AKKey = "";//填写您自己的
    private String AliAPPKey = "";//填写您自己的

    private String AliToken = "";
    private String OpenAIKey = "";//作者的 要过期了 三个都要过期了,请您更换自己的
//    private String OpenAIKey = "";//
//      private String OpenAIKey = "";//萌
    private Button buttonStore;
    NativeNui nui_tts_instance = new NativeNui(Constants.ModeType.MODE_TTS);
    final static String CN_PREVIEW ="";
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
//            Log.i(TAG, "start play");
        }
        @Override
        public void playOver() {
//            Log.i(TAG, "play over");
        }
    });
    boolean initialized =  false;
    private String ttsText = new String();
    private static final String TAG1 = "SpeechRecognizerActivity";
    private String YuYinText;
    NativeNui nui_instance1 = new NativeNui();
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
    private ListView userMessagesList;
    private ListView botMessagesList;
    private EditText chatInputEditText;
    private Button sendButton;

    private ArrayList<Message> userMessages = new ArrayList<>();
    private ArrayList<Message> botMessages = new ArrayList<>();
    private MessageAdapter userMessageAdapter;
    private MessageAdapter botMessageAdapter;


    private String apiEndpoint = "https://api.openai.com/v1/chat/completions";
    private String apiKey = OpenAIKey; // Replace with your actual API key
    private String model = "gpt-3.5-turbo";
    private double temperature = 1.2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

//        初始化
        chatHistory = new ArrayList<>();
        dbHelper = new DBHelper(this);
        List<String> Memos = dbHelper.getAllMemo();
//        System.out.println("调试一下Memos:");
        int cnt = 0;
        for(String memo : Memos)
        {
            cnt++;
            trueMemory += "记忆"+ cnt + ":" + memo;
            System.out.println("记忆"+ cnt + ":" + memo);
        }
        //如果记忆不是空的话，添加提示词
        if(!trueMemory.equals(""))
        {
            trueMemory += "以上是你与当前用户交流的几段之前的对话，这些可能能够帮助你与用户更好的交流。";
            RULE = trueMemory + RULE;
        }
//        System.out.println("-------------------------------------------------------");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AliToken = GetToken();
//                    System.out.println("获取到的Token: " + AliToken);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
        thread.start();
        try {
            // 等待线程执行完毕
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 关闭线程
        thread.interrupt();

//        System.out.println("获取到的Token: " + AliToken);
//        System.out.println("---------------------------------------------------------------");





        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
//        Log.i(TAG1, "current sdk version: " + version);
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChatActivity.this, version_text, Toast.LENGTH_LONG).show();
            }
        });


        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());
        // 初始化视图
        userMessagesList = findViewById(R.id.user_messages_list);
        botMessagesList = findViewById(R.id.bot_messages_list);
        chatInputEditText = findViewById(R.id.chat_input_edit_text);
        sendButton = findViewById(R.id.send_button);


        sendButton.setVisibility(View.GONE);
        chatInputEditText.setVisibility(View.GONE);

        // 设置适配器
        userMessageAdapter = new MessageAdapter(this, userMessages);
        botMessageAdapter = new MessageAdapter(this, botMessages);

        userMessagesList.setAdapter(userMessageAdapter);
        botMessagesList.setAdapter(botMessageAdapter);

        // 发送按钮点击事件
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Send(chatInputEditText.getText().toString().trim());
            }
        });
        initUIWidgets();
        ttsEditView = (EditText) findViewById(R.id.tts_content);
        ttsStartBtn = (Button)findViewById(R.id.tts_start_btn);
        ttsCancelBtn = (Button)findViewById(R.id.tts_cancel_btn);
        ttsPauseBtn = (Button)findViewById(R.id.tts_pause_btn);
        ttsResumeBtn = (Button)findViewById(R.id.tts_resume_btn);
        ttsQuitBtn = (Button)findViewById(R.id.tts_quit_btn);
        ttsClearTextBtn = (Button)findViewById(R.id.tts_clear_btn);

        ttsEditView.setText(CN_PREVIEW);

        ttsStartBtn.setVisibility(View.GONE);
        ttsCancelBtn.setVisibility(View.GONE);
        ttsPauseBtn.setVisibility(View.GONE);
        ttsResumeBtn.setVisibility(View.GONE);
        ttsClearTextBtn.setVisibility(View.GONE);
        ttsQuitBtn.setVisibility(View.GONE);

        ttsStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HeChengYuYin(ttsEditView.getText().toString());
            }
        });
        ttsQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.i(TAG, "tts release");
                mAudioTrack.stop();
                nui_tts_instance.tts_release();
                initialized = false;
            }
        });
        ttsCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.i(TAG, "cancel tts");
                nui_tts_instance.cancelTts("");
                mAudioTrack.stop();
            }
        });

        ttsPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.i(TAG, "pause tts");
                nui_tts_instance.pauseTts();
                mAudioTrack.pause();
            }
        });
        ttsResumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.i(TAG, "resume tts");
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
//        Log.i(TAG, "workpath = " + path);
        asset_path = path;

        //这里主动调用完成SDK配置文件的拷贝
        if (CommonUtils.copyAssetsData(this)) {
//            Log.i(TAG, "copy assets data done");
        } else {
//            Log.i(TAG, "copy assets failed");
            return;
        }

        String version2 = nui_tts_instance.GetVersion();


        if (Constants.NuiResultCode.SUCCESS == Initialize(path)) {
            initialized = true;
        } else {
//            Log.e(TAG, "init failed");
        }

//        -----------------------------------------------------------------------
//        一些需要放在后面的初始化内容
        chatInputEditText.setText(RULE);
        sendButton.performClick();

    }
    private String GetToken() throws IOException {
        AccessToken accessToken = new AccessToken(AKID, AKKey);
        accessToken.apply();
        String token = accessToken.getToken();
        long expireTime = accessToken.getExpireTime();

        return token;
    }
    private void HeChengYuYin(String hc){
        ttsText = hc;
        if (TextUtils.isEmpty(ttsText)) {
//            Log.e(TAG, "tts empty");
            return;
        }
        if (!initialized) {
//            Log.i(TAG, "init tts");
            Initialize(asset_path);
        }

//        Log.i(TAG, "start play tts");
        // 支持一次性合成300字符以内的文字，其中1个汉字、1个英文字母或1个标点均算作1个字符，
        // 超过300个字符的内容将会截断。所以请确保传入的text小于300字符(不包含ssml格式)。
        // 长短文本语音合成收费不同，须另外开通长文本语音服务，请注意。
        // 不需要长文本语音合成功能则无需考虑以下操作。
        int charNum = nui_tts_instance.getUtf8CharsNum(ttsText);
//        Log.i(TAG, "chars:" + charNum + " of text:" + ttsText);
        if (charNum > 300) {
//            Log.w(TAG, "text exceed 300 chars.");
            // 超过300字符设置成 长文本语音合成 模式
            nui_tts_instance.setparamTts("tts_version", "1");
        } else {
            // 未超过300字符设置成 短文本语音合成 模式
            nui_tts_instance.setparamTts("tts_version", "0");
        }

        // 每个instance一个task，若想同时处理多个task，请启动多instance
        nui_tts_instance.startTts("1", "", ttsText);
    }
    private void Send(String ct)
    {
        String userMessage = ct.trim();
        System.out.println("在Send中的信息：" + userMessage);
        if (!userMessage.isEmpty()) {
            addUserMessage(userMessage);
            chatInputEditText.setText("");
//            try {
//                new GenerateBotResponse().execute(userMessage).get();
//            } catch (ExecutionException | InterruptedException e) {
//                e.printStackTrace();
//                addBotMessage("Oops! Something went wrong.");
//            }
                new GenerateBotResponse().execute(userMessage);
        }
    }

    @Override
    protected void onStart() {
//        Log.i(TAG1, "onStart");
        super.onStart();
        doInit ();
    }

    @Override
    protected void onStop() {
//        Log.i(TAG1, "onStop");
        super.onStop();
        nui_instance1.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioTrack.stop();
        mAudioTrack.releaseAudioTrack();
        nui_tts_instance.tts_release();
        initialized = false;
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
//                Log.i(TAG, "tts event:" + event + " task id " + task_id + " ret " + ret_code);
                if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_START) {
                    mAudioTrack.play();
//                    Log.i(TAG, "start play");
                } else if (event == INativeTtsCallback.TtsEvent.TTS_EVENT_END) {
                    /*
                     * 提示: TTS_EVENT_END事件表示TTS已经合成完并通过回调传回了所有音频数据, 而不是表示播放器已经播放完了所有音频数据。
                     */
//                    Log.i(TAG, "play end");

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
//                    Log.i(TAG, "play pause");
                } else if (event == TtsEvent.TTS_EVENT_RESUME) {
                    mAudioTrack.play();
                } else if (event == TtsEvent.TTS_EVENT_ERROR) {
                    // 表示推送完数据, 当播放器播放结束则会有playOver回调
                    mAudioTrack.isFinishSend(true);

                    String error_msg =  nui_tts_instance.getparamTts("error_msg");
//                    Log.e(TAG, "TTS_EVENT_ERROR error_code:" + ret_code + " errmsg:" + error_msg);
                    ToastText(Utils.getMsgWithErrorCode(ret_code, "error"));
                    ToastText("错误码:" + ret_code + " 错误信息:" + error_msg);
                }
            }
            @Override
            public void onTtsDataCallback(String info, int info_len, byte[] data) {
                if (info.length() > 0) {
//                    Log.i(TAG, "info: " + info);
                }
                if (data.length > 0) {
                    mAudioTrack.setAudioData(data);
//                    Log.i(TAG, "write:" + data.length);
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
//                Log.i(TAG, "tts vol " + vol);
            }
        }, genTicket(path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);

        if (Constants.NuiResultCode.SUCCESS != ret) {
//            Log.i(TAG, "create failed");
        }

        // 在线语音合成发音人可以参考阿里云官网
        // https://help.aliyun.com/document_detail/84435.html
        nui_tts_instance.setparamTts("font_name", "ruoxi");

        // 详细参数可见: https://help.aliyun.com/document_detail/173642.html
        nui_tts_instance.setparamTts("sample_rate", "16000");
        // 模型采样率设置16K，则播放器也得设置成相同采样率16K.
        mAudioTrack.setSampleRate(16000);

        nui_tts_instance.setparamTts("enable_subtitle", "1");
        // 调整语速
        nui_tts_instance.setparamTts("speed_level", "1.2");
        // 调整音调
        nui_tts_instance.setparamTts("pitch_level", "-1");
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
            com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject();
            object.put("app_key", AliAPPKey); // 必填
            object.put("token", AliToken); // 必填

            object.put("device_id", Utils.getDeviceId()); // 必填, 可填入用户账户相关id
            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"); // 默认
            object.put("workspace", workpath); // 必填, 且需要有读写权限

            // 设置为在线合成
            //  Local = 0,
            //  Mix = 1,  // init local and cloud
            //  Cloud = 2,
            object.put("mode_type", "2");
            str = object.toString();
        } catch (com.alibaba.fastjson.JSONException e) {
            e.printStackTrace();
        }
//        Log.i(TAG, "UserContext:" + str);
        return str;
    }

    private void ToastText(String text) {
        final String str = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChatActivity.this, str, Toast.LENGTH_LONG).show();
            }
        });
    }
    private void initUIWidgets() {
        asrView = (TextView)findViewById(R.id.textView);
        kwsView = (TextView)findViewById(R.id.kws_text);

        startButton = (Button)findViewById(R.id.button_start);
        cancelButton = (Button)findViewById(R.id.button_cancel);
        buttonStore = findViewById(R.id.button_store);
        mVadSwitch = (Switch)findViewById(R.id.vad_switch);
        mVadSwitch.setVisibility(View.GONE);
        kwsView.setVisibility(View.GONE);
        asrView.setVisibility(View.GONE);
        mVadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
//                Log.i(TAG1, "vad mode onCheckedChanged b=" + b);
                vadMode.set(b);
            }
        });

        //自己添加的储存按钮初始化位置
        setButtonState(buttonStore, true);
        buttonStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                System.out.println("上锁之前的locked：" + locked);
                locked = true;
//                System.out.println("上锁之后的locked：" + locked);
                String store_prompt = "请对这次的对话中的重要内容做一个不超过100字的总结";
                chatInputEditText.setText(store_prompt);
                sendButton.performClick();
            }
        });

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Log.i(TAG1, "start!!!");

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
//                Log.i(TAG1, "cancel");
                setButtonState(startButton, true);
                setButtonState(cancelButton, false);

                if (!checkNotInitToast()) {
                    return;
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        long ret = nui_instance1.stopDialog();
                        System.out.println("语音中的内容是：" + YuYinText);
                        // 将JSON字符串转换为JsonObject对象
                        JSONObject yuyinjson = null;
                        try {
                            yuyinjson = new JSONObject(YuYinText);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        // 获取 payload 中的 result 值
                        String result = null;
                        try {
                            result = yuyinjson.getJSONObject("payload").getString("result");
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        YuYinText = result;
//                        System.out.println(YuYinText);
                        // 切换回主线程更新UI
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                chatInputEditText.setText(YuYinText);
                                sendButton.performClick();
                            }
                        });
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

        //获取工作路径, 这里获得当前nuisdk.aar中assets路径
        String asset_path = CommonUtils.getModelPath(this);
//        Log.i(TAG1, "use workspace " + asset_path);

        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
        //使用者请根据实际情况选择Android设备的MediaRecorder.AudioSource
        //录音麦克风如何选择,可查看https://developer.android.google.cn/reference/android/media/MediaRecorder.AudioSource
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, WAVE_FRAM_SIZE * 4);

        //这里主动调用完成SDK配置文件的拷贝
        if (CommonUtils.copyAssetsData(this)) {
//            Log.i(TAG1, "copy assets data done");
        } else {
//            Log.i(TAG1, "copy assets failed");
            return;
        }

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        int ret = nui_instance1.initialize(this, genInitParams(asset_path,debug_path), Constants.LogLevel.LOG_LEVEL_VERBOSE, true);
//        Log.i(TAG1, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        } else {
            final String msg_text = Utils.getMsgWithErrorCode(ret, "init");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ChatActivity.this, msg_text, Toast.LENGTH_LONG).show();
                }
            });
        }

        //设置相关识别参数，具体参考API文档
        nui_instance1.setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            com.alibaba.fastjson.JSONObject nls_config = new com.alibaba.fastjson.JSONObject();
            nls_config.put("enable_intermediate_result", true);
            //参数可根据实际业务进行配置
            //接口说明可见: https://help.aliyun.com/document_detail/173298.html
            //查看 2.开始识别
            //nls_config.put("enable_punctuation_prediction", true);
            //nls_config.put("enable_inverse_text_normalization", true);
            //nls_config.put("enable_voice_detection", true);
            //nls_config.put("customization_id", "test_id");
            //nls_config.put("vocabulary_id", "test_id");
            //nls_config.put("max_start_silence", 10000);
            //nls_config.put("max_end_silence", 800);
            //nls_config.put("sample_rate", 16000);
            //nls_config.put("sr_format", "opus");
            com.alibaba.fastjson.JSONObject parameters = new com.alibaba.fastjson.JSONObject();

            parameters.put("nls_config", nls_config);
            parameters.put("service_type", Constants.kServiceTypeASR); // 必填

            //如果有HttpDns则可进行设置
            //parameters.put("direct_ip", Utils.getDirectIp());

            params = parameters.toString();
        } catch (com.alibaba.fastjson.JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void startDialog() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Constants.VadMode vad_mode = Constants.VadMode.TYPE_P2T;
                if (vadMode.get()) {
                    vad_mode = Constants.VadMode.TYPE_VAD;
                } else {
                    vad_mode = Constants.VadMode.TYPE_P2T;
                }
                int ret = nui_instance1.startDialog(vad_mode,
                        genDialogParams());
//                Log.i(TAG1, "start done with " + ret);
                if (ret != 0) {
                    final String msg_text = Utils.getMsgWithErrorCode(ret, "start");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ChatActivity.this, msg_text, Toast.LENGTH_LONG).show();
                        }
                    });
                }
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
            com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject();

            //token 24小时过期，因此需要通过阿里云SDK来进行更新
            //接口说明可见: https://help.aliyun.com/document_detail/173298.html
            //查看 1. 鉴权和初始化
            object.put("app_key", AliAPPKey); // 必填
            object.put("token", AliToken); // 必填
            object.put("workspace", workpath); // 必填
            object.put("device_id", Utils.getDeviceId()); // 必填
            object.put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1");
            object.put("debug_path", debugpath);
            object.put("sample_rate", "16000");
            object.put("format", "opus");
//            object.put("save_wav", "true");

            // FullMix = 0   // 选用此模式开启本地功能并需要进行鉴权注册
            // FullCloud = 1
            // FullLocal = 2 // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrMix = 3    // 选用此模式开启本地功能并需要进行鉴权注册
            // AsrCloud = 4
            // AsrLocal = 5  // 选用此模式开启本地功能并需要进行鉴权注册
            object.put("service_mode", Constants.ModeAsrCloud); // 必填
            str = object.toString();
        } catch (com.alibaba.fastjson.JSONException e) {
            e.printStackTrace();
        }

//        Log.i(TAG1, "InsideUserContext:" + str);
        return str;
    }

    private String genDialogParams() {
        String params = "";
        try {
            com.alibaba.fastjson.JSONObject dialog_param = new com.alibaba.fastjson.JSONObject();
            //运行过程中可以在startDialog时更新参数，尤其是更新过期token
//            dialog_param.put("app_key", "");
//            dialog_param.put("token", "");
            params = dialog_param.toString();
        } catch (com.alibaba.fastjson.JSONException e) {
            e.printStackTrace();
        }

//        Log.i(TAG1, "dialog params: " + params);
        return params;
    }

    private boolean checkNotInitToast() {
        if (!mInit) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ChatActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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
//                Log.i(TAG1, "setBtn state " + btn.getText() + " state=" + state);
                btn.setEnabled(state);
            }
        });
    }

    private void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Log.i(TAG1, "showText text=" + text);
                if (TextUtils.isEmpty(text)) {
//                    Log.w(TAG1, "asr text is empty");
                    if (who == kwsView) {
//                        who.setText("激活词");
                    } else {
//                        who.setText("识别文本");
                    }
                } else {
//                    who.setText(text);
                    YuYinText = text;
                }
            }
        });
    }

    private void appendText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Log.i(TAG1, "append text=" + text);
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
//        Log.i(TAG1, "event=" + event);
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
                    Toast.makeText(ChatActivity.this, "ERROR with " + resultCode,
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
//            Log.e(TAG1, "audio recorder not init");
            return -1;
        }
        ret = mAudioRecorder.read(buffer, 0, len);
        return ret;
    }

    //当录音状态发送变化的时候调用
    @Override
    public void onNuiAudioStateChanged(Constants.AudioState state) {
//        Log.i(TAG1, "onNuiAudioStateChanged");
        if (state == Constants.AudioState.STATE_OPEN) {
//            Log.i(TAG1, "audio recorder start");
            mAudioRecorder.startRecording();
//            Log.i(TAG1, "audio recorder start done");
        } else if (state == Constants.AudioState.STATE_CLOSE) {
//            Log.i(TAG1, "audio recorder close");
            mAudioRecorder.release();
        } else if (state == Constants.AudioState.STATE_PAUSE) {
//            Log.i(TAG1, "audio recorder pause");
            mAudioRecorder.stop();
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float val) {
//        Log.i(TAG1, "onNuiAudioRMSChanged vol " + val);
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent event) {
//        Log.i(TAG1, "onNuiVprEventCallback event " + event);
    }
    private void addUserMessage(String message) {
        userMessages.add(new Message(Message.USER_MESSAGE, message));
        System.out.println("运行到重绘聊天页面的地方了！！");
        userMessageAdapter.notifyDataSetChanged();
        userMessagesList.setSelection(userMessageAdapter.getCount() - 1);
    }

    private void addBotMessage(String message) {
        botMessages.add(new Message(Message.BOT_MESSAGE, message));
        botMessageAdapter.notifyDataSetChanged();
        botMessagesList.setSelection(botMessageAdapter.getCount() - 1);
    }

    /**
     * AsyncTask 用于在后台线程中调用 OpenAI API 来生成机器人回复。
     **/
    private class GenerateBotResponse extends AsyncTask<String, Void, Message> {

        private static final int MAX_RETRY_TIMES = 3; // 最大重试次数
        private static final long RETRY_INTERVAL_MS = 5000; // 重试间隔时间（毫秒）
        @Override
        protected Message doInBackground(String... strings) {
            try {
                MediaType mediaType = MediaType.parse("application/json");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("model", model);

                JSONArray messages = new JSONArray();

                // 添加聊天历史到请求体中
                for (Message message : chatHistory) {
                    JSONObject historyMessage = new JSONObject();
                    historyMessage.put("role", message.getType() == 0 ? "user" : "bot");
                    historyMessage.put("content", message.getContent());
                    messages.put(historyMessage);
                }

                // 添加新消息到请求体中
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", strings[0]);
                messages.put(message);

                jsonObject.put("messages", messages);

                jsonObject.put("temperature", temperature);

                RequestBody body = RequestBody.create(mediaType, jsonObject.toString());
                chatHistory.add(new Message(0 , message.getString("content")));
                System.out.println("---------------------------------------------------------");
                System.out.println("准备发送的json："  + jsonObject);

                int retryCount = 0;
                while (retryCount <= MAX_RETRY_TIMES) {
                    Request request = new Request.Builder()
                            .url(apiEndpoint)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .build();

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS) // 连接超时时间为5秒
                            .readTimeout(15, TimeUnit.SECONDS)    // 读取超时时间为15秒
                            .build();
                    Response response = null;
                    try {
                        response = client.newCall(request).execute();
                    } catch (IOException e) {
                        if (retryCount == MAX_RETRY_TIMES) {
                            throw e;
                        }
                        retryCount++;
                        System.out.println("Request failed, will retry in " + RETRY_INTERVAL_MS + " ms.");
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }

                    if (response != null) {
                        String responseBody = response.body().string();
                        Message back = NetworkUtils.parseResponse(responseBody);
                        chatHistory.add(back);
                        System.out.println("back:" + back.toString());
                        return back;
                    }
                }

                return null;
            } catch (JSONException | IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        @Override
        protected void onPostExecute(Message message) {
            if (message == null) {
                // 出现错误时添加错误消息
                addBotMessage("Oops! Something went wrong.");
            } else {
                addBotMessage(message.getContent());
                // 切换回主线程更新UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        如果记忆锁开启，那么不需要语音播报，只需要记忆即可，如果不处于记忆状态，就发出语音播报
                        if(!locked)
                        {
                            String reply = message.getContent();
                            int index1 = reply.indexOf("open_light()");

                            if(index1 != -1)
                            {
//                                System.out.println();
//                                System.out.println("----------------------------------------------------------------");
//                                System.out.println("开灯了！！！在这里处理逻辑或发送网络请求给其他终端处理");
//                                System.out.println("----------------------------------------------------------------");
//                                System.out.println();
                            }
                            int index2 = reply.indexOf("shut_light()");

                            if(index2 != -1)
                            {
//                                System.out.println();
//                                System.out.println("----------------------------------------------------------------");
//                                System.out.println("关灯了！！！在这里处理逻辑或发送网络请求给其他终端处理");
//                                System.out.println("----------------------------------------------------------------");
//                                System.out.println();
                            }

                            ttsEditView.setText(reply);
                            ttsStartBtn.performClick();
                        }else {
                            String memo = message.getContent();
//                            ttsEditView.setText(memo);
                            if(memo != null)
                            {
                                dbHelper.addMemo(memo);
                                chatHistory.clear();
                                String alert = "成功的保存了历史对话。若您要继续使用，请你退出程序重新初始化。";
                                ttsEditView.setText(alert);
                                ttsStartBtn.performClick();
                            }
                            else {
                                String alert = "对不起，保存记录失败了。";
                                ttsEditView.setText(alert);
                                ttsStartBtn.performClick();
                            }
                            locked = false;
//                            System.out.println("记忆锁已还原");
                        }
                    }
                });
            }
        }
    }
}
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
// 切换回主线程更新UI
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        ttsEditView.setText(back.getContent());
//                        ttsStartBtn.performClick();
//                    }
//                });


