package org.example.crs.reservation;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.example.crs.reservation.Reservation.ReservationStatus.CANCELED;
import static org.example.crs.reservation.utils.PredicateUtils.alwaysTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;

/**
 * Implements the reservations repository interface using a in-memory concurrent hash map.
 *
 * @see the ReservationRepository interface for more documentation.
 */
public class ReservationMapRepository implements ReservationRepository
{
  private final Map<UUID, Reservation> reservations = new ConcurrentHashMap();

  @Override
  public CompletableFuture<Reservation> create(ReservationCreateBody body)
  {
    return CompletableFuture.supplyAsync(() ->
    {
      var id = UUID.randomUUID();

      var reservation = Reservation.fromCreate(body);
      reservation.setId(id);

      reservations.put(id, reservation);

      return reservation;
    });
  }

  @Override
  public CompletableFuture<Optional<Reservation>> update(UUID id, ReservationUpdateBody body)
  {
    return CompletableFuture.supplyAsync(() ->
        Optional.ofNullable(reservations.computeIfPresent(id, (__, reservation) ->
        {
          // partial update on what we need to update only
          return reservation.applyUpdate(body);
        }))
    );
  }

  @Override
  public CompletableFuture<Optional<Reservation>> findById(UUID id)
  {
    return CompletableFuture.supplyAsync(() -> Optional.ofNullable(reservations.get(id)));
  }

  @Override
  public CompletableFuture<List<Reservation>> findFrom(LocalDate startAt)
  {
    return findFromExcept(startAt, Optional.empty());
  }

  @Override
  public CompletableFuture<List<Reservation>> findFromExcept(
      LocalDate startAt,
      Optional<UUID> ignoreId)
  {
    // if ignoreId is not provided, the filter has not effect.
    Predicate<Reservation> filterById = ignoreId.map(id ->
        not((Reservation r) -> r.getId().equals(ignoreId.get()))
    ).orElseGet(alwaysTrue());

    Predicate<Reservation> filterByDate = not((r) -> r.getDepartureDate().isBefore(startAt));

    return CompletableFuture.supplyAsync(() ->
        reservations.values()
            .stream()
            // ignored reservation if necessary
            .filter(filterById)
            // taking only reservations with departure date greater or equal to startAt
            .filter(filterByDate)
            .collect(toList())
    );
  }

  @Override
  public CompletableFuture<Optional<Reservation>> cancel(UUID id)
  {
    return update(id, ReservationUpdateBody.builder()
        .status(Optional.of(CANCELED))
        .build());
  }
}
