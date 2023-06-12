package mit.alibaba.nuidemo;

public class Message {

    public static final int USER_MESSAGE = 0;
    public static final int BOT_MESSAGE = 1;

    private int type;
    private String content;

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", content='" + content + '\'' +
                '}';
    }

    public Message(int type, String content) {
        this.type = type;
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}


