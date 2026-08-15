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

package com.shipyard.worker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Worker token 响应 — 一次性返明文, 用户复制到 k8s manifest.
 *
 * <p>只在以下场景返明文:
 * <ul>
 *   <li>POST /api/envs/{envId}/workers (创建时生成 token)</li>
 *   <li>POST /api/workers/{id}/regenerate-token (重新生成 token)</li>
 * </ul>
 *
 * <p>前端拿到后必须提示用户 "请立即复制, 此 token 只展示一次", 之后查 worker 详情
 * 只返 hasToken=true / false, 不返明文.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTokenResponse {

    /** worker ID. */
    private Long workerId;

    /** worker 名 (展示用). */
    private String name;

    /** token 明文 (base64, 32 字节随机). 一次性展示, 之后无法再次获取. */
    private String token;

    /** 提示语 (前端展示给用户, 提醒 "立即复制"). */
    private String notice;
}
