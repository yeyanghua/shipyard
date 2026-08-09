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
import com.shipyard.entity.DockerfileTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DockerfileTemplateMapper extends BaseMapper<DockerfileTemplate> {

    /**
     * 按 name 查 (用于幂等初始化检查 + 业务按 name 选模板).
     */
    @Select("SELECT * FROM dockerfile_template WHERE name = #{name} AND deleted = 0 LIMIT 1")
    DockerfileTemplate selectByName(String name);
}
