package com.shipyard.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置 — 分页插件 + 乐观锁.
 *
 * <p>DataSource / 事务管理器由 spring-boot-starter-jdbc 自动配置 (HikariCP),
 * 这里只补 MyBatis-Plus 的拦截器链.
 *
 * <p>注意: 实体类扫描在 {@link com.shipyard.Application} 用 {@code @MapperScan} 配.
 */
@Configuration
public class DataSourceConfig {

    /**
     * MyBatis-Plus 拦截器链.
     *
     * <p>顺序敏感,必须按: 分页 → 乐观锁 → 其他.
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页 (MySQL 适配,带 count 查询优化)
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 2. 乐观锁 (M4 之后用得上,先注册)
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
