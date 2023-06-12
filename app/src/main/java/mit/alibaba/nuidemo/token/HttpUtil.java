package mit.alibaba.nuidemo.token;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Say something
 *
 * @author xuebin
 */
public class HttpUtil {

    public static final int STATUS_OK = 200;

    /**
     * 发送请求
     * @param request 请求
     * @return response 结果
     */
    public static HttpResponse send(HttpRequest request) throws IOException {
        OutputStream out = null;
        InputStream inputStream = null;
        try {
            String url = "https://"+ request.getDomain() + "/?" +request.getQueryString();
            Log.i("SIGN","request url is :"+url);
            URL realUrl = new URL(url);
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
            // 打开和URL之间的连接
            HttpsURLConnection conn = (HttpsURLConnection) realUrl.openConnection();
            // 设置通用的请求属性
            conn.setRequestMethod(request.getMethod());
            if (HttpRequest.METHOD_POST.equals(request.getMethod())){
                // 发送POST请求必须设置如下两行
                conn.setDoOutput(true);
                conn.setDoInput(true);
            }
            conn.setUseCaches(false);
            final byte[] bodyBytes = request.getBodyBytes();
            if (bodyBytes != null) {
                // 获取URLConnection对象对应的输出流
                out = conn.getOutputStream();
                // 发送请求参数
                out.write(bodyBytes);
                // flush输出流的缓冲
                out.flush();
            }
            final int code = conn.getResponseCode();
            Map<String, List<String>> headerFields = conn.getHeaderFields();
            String responseMessage = conn.getResponseMessage();
            Log.d("HttpUtil",responseMessage);
            if (code ==STATUS_OK){
                inputStream = conn.getInputStream();
            }else {
                inputStream = conn.getErrorStream();
            }
            HttpResponse requestResponse = request.parse(code, readAll(inputStream));
            return requestResponse;
        } finally { // 使用finally块来关闭输出流、输入流
            try {
                if (out != null) {
                    out.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int len = inputStream.read(bytes);
        while (len > 0){
            byteArrayOutputStream.write(bytes, 0, len);
            len = inputStream.read(bytes);
        }
        return byteArrayOutputStream.toByteArray();
    }
}
