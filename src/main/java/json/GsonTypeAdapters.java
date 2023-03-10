package json;

import com.google.gson.*;
import messages.*;
import remote.AggregatedRoute;
import remote.Route;

import java.lang.reflect.Type;

/**
 * Contains type adapters for Gson to serialize and deserialize messages.
 */
public class GsonTypeAdapters {
    /**
     * Serializes a Route to JSON.
     */
    public static class RouteSerializer implements JsonSerializer<Route> {
        @Override
        public JsonElement serialize(Route route, Type type, JsonSerializationContext jsonSerializationContext) {
            Gson gson = new Gson();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("network", route.network);
            jsonObject.addProperty("netmask", route.getNetmask());
            jsonObject.addProperty("peer", route.nextHop);
            jsonObject.addProperty("localpref", route.localpref);
            jsonObject.add("ASPath", gson.toJsonTree(route.ASPath).getAsJsonArray());
            jsonObject.addProperty("selfOrigin", route.selfOrigin);
            jsonObject.addProperty("origin", route.origin.toString());
            return jsonObject;
        }
    }

    /**
     * Serializes a Message to JSON.
     */
    public static class MessageSerializer implements JsonSerializer<Message> {
        @Override
        public JsonElement serialize(Message message, Type type, JsonSerializationContext jsonSerializationContext) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Route.class, new RouteSerializer());
            builder.registerTypeAdapter(AggregatedRoute.class, new RouteSerializer());
            Gson gson = builder.create();
            String json = gson.toJson(message);
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            if (message.msg == null) {
                jsonObject.add("msg", new JsonObject());
            }
            if (message.getType().equals(Message.MessageType.noRoute)) {
                jsonObject.addProperty("type", "no route");
            }
            return jsonObject;
        }
    }

    /**
     * Deserializes an Update Message from JSON.
     */
    public static class UpdateMessageDeserializer implements JsonDeserializer<UpdateMessage> {
        @Override
        public UpdateMessage deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            Gson gson = new Gson();

            UpdateMessage updateMessage = gson.fromJson(jsonElement, UpdateMessage.class);
            updateMessage.msg = gson.fromJson(jsonElement.getAsJsonObject().get("msg"), UpdateMessage.UpdateParams.class);

            return updateMessage;
        }
    }

    /**
     * Deserializes a Withdraw Message from JSON.
     */
    public static class WithdrawMessageDeserializer implements JsonDeserializer<WithdrawMessage> {
        @Override
        public WithdrawMessage deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            Gson gson = new Gson();

            WithdrawMessage withdrawMessage = gson.fromJson(jsonElement, WithdrawMessage.class);
            withdrawMessage.msg = gson.fromJson(jsonElement.getAsJsonObject().get("msg"), WithdrawMessage.WithdrawNetwork[].class);

            return withdrawMessage;
        }
    }

    /**
     * Deserializes a Message from JSON.
     */
    public static class MessageDeserializer implements JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(UpdateMessage.class, new UpdateMessageDeserializer());
            builder.registerTypeAdapter(WithdrawMessage.class, new WithdrawMessageDeserializer());

            Gson gson = builder.create();
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String messageType = jsonObject.get("type").getAsString();
            switch (messageType) {
                case "update":
                    return gson.fromJson(jsonElement, UpdateMessage.class);
                case "data":
                    return gson.fromJson(jsonElement, DataMessage.class);
                case "dump":
                    return gson.fromJson(jsonElement, DumpMessage.class);
                case "withdraw":
                    return gson.fromJson(jsonElement, WithdrawMessage.class);
                default:
                    return gson.fromJson(jsonElement, Message.class);
            }
        }
    }
}
