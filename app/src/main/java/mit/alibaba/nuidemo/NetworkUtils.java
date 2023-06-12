package mit.alibaba.nuidemo;

import com.google.gson.Gson;
import java.util.List;

public class NetworkUtils {

    private static final Gson gson = new Gson();

    public static Message parseResponse(String response) {
        APIResponse apiResponse = gson.fromJson(response, APIResponse.class);
//        System.out.println("工具类中的Response::::" + apiResponse.toString());
        if (apiResponse != null && apiResponse.getChoices() != null && !apiResponse.getChoices().isEmpty()) {
            Choice firstChoice = apiResponse.getChoices().get(0);
            if (firstChoice != null && firstChoice.getMessage() != null) {
                return firstChoice.getMessage();
            }
        }
        return null;
    }

    private static class BotResponse {
        private List<Choice> choices;

        public List<Choice> getChoices() {
            return choices;
        }

        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }

        @Override
        public String toString() {
            return "BotResponse{" +
                    "choices=" + choices.toString() +
                    '}';
        }
    }

}
