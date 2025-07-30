package io.github.mitohondriyaa.inventory;

import com.redis.testcontainers.RedisContainer;
import io.github.mitohondriyaa.inventory.config.TestRedisConfig;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.repository.InventoryRepository;
import io.github.mitohondriyaa.inventory.service.InventoryService;
import io.github.mitohondriyaa.order.event.OrderCancelledEvent;
import io.github.mitohondriyaa.order.event.OrderPlacedEvent;
import io.github.mitohondriyaa.product.event.ProductCreatedEvent;
import io.github.mitohondriyaa.product.event.ProductDeletedEvent;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Import;
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
import java.util.*;

import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
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
	static RedisContainer redisContainer = new  RedisContainer("redis:8.0")
		.withExposedPorts(6379)
		.withNetwork(network)
		.withNetworkAliases("redis");
	static final String PRODUCT_ID = "a876af73h3uf3hj";
	@LocalServerPort
	Integer port;
	@MockitoBean
	JwtDecoder jwtDecoder;
	final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
	final KafkaTemplate<String, Object> kafkaTemplate;
	final ConsumerFactory<String, Object> consumerFactory;
	final InventoryRepository inventoryRepository;
	@MockitoSpyBean
	InventoryService inventoryService;

	static {
		mySQLContainer.start();
		kafkaContainer.start();
		schemaRegistryContainer.start();
		redisContainer.start();
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.producer.properties.schema.registry.url",
			() -> "http://localhost:" + schemaRegistryContainer.getMappedPort(8081));
		registry.add("spring.kafka.consumer.properties.schema.registry.url",
			() -> "http://localhost:" + schemaRegistryContainer.getMappedPort(8081));
		registry.add("redis.port",
			() -> redisContainer.getMappedPort(6379));
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
		productCreatedEvent.setProductId(PRODUCT_ID);

		ProducerRecord<String, Object> producerRecord
			= new ProducerRecord<>("product-created", productCreatedEvent);
		producerRecord.headers().add("messageId", UUID.randomUUID().toString().getBytes());

		kafkaTemplate.send(producerRecord);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.createInventory(productCreatedEvent));

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> {
				Optional<Inventory> optionalInventory
					= inventoryRepository.findByProductId(PRODUCT_ID);
				optionalInventory
					.ifPresentOrElse(inventory -> {
						Assertions.assertNotNull(inventory.getId());
						Assertions.assertEquals(PRODUCT_ID, inventory.getProductId());
						Assertions.assertEquals(0, inventory.getQuantity());
					}, () -> Assertions.fail("Inventory not found"));
			});
	}

	@Test
	void shouldDeductStockWhenQuantityIsEnough() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		OrderPlacedEvent orderPlacedEvent = new OrderPlacedEvent();
		orderPlacedEvent.setOrderNumber("748f7f87ff78893983k");
		orderPlacedEvent.setProductId(PRODUCT_ID);
		orderPlacedEvent.setQuantity(10);
		orderPlacedEvent.setEmail("test@example.com");
		orderPlacedEvent.setFirstName("Alexander");
		orderPlacedEvent.setLastName("Sidorov");

		ProducerRecord<String, Object> producerRecord
			= new ProducerRecord<>("order-placed", orderPlacedEvent);
		producerRecord.headers().add("messageId", UUID.randomUUID().toString().getBytes());

		kafkaTemplate.send(producerRecord);

		try (Consumer<String, Object> consumer = consumerFactory.createConsumer("testNotificationService", "test-client")) {
			consumer.subscribe(List.of("inventory-reserved"));

			ConsumerRecords<String , Object> records =
				KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

			Assertions.assertFalse(records.isEmpty());
		}
	}

	@Test
	void shouldDeductStockWhenQuantityIsNotEnough() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		OrderPlacedEvent orderPlacedEvent = new OrderPlacedEvent();
		orderPlacedEvent.setOrderNumber("748f7f87ff78893983k");
		orderPlacedEvent.setProductId(PRODUCT_ID);
		orderPlacedEvent.setQuantity(25);
		orderPlacedEvent.setEmail("test@example.com");
		orderPlacedEvent.setFirstName("Alexander");
		orderPlacedEvent.setLastName("Sidorov");

		ProducerRecord<String, Object> producerRecord
			= new ProducerRecord<>("order-placed", orderPlacedEvent);
		producerRecord.headers().add("messageId", UUID.randomUUID().toString().getBytes());

		kafkaTemplate.send(producerRecord);

		try (Consumer<String, Object> consumer = consumerFactory.createConsumer("testNotificationService", "test-client")) {
			consumer.subscribe(List.of("inventory-rejected"));

			ConsumerRecords<String , Object> records =
				KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

			Assertions.assertFalse(records.isEmpty());
		}
	}

	@Test
	void shouldGetAllInventories() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		RestAssured.given()
			.header("Authorization", "Bearer mock-token")
			.when()
			.get("/api/inventory")
			.then()
			.statusCode(200)
			.body("size()", Matchers.is(1));
	}

	@Test
	void shouldCheckStock() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		RestAssured.given()
			.header("Authorization", "Bearer mock-token")
			.queryParam("productId", PRODUCT_ID)
			.queryParam("quantity", 20)
			.when()
			.get("/api/inventory/check")
			.then()
			.statusCode(200)
			.body(Matchers.equalTo("true"));
	}

	@Test
	void shouldGetInventoryByProductId() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		RestAssured.given()
			.header("Authorization", "Bearer mock-token")
			.when()
			.get("/api/inventory/" + PRODUCT_ID)
			.then()
			.statusCode(200)
			.body("id", Matchers.notNullValue())
			.body("productId", Matchers.equalTo(PRODUCT_ID))
			.body("quantity", Matchers.equalTo(20));
	}

	@Test
	void shouldUpdateInventoryByProductId() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		String requestBody = """
			{
				"productId": "%s",
				"quantity": 40
			}
			""".formatted(PRODUCT_ID);

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer mock-token")
			.body(requestBody)
			.when()
			.put("/api/inventory")
			.then()
			.statusCode(200)
			.body("id", Matchers.notNullValue())
			.body("productId", Matchers.equalTo(PRODUCT_ID))
			.body("quantity", Matchers.equalTo(40));
	}

	@Test
	void shouldDeleteInventoryByProductId() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		ProductDeletedEvent productDeletedEvent = new ProductDeletedEvent();
		productDeletedEvent.setProductId(PRODUCT_ID);

		ProducerRecord<String, Object> producerRecord
			= new ProducerRecord<>("product-deleted", productDeletedEvent);
		producerRecord.headers().add("messageId", UUID.randomUUID().toString().getBytes());

		kafkaTemplate.send(producerRecord);

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> verify(inventoryService, atLeastOnce())
				.deleteInventoryByProductID(eq(productDeletedEvent)));

		Awaitility.await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> {
				Optional<Inventory> optionalInventory
					= inventoryRepository.findByProductId(PRODUCT_ID);
				Assertions.assertTrue(optionalInventory.isEmpty());
			});
	}

	@Test
	void shouldRejectInventory() {
		Inventory inventory = new Inventory();
		inventory.setProductId(PRODUCT_ID);
		inventory.setQuantity(20);

		inventoryRepository.save(inventory);

		OrderCancelledEvent orderCancelledEvent = new OrderCancelledEvent();
		orderCancelledEvent.setOrderNumber("748f7f87ff78893983k");
		orderCancelledEvent.setProductId(PRODUCT_ID);
		orderCancelledEvent.setQuantity(10);
		orderCancelledEvent.setEmail("test@example.com");
		orderCancelledEvent.setFirstName("Alexander");
		orderCancelledEvent.setLastName("Sidorov");

		ProducerRecord<String, Object> producerRecord
			= new ProducerRecord<>("order-cancelled", orderCancelledEvent);
		producerRecord.headers().add("messageId", UUID.randomUUID().toString().getBytes());

		kafkaTemplate.send(producerRecord);

		try (Consumer<String, Object> consumer = consumerFactory.createConsumer("testNotificationService", "test-client")) {
			consumer.subscribe(List.of("inventory-rejected"));

			ConsumerRecords<String , Object> records =
				KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

			Assertions.assertFalse(records.isEmpty());
		}

		@SuppressWarnings("OptionalGetWithoutIsPresent")
		Inventory verifiableInventory = inventoryRepository
			.findByProductId(PRODUCT_ID).get();

		Assertions.assertEquals(30, verifiableInventory.getQuantity());
	}

	@AfterEach
	void tearDown() {
		kafkaListenerEndpointRegistry.getAllListenerContainers()
			.forEach(Lifecycle::stop);

		inventoryRepository.deleteAll();
	}

	@AfterAll
	static void stopContainers() {
		mySQLContainer.stop();
		kafkaContainer.stop();
		schemaRegistryContainer.stop();
		redisContainer.stop();
		network.close();
	}
}
