package mit.alibaba.nuidemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class MessageAdapter extends ArrayAdapter<Message> {

    private Context mContext;
    private ArrayList<Message> mMessages;

    public MessageAdapter(Context context, ArrayList<Message> messages) {
        super(context, 0, messages);
        mContext = context;
        mMessages = messages;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.message_item, parent, false);
        }

        TextView contentTextView = convertView.findViewById(R.id.content_text_view);

        Message message = mMessages.get(position);

        if (message.getType() == Message.USER_MESSAGE) {
            // 用户消息
            contentTextView.setBackgroundResource(R.drawable.user_message_background);
        } else {
            // 机器人消息
            contentTextView.setBackgroundResource(R.drawable.bot_message_background);
        }

        contentTextView.setText(message.getContent());

        return convertView;
    }
}
