package com.acme.json.helper.core.parser;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * JWT解析器<br/>
 * JWT转JSON
 * @author 拒绝者
 * @date 2025-04-27
 */
public class JwtParser {
    /**
     * Base64解码器
     */
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * 支持多密钥的JWT转换
     * @param token 令牌
     * @return 异步JSON结果
     */
    public static CompletableFuture<String> convert(final String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Opt.ofBlankAble(token)
                        .map(JWT::decode).filter(Objects::nonNull)
                        .map(JwtParser::convertToJson).filter(StrUtil::isNotEmpty)
                        .orElse("");
            } catch (final JWTDecodeException e) {
                return "";
            }
        });
    }

    /**
     * 转换为JSON
     * @param decode 解码后的JWT
     * @return JSON字符串
     */
    private static String convertToJson(final DecodedJWT decode) {
        return JSONObject.of(
                "token", decode.getToken(),
                "signature", decode.getSignature(),
                "header", decode(decode.getHeader()),
                "payload", decode(decode.getPayload())
        ).toJSONString();
    }

    /**
     * 解码Base64 JSON
     * @param base64Str Base64字符串
     * @return 解码后的JSON对象
     */
    private static Object decode(final String base64Str) {
        return Opt.ofBlankAble(base64Str)
                .map(item -> StrUtil.utf8Str(DECODER.decode(item))).filter(JSON::isValid)
                .map(JSON::parse).filter(Objects::nonNull).orElse("");
    }
}