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

package com.shipyard.common;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean 工具 — 比 Spring {@code BeanUtils} 更精细的复制.
 *
 * <p>核心方法: {@link #copyNonNullProperties(Object, Object, String...)} —
 * 跟 Spring 一样用 getter/setter 复制, 但 <b>跳过 source 是 null 的字段</b>.
 *
 * <p>用途: Service.update() 场景下, 请求 DTO 只填了要改的字段, 其他字段是 null,
 * 这时不能直接 {@code BeanUtils.copyProperties} — 会把 target 的字段都覆盖成 null.
 */
public final class BeanUtils {

    private BeanUtils() {}

    /**
     * 复制 source → target, 跳过 null 字段.
     *
     * <p>额外 ignore 字段 (e.g. id, createdAt, deleted) 通过 {@code ignoreProperties} 传.
     */
    public static void copyNonNullProperties(Object source, Object target, String... ignoreProperties) {
        if (source == null || target == null) {
            return;
        }
        Set<String> ignore = new HashSet<>();
        if (ignoreProperties != null) {
            for (String p : ignoreProperties) ignore.add(p);
        }
        for (String nullProp : getNullPropertyNames(source)) {
            ignore.add(nullProp);
        }

        org.springframework.beans.BeanUtils.copyProperties(source, target, ignore.toArray(new String[0]));
    }

    /**
     * 找出 source 中所有 null 字段名.
     */
    private static String[] getNullPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();
        Set<String> emptyNames = new HashSet<>();
        for (PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }
        return emptyNames.toArray(new String[0]);
    }
}
