package com.deskweave.ui;

/**
 * Implemented by JavaFX views that reload live data on navigation or background updates.
 */
public interface RefreshableView {

    void refreshData();
}
