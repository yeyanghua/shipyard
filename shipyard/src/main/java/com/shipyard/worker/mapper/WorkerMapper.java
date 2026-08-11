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

package com.shipyard.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shipyard.worker.entity.Worker;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Worker Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>自定义 SQL:
 * <ul>
 *   <li>{@link #updateHeartbeat} — 原子更新 last_heartbeat_at + status, 不走 ORM 全字段映射
 *   (worker 心跳高频, 不应该 load → modify → save, 一次 UPDATE 完事)
 * </ul>
 */
@Mapper
public interface WorkerMapper extends BaseMapper<Worker> {

    /**
     * 更新 worker 心跳 — 一次 SQL 搞定, 不走 MyBatis-Plus 全字段更新.
     *
     * @param id worker ID
     * @param heartbeatAt 心跳时间
     * @param status 新状态 (online / unhealthy)
     * @return 影响行数 (0 = worker 不存在)
     */
    @Update("UPDATE worker SET last_heartbeat_at = #{heartbeatAt}, status = #{status}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int updateHeartbeat(@Param("id") Long id,
                       @Param("heartbeatAt") LocalDateTime heartbeatAt,
                       @Param("status") String status);

    /**
     * 按 env_id + role + status 查 worker — M9 选 deploy worker 用.
     *
     * <p>按 last_heartbeat_at DESC 选最新心跳的 1 个, 适配 M9.5 round-robin 加 weight 字段.
     *
     * <p>当前调用场景:
     * <ul>
     *   <li>DeployService.selectDeployWorker: env_id + role='PRIMARY' + status='online'</li>
     *   <li>WorkerHeartbeatScanner: env_id + role='STANDBY' + status='online' (升级备用)</li>
     * </ul>
     */
    @Select("SELECT * FROM worker "
            + "WHERE env_id = #{envId} AND role = #{role} AND status = #{status} AND deleted = 0 "
            + "ORDER BY last_heartbeat_at DESC LIMIT 1")
    Worker selectByEnvAndRole(@Param("envId") Long envId,
                              @Param("role") String role,
                              @Param("status") String status);

    /**
     * 查某 env 下所有 online worker — M9 self-elect 时算同 env 已有数量用.
     */
    @Select("SELECT * FROM worker WHERE env_id = #{envId} AND status = 'online' AND deleted = 0")
    List<Worker> selectOnlineByEnv(@Param("envId") Long envId);
}
