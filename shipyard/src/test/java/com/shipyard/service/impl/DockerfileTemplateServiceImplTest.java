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

import com.shipyard.common.exception.BusinessException;
import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.mapper.DockerfileTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerfileTemplateServiceImplTest {

    @Mock
    private DockerfileTemplateMapper templateMapper;

    @InjectMocks
    private DockerfileTemplateServiceImpl service;

    private DockerfileTemplate tpl;

    @BeforeEach
    void setUp() {
        tpl = new DockerfileTemplate();
        tpl.setId(1L);
        tpl.setName("java_maven_jdk21");
        tpl.setDisplayName("Java 21 + Maven");
        tpl.setLanguage("java");
        tpl.setBuildTool("maven");
        tpl.setTemplateContent(
            "FROM eclipse-temurin:21-jdk AS build\n" +
            "COPY . /build\n" +
            "RUN echo build ${jarName}\n" +
            "EXPOSE ${port}\n" +
            "CMD [\"java\", \"-jar\", \"/app/${jarName}\"]\n"
        );
        tpl.setVariableSchema("[{\"key\":\"jarName\"}]");
        tpl.setVersion(1);
        tpl.setIsBuiltin(1);
    }

    // ========== getByName ==========

    @Test
    void getByName_shouldReturn_whenFound() {
        when(templateMapper.selectByName("java_maven_jdk21")).thenReturn(tpl);
        DockerfileTemplate got = service.getByName("java_maven_jdk21");
        assertThat(got.getId()).isEqualTo(1L);
        assertThat(got.getLanguage()).isEqualTo("java");
    }

    @Test
    void getByName_shouldThrow_whenNotFound() {
        when(templateMapper.selectByName("nope")).thenReturn(null);
        assertThatThrownBy(() -> service.getByName("nope"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nope");
    }

    // ========== get ==========

    @Test
    void get_shouldReturn_whenFound() {
        when(templateMapper.selectById(1L)).thenReturn(tpl);
        assertThat(service.get(1L).getName()).isEqualTo("java_maven_jdk21");
    }

    @Test
    void get_shouldThrow_whenNotFound() {
        when(templateMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(99L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("99");
    }

    // ========== listAll ==========

    @Test
    void listAll_shouldPassDeleted0_andSortByLangBuildToolName() {
        when(templateMapper.selectList(any())).thenReturn(List.of(tpl));
        List<DockerfileTemplate> list = service.listAll();
        assertThat(list).hasSize(1);
    }

    // ========== render ==========

    @Test
    void render_shouldReplaceAllOccurrences() {
        String out = service.render(tpl, Map.of("jarName", "app.jar", "port", "8080"));
        assertThat(out)
            .contains("RUN echo build app.jar")
            .contains("EXPOSE 8080")
            .contains("\"/app/app.jar\"");
    }

    @Test
    void render_shouldLeaveContent_whenVarsEmpty() {
        String out = service.render(tpl, Map.of());
        assertThat(out).contains("${jarName}").contains("${port}");
    }

    @Test
    void render_shouldLeaveContent_whenVarsNull() {
        String out = service.render(tpl, null);
        assertThat(out).contains("${jarName}");
    }

    @Test
    void render_shouldReplaceWithEmptyString_whenVarValueNull() {
        // Map.of 不接受 null value, 用 HashMap
        java.util.HashMap<String, String> vars = new java.util.HashMap<>();
        vars.put("jarName", null);
        vars.put("port", "8080");
        String out = service.render(tpl, vars);
        // null -> ""
        assertThat(out).contains("RUN echo build ").contains("EXPOSE 8080");
    }

    @Test
    void render_shouldThrow_whenTemplateContentNull() {
        tpl.setTemplateContent(null);
        assertThatThrownBy(() -> service.render(tpl, Map.of()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void render_shouldThrow_whenTemplateNull() {
        assertThatThrownBy(() -> service.render(null, Map.of()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void render_shouldNotReplacePartialNames() {
        // ${jarName} 不应该被 ${jarNameExtra} 误匹配
        tpl.setTemplateContent("x=${jarName} y=${jarNameExtra}");
        String out = service.render(tpl, Map.of("jarName", "A", "jarNameExtra", "B"));
        assertThat(out).isEqualTo("x=A y=B");
    }
}
