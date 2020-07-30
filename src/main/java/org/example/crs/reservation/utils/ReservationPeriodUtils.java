package org.example.crs.reservation.utils;

import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;
import static java.util.Collections.singletonList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.SimpleReservationPeriod.createPeriod;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import org.example.crs.reservation.command.param.Availability;
import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.example.crs.reservation.exception.ReservationAvailabilityCheckException;
import org.example.crs.reservation.exception.ReservationException;

/**
 * Some Reservation Period utility functions.
 */
@Slf4j
@UtilityClass
public class ReservationPeriodUtils
{
  /**
   * Describes a reservation Period. It is used for a Reservation, but also for a
   * ReservationCreateBody or a ReservationUpdateBody.
   */
  public static interface ReservationPeriod
  {
    LocalDate getArrivalDate();

    LocalDate getDepartureDate();
  }

  /**
   * A simple ReservationPeriod implementation, without anything else.
   */
  @Getter
  @RequiredArgsConstructor
  public static class SimpleReservationPeriod implements ReservationPeriod
  {
    private final LocalDate arrivalDate;
    private final LocalDate departureDate;

    /**
     * Creates a reservation period from dates.
     *
     * @param arrivaDate Date of arrival.
     * @param departureDate Date of departure.
     * @return The corresponding reservation period.
     */
    public static ReservationPeriod createPeriod(LocalDate arrivaDate, LocalDate departureDate)
    {
      return new SimpleReservationPeriod(arrivaDate, departureDate);
    }

    /**
     * Creates a reservation period from an update body.
     *
     * @param body The reservation update body.
     * @return The corresponding reservation period, with default dates if necessary.
     */
    public static ReservationPeriod createPeriod(ReservationUpdateBody body)
    {
      return new SimpleReservationPeriod(
          body.getArrivalDate().orElseGet(LocalDate::now),
          body.getDepartureDate().orElseGet(LocalDate::now));
    }
  }

  /**
   * Validates a reservation (check) period. It must not be past. It must contains at least one day.
   * It must start tomorrow at least. It must not start more than in one month.
   *
   * @param period The reservation (check) period to validate.
   * @throws ReservationAvailabilityCheckException In case the reservation period is invalid.
   */
  public static void validateAvailabilitiesCheckPeriod(ReservationPeriod period) throws ReservationAvailabilityCheckException
  {
    var now = LocalDate.now();

    // already past
    if (period.getArrivalDate().isBefore(now))
    {
      throw ReservationAvailabilityCheckException.alreadyPast();
    }

    // departure is before or equal to arrival
    if (!period.getDepartureDate().isAfter(period.getArrivalDate()))
    {
      throw ReservationAvailabilityCheckException.tooShort();
    }

    // we can book only from tomorrow or more
    var minReservationDate = now.plus(ofDays(1));
    if (period.getArrivalDate().isBefore(minReservationDate))
    {
      throw ReservationAvailabilityCheckException.tooSoon();
    }

    // cannot book more than a month in advance
    var maxReservationDate = now.plus(ofMonths(1));
    if (period.getArrivalDate().isAfter(maxReservationDate))
    {
      throw ReservationAvailabilityCheckException.tooFar();
    }
  }

  /**
   * Validates a reservation period. It must be a valid reservation (check) period. It must contains
   * three days maximum.
   *
   * @param period The reservation period to validate.
   * @throws ReservationAvailabilityCheckException In case the reservation (check) period is
   * invalid.
   * @throws ReservationException In case the reservation period is invalid
   */
  public static void validateReservationPeriod(ReservationPeriod period)
      throws ReservationAvailabilityCheckException, ReservationException
  {
    validateAvailabilitiesCheckPeriod(period);

    var maxDepartureDate = period.getArrivalDate().plus(ofDays(3));
    if (maxDepartureDate.isBefore(period.getDepartureDate()))
    {
      throw ReservationException.tooLong();
    }
  }

  /**
   * Extracts a list of availability from a list of reservations.
   *
   * @param checkFrom The date from which we are checking availabilities.
   * @param checkTo The date to which we are checking availabilities.
   * @param reservations The list of reservations inside which we are looking for availabilities.
   * @return The list of availabilities found, or an empty list.
   * @throws ReservationAvailabilityCheckException In case the reservation (check) period is
   * invalid.
   * @throws ReservationException In case the reservation period is invalid.
   */
  public static List<Availability> getReservationAvailabilities(
      LocalDate checkFrom,
      LocalDate checkTo,
      List<ReservationPeriod> reservations)
      throws ReservationAvailabilityCheckException, ReservationException
  {
    validateAvailabilitiesCheckPeriod(createPeriod(checkFrom, checkTo));

    // no reservations -> the full period is available
    if (reservations.isEmpty())
    {
      return singletonList(new Availability(checkFrom, checkTo));
    }

    var reservationsInTheSamePeriod = reservations.stream()
        .filter(not(r ->
            r.getDepartureDate().isBefore(checkFrom) ||
            r.getDepartureDate().isEqual(checkFrom) ||
            r.getArrivalDate().isAfter(checkTo) ||
            r.getArrivalDate().isEqual(checkTo)
        ))
        .sorted((r1, r2) -> r1.getArrivalDate().compareTo(r2.getArrivalDate()))
        .collect(toList());

    // the full period is available
    if (reservationsInTheSamePeriod.isEmpty())
    {
      return singletonList(new Availability(checkFrom, checkTo));
    }

    /*
     * Here starts the algorithm that extracts availabilities.
     */
    // cursors initialization
    var from = checkFrom.plus(ofDays(0));
    var to = from.plus(ofDays(0));

    // the final list we'll return
    var availabilities = new ArrayList<Availability>();

    for (var r : reservationsInTheSamePeriod)
    {
      // target arrival between a reservation
      if ((r.getArrivalDate().isBefore(from) ||
          r.getArrivalDate().isEqual(from)) &&
          from.isBefore(r.getDepartureDate()))
      {
        from = r.getDepartureDate().plus(ofDays(0));
        to = from.plus(ofDays(0));
        continue;
      }

      // expanding to next arrival
      if (to.isBefore(r.getArrivalDate()))
      {
        to = to.plus(Period.between(to, r.getArrivalDate()));
      }

      // at least one day
      if (to.isAfter(from))
      {
        availabilities.add(new Availability(from, to));
      }

      // move cursors at the end of the current reservation
      from = r.getDepartureDate().plus(ofDays(0));
      to = from.plus(ofDays(0));
    }

    // there are still some dates after the last reservation
    var lastReservation = reservationsInTheSamePeriod.get(reservationsInTheSamePeriod.size() - 1);
    if (lastReservation.getDepartureDate().isBefore(checkTo))
    {
      availabilities.add(new Availability(lastReservation.getDepartureDate(), checkTo));
    }

    return availabilities;
  }
}
