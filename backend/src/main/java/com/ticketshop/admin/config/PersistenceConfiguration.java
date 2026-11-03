package com.ticketshop.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfiguration {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        plugins.addInnerInterceptor(pagination);
        return plugins;
    }
}
