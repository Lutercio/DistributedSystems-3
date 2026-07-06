package com.distributed_systems.question.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DownstreamProperties.class)
class DownstreamClientConfiguration {

	@Bean
	WebClient normalizerWebClient(
			WebClient.Builder builder,
			ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter,
			DownstreamProperties properties
	) {
		return builder.clone()
				.baseUrl(properties.normalizerUrl().toString())
				.filter(loadBalancerFilter)
				.build();
	}

	@Bean
	HttpGraphQlClient ragGraphQlClient(
			WebClient.Builder builder,
			ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter,
			DownstreamProperties properties
	) {
		WebClient webClient = builder.clone()
				.baseUrl(properties.ragGraphqlUrl().toString())
				.filter(loadBalancerFilter)
				.build();
		return HttpGraphQlClient.create(webClient);
	}

}
