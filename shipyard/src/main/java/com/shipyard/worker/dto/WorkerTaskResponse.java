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

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Worker 端 deploy/rollback/scale/stop/getManifest 5 端点统一响应.
 *
 * <p>worker 返 {code, message, data} 业务码体系, shipyard 后端把 data 解析成
 * 这个对象统一处理.
 */
@Data
@Builder
public class WorkerTaskResponse {

    /** worker 业务码 (0=成功, 4xx=客户端错, 5xx=k8s API 错) */
    private int code;

    /** worker 业务消息 */
    private String message;

    /** k8s API 调用结果 (apply/rollback 时返 {phase, message, manifest}, scale 返 {phase, replicas}, getManifest 返 yaml 字符串) */
    private Map<String, Object> data;
}
