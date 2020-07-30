package org.example.crs.reservation;

import static java.util.stream.Collectors.toList;
import static org.example.crs.reservation.Reservation.ReservationStatus.ACTIVE;
import static org.example.crs.reservation.exception.ReservationException.notAvailable;
import static org.example.crs.reservation.exception.ReservationException.notReactivableWithoutPeriod;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.SimpleReservationPeriod.createPeriod;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.getReservationAvailabilities;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.validateReservationPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.crs.reservation.command.param.Availability;
import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.example.crs.reservation.exception.ReservationAvailabilityCheckException;
import org.example.crs.reservation.exception.ReservationException;
import org.example.crs.reservation.utils.ReservationPeriodUtils.ReservationPeriod;

@Slf4j
@RequiredArgsConstructor
public class ReservationService
{
  private final ReservationRepository repository;

  /**
   * Creates a reservation.
   *
   * @param body The create body.
   * @return The promise of the reservation created.
   */
  public CompletableFuture<Reservation> create(ReservationCreateBody body)
  {
    try
    {
      validateReservationPeriod(body);
    }
    catch (ReservationAvailabilityCheckException | ReservationException ex)
    {
      return CompletableFuture.<Reservation>failedFuture(ex);
    }

    return isAvailable(body).thenComposeAsync((isPeriodAvailable) ->
    {
      if (!isPeriodAvailable)
      {
        return CompletableFuture.<Reservation>failedFuture(notAvailable());
      }

      return repository.create(body);
    });
  }

  /**
   * Updates a reservation.
   *
   * @param id The reservation id.
   * @param body The update body.
   * @return The promise of the optional reservation updated.
   */
  public CompletableFuture<Optional<Reservation>> update(UUID id, ReservationUpdateBody body)
  {
    if (!body.isPeriodUpdate())
    {
      // in order to reactivate a reservarion via the update, the body must contains
      // a valid period (because we must re-check if the period is still available)
      if (body.getStatus().isPresent() && body.getStatus().get() == ACTIVE)
      {
        return CompletableFuture.<Optional<Reservation>>failedFuture(notReactivableWithoutPeriod());
      }

      return repository.update(id, body);
    }

    var periodUpdate = createPeriod(body);
    try
    {
      validateReservationPeriod(periodUpdate);
    }
    catch (ReservationAvailabilityCheckException | ReservationException ex)
    {
      return CompletableFuture.<Optional<Reservation>>failedFuture(ex);
    }

    return isAvailable(periodUpdate, Optional.of(id)).thenComposeAsync(isPeriodAvailable ->
    {
      if (!isPeriodAvailable)
      {
        return CompletableFuture.<Optional<Reservation>>failedFuture(notAvailable());
      }

      return repository.update(id, body);
    });
  }

  /**
   * @param id The reservation id.
   * @return The promise of the optional reservation found.
   */
  public CompletableFuture<Optional<Reservation>> findById(UUID id)
  {
    return repository.findById(id);
  }

  /**
   *
   * @param checkFrom
   * @param checkTo
   * @return
   */
  public CompletableFuture<List<Availability>> getAvailabilities(LocalDate checkFrom, LocalDate checkTo)
  {
    return getAvailabilities(checkFrom, checkTo, Optional.empty());
  }

  public CompletableFuture<List<Availability>> getAvailabilities(
      LocalDate checkFrom,
      LocalDate checkTo,
      Optional<UUID> ignoreId)
  {
    return repository.findFromExcept(checkFrom, ignoreId)
        .thenApplyAsync((reservations) ->
        {
          var reservationPeriods = reservations.stream()
              .filter(Reservation.activeOnly())
              .map(ReservationPeriod.class::cast)
              .collect(toList());
          try
          {
            return getReservationAvailabilities(checkFrom, checkTo, reservationPeriods);
          }
          catch (ReservationAvailabilityCheckException | ReservationException ex)
          {
            throw new CompletionException(ex);
          }
        });
  }

  public CompletableFuture<Optional<Reservation>> cancelReservation(UUID id)
  {
    return repository.cancel(id);
  }

  public CompletableFuture<Boolean> isAvailable(ReservationPeriod period)
  {
    return isAvailable(period, Optional.empty());
  }

  public CompletableFuture<Boolean> isAvailable(ReservationPeriod period, Optional<UUID> ignoreId)
  {
    return getAvailabilities(period.getArrivalDate(), period.getDepartureDate(), ignoreId)
        .thenApplyAsync(availabilities ->
        {
          if (availabilities.isEmpty())
          {
            return false;
          }

          // must be strictly equal to the desired reservation period
          var availability = availabilities.get(0);
          return availability.getFrom().isEqual(period.getArrivalDate()) &&
              availability.getTo().isEqual(period.getDepartureDate());
        });
  }
}
