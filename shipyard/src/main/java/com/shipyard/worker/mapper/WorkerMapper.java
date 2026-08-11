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
     * M9 commit-4: 心跳时同时更新 health + health_detail.
     *
     * <p>跟 {@link #updateHeartbeat} 区别: 多更新 health 字段 (worker 自报健康状态).
     * WorkerSelector 选 worker 时额外过滤 health='HEALTHY' (不健康不派活).
     */
    @Update("UPDATE worker SET last_heartbeat_at = #{heartbeatAt}, status = #{status}, "
            + "health = #{health}, health_detail = #{healthDetail}, "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int updateHeartbeatWithHealth(@Param("id") Long id,
                                  @Param("heartbeatAt") LocalDateTime heartbeatAt,
                                  @Param("status") String status,
                                  @Param("health") String health,
                                  @Param("healthDetail") String healthDetail);

    /**
     * M9 commit-4: 标 worker 离线 (心跳超时, shipyard @Scheduled 30s 扫一次).
     *
     * <p>条件 {@code status = 'online'} 防止重复标 (UNHEALTHY 状态 worker 也会被心跳,
     * 但标 offline 只对 online 状态生效).
     */
    @Update("UPDATE worker SET status = 'offline', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = 'online' AND deleted = 0")
    int markOffline(@Param("id") Long id);

    /**
     * 按 env_id + status 查 worker 列表 — M9 选 deploy worker 用 (WorkerSelector 抽象).
     *
     * <p>不限定 role (M9 fix-commit 后 worker 不再分 PRIMARY/STANDBY):
     * WorkerSelector 实现 (RoundRobinSelector / FirstAvailableSelector / RandomSelector)
     * 从这个列表里按各自策略选 1 个.
     *
     * <p>按 last_heartbeat_at DESC 排序, "最新心跳在前", 跟 V1.5 加 weight 字段兼容.
     *
     * <p>调用场景: DeployService.selectDeployWorker → WorkerSelector.select(envId)
     */
    @Select("SELECT * FROM worker "
            + "WHERE env_id = #{envId} AND status = #{status} AND deleted = 0 "
            + "ORDER BY last_heartbeat_at DESC")
    List<Worker> selectByEnvAndStatus(@Param("envId") Long envId,
                                      @Param("status") String status);

    /**
     * 查某 env 下所有 online worker — M9 self-elect / Workers.vue 列表 / 调度 debug 用.
     */
    @Select("SELECT * FROM worker WHERE env_id = #{envId} AND status = 'online' AND deleted = 0")
    List<Worker> selectOnlineByEnv(@Param("envId") Long envId);

    /**
     * 查某 env 下所有 unhealthy worker (last_heartbeat_at < 阈值) — M9 WorkerHealthScanner 用.
     *
     * <p>Shipyard @Scheduled 30s 扫一次, 找出心跳超时的 worker, 标 status=offline,
     * 从 WorkerSelector 候选池剔除.
     */
    @Select("SELECT * FROM worker "
            + "WHERE env_id = #{envId} AND status = 'online' "
            + "AND last_heartbeat_at < #{threshold} AND deleted = 0")
    List<Worker> selectStaleOnlineByEnv(@Param("envId") Long envId,
                                        @Param("threshold") java.time.LocalDateTime threshold);

    /**
     * M9 commit-4: 全表扫, 找心跳超时的 online worker (不限 env).
     *
     * <p>{@link WorkerHealthScanner} 30s 调一次. V1 worker 数量 < 100 全表扫够用,
     * V1.5+ worker 多起来时考虑按 env 切分多线程扫, 或加 idx_worker_heartbeat 索引.
     */
    @Select("SELECT * FROM worker "
            + "WHERE status = 'online' AND last_heartbeat_at < #{threshold} AND deleted = 0")
    List<Worker> selectStaleOnline(@Param("threshold") java.time.LocalDateTime threshold);
}
