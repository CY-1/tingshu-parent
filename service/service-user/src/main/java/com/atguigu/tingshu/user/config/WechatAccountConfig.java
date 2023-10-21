package com.atguigu.tingshu.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.login")
public class WechatAccountConfig {
    //  公众平台的appdId
    private String appId;
    //  小程序微信公众平台秘钥
    private String appSecret;
}