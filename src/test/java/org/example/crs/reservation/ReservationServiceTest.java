package org.example.crs.reservation;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.example.crs.reservation.utils.ReservationPeriodUtils.SimpleReservationPeriod.createPeriod;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

public class ReservationServiceTest
{
  private static final int TIMEOUT_MS = 150;

  private ReservationRepository repository;
  private ReservationService service;

  @Before
  public void before()
  {
    repository = mock(ReservationRepository.class);

    service = new ReservationService(repository);
  }

  @Test
  public void testIsAvailable() throws Exception
  {
    var arrivalDate = now().plus(ofDays(1));
    var departureDate = arrivalDate.plus(ofDays(1));

    when(repository.findFromExcept(eq(arrivalDate), eq(Optional.empty())))
        .thenReturn(CompletableFuture.completedFuture(emptyList()));

    var checkPeriod = createPeriod(arrivalDate, departureDate);

    var isPeriodAvailable = service.isAvailable(checkPeriod).get(TIMEOUT_MS, MILLISECONDS);
    assertTrue(isPeriodAvailable);
  }
}
