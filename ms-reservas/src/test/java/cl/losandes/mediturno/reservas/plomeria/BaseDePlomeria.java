package cl.losandes.mediturno.reservas.plomeria;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de plomería: levanta PostgreSQL, Redis y Kafka de verdad, en
 * contenedores efímeros, y arranca el contexto completo de ms-reservas contra ellos.
 *
 * <p>Estas pruebas <b>no</b> son el trabajo de la Entrega 3. Comprueban que las cañerías
 * están conectadas —que la base responde, que el broker acepta mensajes, que el contexto
 * levanta— y nada más. No verifican ninguna regla de negocio. Vienen con el repositorio
 * para que, cuando algo falle, se pueda distinguir "el ambiente está mal" de "el sistema
 * está mal", que es una distinción que cuesta cara descubrir tarde.</p>
 *
 * <p>Los contenedores son estáticos: se levantan una vez y los comparten todas las
 * pruebas que heredan de esta clase. Levantar Kafka por cada prueba haría la suite
 * inviable.</p>
 *
 * <p>Se inician a mano en el bloque estático, y no con {@code @Testcontainers} y
 * {@code @Container}: esas anotaciones detienen los contenedores al terminar cada clase
 * de prueba, mientras Spring reutiliza el contexto en caché, que queda apuntando a
 * puertos que ya no existen. Arrancados una sola vez, viven toda la ejecución y Ryuk
 * los elimina al final.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // La tarea de barrido se apaga: una tarea que se dispara sola mientras
                // corre la suite es una de las causas clásicas de pruebas inestables.
                "mediturno.vencimiento-ofertas.habilitado=false",
                // Los servicios vecinos no se levantan: la plomería no los necesita.
                "mediturno.clientes.tarificacion.url=http://localhost:9",
                "mediturno.clientes.pacientes.url=http://localhost:9",
                "mediturno.clientes.validador-prevision.url=http://localhost:9",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("plomeria")
public abstract class BaseDePlomeria {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mediturno_reservas")
                    .withUsername("mediturno")
                    .withPassword("mediturno");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    static {
        Startables.deepStart(POSTGRES, REDIS, KAFKA).join();
    }
}
