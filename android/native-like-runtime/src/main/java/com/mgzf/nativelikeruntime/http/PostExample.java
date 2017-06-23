package com.mgzf.nativelikeruntime.http;

import android.annotation.TargetApi;
import android.os.Build;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by heruijun on 2017/6/22.
 */

public class PostExample {
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    @TargetApi(Build.VERSION_CODES.KITKAT)
    String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    String bowlingJson() {
        return "{\n" +
                "    \"head\": {\n" +
                "        \"appType\": 1,\n" +
                "        \"appVersion\": \"2.2.0\",\n" +
                "        \"channel\": \"房东个人版\",\n" +
                "        \"dataScope\": 1,\n" +
                "        \"key\": \"3d8bc1f97eb1f5f07de2110b72295290\",\n" +
                "        \"model\": \"NX505J\",\n" +
                "        \"orgId\": 0,\n" +
                "        \"os\": \"Android\",\n" +
                "        \"osVersion\": \"5.1.1\",\n" +
                "        \"regId\": \"\",\n" +
                "        \"userType\": 0,\n" +
                "        \"uuid\": \"14048365827cf71\"\n" +
                "    },\n" +
                "    \"para\": {\n" +
                "        \"idfa\": \"14048365827cf71\"\n" +
                "    }\n" +
                "}";
    }

    public static void main(String[] args) throws IOException {
        PostExample example = new PostExample();
        String json = example.bowlingJson();
        String response = example.post("http://192.168.60.104:8006/mogoroom-papp/common/getAppFinalPackage", json);
        System.out.println(response);
    }
}