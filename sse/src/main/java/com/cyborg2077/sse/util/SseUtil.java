package com.cyborg2077.sse.util;



import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SseUtil {
    // timeout -> 0表示不过期，默认是30秒，超过时间未完成（断开）会抛出异常
    private static final Long DEFAULT_TIME_OUT = 0L;
    // 会话map
    private static Map<String, SseEmitter> conversationMap = new ConcurrentHashMap<>();

    /**
     * 建立连接
     *
     * @param conversationId - 会话Id
     * @return
     */
    public static SseEmitter getConnect(String conversationId) {
        // 创建SSE
        SseEmitter sseEmitter = new SseEmitter(DEFAULT_TIME_OUT);
        // 异常
        try {
            // 设置前端重试时5s
            sseEmitter.send(SseEmitter.event().reconnectTime(5_000L).data("SSE建立成功"));
            // 连接超时
            sseEmitter.onTimeout(() -> SseUtil.timeout(conversationId));
            // 连接断开
            sseEmitter.onCompletion(() -> SseUtil.completion(conversationId));
            // 错误
            sseEmitter.onError((e) -> SseUtil.error(conversationId, e.getMessage()));
            // 添加sse
            conversationMap.put(conversationId, sseEmitter);
            // 连接成功
            log.info("创建sse连接成功 ==> 当前连接总数={}， 会话Id={}", conversationMap.size(), conversationId);
        } catch (IOException e) {
            // 日志
            log.error("前端重连异常 ==> 会话Id={}, 异常信息={}", conversationId, e.getMessage());
        }
        // 返回
        return sseEmitter;
    }

    /***
     * 获取消息实例
     *
     * @param conversationId - 会话Id
     * @return
     */
    public static SseEmitter getInstance(String conversationId) {
        return conversationMap.get(conversationId);
    }

    /***
     * 断开连接
     *
     * @param conversationId - 会话Id
     * @return
     */
    public static void disconnect(String conversationId) {
        SseUtil.getInstance(conversationId).complete();
    }

    /**
     * 给指定会话发送消息，如果发送失败，返回false
     *
     * @param conversationId - 会话Id
     * @param jsonMsg        - 消息
     */
    public static boolean sendMessage(String conversationId, String jsonMsg) {
        // 判断该会话是否已建立连接
        // 已建立连接
        if (SseUtil.getIsExistClientId(conversationId)) {
            try {
                // 发送消息
                SseUtil.getInstance(conversationId).send(jsonMsg, MediaType.APPLICATION_JSON);
                return true;
            } catch (IOException e) {
                // 日志
                SseUtil.removeClientId(conversationId);
                log.error("发送消息异常 ==> 会话Id={}, 异常信息={}", conversationId, e.getMessage());
                return false;
            }
        } else {
            // 未建立连接
            log.error("连接不存在或者超时 ==> 会话Id={}会话自动关闭", conversationId);
            SseUtil.removeClientId(conversationId);
            return false;
        }
    }

    /**
     * 移除会话Id
     *
     * @param conversationId - 会话Id
     */
    public static void removeClientId(String conversationId) {
        // 不存在存在会话
        if (!SseUtil.getIsExistClientId(conversationId)) {
            return;
        }
        // 删除该会话
        conversationMap.remove(conversationId);
        // 日志
        log.info("移除会话成功 ==> 会话Id={}", conversationId);
    }

    /**
     * 获取是否存在会话
     *
     * @param conversationId - 会话Id
     */
    public static boolean getIsExistClientId(String conversationId) {
        return conversationMap.containsKey(conversationId);
    }

    /**
     * 获取当前连接总数
     *
     * @return - 连接总数
     */
    public static int getConnectTotal() {
        log.error("当前连接数：{}", conversationMap.size());
        for (String s : conversationMap.keySet()) {
            log.error("输出SSE-Map：{}", conversationMap.get(s));
        }
        return conversationMap.size();
    }

    /**
     * 超时
     *
     * @param conversationId String 会话Id
     */
    public static void timeout(String conversationId) {
        // 日志
        log.error("sse连接超时 ==> 会话Id={}", conversationId);
        // 移除会话
        SseUtil.removeClientId(conversationId);
    }

    /**
     * 完成
     *
     * @param conversationId String 会话Id
     */
    public static void completion(String conversationId) {
        // 日志
        log.info("sse连接已断开 ==> 会话Id={}", conversationId);
        // 移除会话
        SseUtil.removeClientId(conversationId);
    }

    /**
     * 错误
     *
     * @param conversationId String 会话Id
     */
    public static void error(String conversationId, String message) {
        // 日志
        log.error("sse服务异常 ==> 会话Id={}, 异常信息={}", conversationId, message);
        // 移除会话
        SseUtil.removeClientId(conversationId);
    }


}
