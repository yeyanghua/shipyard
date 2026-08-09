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
 * 阿里云通义千问 LLM Adapter — V1 留接口骨架, V1.5/M12 接真实 DashScope SDK.
 *
 * <p>V1 demo 不实现, 调用时直接抛 {@link LlmException} (kind=NETWORK_ERROR + "not implemented"),
 * 跟网络错一样的兜底路径, 不影响 ai_interaction 落痕.
 *
 * <p>为啥不直接 throw {@code UnsupportedOperationException}? — LlmService 统一 catch
 * {@link LlmException} 落痕, 用别的异常会跳过落痕路径.
 *
 * <p>V1.5 接入步骤 (备忘):
 * <ol>
 *   <li>加 dep: {@code com.alibaba:dashscope-sdk-java:1.x}</li>
 *   <li>注入 {@code com.alibaba.dashscope.aigc.generation.Generation}</li>
 *   <li>读 env {@code TONGYI_API_KEY} (启动时 {@code ShipyardAiProperties.validate()} 已校验)</li>
 *   <li>把 LlmRequest 转 DashScope 的 {@code QwenParam}, 调 {@code generation.call(param)}</li>
 *   <li>解析 {@code GenerationResult.getOutput().getChoices()} 取 content</li>
 *   <li>包成 {@link LlmResponse} 返, 含 prompt/completion tokens (DashScope 返 usage)</li>
 * </ol>
 */
@Slf4j
@Component
public class TongyiLlmAdapter implements LlmAdapter {

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        log.warn("[TongyiLlm] V1 demo 不实现通义千问真实调用, 请配 default-provider=mock");
        throw new LlmException(
                LlmProvider.TONGYI,
                LlmException.Kind.NETWORK_ERROR,
                "TongyiLlmAdapter V1 demo 未实现真实 HTTP 调用. "
                        + "M12 才接 DashScope SDK. 当前先改 shipyard.ai.default-provider=mock");
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.TONGYI;
    }
}
