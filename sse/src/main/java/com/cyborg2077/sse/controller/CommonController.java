package com.cyborg2077.sse.controller;

import com.cyborg2077.sse.util.SseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/sse")
public class CommonController {
    @GetMapping("/hello")
    public SseEmitter helloworld(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("utf-8");
        SseEmitter sseEmitter = new SseEmitter();
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(1000L);
                    sseEmitter.send(SseEmitter.event().data(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                }
            } catch (Exception e) {
                log.error("Error in SSE: {}", e.getMessage());
                sseEmitter.completeWithError(e);
            }
        }).start();
        return sseEmitter;
    }

    @GetMapping("/demo")
    public SseEmitter timeStamp(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("utf-8");
        // 生成会话ID
        String conversationId = "123456";
        // 建立连接
        SseEmitter sseEmitter = SseUtil.getConnect(conversationId);
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(1000L);
                    // 向会话发送消息
                    String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    SseUtil.sendMessage(conversationId, timeStamp);
                    if (timeStamp.endsWith("00")) {
                        SseUtil.removeClientId(conversationId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error in SSE: {}", e.getMessage());
                sseEmitter.completeWithError(e);
            }
        }).start();
        return sseEmitter;
    }
}
