/*
 * Copyright 2026 The shipyard Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipyard.ai;

import com.shipyard.common.enums.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DeepSeek LLM Adapter — V1 留接口骨架, V1.5/M12 接真实 OpenAI 协议 HTTP 调用.
 *
 * <p>DeepSeek 走 OpenAI 协议 (跟 ChatGPT 一样的 API shape), 可以用 OpenAI Java SDK.
 *
 * <p>V1 demo 不实现, 调用时直接抛 {@link LlmException}, 跟 {@code TongyiLlmAdapter} 同样思路.
 *
 * <p>V1.5 接入步骤 (备忘):
 * <ol>
 *   <li>加 dep: {@code com.openai:openai-java:3.x} (或用 {@code java.net.http.HttpClient} 手写)</li>
 *   <li>读 env {@code DEEPSEEK_API_KEY} (启动时 {@code ShipyardAiProperties.validate()} 已校验)</li>
 *   <li>base URL: {@code https://api.deepseek.com/v1/chat/completions}</li>
 *   <li>把 LlmRequest 转 OpenAI 的 {@code ChatCompletionRequest}, POST 上去</li>
 *   <li>解析 {@code choices[0].message.content} 取 content</li>
 *   <li>包成 {@link LlmResponse} 返, 含 prompt/completion tokens (OpenAI 协议返 usage)</li>
 * </ol>
 */
@Slf4j
@Component
public class DeepseekLlmAdapter implements LlmAdapter {

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        log.warn("[DeepseekLlm] V1 demo 不实现 DeepSeek 真实调用, 请配 default-provider=mock");
        throw new LlmException(
                LlmProvider.DEEPSEEK,
                LlmException.Kind.NETWORK_ERROR,
                "DeepseekLlmAdapter V1 demo 未实现真实 HTTP 调用. "
                        + "M12 才接 OpenAI 协议 SDK. 当前先改 shipyard.ai.default-provider=mock");
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.DEEPSEEK;
    }
}
