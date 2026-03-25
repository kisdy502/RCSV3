package com.jizhi.api;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class NewUrlInterceptor implements Interceptor {
    private final DynamicUrlInterceptor delegate;

    public NewUrlInterceptor(String initialUrl) {
        this.delegate = new DynamicUrlInterceptor(initialUrl);
    }

    public void updateUrl(String newUrl) {
        delegate.updateUrl(newUrl);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        return delegate.intercept(chain);
    }
}

