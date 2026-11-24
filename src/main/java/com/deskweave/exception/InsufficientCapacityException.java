package com.deskweave.exception;

/**
 * Thrown when requested attendees exceed workspace capacity or demand exceeds inventory.
 */
public class InsufficientCapacityException extends DeskWeaveException {

    private final int requestedCapacity;
    private final int availableCapacity;

    public InsufficientCapacityException(String message, int requestedCapacity, int availableCapacity) {
        super(message);
        this.requestedCapacity = requestedCapacity;
        this.availableCapacity = availableCapacity;
    }

    public int getRequestedCapacity() {
        return requestedCapacity;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }
}
