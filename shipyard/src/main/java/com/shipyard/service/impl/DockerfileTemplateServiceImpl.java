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

package com.shipyard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.mapper.DockerfileTemplateMapper;
import com.shipyard.service.DockerfileTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DockerfileTemplateServiceImpl implements DockerfileTemplateService {

    private final DockerfileTemplateMapper templateMapper;

    @Override
    public List<DockerfileTemplate> listAll() {
        return templateMapper.selectList(
            new QueryWrapper<DockerfileTemplate>()
                .eq("deleted", 0)
                .orderByAsc("language", "build_tool", "name")
        );
    }

    @Override
    public DockerfileTemplate getByName(String name) {
        DockerfileTemplate t = templateMapper.selectByName(name);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dockerfile 模板不存在: " + name);
        }
        return t;
    }

    @Override
    public DockerfileTemplate get(Long id) {
        DockerfileTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dockerfile 模板不存在: id=" + id);
        }
        return t;
    }

    @Override
    public String render(DockerfileTemplate template, Map<String, String> vars) {
        if (template == null || template.getTemplateContent() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板内容为空");
        }
        String content = template.getTemplateContent();
        if (vars == null || vars.isEmpty()) {
            return content;
        }
        // 简单 ${var} 替换 — 不用正则, 用 String.replace(placeholder, value)
        // 注意: ${mainClassArgs} 这种含 var 嵌套的也 OK, 先替换内层
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String placeholder = "${" + e.getKey() + "}";
            String value = e.getValue() == null ? "" : e.getValue();
            content = content.replace(placeholder, value);
        }
        return content;
    }
}
