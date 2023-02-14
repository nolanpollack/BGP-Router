package messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import json.GsonTypeAdapters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UpdateMessageTest {

    @BeforeEach
    void setUp() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(UpdateMessage.class, new GsonTypeAdapters.UpdateMessageDeserializer());
        Gson gson = builder.create();
        String json = "{'type': 'update', 'src': '192.168.0.2', 'dst': '192.168.0.1', 'msg': {'network': '192.168.0.0', 'netmask': '255.255.255.0', 'localpref': 100, 'ASPath': [1], 'origin': 'EGP', 'selfOrigin': True}}";
        UpdateMessage message = gson.fromJson(json, UpdateMessage.class);
        System.out.println(message);
    }

    @Test
    void testToString() {

    }
}