package json;

import com.google.gson.*;
import messages.DataMessage;
import messages.Message;
import messages.UpdateMessage;

import java.lang.reflect.Type;

public class GsonTypeAdapters {
    public static class MessageSerializer implements JsonSerializer<Message> {
        @Override
        public JsonElement serialize(Message message, Type type, JsonSerializationContext jsonSerializationContext) {
            Gson gson = new Gson();
            String json = gson.toJson(message);
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            if (message.msg == null) {
                jsonObject.add("msg", new JsonObject());
            }
            return jsonObject;
        }
    }

    public static class UpdateMessageDeserializer implements JsonDeserializer<UpdateMessage> {
        @Override
        public UpdateMessage deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            Gson gson = new Gson();

            UpdateMessage updateMessage = gson.fromJson(jsonElement, UpdateMessage.class);
            updateMessage.msg = gson.fromJson(jsonElement.getAsJsonObject().get("msg"), UpdateMessage.UpdateParams.class);

            return updateMessage;
        }
    }

    public static class MessageDeserializer implements JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(UpdateMessage.class, new UpdateMessageDeserializer());

            Gson gson = builder.create();
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String messageType = jsonObject.get("type").getAsString();
            switch (messageType) {
                case "update":
                    return gson.fromJson(jsonElement, UpdateMessage.class);
                case "data":
                    return gson.fromJson(jsonElement, DataMessage.class);
                default:
                    return gson.fromJson(jsonElement, Message.class);
            }
        }
    }
}
