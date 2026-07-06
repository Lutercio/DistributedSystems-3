package com.distributed_systems.regulationmcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegulationProperties.class)
class RegulationConfiguration {
}
