/*
 * Copyright 2026 The shipyard Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.shipyard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.entity.Project;
import com.shipyard.entity.ProjectDockerfile;
import com.shipyard.mapper.ProjectDockerfileMapper;
import com.shipyard.service.DockerfileTemplateService;
import com.shipyard.service.ProjectService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectDockerfileServiceImplTest {

    @Mock
    private DockerfileTemplateService templateService;

    @Mock
    private ProjectDockerfileMapper projectDockerfileMapper;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectDockerfileServiceImpl service;

    private DockerfileTemplate tpl;
    private Project project;

    @BeforeEach
    void setUp() {
        tpl = new DockerfileTemplate();
        tpl.setId(1L);
        tpl.setName("java_maven_jdk21");
        tpl.setTemplateContent("FROM openjdk:21\nEXPOSE ${port}");

        project = new Project();
        project.setId(100L);
        project.setName("myapp");
    }

    // ========== preview ==========

    @Test
    void preview_shouldCallRender_andNotPersist() {
        when(templateService.getByName("java_maven_jdk21")).thenReturn(tpl);
        when(templateService.render(eq(tpl), any())).thenReturn("FROM openjdk:21\nEXPOSE 8080");

        String out = service.preview("java_maven_jdk21", Map.of("port", "8080"));

        assertThat(out).contains("EXPOSE 8080");
        verify(templateService).render(eq(tpl), eq(Map.of("port", "8080")));
        // 没调用 mapper.insert
        verify(projectDockerfileMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void preview_shouldThrow_whenTemplateNotFound() {
        when(templateService.getByName("nope"))
                .thenThrow(new BusinessException(com.shipyard.common.exception.ErrorCode.NOT_FOUND, "not found"));

        assertThatThrownBy(() -> service.preview("nope", Map.of())).isInstanceOf(BusinessException.class);
    }

    // ========== generate ==========

    @Test
    void generate_shouldInsert_withStatusDraft_andBranchMain() {
        when(projectService.get(100L)).thenReturn(project);
        when(templateService.render(eq(tpl), any())).thenReturn("FROM ...\nEXPOSE 8080");

        ProjectDockerfile out = service.generate(100L, tpl, Map.of("port", "8080"), null, null);

        ArgumentCaptor<ProjectDockerfile> captor = ArgumentCaptor.forClass(ProjectDockerfile.class);
        verify(projectDockerfileMapper).insert(captor.capture());

        ProjectDockerfile inserted = captor.getValue();
        assertThat(inserted.getProjectId()).isEqualTo(100L);
        assertThat(inserted.getDockerfileTemplateId()).isEqualTo(1L);
        assertThat(inserted.getStatus()).isEqualTo("draft");
        assertThat(inserted.getRepoBranch()).isEqualTo("main"); // null → main
        assertThat(inserted.getCommitMessage()).isEqualTo("chore: add Dockerfile via shipyard");
        assertThat(inserted.getRenderedContent()).contains("EXPOSE 8080");
        assertThat(inserted.getRepoCommitSha()).isNull(); // V1 留空
        assertThat(inserted.getVariableValues()).contains("\"port\":\"8080\"");

        // service.generate 返回的 entity 是 mapper.insert 之前的 pd 对象 (我们看 inserted 字段即可)
        assertThat(out).isNotNull();
    }

    @Test
    void generate_shouldKeepBranchAndCommitMessage_whenProvided() {
        when(projectService.get(100L)).thenReturn(project);
        when(templateService.render(eq(tpl), any())).thenReturn("FROM openjdk:21");

        service.generate(100L, tpl, Map.of("port", "8080"), "feat/docker", "chore: shipyard generated Dockerfile");

        ArgumentCaptor<ProjectDockerfile> captor = ArgumentCaptor.forClass(ProjectDockerfile.class);
        verify(projectDockerfileMapper).insert(captor.capture());
        ProjectDockerfile inserted = captor.getValue();
        assertThat(inserted.getRepoBranch()).isEqualTo("feat/docker");
        assertThat(inserted.getCommitMessage()).isEqualTo("chore: shipyard generated Dockerfile");
    }

    @Test
    void generate_shouldThrow_whenProjectNotFound() {
        when(projectService.get(99L))
                .thenThrow(new BusinessException(
                        com.shipyard.common.exception.ErrorCode.NOT_FOUND, "project 99 not found"));

        assertThatThrownBy(() -> service.generate(99L, tpl, Map.of(), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
    }

    @Test
    void generate_shouldEscapeJsonSpecialChars_inVariableValues() {
        when(projectService.get(100L)).thenReturn(project);
        when(templateService.render(eq(tpl), any())).thenReturn("...");

        service.generate(100L, tpl, Map.of("msg", "hello \"world\"\\path"), null, null);

        ArgumentCaptor<ProjectDockerfile> captor = ArgumentCaptor.forClass(ProjectDockerfile.class);
        verify(projectDockerfileMapper).insert(captor.capture());
        // msg: "hello \"world\"\\path" 应转义成 JSON 安全
        assertThat(captor.getValue().getVariableValues())
                .contains("\\\"world\\\"") // " 转义
                .contains("\\\\path"); // \ 转义
    }

    // ========== listByProject / get ==========

    @Test
    void listByProject_shouldDelegateToMapper() {
        when(projectDockerfileMapper.listByProject(100L)).thenReturn(java.util.List.of(new ProjectDockerfile()));
        assertThat(service.listByProject(100L)).hasSize(1);
    }

    @Test
    void get_shouldThrow_whenNotFound() {
        when(projectDockerfileMapper.selectById(anyLong())).thenReturn(null);
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
    }
}
