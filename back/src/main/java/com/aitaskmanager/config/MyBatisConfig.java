package com.aitaskmanager.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import javax.sql.DataSource;

@Configuration
@MapperScan("com.aitaskmanager.repository.customMapper")
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // mapper.xml の場所を指定
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources(
                "classpath*:com/aitaskmanager/repository/customMapper/*.xml," +
                "classpath*:com/aitaskmanager/repository/generator/*.xml"
            )
        );

        return sessionFactory.getObject();
    }
}
