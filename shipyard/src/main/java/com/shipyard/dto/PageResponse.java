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

package com.shipyard.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页响应体.
 *
 * <p>跟 MyBatis-Plus {@link IPage} 解耦 — 前端只关心 4 个字段.
 *
 * <pre>{@code
 * {
 *   "records": [...],
 *   "total": 42,
 *   "page": 1,
 *   "size": 20
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;

    /**
     * 从 MyBatis-Plus {@link IPage} 转 PageResponse, 用 {@code mapper} 把 Entity 转 DTO.
     */
    public static <E, T> PageResponse<T> from(IPage<E> page, Function<E, T> mapper) {
        List<T> mapped = page.getRecords().stream()
            .map(mapper)
            .collect(Collectors.toList());
        return new PageResponse<>(
            mapped,
            page.getTotal(),
            (int) page.getCurrent(),
            (int) page.getSize()
        );
    }
}
