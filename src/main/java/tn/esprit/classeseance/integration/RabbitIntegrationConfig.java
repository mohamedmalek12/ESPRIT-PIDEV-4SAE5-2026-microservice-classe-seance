package tn.esprit.classeseance.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableRabbit
@Profile("!test")
public class RabbitIntegrationConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitIntegrationConfig.class);

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Must match {@code salles-materiels} declaration: durable, no extra args.
     * Redeclaring with x-dead-letter-* fails with PRECONDITION_FAILED if the queue already exists.
     */
    @Bean
    public Queue materialWarningsQueue() {
        return new Queue(IntegrationQueues.MATERIAL_WARNINGS, true);
    }

    @Bean
    public Queue materielUsageRpcQueue() {
        return new Queue(IntegrationQueues.MATERIEL_USAGE_RPC, true);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setReplyTimeout(15_000L);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        factory.setErrorHandler(t -> log.error("Rabbit listener container error: {}", t.getMessage(), t));
        return factory;
    }

    @Bean
    public RabbitListenerErrorHandler materialWarningsErrorHandler() {
        return (amqpMessage, message, exception) -> {
            Object payload = message != null ? message.getPayload() : null;
            log.error("Material warning listener failed. payload={}, cause={}", payload, exception.getMessage(), exception);
            throw exception;
        };
    }
}
