package org.kata.elevator;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ElevatorController {

    public ElevatorController(List<Elevator> elevators) {
        throw new UnsupportedOperationException();
    }

    public synchronized Optional<Integer> call(Request request) {
        throw new UnsupportedOperationException();
    }

    public synchronized void selectFloor(int elevatorId, int floor) {
        throw new UnsupportedOperationException();
    }

    public synchronized void tick() {
        throw new UnsupportedOperationException();
    }

    public List<Elevator> elevators() {
        throw new UnsupportedOperationException();
    }
}
