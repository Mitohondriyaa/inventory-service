package io.github.mitohondriyaa.inventory;

import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryServiceApplicationTests {
	@Container
	@ServiceConnection
	static MySQLContainer<?> mySQLContainer =  new MySQLContainer<>("mysql:8");
	@LocalServerPort
	Integer port;

	@BeforeEach
	void setUp() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	@Test
	void shouldCheckStock() {
		RestAssured.given()
				.queryParam("skuCode", "iPhone_15")
				.queryParam("quantity", 100)
				.when()
				.get("/api/inventory")
				.then()
				.statusCode(200)
				.body(Matchers.equalTo("true"));
	}
}
