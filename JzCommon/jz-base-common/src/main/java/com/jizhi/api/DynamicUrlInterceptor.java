package com.jizhi.api;


import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class DynamicUrlInterceptor implements Interceptor {
    private volatile String baseUrl;
    private final Object lock = new Object();

    public DynamicUrlInterceptor(String initialUrl) {
        this.baseUrl = initialUrl;
    }

    @SuppressWarnings("KotlinInternalInJava")
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        synchronized (lock) {
            HttpUrl originalHttpUrl = originalRequest.url();
            HttpUrl newBaseUrl = HttpUrl.get(baseUrl);

            if (newBaseUrl == null) {
                throw new IOException("Malformed URL: " + baseUrl);
            }

            // 构建新URL，保留原始请求的路径和查询参数
            HttpUrl newUrl = newBaseUrl.newBuilder()
                    .encodedPath(originalHttpUrl.encodedPath())
                    .encodedQuery(originalHttpUrl.encodedQuery())
                    .build();

            Request.Builder builder = originalRequest.newBuilder()
                    .url(newUrl)
                    .method(originalRequest.method(), originalRequest.body());

            // 修正后的请求头处理方式
            for (String name : originalRequest.headers().names()) {
                builder.addHeader(name, originalRequest.header(name));
            }

            return chain.proceed(builder.build());
        }
    }

    public void updateUrl(String newUrl) {
        synchronized (lock) {
            this.baseUrl = newUrl.endsWith("/") ?
                    newUrl.substring(0, newUrl.length() - 1) : newUrl;
        }
    }
}



