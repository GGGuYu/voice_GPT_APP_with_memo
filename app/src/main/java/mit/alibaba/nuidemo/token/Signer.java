package mit.alibaba.nuidemo.token;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Code from Aliyun POP core SDK.
 * Just replace javax.xml.bind.DatatypeConverter with android.util.Base64
 *
 * @author xuebin
 */
public class Signer {

    private static final String ALGORITHM_NAME = "HmacSHA1";
    public static final String ENCODING = "UTF-8";

    public static String signString(String stringToSign, String accessKeySecret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM_NAME);
            mac.init(new SecretKeySpec(
                accessKeySecret.getBytes(ENCODING),
                ALGORITHM_NAME
            ));
            byte[] signData = mac.doFinal(stringToSign.getBytes(ENCODING));
            String encodeToString = Base64.encodeToString(signData, Base64.DEFAULT);
            return encodeToString.substring(0, encodeToString.length()-1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e.toString());
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    public String getSignerName() {
        return "HMAC-SHA1";
    }

    public String getSignerVersion() {
        return "1.0";
    }

    public String getSignerType() {
        return null;
    }


    /**
     * 等同于javaScript中的 new Date().toUTCString();
     */
    public static String toGMTString() {
        return toGMTString(new Date());
    }

    private static String toGMTString(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.UK);
        df.setTimeZone(new SimpleTimeZone(0, "GMT"));
        String text = df.format(date);
        if (!text.endsWith("GMT")){
            // delete +00:00 from the end of string: Sun, 30 Sep 2018 08:42:06 GMT+00:00
            text = text.substring(0, text.length()-6);
        }
        return text;
    }
}
