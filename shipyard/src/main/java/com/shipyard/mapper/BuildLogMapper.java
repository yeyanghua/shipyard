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

package com.shipyard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shipyard.entity.BuildLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * BuildLog Mapper — drone webhook 落日志用.
 *
 * <p>V1 自定义 SQL:
 * <ul>
 *   <li>{@link #selectByBuildRecordIdOrderByStepOrder(Long)} — 构建详情页 step 列表</li>
 *   <li>{@link #selectByBuildRecordIdAndStepName(Long, String)} — 查单个 step 日志</li>
 * </ul>
 */
@Mapper
public interface BuildLogMapper extends BaseMapper<BuildLog> {

    /**
     * 查 build 全部 step 日志 — 按 step_order 升序.
     *
     * <p>对应 SQL: {@code SELECT * FROM build_log WHERE build_record_id = #{buildRecordId}
     * ORDER BY step_order ASC}.
     */
    @Select("SELECT * FROM build_log WHERE build_record_id = #{buildRecordId} ORDER BY step_order ASC")
    List<BuildLog> selectByBuildRecordIdOrderByStepOrder(@Param("buildRecordId") Long buildRecordId);

    /**
     * 查单个 step 日志 — drone webhook 重放 / 详情页用.
     *
     * <p>对应 SQL: {@code SELECT * FROM build_log WHERE build_record_id = #{buildRecordId}
     * AND step_name = #{stepName} LIMIT 1}.
     */
    @Select("SELECT * FROM build_log WHERE build_record_id = #{buildRecordId} " +
        "AND step_name = #{stepName} LIMIT 1")
    BuildLog selectByBuildRecordIdAndStepName(@Param("buildRecordId") Long buildRecordId,
                                              @Param("stepName") String stepName);
}
