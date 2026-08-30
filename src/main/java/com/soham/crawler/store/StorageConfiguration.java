package com.soham.crawler.store;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class StorageConfiguration {
    @Bean
    @Profile("!postgres")
    DocumentStore inMemoryDocumentStore() {
        return new InMemoryDocumentStore();
    }

    @Bean
    @Profile("postgres")
    DataSource postgresDataSource(
            @Value("${CRAWLER_DB_URL:jdbc:postgresql://localhost:5432/crawler}") String url,
            @Value("${CRAWLER_DB_USER:crawler}") String user,
            @Value("${CRAWLER_DB_PASSWORD:crawler}") String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    @Profile("postgres")
    DocumentStore jdbcDocumentStore(DataSource dataSource) {
        return new JdbcDocumentStore(new JdbcTemplate(dataSource));
    }
}
