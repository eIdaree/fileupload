package kz.kaspi.fileupload.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "kz.kaspi.fileupload.domain.repository")
@EnableReactiveMongoAuditing
public class MongoConfig {
}