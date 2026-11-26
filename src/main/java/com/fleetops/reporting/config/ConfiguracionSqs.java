package com.fleetops.reporting.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Cliente SQS. Solo se crea con SQS_ENABLED=true. En AWS las credenciales salen del rol IAM de la EC2; en local,
 * SQS_ENDPOINT apunta a LocalStack.
 */
@Configuration
@ConditionalOnProperty(name = "reporting.sqs.enabled", havingValue = "true")
public class ConfiguracionSqs {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(ReportingProperties props) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(props.sqs().region()));
        String endpoint = props.sqs().endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
