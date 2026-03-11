package com.timemap.util;

import com.timemap.config.WxConfig;
import com.timemap.model.dto.WxAccessTokenResponse;
import com.timemap.model.dto.WxPhoneNumberResponse;
import com.timemap.model.dto.WxSessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WxApiUtil {

    private final WxConfig wxConfig;
    private final RestTemplate restTemplate;

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";
    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
    private static final String PHONE_NUMBER_URL =
            "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={accessToken}";

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt;

    public WxSessionResponse code2Session(String code) {
        WxSessionResponse response = restTemplate.getForObject(
                CODE2SESSION_URL,
                WxSessionResponse.class,
                wxConfig.getAppId(),
                wxConfig.getAppSecret(),
                code
        );

        if (response == null || !response.isSuccess()) {
            String errMsg = response != null ? response.getErrmsg() : "微信接口无响应";
            throw new RuntimeException("微信登录失败: " + errMsg);
        }

        return response;
    }

    public WxPhoneNumberResponse getPhoneNumber(String code) {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(body, headers);

        try {
            WxPhoneNumberResponse response = restTemplate.postForObject(
                    PHONE_NUMBER_URL,
                    httpEntity,
                    WxPhoneNumberResponse.class,
                    accessToken
            );

            if (response == null || !response.isSuccess() || response.getPhoneInfo() == null) {
                String errMsg = response != null ? response.getErrmsg() : "微信接口无响应";
                log.error("获取手机号失败: errcode={}, errmsg={}", response != null ? response.getErrcode() : null, errMsg);
                throw new RuntimeException("获取手机号失败: " + errMsg);
            }

            return response;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            log.warn("微信 getuserphonenumber 返回 {}: {}", status, body != null && !body.isEmpty() ? body : "[no body]");
            if (status == 412) {
                throw new RuntimeException("当前无法获取手机号。请确认：1) 小程序已企业认证并开通「手机号快速验证」；2) 在真机上授权（开发工具/模拟器的 code 可能无效）。");
            }
            throw new RuntimeException("获取手机号失败，请稍后重试");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("获取手机号失败")) {
                throw e;
            }
            log.error("调用微信获取手机号接口异常", e);
            throw new RuntimeException("获取手机号失败，请稍后重试");
        }
    }

    public synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && accessTokenExpiresAt != null && now.isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }

        try {
            WxAccessTokenResponse response = restTemplate.getForObject(
                    ACCESS_TOKEN_URL,
                    WxAccessTokenResponse.class,
                    wxConfig.getAppId(),
                    wxConfig.getAppSecret()
            );

            if (response == null || !response.isSuccess() || response.getAccessToken() == null) {
                String errMsg = response != null ? response.getErrmsg() : "微信接口无响应";
                log.error("获取 access_token 失败: errcode={}, errmsg={}", response != null ? response.getErrcode() : null, errMsg);
                throw new RuntimeException("获取微信 access_token 失败: " + errMsg);
            }

            long expiresIn = response.getExpiresIn() != null ? response.getExpiresIn() : 7200L;
            cachedAccessToken = response.getAccessToken();
            accessTokenExpiresAt = now.plusSeconds(Math.max(60, expiresIn - 300));
            return cachedAccessToken;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("获取微信 access_token")) {
                throw e;
            }
            log.error("调用微信 access_token 接口异常", e);
            throw new RuntimeException("获取微信 access_token 失败，请稍后重试");
        }
    }
}
