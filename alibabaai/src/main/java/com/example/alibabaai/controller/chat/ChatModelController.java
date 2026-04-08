package com.example.alibabaai.controller.chat;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.swagger.annotations.Api;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/model")
@Api(tags = "聊天（model）")
public class ChatModelController {
    private static final String DEFAULT_PROMPT = "你好，介绍下你自己吧。";

    private final ChatModel dashScopeChatModel;

    public ChatModelController(ChatModel chatModel) {
        this.dashScopeChatModel = chatModel;
    }

    @GetMapping("/simple/chat")
    public String simpleChat(@RequestParam(value = "input", required = false) String input) {

        String prompt = (input == null || input.isBlank()) ? DEFAULT_PROMPT : input;

        return dashScopeChatModel.call(new Prompt(prompt, DashScopeChatOptions
                .builder()
                .withModel(DashScopeApi.ChatModel.QWEN_PLUS.getModel())
                .build())).getResult().getOutput().getText();
    }
    /**
     * Stream 流式调用。可以使大模型的输出信息实现打字机效果。
     * @return Flux<String> types.
     */
    @GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam(value = "input", required = false) String input,
                                   HttpServletResponse response) {

        String prompt = (input == null || input.isBlank()) ? DEFAULT_PROMPT : input;

        // 避免返回乱码
        response.setCharacterEncoding("UTF-8");

        Flux<ChatResponse> stream = dashScopeChatModel.stream(new Prompt(prompt, DashScopeChatOptions
                .builder()
                .withModel(DashScopeApi.ChatModel.QWEN_PLUS.getModel())
                .build()));
        return stream.map(resp -> {
            if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) {
                return "";
            }
            String text = resp.getResult().getOutput().getText();
            return text == null ? "" : text;
        });
    }

    /**
     * 演示如何获取 LLM 得 token 信息
     */
    @GetMapping("/tokens")
    public Map<String, Object> tokens(@RequestParam(value = "input", required = false) String input) {

        String prompt = (input == null || input.isBlank()) ? DEFAULT_PROMPT : input;

        ChatResponse chatResponse = dashScopeChatModel.call(new Prompt(prompt, DashScopeChatOptions
                .builder()
                .withModel(DashScopeApi.ChatModel.QWEN_PLUS.getModel())
                .build()));

        Map<String, Object> res = new HashMap<>();
        res.put("output", chatResponse.getResult().getOutput().getText());
        res.put("output_token", chatResponse.getMetadata().getUsage().getCompletionTokens());
        res.put("input_token", chatResponse.getMetadata().getUsage().getPromptTokens());
        res.put("total_token", chatResponse.getMetadata().getUsage().getTotalTokens());

        return res;
    }

    /**
     * 使用编程方式自定义 LLMs ChatOptions 参数， {@link com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions}
     * 优先级高于在 application.yml 中配置的 LLMs 参数！
     */
    @GetMapping("/custom/chat")
    public String customChat(@RequestParam(value = "input", required = false) String input,
                             @RequestParam(value = "topP", required = false, defaultValue = "0.7") Double topP,
                             @RequestParam(value = "topK", required = false, defaultValue = "50") Integer topK,
                             @RequestParam(value = "temperature", required = false, defaultValue = "0.8") Double temperature,
                             @RequestParam(value = "model", required = false, defaultValue = "qwen-plus") String model) {

        String prompt = (input == null || input.isBlank()) ? DEFAULT_PROMPT : input;

        DashScopeChatOptions customOptions = DashScopeChatOptions.builder()
                .withModel(model)
                .withTopP(topP)      //核采样，通常用于控制模型生成时的随机性，越小越明确，可能会缺乏多样性，越大越随机
                .withTopK(topK)       //前 K 采样,表示从概率分布中选择概率最高的前 K 个词进行采样
                .withTemperature(temperature) //控制随机性，temperature 值较低（接近 0），模型会更加倾向于选择概率最高的词，生成的内容更加确定和精准。
                .build();

        return dashScopeChatModel.call(new Prompt(prompt, customOptions)).getResult().getOutput().getText();
    }

}
