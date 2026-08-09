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
import com.shipyard.entity.ProjectDockerfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectDockerfileMapper extends BaseMapper<ProjectDockerfile> {

    /** 按 project 查所有 Dockerfile 实例 (按 id DESC) */
    @Select("SELECT * FROM project_dockerfile WHERE project_id = #{projectId} AND deleted = 0 ORDER BY id DESC")
    List<ProjectDockerfile> listByProject(Long projectId);
}
