package org.example.crs.reservation;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.util.stream.Collectors.toList;
import static org.example.crs.reservation.Reservation.ReservationStatus.CANCELED;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateCreateBody;
import static org.junit.Assert.assertEquals;

import java.util.stream.Stream;

import org.junit.Test;

public class ReservationTest
{
  @Test
  public void testCreateFromCreateBody()
  {
    var body = generateCreateBody();
    var reservation = Reservation.fromCreate(body);
    assertEquals(body.getClientEmail(), reservation.getClientEmail());
    assertEquals(body.getClientName(), reservation.getClientName());
    assertEquals(body.getArrivalDate(), reservation.getArrivalDate());
    assertEquals(body.getDepartureDate(), reservation.getDepartureDate());
  }

  @Test
  public void testNbDays()
  {
    var reservation = Reservation.builder()
        .arrivalDate(now())
        .departureDate(now().plus(ofDays(2)))
        .build();
    assertEquals(2, reservation.getNbDays());
  }

  @Test
  public void testActiveOnlyPredicate()
  {
    var active = Reservation.builder().build();
    var inactive = Reservation.builder().status(CANCELED).build();

    var actives = Stream.of(active, inactive).filter(Reservation.activeOnly()).collect(toList());
    assertEquals(1, actives.size());
    assertEquals(active, actives.get(0));
  }
}
