package com.aitaskmanager.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatisの設定クラス
 */
@Configuration
@MapperScan("com.aitaskmanager.repository.customMapper")
public class MyBatisConfig {
}
