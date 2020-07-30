package org.example.crs.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;

/**
 * Describes the contract of the reservation repository.
 */
public interface ReservationRepository
{
  /**
   * @param body The reservation create body.
   * @return The promise of the reservation created.
   */
  CompletableFuture<Reservation> create(ReservationCreateBody body);

  /**
   * @param id The reservation id.
   * @param body The reservation update body.
   * @return The promise of the optional reservation updated.
   */
  CompletableFuture<Optional<Reservation>> update(UUID id, ReservationUpdateBody body);

  /**
   * @param id The reservation id.
   * @return The promise of the optional reservation found.
   */
  CompletableFuture<Optional<Reservation>> findById(UUID id);

  /**
   * @param startAt The date from which we want to get reservations.
   * @return The list of reservations that finish at the 'startAt' date or after.
   */
  CompletableFuture<List<Reservation>> findFrom(LocalDate startAt);

  /**
   * @param startAt The date from which we want to get reservations.
   * @param ignoreId A reservation id we want to exclude.
   * @return The list of reservations that finish at the 'startAt' date or after, excluding ignoreId
   * if provided.
   */
  CompletableFuture<List<Reservation>> findFromExcept(
      LocalDate startAt,
      Optional<UUID> ignoreId);

  CompletableFuture<Optional<Reservation>> cancel(UUID id);
}
