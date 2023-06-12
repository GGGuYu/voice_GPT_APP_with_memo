/*
 * 测试demo入口
 */

package mit.alibaba.nuidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = "Main";
    private static boolean haveAuth = false;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(R.id.activity_list);
        ArrayList<HashMap<String, Object>> listItems = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> item = new HashMap<String, Object>();
        TextView versionView = (TextView)findViewById(R.id.versionView);
        Button chatButton = findViewById(R.id.chat);


        versionView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);



        chatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this , ChatActivity.class);
                startActivity(intent);
                finish();
            }
        });

        final String demoVersion = "Demo Version: " + getGitRevision();
        Log.i(TAG, demoVersion);

        item = new HashMap<String, Object>();
        item.put("activity_name", "一句话识别");
        item.put("activity_class", SpeechRecognizerActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", "唤醒识别(暂不对外)");
        item.put("activity_class", WakeupAndSpeechRecognizerActivity.class);
        listItems.add(item);

        item = new HashMap<String, Object>();
        item.put("activity_name", "实时转写");
        item.put("activity_class", SpeechTranscriberActivity.class);
        listItems.add(item);


        item = new HashMap<>();
        item.put("activity_name","语音合成");
        item.put("activity_class", TtsBasicActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name","离线语音合成");
        item.put("activity_class", TtsLocalActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name","文件转写");
        item.put("activity_class", FileTranscriberActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name","本地一句话识别(暂不对外)");
        item.put("activity_class", LocalSpeechRecognizerActivity.class);
        listItems.add(item);

//        item = new HashMap<String, Object>();
//        item.put("activity_name", "对话");
//        item.put("activity_class", DialogActivity.class);
//        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name","实时转写独立接口样例(暂不对外)");
        item.put("activity_class", SpeechTranscriberNewActivity.class);
        listItems.add(item);

        item = new HashMap<>();
        item.put("activity_name","一句话识别独立接口样例(暂不对外)");
        item.put("activity_class", SpeechRecognizerNewActivity.class);
        listItems.add(item);

        SimpleAdapter adapter = new SimpleAdapter(this, listItems, R.layout.list_item,
                new String[] { "activity_name" }, new int[] { R.id.text_item });

        listView.setAdapter(adapter);
        listView.setDividerHeight(2);
        listView.setOnItemClickListener(this);

        versionView.setText(
                demoVersion + "\n进入具体示例后，有弹窗提示内部SDK版本号\n");
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Map<?, ?> map = (HashMap<?, ?>) parent.getAdapter().getItem(position);
        Class<?> clazz = (Class<?>) map.get("activity_class");
        Intent it = new Intent(this, clazz);
        this.startActivity(it);
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private String getGitRevision(){
        return BuildConfig.gitVersionId;
    }
}
