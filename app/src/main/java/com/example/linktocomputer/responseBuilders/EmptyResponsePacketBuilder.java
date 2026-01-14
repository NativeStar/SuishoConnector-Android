package com.example.linktocomputer.responseBuilders;

import com.example.linktocomputer.jsonClass.MainServiceJson;
import com.google.gson.JsonObject;

public class EmptyResponsePacketBuilder {
    public static JsonObject buildEmptyResponsePacket(MainServiceJson request){
        JsonObject object=new JsonObject();
        object.addProperty("_isResponsePacket",true);
        object.addProperty("_responseId",request._request_id);
        return object;
    }
}
