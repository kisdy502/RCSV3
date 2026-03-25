package com.jizhi.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface RmsApi {
    @GET("/task_node/get_task_node_list")
    @Headers("Content-Type:application/json")
    Call<ResponseBody> getTaskNodeList(@Query("mapId") int mapId);
}

