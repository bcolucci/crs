package org.example.crs.reservation.utils;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;
import static java.util.Collections.emptyList;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.SimpleReservationPeriod.createPeriod;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.getReservationAvailabilities;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.validateReservationPeriod;
import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.example.crs.reservation.exception.ReservationAvailabilityCheckException;
import org.example.crs.reservation.exception.ReservationException;
import org.example.crs.reservation.utils.ReservationPeriodUtils.ReservationPeriod;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@Slf4j
public class ReservationPeriodUtilsTest
{
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testReservationValidation_AlreadyPast() throws Exception
  {
    thrown.expect(ReservationAvailabilityCheckException.class);
    thrown.expectMessage(ReservationAvailabilityCheckException.ALREADY_PAST_MESSAGE);

    validateReservationPeriod(createPeriod(now().minus(ofDays(1)), now()));
  }

  @Test
  public void testReservationValidation_ArrivalIsBeforeDeparture() throws Exception
  {
    thrown.expect(ReservationAvailabilityCheckException.class);
    thrown.expectMessage(ReservationAvailabilityCheckException.TOO_SHORT_MESSAGE);

    validateReservationPeriod(createPeriod(now(), now().minus(ofDays(1))));
  }

  @Test
  public void testReservationValidation_ArrivalIsStrictlyBeforeDeparture() throws Exception
  {
    thrown.expect(ReservationAvailabilityCheckException.class);
    thrown.expectMessage(ReservationAvailabilityCheckException.TOO_SHORT_MESSAGE);

    validateReservationPeriod(createPeriod(now(), now()));
  }

  @Test
  public void testReservationValidation_ArrivalIsTomorrowAtLeast() throws Exception
  {
    thrown.expect(ReservationAvailabilityCheckException.class);
    thrown.expectMessage(ReservationAvailabilityCheckException.TOO_SOON_MESSAGE);

    validateReservationPeriod(createPeriod(now(), now().plus(ofDays(1))));
  }

  @Test
  public void testReservationValidation_ArrivalIsInOneMonthAtMost() throws Exception
  {
    thrown.expect(ReservationAvailabilityCheckException.class);
    thrown.expectMessage(ReservationAvailabilityCheckException.TOO_FAR_MESSAGE);

    var checkFrom = now().plus(ofMonths(1)).plus(ofDays(1));
    validateReservationPeriod(createPeriod(checkFrom, checkFrom.plus(ofDays(1))));
  }

  @Test
  public void testReservationValidation_ReservationIsThreeDaysMax() throws Exception
  {
    thrown.expect(ReservationException.class);
    thrown.expectMessage(ReservationException.TOO_LONG_MESSAGE);

    var arrivalDate = now().plus(ofDays(1));
    validateReservationPeriod(createPeriod(arrivalDate, arrivalDate.plus(ofDays(4))));
  }

  @Test
  public void testReservationValidation_ValidCase() throws Exception
  {
    var checkFrom = now().plus(ofDays(6));
    validateReservationPeriod(createPeriod(checkFrom, checkFrom.plus(ofDays(3))));
  }

  @Test
  public void testReservationAvailabilities_WhenNoReservations() throws Exception
  {
    var checkFrom = now().plus(ofDays(1));
    var checkTo = checkFrom.plus(ofDays(1));

    assertPeriodIsAllFree(checkFrom, checkTo, emptyList());
  }

  @Test
  public void testReservationAvailabilities_WhenAllReservationsAreBeforeArrival() throws Exception
  {
    var checkFrom = now().plus(ofDays(1));
    var checkTo = checkFrom.plus(ofDays(1));

    var reservations = new ArrayList<ReservationPeriod>();
    reservations.add(createPeriod(checkFrom.minus(ofDays(5)), checkFrom.minus(ofDays(3))));
    reservations.add(createPeriod(checkFrom.minus(ofDays(2)), checkFrom));

    assertPeriodIsAllFree(checkFrom, checkTo, reservations);
  }

  @Test
  public void testReservationAvailabilities_WhenAllReservationsAreAfterDeparture() throws Exception
  {
    var checkFrom = now().plus(ofDays(1));
    var checkTo = checkFrom.plus(ofDays(1));

    var reservations = new ArrayList<ReservationPeriod>();
    reservations.add(createPeriod(checkFrom.minus(ofDays(2)), checkFrom));
    reservations.add(createPeriod(checkFrom.plus(ofDays(1)), checkFrom.plus(ofDays(3))));

    assertPeriodIsAllFree(checkFrom, checkTo, reservations);
  }

  @Test
  public void testReservationAvailabilities_WhenThereAreReservationsInTheSamePeriod() throws Exception
  {
    var checkFrom = now().plus(ofDays(1));
    var checkTo = checkFrom.plus(ofDays(10));

    log.info("Check availabilities from {} to {}", checkFrom, checkTo);

    var reservations = new ArrayList<ReservationPeriod>();
    reservations.add(createPeriod(checkFrom.minus(ofDays(1)), checkFrom.plus(ofDays(1))));
    reservations.add(createPeriod(checkFrom.plus(ofDays(4)), checkFrom.plus(ofDays(5))));
    reservations.add(createPeriod(checkTo.minus(ofDays(1)), checkTo.plus(ofDays(1))));

    reservations.forEach(r -> log.info("Reservation from {} to {}", r.getArrivalDate(), r.getDepartureDate()));

    var availabilities = getReservationAvailabilities(checkFrom, checkTo, reservations);
    assertEquals(2, availabilities.size());

    availabilities.forEach(a -> log.info("Availability from {} to {}", a.getFrom(), a.getTo()));

    var availability1 = availabilities.get(0);
    assertEquals(checkFrom.plus(ofDays(1)), availability1.getFrom());
    assertEquals(checkFrom.plus(ofDays(4)), availability1.getTo());

    var availability2 = availabilities.get(1);
    assertEquals(checkFrom.plus(ofDays(5)), availability2.getFrom());
    assertEquals(checkFrom.plus(ofDays(9)), availability2.getTo());
  }

  @Test
  public void testCreatePeriodDates()
  {
    var period = createPeriod(now(), now().plus(ofDays(1)));
    assertEquals(now(), period.getArrivalDate());
    assertEquals(now().plus(ofDays(1)), period.getDepartureDate());
  }

  @Test
  public void testCreatePeriodFromUpdateBody()
  {
    var body = ReservationUpdateBody.builder()
        .arrivalDate(Optional.of(now()))
        .departureDate(Optional.of(now().plus(ofDays(1))))
        .build();
    var period = createPeriod(body);
    assertEquals(body.getArrivalDate().get(), period.getArrivalDate());
    assertEquals(body.getDepartureDate().get(), period.getDepartureDate());
  }

  @Test
  public void testCreatePeriodFromUpdateBody_WithDefaults()
  {
    var period = createPeriod(ReservationUpdateBody.builder().build());
    assertEquals(now(), period.getArrivalDate());
    assertEquals(now(), period.getDepartureDate());
  }

  private void assertPeriodIsAllFree(
      LocalDate checkFrom,
      LocalDate checkTo,
      List<ReservationPeriod> reservations) throws Exception
  {
    var availabilities = getReservationAvailabilities(checkFrom, checkTo, reservations);
    assertEquals(1, availabilities.size());

    var availability = availabilities.get(0);
    assertEquals(checkFrom, availability.getFrom());
    assertEquals(checkTo, availability.getTo());
  }
}
