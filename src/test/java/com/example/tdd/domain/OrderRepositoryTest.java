package com.example.tdd.domain;

import com.example.tdd.domain.order.Order;
import com.example.tdd.domain.order.OrderRepository;
import com.example.tdd.domain.order.OrderStatus;
import com.example.tdd.support.OrderFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderRepository repo;
    @Autowired TestEntityManager em;

    @Test
    void save_andRoundtrip() {
        Order saved = repo.save(OrderFixtures.simple("USR-1"));

        em.flush();
        em.clear();

        Order loaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(loaded.getLines()).hasSize(2);
        assertThat(loaded.total()).isEqualByComparingTo("25.48");
    }

    @Test
    void findByUserIdAndStatus_filtersCorrectly() {
        repo.save(OrderFixtures.simple("USR-1"));
        repo.save(OrderFixtures.simple("USR-2"));
        em.flush();

        List<Order> result = repo.findByUserIdAndStatus("USR-1", OrderStatus.NEW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("USR-1");
    }

    @Test
    void uniqueConstraint_onIdempotencyKey_throws() {
        Order a = OrderFixtures.simple("USR-1").setIdempotencyKey("KEY-1");
        Order b = OrderFixtures.simple("USR-2").setIdempotencyKey("KEY-1");

        repo.save(a);
        em.flush();

        repo.save(b);
        assertThatThrownBy(() -> em.flush())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdempotencyKey_returnsExisting() {
        Order saved = repo.save(OrderFixtures.simple("USR-1").setIdempotencyKey("KEY-2"));
        em.flush();
        em.clear();

        assertThat(repo.findByIdempotencyKey("KEY-2"))
            .isPresent()
            .get()
            .extracting(Order::getId)
            .isEqualTo(saved.getId());
    }

    @Test
    void optimisticLock_concurrentUpdates_throwsOnSecond() {
        Order saved = repo.save(OrderFixtures.simple("USR-1"));
        em.flush();
        em.clear();

        // Two independent detached snapshots, both at version 0.
        // Without these clears, the persistence-context's first-level cache
        // would hand back the SAME managed instance for both findById calls
        // and there would be no version conflict to detect.
        Order loadedA = repo.findById(saved.getId()).orElseThrow();
        em.detach(loadedA);
        Order loadedB = repo.findById(saved.getId()).orElseThrow();
        em.detach(loadedB);

        // First "transaction" wins: merges into a managed copy at v=0,
        // UPDATE ... WHERE version=0 succeeds, version becomes 1 in DB.
        loadedA.markStatus(OrderStatus.PAID);
        repo.saveAndFlush(loadedA);
        em.clear(); // discard the now-managed copy so the next merge re-loads from DB

        // Second "transaction" loses: loadedB still carries version=0, but the row
        // is at version=1. UPDATE ... WHERE version=0 affects 0 rows, Hibernate
        // throws StaleObjectStateException → Spring translates → ObjectOptimisticLockingFailureException.
        loadedB.markStatus(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> repo.saveAndFlush(loadedB))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void notNullConstraint_onUserId_throws() {
        Order broken = OrderFixtures.simple("USR-1");
        ReflectionTestUtils.setField(broken, "userId", null);

        repo.save(broken);
        assertThatThrownBy(() -> em.flush())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteCascadesToLines() {
        UUID id = repo.save(OrderFixtures.simple("USR-X")).getId();
        em.flush();

        repo.deleteById(id);
        em.flush();

        assertThat(repo.findById(id)).isEmpty();
    }
}
