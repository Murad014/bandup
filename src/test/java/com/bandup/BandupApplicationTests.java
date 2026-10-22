package com.bandup;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BandupApplicationTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	Flyway flyway;

	@Test
	void contextLoads() {
	}

	@Test
	void runsOnPostgresql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
			assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
		}
	}

	@Test
	void flywayMigrationsAreAllApplied() {
		var info = flyway.info();
		assertThat(info.pending()).isEmpty();
		assertThat(info.applied()).allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
	}

}
