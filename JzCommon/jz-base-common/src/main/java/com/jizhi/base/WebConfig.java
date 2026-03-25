package com.jizhi.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * @author Andon
 * 2021/12/29
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private Environment environment;

    /**
     * 发现如果继承了WebMvcConfigurationSupport，则在yml中配置的相关内容会失效。 需要重新指定静态资源
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取所有激活的Profile
        String[] activeProfiles = environment.getActiveProfiles();

        // 判断是否包含目标Profile
        boolean isDev = environment.acceptsProfiles("dev");
        boolean isProd = environment.acceptsProfiles("prod");
        if(isProd){
            //配置静态资源的缓存策略
            registry.addResourceHandler("/js/**", "/css/**")
                    .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                    .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        }else {
            registry.addResourceHandler("/js/**", "/css/**")
                    .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                    .setCacheControl(CacheControl.noStore());
        }
    }

}

