
package com.aitaskmanager.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MyBatisの設定クラス
 */
@Configuration
public class MyBatisConfig {

    /**
     * SqlSessionFactoryのBean定義
     * 
     * @param dataSource データソース
     * @return SqlSessionFactoryオブジェクト
     * @throws Exception 例外
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] custom = resolver.getResources("classpath*:com/aitaskmanager/repository/customMapper/*.xml");
        Resource[] generator = resolver.getResources("classpath*:com/aitaskmanager/repository/generator/*.xml");

        List<Resource> all = new ArrayList<>();
        all.addAll(Arrays.asList(custom));
        all.addAll(Arrays.asList(generator));
        sessionFactory.setMapperLocations(all.toArray(new Resource[0]));

        return sessionFactory.getObject();
    }

    /**
     * SqlSessionTemplateのBean定義
     * 
     * @param sqlSessionFactory SqlSessionFactoryオブジェクト
     * @return SqlSessionTemplateオブジェクト
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
