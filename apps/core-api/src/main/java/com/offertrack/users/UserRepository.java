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
            USERS.EMAIL_VERIFIED_AT,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .fetchOne(
            record ->
                new User(
                    record.get(USERS.ID),
                    record.get(USERS.EMAIL),
                    record.get(USERS.PASSWORD_HASH),
                    record.get(USERS.NAME),
                    record.get(USERS.EMAIL_VERIFIED_AT),
                    record.get(USERS.CREATED_AT),
                    record.get(USERS.UPDATED_AT)));
  }

  public Optional<User> insertVerifiedOAuthUserIfAbsent(
      String email, String name, OffsetDateTime verifiedAt) {
    UUID id = UUID.randomUUID();

    return dsl.insertInto(USERS)
        .set(USERS.ID, id)
        .set(USERS.EMAIL, email)
        .set(USERS.PASSWORD_HASH, (String) null)
        .set(USERS.NAME, name)
        .set(USERS.EMAIL_VERIFIED_AT, verifiedAt)
        .set(USERS.CREATED_AT, verifiedAt)
        .set(USERS.UPDATED_AT, verifiedAt)
        .onConflict(USERS.EMAIL)
        .doNothing()
        .returning(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.EMAIL_VERIFIED_AT,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .fetchOptional(this::mapUser);
  }

  public Optional<User> findByEmail(String email) {
    return dsl.select(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.EMAIL_VERIFIED_AT,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .from(USERS)
        .where(USERS.EMAIL.eq(email))
        .fetchOptional(this::mapUser);
  }

  public Optional<User> findById(UUID id) {
    return dsl.select(
            USERS.ID,
            USERS.EMAIL,
            USERS.PASSWORD_HASH,
            USERS.NAME,
            USERS.EMAIL_VERIFIED_AT,
            USERS.CREATED_AT,
            USERS.UPDATED_AT)
        .from(USERS)
        .where(USERS.ID.eq(id))
        .fetchOptional(this::mapUser);
  }

  public void markEmailVerified(UUID id, OffsetDateTime verifiedAt) {
    dsl.update(USERS)
        .set(USERS.EMAIL_VERIFIED_AT, verifiedAt)
        .set(USERS.UPDATED_AT, verifiedAt)
        .where(USERS.ID.eq(id))
        .and(USERS.EMAIL_VERIFIED_AT.isNull())
        .execute();
  }

  public void updatePasswordHash(UUID id, String passwordHash, OffsetDateTime updatedAt) {
    dsl.update(USERS)
        .set(USERS.PASSWORD_HASH, passwordHash)
        .set(USERS.UPDATED_AT, updatedAt)
        .where(USERS.ID.eq(id))
        .execute();
  }

  private User mapUser(org.jooq.Record record) {
    return new User(
        record.get(USERS.ID),
        record.get(USERS.EMAIL),
        record.get(USERS.PASSWORD_HASH),
        record.get(USERS.NAME),
        record.get(USERS.EMAIL_VERIFIED_AT),
        record.get(USERS.CREATED_AT),
        record.get(USERS.UPDATED_AT));
  }
}
