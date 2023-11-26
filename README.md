# 前言
- 在使用ChatGPT的时候，发现输入prompt后，是使用流式的效果返回数据，给用户的是一个打字机的效果。查看其网络请求，发现这个接口的通响应类型是`text/event-stream`，一种基于EventStream的事件流。
![](https://z1.ax1x.com/2023/11/25/piwzPk8.png)
- 那么为什么要这样传输呢？从使用场景上来说，ChatGPT是一个基于深度学习的大型语言模型，处理自然语言需要大量的计算资源和时间，那么响应速度肯定是比一般业务要慢的，那么接口等待时间过长，显然也不合适，那么对于这种对话场景，采用SSE技术边计算边返回，避免用户因为等待时间过长而关闭页面。
- 前往我的博客访问体验更佳：https://cyborg2077.github.io/2023/11/25/ServerSendEvents/

# 概述
> SSE(Server Sent Event)，直译为服务器发送事件，也就是服务器主动发送事件，客户端可以获取到服务器发送的事件。
- 常见的HTTP交互方式主要是客户端发起请求，然后服务端响应，然后一次性请求完毕。但是在SSE的使用场景下，客户端发起请求，然后建立SSE连接一直保持，服务端就可以返回数据给客户端。
- SSE简单来说就是服务器主动向前端推送数据的一种技术，它是单向的。SSE适用于消息推送、监控等只需要服务端推送数据的场景中。

# 特点
- 服务端主动推送
    1. HTML5新标准，用于从服务端试试推送数据到浏览器端。
    2. 直接建立在当前HTTP连接上，本质上是一个HTTP长连接。

# SSE与WebSocket的区别
- SSE是单工的，只能由服务端想客户端发送消息，而WebSocket是双工的

|            SSE           |       WebScoket       |
|:------------------------:|:---------------------:|
|         http 协议        | 独立的 websocket 协议 |
|      轻量，使用简单      |        相对复杂       |
|     默认支持断线重连     |  需要自己实现断线重连 |
|         文本传输         |       二进制传输      |
| 支持自定义发送的消息类型 |           -           |

# SSE规范
- 在HTML5中，服务端SSE一般要遵循以下要求
    1. 请求头：开启长连接 + 流式传递
    ``` 
    Content-Type: text/event-stream;charset=UTF-8
    Cache-Control: no-cache
    Connection: keep-alive
    ```
    2. 数据格式：服务端发送的消息，由message组成，其格式如下
    ``` 
    field:value
    ```

# SSE实践
- 这里简单做一个时钟效果，有服务端主动推送当前时间数据给前端，前端页面接收后展示。

## SseEmitter类简介
- SpringBoot使用SseEmitter来支持SSE，并对SSE规范做了一些封装，使用起来非常简单。我们在操作SseEmitter对象时，只需要关注发送的消息文本即可。
- SseEmittter类的几个方法：
    1. send()：发送数据，如果传入的是一个非SseEventBuilder对象，那么传递参数会被封装到data中。
    2. complete()：表示执行完成，会断开连接（如果是一些轮询进度的任务，我们可以在任务进度完成时，主动断开连接）
    3. onTimeout()：连接超时时回调触发。
    4. onCompletion()：结束之后的回调触发。
    5. onError()：报错时的回调触发。

## 示例Demo
- 前端HTML
``` HTML
<html>
<body>
    <div id="msg_from_server"></div>
</body>
<script>
    const sse = new EventSource("http://localhost/sse/hello");
    sse.onmessage = function (event) {
        var eventVal = document.getElementById("msg_from_server");
        eventVal.innerHTML = event.data;
    };
</script>
</html>
```
- 后端接口
```
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
}
```
- 大功告成
![](https://z1.ax1x.com/2023/11/25/pi0EfLF.png)

## 注意事项
- 这里的协议是`http/1.1`，仅支持6个连接数（开第七个标签页的时候，就建立不了SSE连接了），而`HTTP/2`默认支持100个连接数，同时这里每30秒重新建立了一个新连接，这是SSE默认的连接超时时间，我们可以通过配置连接超时时间来达到不过期的目的，那么就需要我们在业务逻辑里`手动关闭连接`。
- 同时，每建立一个SSE连接的时候，都需要一个线程，那么这里就需要创建一个线程池来处理并发问题，同时也要根据自身的业务需求来做好压测。
![](https://z1.ax1x.com/2023/11/25/pi0VFQf.png)
- 但是`HTTP/2`仅支持`HTTPS`，我这里就不演示了，感兴趣的小伙伴可以去了解一下使用OpenSSL生成一个`自签名的SSL证书`

## 工具类封装
- 下面是我封装的一个简单的SseUtil
``` JAVA
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
    // 会话map, 方便管理连接数
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
```
- 还是用刚刚推送当前时间的例子，这里我们做一下主动关闭连接，我这里简单的逻辑就是遍历到一个整分，就停止推送
``` JAVA
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
```

# SSE实战
- 我这里也是在我项目里的轮询订单进度的时候尝试用了一下，因为我这个项目也是文本生成方向的，之前是前端定时轮询我这边的接口，现在换成我主动向前端推送数据，然后前端拿到数据自己解析内容就好了。这里用的工具类就是我刚刚封装的那个
- 这里是使用的前端传入的orderId作为会话ID
- Controller层
``` JAVA
@CrossOrigin
@GetMapping("/getOrderDetail")
public SseEmitter getOrderDetailById(String orderId, HttpServletResponse httpServletResponse) {
    httpServletResponse.setContentType("text/event-stream");
    httpServletResponse.setCharacterEncoding("utf-8");
    return orderService.getOrderDetailById(orderId, httpServletResponse);
}
```
- Service层
``` JAVA
// 简单来个线程池
ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

@Override
public SseEmitter getOrderDetailById(String orderId, HttpServletResponse httpServletResponse) {
    // 建立连接
    SseEmitter emitter = SseUtil.getConnect(orderId);
    executor.execute(() -> {
        while (true) {
            log.error("=========SSE轮询中=========");
            try {
                // 每5秒推送一次数据
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // 查询订单数据
            Torder torder = orderMapper.selectOne(Wrappers.lambdaQuery(Torder.class).eq(Torder::getOrderId, orderId));
            if (torder == null) {
                // 如果订单不存在，返回错误，主动断开连接
                SseUtil.sendMessage(orderId, JSON.toJSONString(ErrorCodeEnum.ORDER_ID_NOT_EXIST));
                SseUtil.removeClientId(orderId);
                break;
            }
            OrderDetailVO detailVO = new OrderDetailVO();
            detailVO.setIsExpire(stringRedisTemplate.opsForValue().get(orderId) == null);
            detailVO.setOrderId(orderId);
            detailVO.setCreateTime(torder.getCreateTime());
            detailVO.setOrderType(torder.getPolishType());
            detailVO.setAmount(torder.getAmount().doubleValue());
            // 根据不同的订单类型来封装不同的参数（这里为了满足产品的需求，想用一个接口显示不同种类订单的信息，用了SQL反模式设计数据库，导致代码很不优雅）
            if (torder.getOrderType() == 0) {
                Wrapper<Object> statusByOrderId = getStatusByOrderId(orderId);
                if (statusByOrderId.getCode() != 0) {
                    // 订单状态查询异常，返回错误，主动断开连接
                    SseUtil.sendMessage(orderId, JSON.toJSONString(ErrorCodeEnum.ASYNC_SERVICE_ERROR));
                    SseUtil.removeClientId(orderId);
                    break;
                }
                if (torder.getPolishType() == Common.POLISH_TYPE_WITH_PAPER) {
                    PaperStatusByOrderIdVO paperVO = (PaperStatusByOrderIdVO) statusByOrderId.getResult();
                    BeanUtils.copyProperties(paperVO, detailVO);
                    detailVO.setProgress(Double.valueOf(paperVO.getProgress()));
                    detailVO.setTitle(paperVO.getPaperTitle());
                    detailVO.setOrderStatus(paperVO.getStatus());
                } else {
                    TextStatusByOrderIdVO textVO = (TextStatusByOrderIdVO) statusByOrderId.getResult();
                    BeanUtils.copyProperties(textVO, detailVO);
                    detailVO.setProgress(Double.valueOf(textVO.getProgress()));
                    detailVO.setTitle(textVO.getPaperTitle());
                    detailVO.setOrderStatus(textVO.getStatus());
                }
            } else if (torder.getOrderType() == 1) {
                CheckpassOrder checkpassOrder = checkpassOrderMapper.selectOne(Wrappers.lambdaQuery(CheckpassOrder.class).eq(CheckpassOrder::getOrderId, orderId));
                CheckpassReport checkpassReport = checkpassReportMapper.selectOne(Wrappers.lambdaQuery(CheckpassReport.class).eq(CheckpassReport::getPaperId, checkpassOrder.getPaperId()));
                detailVO.setOrderStatus(checkpassOrder.getStatus());
                detailVO.setAuthor(checkpassReport.getAuthor());
                detailVO.setTitle(checkpassReport.getTitle());
                detailVO.setProgress(checkpassReport.getCopyPercent() == null ? 0 : checkpassReport.getCopyPercent());
                detailVO.setCheckVersion(CommonUtil.getCheckVersion(checkpassOrder.getJaneName()));
            }
            boolean flag = SseUtil.sendMessage(orderId, JSON.toJSONString(detailVO));
            if (!flag) {
                break;
            }
            if (torder.getStatus() == Common.ORDER_FINISH_STATUS) {
                // 订单完成，主动关闭连接
                try {
                    emitter.send(SseEmitter.event().reconnectTime(5000L).data("SSE关闭连接"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                SseUtil.removeClientId(orderId);
                break;
            }
        }
    });
    return emitter;
```
