package com.deskweave.model;

import com.deskweave.exception.InvalidTimeSlotException;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable half-open time interval {@code [startTime, endTime)} on a specific date.
 */
public final class TimeSlot implements Serializable, Comparable<TimeSlot> {

    private static final long serialVersionUID = 1L;
    private static final Comparator<TimeSlot> NATURAL_ORDER = Comparator
            .comparing(TimeSlot::getDate)
            .thenComparing(TimeSlot::getStartTime)
            .thenComparing(TimeSlot::getEndTime);

    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private TimeSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Validates and constructs a {@link TimeSlot} ensuring {@code startTime < endTime}.
     */
    public static TimeSlot of(LocalDate date, LocalTime startTime, LocalTime endTime) throws InvalidTimeSlotException {
        if (date == null || startTime == null || endTime == null) {
            throw new InvalidTimeSlotException("Date, start time, and end time must not be null", startTime, endTime);
        }
        if (!startTime.isBefore(endTime)) {
            throw new InvalidTimeSlotException(
                    "Start time (" + startTime + ") must be strictly before end time (" + endTime + ")",
                    startTime,
                    endTime);
        }
        return new TimeSlot(date, startTime, endTime);
    }

    /**
     * Returns true when this slot shares positive overlap time with {@code other}.
     * Touching boundaries (e.g., 09:00-10:00 and 10:00-11:00) do not overlap.
     */
    public boolean overlaps(TimeSlot other) {
        Objects.requireNonNull(other, "other TimeSlot must not be null");
        return this.date.equals(other.date)
                && this.startTime.isBefore(other.endTime)
                && other.startTime.isBefore(this.endTime);
    }

    public long durationMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }

    public double durationHours() {
        return durationMinutes() / 60.0;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    @Override
    public int compareTo(TimeSlot other) {
        return NATURAL_ORDER.compare(this, other);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TimeSlot other)) {
            return false;
        }
        return date.equals(other.date)
                && startTime.equals(other.startTime)
                && endTime.equals(other.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, startTime, endTime);
    }

    @Override
    public String toString() {
        return date + " " + startTime + "-" + endTime;
    }
}
