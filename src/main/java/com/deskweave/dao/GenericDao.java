package com.deskweave.dao;

import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object contract for CRUD persistence operations.
 */
public interface GenericDao<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAll();

    T save(T entity);

    boolean update(T entity);

    boolean deleteById(ID id);
}
