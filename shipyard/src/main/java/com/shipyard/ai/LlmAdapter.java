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

/**
 * LLM Provider 适配器 — 跨 Mock / Tongyi / DeepSeek 的统一接口.
 *
 * <p>V1 实现:
 * <ul>
 *   <li>{@link MockLlmAdapter} — V1 demo 默认, 离线 canned data, 无需 env var</li>
 *   <li>{@link TongyiLlmAdapter} — 阿里云通义千问 (DashScope SDK), M6 2 留接口骨架, M12 接真</li>
 *   <li>{@link DeepseekLlmAdapter} — DeepSeek (OpenAI 协议), M6 2 留接口骨架, M12 接真</li>
 * </ul>
 *
 * <p>调用方 (例 {@code LlmService}) 通过 {@code @ConditionalOnProperty} 或手动 lookup
 * 拿到具体 adapter, 不直接 new. 3 个 adapter 都是 {@code @Component}, 通过
 * {@code provider()} 方法自报家门, LlmService 路由.
 */
public interface LlmAdapter {

    /**
     * 同步 LLM 调用 — 阻塞等响应.
     *
     * <p>异常语义:
     * <ul>
     *   <li>{@link LlmException} — 调 LLM 失败 (网络 / 鉴权 / 限流), LlmService 会落痕 + 重抛</li>
     *   <li>其他 RuntimeException — adapter 自身 bug, LlmService 也会落痕 (但只记 error 不记 response)</li>
     * </ul>
     *
     * @param request 调用请求 (含 capability / prompt / model / provider)
     * @return 统一格式的响应
     * @throws LlmException 调 LLM 失败时抛
     */
    LlmResponse complete(LlmRequest request) throws LlmException;

    /**
     * 自报 provider — 用于 {@code LlmService} 路由.
     */
    LlmProvider provider();
}
