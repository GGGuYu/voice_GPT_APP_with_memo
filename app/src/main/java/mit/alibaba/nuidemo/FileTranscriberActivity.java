package mit.alibaba.nuidemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.INativeFileTransCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;

import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;


public class FileTranscriberActivity extends Activity implements INativeFileTransCallback{
    private static final String TAG = "FileTranscriberActivity";

    NativeNui nui_instance = new NativeNui();

    private int MAX_TASKS = 10;
    private Button startButton;
    private Button cancelButton;

    private TextView asrView;

    private HandlerThread mHanderThread;

    private boolean mInit = false;
    private List<String> task_list = new ArrayList<String>();
    private Handler mHandler;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_file_trans);


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
        final String version_text = "内部SDK版本号:" + version;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileTranscriberActivity.this, version_text, Toast.LENGTH_LONG).show();
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
        nui_instance.release();
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
                startTranscriber();
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
                        synchronized(task_list) {
                            int size = task_list.size();
                            for (int i = 0; i < size; i++) {
                                String task = task_list.get(i);
                                int ret = nui_instance.cancelFileTranscriber(task);
                                Log.i(TAG, "cancel dialog " + ret + " end");
                            }
                            task_list.clear();
                        }
                    }
                });

            }
        });
    }

    private void doInit() {
        showText(asrView, "");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        //这里主动调用完成SDK配置文件的拷贝
        if (CommonUtils.copyAssetsData(this)) {
            Log.i(TAG, "copy assets data done");
        } else {
            Log.i(TAG, "copy assets failed");
            return;
        }
        //获取工作路径
        String assets_path = CommonUtils.getModelPath(this);
        Log.i(TAG, "use workspace " + assets_path);


        String debug_path = getExternalCacheDir().getAbsolutePath() + "/debug_" + System.currentTimeMillis();
        Utils.createDir(debug_path);

        //初始化SDK，注意用户需要在Auth.getAliYunTicket中填入相关ID信息才可以使用。
        int ret = nui_instance.initialize(this, genInitParams(assets_path, debug_path), Constants.LogLevel.LOG_LEVEL_VERBOSE);
        Log.i(TAG, "result = " + ret);
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true;
        }

        //设置相关识别参数，具体参考API文档
        nui_instance.setParams(genParams());
    }

    private String genParams() {
        String params = "";
        try {
            JSONObject nls_config = new JSONObject();

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

    private void startTranscriber() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized(task_list) {
                    task_list.clear();
                    for (int i = 0; i < MAX_TASKS; i++) {
                        byte[] task_id = new byte[32];
                        int ret = nui_instance.startFileTranscriber(genDialogParams(), task_id);
                        String taskId = new String(task_id);
                        task_list.add(taskId);
                        Log.i(TAG, "start task id " + taskId + " done with " + ret);
                    }
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
            JSONObject object = new JSONObject();
            object.put("app_key", "");
            object.put("token", "");
            object.put("url", "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/FlashRecognizer");

            object.put("device_id", Utils.getDeviceId());
            object.put("workspace", workpath);
            object.put("debug_path", debugpath);
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
            //若想在运行时切换app_key
            //dialog_param.put("app_key", "");
            dialog_param.put("file_path", "/sdcard/test.wav");

            JSONObject nls_config = new JSONObject();
            nls_config.put("format", "wav");

            dialog_param.put("nls_config", nls_config);
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
                    Toast.makeText(FileTranscriberActivity.this, "SDK未成功初始化.", Toast.LENGTH_LONG).show();
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

    @Override
    public void onFileTransEventCallback(Constants.NuiEvent event, int resultCode, int finish, AsrResult asrResult, String taskId) {
        Log.i(TAG, "event=" + event + " task_id " + taskId);
        if (event == Constants.NuiEvent.EVENT_FILE_TRANS_UPLOADED) {
            showText(asrView, "完成上传，正在转写...");
        } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_RESULT) {
            showText(asrView, asrResult.asrResult);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
            Log.i(TAG, "error happened: " + resultCode);
            showText(asrView, asrResult.asrResult);
            setButtonState(startButton, true);
            setButtonState(cancelButton, false);
        }
//        if (finish == 1) {
//            synchronized(task_list) {
//                task_list.remove(taskId);
//            }
//        }
    }
}



