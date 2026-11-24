package com.deskweave.dao;

import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.util.List;
import java.util.Optional;

/**
 * DAO contract for {@link Workspace} persistence and multi-criteria filtering.
 */
public interface WorkspaceDao extends GenericDao<Workspace, Integer> {

    Optional<Workspace> findByCode(String code);

    List<Workspace> findActive();

    List<Workspace> findByFilter(WorkspaceType type,
                                 Integer floor,
                                 int minCapacity,
                                 boolean requireMonitor,
                                 boolean requireStanding,
                                 boolean requireProjector,
                                 boolean requireWhiteboard);
}
