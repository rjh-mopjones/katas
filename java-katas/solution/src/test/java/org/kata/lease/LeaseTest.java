package org.kata.lease;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeaseTest {

    @Test
    void try_with_resources_returns_the_lease_to_the_pool() {
        Pool<Object> pool = newPool(2);
        assertEquals(2, pool.available());

        try (Lease<Object> lease = pool.acquire()) {
            assertNotNull(lease.get());
            assertEquals(1, pool.available());
        }

        assertEquals(2, pool.available());
    }

    @Test
    void body_throwing_still_returns_the_resource() {
        Pool<Object> pool = newPool(1);

        assertThrows(RuntimeException.class, () -> {
            try (Lease<Object> lease = pool.acquire()) {
                throw new RuntimeException("boom");
            }
        });

        assertEquals(1, pool.available());
    }

    @Test
    void double_close_is_idempotent() {
        Pool<Object> pool = newPool(1);
        Lease<Object> lease = pool.acquire();

        lease.close();
        lease.close();

        assertEquals(1, pool.available());
    }

    @Test
    void closed_lease_reuses_the_same_resource_on_next_acquire() {
        Pool<Object> pool = newPool(1);
        Lease<Object> first = pool.acquire();
        Object resource = first.get();
        first.close();

        Lease<Object> second = pool.acquire();

        assertSame(resource, second.get());
    }

    @Test
    void acquire_when_exhausted_throws() {
        Pool<Object> pool = newPool(1);
        pool.acquire();

        assertThrows(IllegalStateException.class, pool::acquire);
    }

    @Test
    void faulty_resource_close_exception_is_suppressed_under_the_body_exception() {
        Pool<FaultyResource> pool = new Pool<>(FaultyResource::new, 1);
        RuntimeException bodyFailure = new RuntimeException("body failed");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            try (Lease<FaultyResource> lease = pool.acquire();
                 FaultyResource resource = lease.get()) {
                throw bodyFailure;
            }
        });

        assertSame(bodyFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, thrown.getSuppressed()[0]);
        // Lease.close() still ran (after the faulty resource's close) and returned the resource.
        assertEquals(1, pool.available());
    }

    private static Pool<Object> newPool(int size) {
        return new Pool<>(Object::new, size);
    }
}
