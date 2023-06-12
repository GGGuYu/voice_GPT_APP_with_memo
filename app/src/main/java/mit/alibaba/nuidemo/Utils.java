package mit.alibaba.nuidemo;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Utils {
    private static final String TAG = "DemoUtils";

    public static String ip = "";
    public static int createDir (String dirPath) {
        File dir = new File(dirPath);
        //文件夹是否已经存在
        if (dir.exists()) {
            Log.w(TAG,"The directory [ " + dirPath + " ] has already exists");
            return 1;
        }

        if (!dirPath.endsWith(File.separator)) {//不是以 路径分隔符 "/" 结束，则添加路径分隔符 "/"
            dirPath = dirPath + File.separator;
        }

        //创建文件夹
        if (dir.mkdirs()) {
            Log.d(TAG,"create directory [ "+ dirPath + " ] success");
            return 0;
        }

        Log.e(TAG,"create directory [ "+ dirPath + " ] failed");
        return -1;
    }

    /** Returns the consumer friendly device name */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    public static String getDeviceId() {
        return android.os.Build.SERIAL;
    }

    public static String getDirectIp() {
        Log.i(TAG, "direct ip is " + Utils.ip);
        Thread th = new Thread(){
            @Override
            public void run() {
                try {
                    InetAddress addr = InetAddress.getByName("nls-gateway-inner.aliyuncs.com");
                    Utils.ip = addr.getHostAddress();
                    Log.i(TAG, "direct ip is " + Utils.ip);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }

        };
        th.start();
        try {
            th.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ip;
    }

    public static String getMsgWithErrorCode(int code, String status) {
        String str = "错误码:" + code;
        switch (code) {
            case 140001:
                str += " 错误信息: 引擎未创建, 请检查是否成功初始化, 详情可查看运行日志.";
                break;
            case 140008:
                str += " 错误信息: 鉴权失败, 请关注日志中详细失败原因.";
                break;
            case 140011:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause接口.";
                break;
            case 140013:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause接口.";
                break;
            case 140900:
                str += " 错误信息: tts引擎创建失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140901:
                str += " 错误信息: tts引擎初始化失败, 请检查使用的SDK是否支持离线语音合成功能.";
                break;
            case 140903:
                str += " 错误信息: tts引擎创建失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140908:
                str += " 错误信息: 发音人资源无法获得正确采样率, 请检查发音人资源是否正确.";
                break;
            case 140910:
                str += " 错误信息: 发音人资源路径无效, 请检查发音人资源文件路径是否正确.";
                break;
            case 144003:
                str += " 错误信息: token过期或无效, 请检查token是否有效.";
                break;
            case 144006:
                str += " 错误信息: 云端返回未分类错误, 请看详细的错误信息.";
                break;
            case 170008:
                str += " 错误信息: 鉴权成功, 但是存储鉴权信息的文件路径不存在或无权限.";
                break;
            case 170806:
                str += " 错误信息: 请设置SecurityToken.";
                break;
            case 170807:
                str += " 错误信息: SecurityToken过期或无效, 请检查SecurityToken是否有效.";
                break;
            case 240011:
                str += " 错误信息: SDK未成功初始化.";
                break;
            case 240005:
                if (status == "init") {
                    str += " 错误信息: 请检查appkey、akId、akSecret等初始化参数是否无效或空.";
                } else {
                    str += " 错误信息: 传入参数无效, 请检查参数正确性.";
                }
                break;
            case 240070:
                str += " 错误信息: 鉴权失败, 请查看日志确定具体问题, 特别是关注日志 E/iDST::ErrMgr: errcode=.";
                break;
            case 999999:
                str += " 错误信息: 库加载失败, 可能是库不支持当前activity, 或库加载时崩溃, 可详细查看日志判断.";
                break;
            default:
                str += " 未知错误信息, 请查看官网错误码和运行日志确认问题.";
        }
        return str;
    }

    public static String getSerialNumber() {
        String serialNumber;

        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);

            serialNumber = (String) get.invoke(c, "gsm.sn1");
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown"))
                serialNumber = (String) get.invoke(c, "ril.serialnumber");
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown"))
                serialNumber = (String) get.invoke(c, "ro.serialno");
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown"))
                serialNumber = (String) get.invoke(c, "ro.boot.serialno");
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown"))
                serialNumber = (String) get.invoke(c, "sys.serialnumber");
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown")) {
                serialNumber = Build.SERIAL;
                Log.i(TAG, "get serialNumber from Build.SERIAL " + serialNumber);
            }
            if (serialNumber.equals("") || serialNumber.equalsIgnoreCase("unknown")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    serialNumber = Build.getSerial();
                    Log.i(TAG, "get serialNumber from Build.getSerial() " + serialNumber);
                }
            }

            // If none of the methods above worked
            if (serialNumber.equals(""))
                serialNumber = "null";

        } catch (Exception e) {
            e.printStackTrace();
            serialNumber = "null";
        }

        return serialNumber;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }
}
