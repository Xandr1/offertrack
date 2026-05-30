package com.offertrack.users;

import static com.offertrack.jooq.generated.tables.Users.USERS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final DSLContext dsl;

  public UserRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public User createUser(String email, String passwordHash, String name) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, email)
        .set(USERS.PASSWORD_HASH, passwordHash)
        .set(USERS.NAME, name)
        .set(USERS.CREATED_AT, now)
        .set(USERS.UPDATED_AT, now)
        .returning(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .fetchOne(
            record ->
                new User(
                    record.get(USERS.ID),
                    record.get(USERS.EMAIL),
                    record.get(USERS.PASSWORD_HASH),
                    record.get(USERS.NAME),
                    record.get(USERS.CREATED_AT),
                    record.get(USERS.UPDATED_AT)));
  }

  public Optional<User> findByEmail(String email) {
    return dsl.select(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .from(USERS)
        .where(USERS.EMAIL.eq(email))
        .fetchOptional(
            record ->
                new User(
                    record.get(USERS.ID),
                    record.get(USERS.EMAIL),
                    record.get(USERS.PASSWORD_HASH),
                    record.get(USERS.NAME),
                    record.get(USERS.CREATED_AT),
                    record.get(USERS.UPDATED_AT)));
  }

  public Optional<User> findById(UUID id) {
    return dsl.select(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .fetchOptional(
            record ->
                new User(
                    record.get(USERS.ID),
                    record.get(USERS.EMAIL),
                    record.get(USERS.PASSWORD_HASH),
                    record.get(USERS.NAME),
                    record.get(USERS.CREATED_AT),
                    record.get(USERS.UPDATED_AT)));
  }
}
