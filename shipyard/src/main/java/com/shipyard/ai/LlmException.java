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
 * LLM 调用异常 — adapter 抛, LlmService 接住落痕后转 {@code BusinessException} 给前端.
 *
 * <p>异常种类:
 * <ul>
 *   <li>{@code AUTH_FAILED} — provider API key 无效 (401/403), 启动时 {@code ShipyardAiProperties.validate()} 应该先拦掉</li>
 *   <li>{@code RATE_LIMITED} — provider 限流 (429), 业务上可以重试</li>
 *   <li>{@code NETWORK_ERROR} — 网络 / 超时, 业务上可以重试</li>
 *   <li>{@code INVALID_RESPONSE} — provider 返 200 但 body 解析失败 (malformed JSON), 不可重试</li>
 *   <li>{@code UNKNOWN} — 其他未知</li>
 * </ul>
 */
public class LlmException extends RuntimeException {

    public enum Kind {
        AUTH_FAILED,
        RATE_LIMITED,
        NETWORK_ERROR,
        INVALID_RESPONSE,
        UNKNOWN
    }

    private final LlmProvider provider;
    private final Kind kind;

    public LlmException(LlmProvider provider, Kind kind, String message) {
        super(message);
        this.provider = provider;
        this.kind = kind;
    }

    public LlmException(LlmProvider provider, Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.kind = kind;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public Kind getKind() {
        return kind;
    }
}
