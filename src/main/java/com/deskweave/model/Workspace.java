package com.deskweave.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base entity representing a reservable physical space (desk or meeting room).
 */
public abstract class Workspace implements Bookable, Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String code;
    private final String name;
    private final int floor;
    private final int capacity;
    private final boolean active;

    protected Workspace(int id, String code, String name, int floor, int capacity, boolean active) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Workspace code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace name must not be blank");
        }
        if (floor < 1) {
            throw new IllegalArgumentException("Floor must be >= 1");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1");
        }
        this.id = id;
        this.code = code.trim();
        this.name = name.trim();
        this.floor = floor;
        this.capacity = capacity;
        this.active = active;
    }

    public abstract WorkspaceType getWorkspaceType();

    public abstract int getDefaultGraceMinutes();

    public abstract String getAmenitySummary();

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getFloor() {
        return floor;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public String getDisplayName() {
        return code + " — " + name;
    }

    @Override
    public boolean canAccommodate(int attendees) {
        return active && attendees > 0 && attendees <= capacity;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Workspace other)) {
            return false;
        }
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }
        return this.code.equalsIgnoreCase(other.code);
    }

    @Override
    public int hashCode() {
        return id > 0 ? Integer.hashCode(id) : Objects.hashCode(code.toUpperCase());
    }

    @Override
    public String toString() {
        return getDisplayName() + " [Floor " + floor + ", Cap " + capacity + "]";
    }
}
