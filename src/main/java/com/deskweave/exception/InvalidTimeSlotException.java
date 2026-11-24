package com.deskweave.exception;

import java.time.LocalTime;

/**
 * Thrown when a booking or simulation time slot has invalid boundaries or mixed dates.
 */
public class InvalidTimeSlotException extends DeskWeaveException {

    private final LocalTime startTime;
    private final LocalTime endTime;

    public InvalidTimeSlotException(String message) {
        this(message, null, null);
    }

    public InvalidTimeSlotException(String message, LocalTime startTime, LocalTime endTime) {
        super(message);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
