package mit.alibaba.nuidemo.token;

import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Http request
 *
 * @author xuebin
 */
public class HttpRequest {
    public static final String TAG= "SIGN";

    public static final String CHARSET_UTF8 = "UTF-8";

    public static final String METHOD_POST = "POST";
    public static final String METHOD_GET = "GET";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String ACCEPT = "application/json";
    public static final String Format = "JSON";
    public static final String SignatureMethod = "HMAC-SHA1";
    public static final String SignatureVersion = "1.0";
    private String AccessKeyId;
    private String AccessKeySecret;

    private static final String ENCODING = "UTF-8";
    private final static String FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private final static String TIME_ZONE = "GMT";

    protected Map<String, String> header;

    private String domain;
    private String regionId;
    private String queryString;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String action = "CreateToken";
    private String version;


    private String url;

    public HttpRequest(String accessKeyId, String accessKeySecret,
                       String domain, String regionId, String version) {

        AccessKeyId = accessKeyId;
        AccessKeySecret = accessKeySecret;
        setDomain(domain);
        setVersion(version);
        setRegionId(regionId);

        header = new HashMap<String, String>();
        header.put(HEADER_ACCEPT, ACCEPT);
        header.put(HEADER_CONTENT_TYPE, CONTENT_TYPE);
        // POP doesn't support gzip
//        header.put(HEADER_ACCEPT_ENCODING, "identity");
        header.put("HOST", domain);
    }

    /**
     * 返回此 request 对应的 url 地址
     * @return url 地址
     */
    public String getUrl() {
        return url;
    }

    /**
     * 返回 http body
     * @return 二进制形式的http body
     */
    public byte[] getBodyBytes() {
        return null;
    }



    public String getMethod() {
        return METHOD_GET;
    }

    /**
     * 解析http返回结果,构建response对象
     * @param statusCode http 状态码
     * @param bytes 返回的二进制数据
     * @return response对象
     */
    public HttpResponse parse(int statusCode, byte[] bytes) throws IOException {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(statusCode);
        String result = new String(bytes, HttpRequest.CHARSET_UTF8);
        if (response.getStatusCode() == HttpUtil.STATUS_OK){
            response.setResult(result);
        }else {
            response.setErrorMessage(result);
        }
        return response;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void authorize(){
        Map<String, String> queryParamsMap = new HashMap<String, String>();
        queryParamsMap.put("AccessKeyId", this.AccessKeyId);
        queryParamsMap.put("Action", this.action);
        queryParamsMap.put("Version", this.version);
        queryParamsMap.put("RegionId", this.regionId);
        queryParamsMap.put("Timestamp", getISO8601Time(null));
        queryParamsMap.put("Format", Format);
        queryParamsMap.put("SignatureMethod", SignatureMethod);
        queryParamsMap.put("SignatureVersion", SignatureVersion);
        queryParamsMap.put("SignatureNonce", getUniqueNonce());

        String queryString = canonicalizedQuery(queryParamsMap);
        if (null == queryString) {
            Log.e(TAG,"create the canonicalized query failed");
            return;
        }

        String method = "GET";
        String urlPath = "/";
        String stringToSign = createStringToSign(method, urlPath, queryString);
        Log.i(TAG,"String to sign is :"+ stringToSign);
        // 2.计算 HMAC-SHA1
        String signature = Signer.signString(stringToSign, AccessKeySecret+"&");
//        System.out.println(signature);
        // 3.得到 authorization header
        String queryStringWithSign = null;
        try {
            queryStringWithSign = "Signature=" + percentEncode(signature) + "&" + queryString;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        this.queryString = queryStringWithSign;
    }

    public String getQueryString(){
        return this.queryString;
    }

    /**
     * 获取时间戳
     * 必须符合ISO8601规范，并需要使用UTC时间，时区为+0
     */

    private String getISO8601Time(Date date) {
        Date nowDate = date;
        if (null == date) {
            nowDate = new Date();
        }
        SimpleDateFormat df = new SimpleDateFormat(FORMAT_ISO8601);
        df.setTimeZone(new SimpleTimeZone(0, TIME_ZONE));
        return df.format(nowDate);
    }

    /**
     * 获取UUID
     */
    private String getUniqueNonce() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /***
     * 将参数排序后，进行规范化设置，组合成请求字符串
     * @param queryParamsMap   所有请求参数
     * @return 规范化的请求字符串
     */
    private String canonicalizedQuery(Map<String, String> queryParamsMap) {
        String[] sortedKeys = queryParamsMap.keySet().toArray(new String[] {});
        Arrays.sort(sortedKeys);
        String queryString = null;
        try {
            StringBuilder canonicalizedQueryString = new StringBuilder();
            for (String key : sortedKeys) {
                canonicalizedQueryString.append("&")
                        .append(percentEncode(key)).append("=")
                        .append(percentEncode(queryParamsMap.get(key)));
            }
            queryString = canonicalizedQueryString.toString().substring(1);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,"UTF-8 encoding is not supported.");
            e.printStackTrace();
        }
        return queryString;
    }

    /**
     * URL编码
     * 使用UTF-8字符集按照 RFC3986 规则编码请求参数和参数取值
     */
    private String percentEncode(String value) throws UnsupportedEncodingException {
        return value != null ? URLEncoder.encode(value, ENCODING).replace("+", "%20")
                .replace("*", "%2A").replace("%7E", "~") : null;
    }

    /***
     * 构造签名字符串
     * @param method       HTTP请求的方法
     * @param urlPath      HTTP请求的资源路径
     * @param queryString  规范化的请求字符串
     * @return 签名字符串
     */
    private String createStringToSign(String method, String urlPath, String queryString) {
        String stringToSign = null;
        try {
            StringBuilder strBuilderSign = new StringBuilder();
            strBuilderSign.append(method);
            strBuilderSign.append("&");
            strBuilderSign.append(percentEncode(urlPath));
            strBuilderSign.append("&");
            strBuilderSign.append(percentEncode(queryString));
            stringToSign = strBuilderSign.toString();
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,"UTF-8 encoding is not supported.");
            e.printStackTrace();
        }
        return stringToSign;
    }
}
