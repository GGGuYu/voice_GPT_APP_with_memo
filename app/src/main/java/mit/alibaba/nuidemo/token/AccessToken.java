package mit.alibaba.nuidemo.token;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

/**
 * 用来获取访问令牌的工具类
 *
 * @author xuebin
 */
public class AccessToken{
    private static final String TAG="AliSpeechSDK";
    private static final String NODE_TOKEN = "Token";
    private String accessKeyId;
    private String accessKeySecret;
    private String token;
    private long expireTime;
    private int statusCode;
    private String errorMessage;
    private String domain = "nls-meta.cn-shanghai.aliyuncs.com";
    private String regionId= "cn-shanghai";
    private String version ="2019-02-28";
    private String action = "CreateToken";

    /**
     * 构造实例
     * @param accessKeyId
     * @param accessKeySecret
     */
    public AccessToken(String accessKeyId, String accessKeySecret) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    /**
     * 构造实例
     * @param accessKeyId
     * @param accessKeySecret
     */
    public AccessToken(String accessKeyId, String accessKeySecret,String domain, String regionId, String version) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.domain = domain;
        this.regionId = regionId;
        this.version = version;
    }

    /**
     * 向服务端申请访问令牌，申请成功后会更新token和expireTime，然后返回
     * @throws IOException https调用出错，或返回数据解析出错
     */
    public void apply() throws IOException {
        HttpRequest request = new HttpRequest(accessKeyId,accessKeySecret,this.domain,this.regionId,this.version);

        request.authorize();
        HttpResponse response = HttpUtil.send(request);
        Log.i(TAG,"Get response token info :" + JSON.toJSONString(response));
        if (response.getErrorMessage() == null) {
            String result = response.getResult();
            try {
                JSONObject jsonObject = JSON.parseObject(result);
                if (jsonObject.containsKey(NODE_TOKEN)) {
                    this.token = jsonObject.getJSONObject(NODE_TOKEN).getString("Id");
                    this.expireTime = jsonObject.getJSONObject(NODE_TOKEN).getIntValue("ExpireTime");
                } else {
                    statusCode = 500;
                    errorMessage = "Received unexpected result: " + result;
                }
            } catch (JSONException e) {
                throw new IOException("Failed to parse result: " + result, e);
            }
        } else {
            Log.e(TAG, response.getErrorMessage());
            statusCode = response.getStatusCode();
            errorMessage = response.getErrorMessage();
        }
    }

    public String getToken() {
        return token;
    }

    public long getExpireTime() {
        return expireTime;
    }

}
