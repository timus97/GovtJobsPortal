package in.govtjobs.web.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FeatureFlags.class, GovtJobsProperties.class})
public class WebConfig {}
