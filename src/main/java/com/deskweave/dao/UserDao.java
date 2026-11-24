package com.deskweave.dao;

import com.deskweave.model.User;

import java.util.Optional;

/**
 * DAO contract for {@link User} persistence.
 */
public interface UserDao extends GenericDao<User, Integer> {

    Optional<User> findByEmail(String email);
}
