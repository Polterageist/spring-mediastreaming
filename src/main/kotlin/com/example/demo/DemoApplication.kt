package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer


@SpringBootApplication
class DemoApplication {
	@Bean
	fun redisConnectionFactory(): RedisConnectionFactory {
		return LettuceConnectionFactory()
	}

	@Bean
	fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
		val template = RedisTemplate<String, Any>()
		template.connectionFactory = redisConnectionFactory
		template.keySerializer = StringRedisSerializer()
		template.valueSerializer = GenericJackson2JsonRedisSerializer()
		return template
	}
}

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}
