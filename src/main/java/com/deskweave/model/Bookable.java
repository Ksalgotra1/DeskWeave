package com.deskweave.model;

/**
 * Contract for any physical workspace resource that can be reserved in DeskWeave.
 */
public interface Bookable {

    int getId();

    String getCode();

    String getDisplayName();

    int getCapacity();

    boolean isActive();

    boolean canAccommodate(int attendees);
}
