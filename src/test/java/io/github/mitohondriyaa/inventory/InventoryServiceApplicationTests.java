package io.github.mitohondriyaa.inventory;

import io.github.mitohondriyaa.inventory.service.InventoryService;
import io.github.mitohondriyaa.order.event.OrderPlacedEvent;
import io.github.mitohondriyaa.product.event.ProductCreatedEvent;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.Lifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
@ActiveProfiles("test")
class InventoryServiceApplicationTests {
	static Network network = Network.newNetwork();
	@ServiceConnection
	@SuppressWarnings("resource")
	static MySQLContainer<?> mySQLContainer =  new MySQLContainer<>("mysql:8")
		.withNetwork(network)
		.withNetworkAliases("mysql");
	@ServiceConnection
	static ConfluentKafkaContainer kafkaContainer = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.4.0")
		.withListener("kafka:19092")
		.withNetwork(network)
		.withNetworkAliases("kafka");
	@SuppressWarnings("resource")
	static GenericContainer<?> schemaRegistryContainer = new GenericContainer<>("confluentinc/cp-schema-registry:7.4.0")
		.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
		.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
		.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
		.withExposedPorts(8081)
		.withNetwork(network)
		.withNetworkAliases("schema-registry")
		.waitingFor(Wait.forHttp("/subjects"));
	@LocalServerPort
	Integer port;
	@MockitoBean
	JwtDecoder jwtDecoder;
	final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
	final KafkaTemplate<String, Object> kafkaTemplate;
	final ConsumerFactory<String, Object> consumerFactory;
	@MockitoSpyBean
	InventoryService inventoryService;

	static {
		mySQLContainer.start();
		kafkaContainer.start();
		schemaRegistryContainer.start();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.producer.properties.schema.registry.url",
			() -> "http://localhost:" + schemaRegistryContainer.getMappedPort(8081));
		registry.add("spring.kafka.consumer.properties.schema.registry.url",
			() -> "http://localhost:" + schemaRegistryContainer.getMappedPort(8081));
	}

	@BeforeEach
	void setUp() throws InterruptedException {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;

		Map<String, Object> realmAccess = new HashMap<>();
		realmAccess.put("roles", List.of("INVENTORY_MANAGER"));

		Jwt jwt = Jwt.withTokenValue("mock-token")
			.header("alg", "none")
			.claim("email", "test@example.com")
			.claim("given_name", "Alexander")
			.claim("family_name", "Sidorov")
			.claim("sub", "h7g3hg383837h7733hf38h37")
			.claim("realm_access", realmAccess)
			.build();

		when(jwtDecoder.decode(anyString())).thenReturn(jwt);

		kafkaListenerEndpointRegistry.getAllListenerContainers()
			.forEach(Lifecycle::start);

		Thread.sleep(5000);
	}

	@Test
	void shouldCreateInventory() {
		ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
		productCreatedEvent.setProductId("a876af73h3uf3hj");

		kafkaTemplate.send("product-created", productCreatedEvent);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.createInventory(any()));
	}

	@Test
	void shouldDeductStockWhenQuantityIsEnough() throws InterruptedException {
		ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
		productCreatedEvent.setProductId("a876af73h3uf3hj");

		kafkaTemplate.send("product-created", productCreatedEvent);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.createInventory(any()));

		String requestBody = """
			{
				"productId": "a876af73h3uf3hj",
				"quantity": 20
			}
			""";

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer mock-token")
			.body(requestBody)
			.when()
			.put("/api/inventory")
			.then()
			.statusCode(200);

		OrderPlacedEvent orderPlacedEvent = new OrderPlacedEvent();
		orderPlacedEvent.setOrderNumber("748f7f87ff78893983k");
		orderPlacedEvent.setProductId("a876af73h3uf3hj");
		orderPlacedEvent.setQuantity(10);
		orderPlacedEvent.setEmail("test@example.com");
		orderPlacedEvent.setFirstName("Alexander");
		orderPlacedEvent.setLastName("Sidorov");

		kafkaTemplate.send("order-placed", orderPlacedEvent);

		try (Consumer<String, Object> consumer = consumerFactory.createConsumer("testNotificationService", "test-client")) {
			consumer.subscribe(List.of("inventory-reserved"));

			ConsumerRecords<String , Object> records =
				KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

			Assertions.assertFalse(records.isEmpty());
		}
	}

	@Test
	void shouldDeductStockWhenQuantityIsNotEnough() throws InterruptedException {
		ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
		productCreatedEvent.setProductId("a876af73h3uf3hj");

		kafkaTemplate.send("product-created", productCreatedEvent);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.createInventory(any()));

		String requestBody = """
			{
				"productId": "a876af73h3uf3hj",
				"quantity": 20
			}
			""";

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer mock-token")
			.body(requestBody)
			.when()
			.put("/api/inventory")
			.then()
			.statusCode(200);

		OrderPlacedEvent orderPlacedEvent = new OrderPlacedEvent();
		orderPlacedEvent.setOrderNumber("748f7f87ff78893983k");
		orderPlacedEvent.setProductId("a876af73h3uf3hj");
		orderPlacedEvent.setQuantity(25);
		orderPlacedEvent.setEmail("test@example.com");
		orderPlacedEvent.setFirstName("Alexander");
		orderPlacedEvent.setLastName("Sidorov");

		kafkaTemplate.send("order-placed", orderPlacedEvent);

		try (Consumer<String, Object> consumer = consumerFactory.createConsumer("testNotificationService", "test-client")) {
			consumer.subscribe(List.of("inventory-rejected"));

			ConsumerRecords<String , Object> records =
				KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

			Assertions.assertFalse(records.isEmpty());
		}
	}

	@Test
	void shouldCheckStock() {
		ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
		productCreatedEvent.setProductId("a876af73h3uf3hj");

		kafkaTemplate.send("product-created", productCreatedEvent);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.createInventory(any()));

		String requestBody = """
			{
				"productId": "a876af73h3uf3hj",
				"quantity": 20
			}
			""";

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer mock-token")
			.body(requestBody)
			.when()
			.put("/api/inventory")
			.then()
			.statusCode(200);

		RestAssured.given()
			.header("Authorization", "Bearer mock-token")
			.queryParam("productId", "a876af73h3uf3hj")
			.queryParam("quantity", 20)
			.when()
			.get("/api/inventory/check")
			.then()
			.statusCode(200)
			.body(Matchers.equalTo("true"));
	}

	@AfterEach
	void tearDown() {
		kafkaListenerEndpointRegistry.getAllListenerContainers()
			.forEach(Lifecycle::stop);
	}

	@AfterAll
	static void stopContainers() {
		mySQLContainer.stop();
		kafkaContainer.stop();
		schemaRegistryContainer.stop();
	}
}
