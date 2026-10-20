package cl.losandes.mediturno.reservas.infraestructura.mensajeria;

import cl.losandes.mediturno.reservas.aplicacion.eventos.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Mensajería asíncrona de ms-reservas.
 *
 * <p>Las cargas útiles viajan como texto JSON plano, sin cabecera de tipo. Es deliberado:
 * si el mensaje llevara el nombre de la clase Java del productor, el consumidor quedaría
 * atado a la estructura interna de otro servicio y el contrato dejaría de ser el JSON
 * para pasar a ser el código. Deserializar a mano también obliga a decidir de forma
 * explícita qué hacer con una carga útil corrupta.</p>
 */
@Configuration
@Profile("!efimero")
public class ConfiguracionKafka {

    @Value("${mediturno.kafka.particiones:3}")
    private int particiones;

    @Value("${mediturno.kafka.replicas:1}")
    private short replicas;

    @Value("${mediturno.kafka.reintentos:2}")
    private long reintentos;

    @Value("${mediturno.kafka.espera-entre-reintentos-ms:1000}")
    private long esperaMs;

    private NewTopic topic(String nombre) {
        return TopicBuilder.name(nombre).partitions(particiones).replicas(replicas).build();
    }

    @Bean NewTopic topicReservasEventos() { return topic(Topics.RESERVAS_EVENTOS); }

    @Bean NewTopic topicReservasEventosDlq() { return topic(Topics.dlqDe(Topics.RESERVAS_EVENTOS)); }

    @Bean NewTopic topicNotificaciones() { return topic(Topics.NOTIFICACIONES_SOLICITUDES); }

    @Bean NewTopic topicNotificacionesDlq() {
        return topic(Topics.dlqDe(Topics.NOTIFICACIONES_SOLICITUDES));
    }

    @Bean NewTopic topicPagos() { return topic(Topics.PAGOS_CONFIRMACIONES); }

    @Bean NewTopic topicPagosDlq() { return topic(Topics.dlqDe(Topics.PAGOS_CONFIRMACIONES)); }

    /**
     * Reintenta y, si no hay caso, deriva el mensaje a la cola de mensajes muertos
     * conservando la partición de origen.
     *
     * <p>Un JSON que no se puede interpretar no mejora reintentando: va directo a la DLQ.</p>
     */
    @Bean
    public DefaultErrorHandler manejadorDeErroresKafka(KafkaTemplate<String, String> plantilla) {
        DeadLetterPublishingRecoverer aLaDlq = new DeadLetterPublishingRecoverer(plantilla,
                (registro, excepcion) -> new TopicPartition(
                        Topics.dlqDe(registro.topic()), registro.partition()));
        DefaultErrorHandler manejador =
                new DefaultErrorHandler(aLaDlq, new FixedBackOff(esperaMs, reintentos));
        manejador.addNotRetryableExceptions(JsonProcessingException.class,
                IllegalArgumentException.class);
        return manejador;
    }
}
