package com.durak.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String GAME_EXCHANGE = "durak.game";
    public static final String VOICE_EXCHANGE = "durak.voice";

    @Bean
    public TopicExchange gameExchange() {
        return new TopicExchange(GAME_EXCHANGE);
    }

    @Bean
    public TopicExchange voiceExchange() {
        return new TopicExchange(VOICE_EXCHANGE);
    }

    @Bean
    public Queue gameQueue() { return new Queue("durak.game.queue", false); }

    @Bean
    public Binding gameBinding(Queue gameQueue, TopicExchange gameExchange) {
        return BindingBuilder.bind(gameQueue).to(gameExchange).with("game.#");
    }
}
