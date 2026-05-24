package org.kata.elevator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElevatorControllerTest {

    @Test
    void single_elevator_serves_call() {
        var e = new Elevator(1, 0, 0, 10);
        var ctrl = new ElevatorController(List.of(e));
        ctrl.call(new Request(5, Direction.UP));
        for (int i = 0; i < 10 && e.currentFloor() != 5; i++) ctrl.tick();
        assertEquals(5, e.currentFloor());
    }

    @Test
    void dispatcher_picks_nearest_elevator() {
        var e1 = new Elevator(1, 0, 0, 20);
        var e2 = new Elevator(2, 15, 0, 20);
        var ctrl = new ElevatorController(List.of(e1, e2));

        int chosen = ctrl.call(new Request(14, Direction.DOWN)).orElseThrow();
        assertEquals(2, chosen);   // e2 at floor 15 is closer to 14 than e1 at floor 0
    }

    @Test
    void elevator_serves_multiple_targets_in_one_pass() {
        var e = new Elevator(1, 0, 0, 10);
        var ctrl = new ElevatorController(List.of(e));
        ctrl.call(new Request(3, Direction.UP));
        ctrl.call(new Request(7, Direction.UP));

        // Tick until both reached
        for (int i = 0; i < 20 && e.direction() != Direction.IDLE; i++) ctrl.tick();
        assertEquals(7, e.currentFloor());
        assertEquals(Direction.IDLE, e.direction());
    }

    @Test
    void elevator_reverses_after_serving_all_in_direction() {
        var e = new Elevator(1, 5, 0, 10);
        var ctrl = new ElevatorController(List.of(e));
        ctrl.call(new Request(8, Direction.UP));    // go up first
        ctrl.call(new Request(2, Direction.DOWN));  // then come back down

        for (int i = 0; i < 30 && e.direction() != Direction.IDLE; i++) ctrl.tick();
        assertEquals(2, e.currentFloor());
    }
}
