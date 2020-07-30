package org.example.crs.reservation;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.example.crs.reservation.Reservation.ReservationStatus.CANCELED;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateCreateBody;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateUpdateBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.junit.Before;
import org.junit.Test;

public class ReservationMapRepositoryTest
{
  private static final int TIMEOUT_MS = 150;

  private ReservationRepository mapRepository;

  @Before
  public void before()
  {
    mapRepository = new ReservationMapRepository();
  }

  @Test
  public void testCreate() throws Exception
  {
    var body = generateCreateBody();
    var reservation = mapRepository.create(body).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);
    assertNotNull(reservation.getId());
    assertEquals(body.getClientEmail(), reservation.getClientEmail());
    assertEquals(body.getClientName(), reservation.getClientName());
    assertEquals(body.getArrivalDate(), reservation.getArrivalDate());
  }

  @Test
  public void testFindById_Found() throws Exception
  {
    var reservation = mapRepository.create(generateCreateBody()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);

    var maybeFound = mapRepository.findById(reservation.getId()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeFound);
    assertTrue(maybeFound.isPresent());
    assertEquals(reservation, maybeFound.get());
  }

  @Test
  public void testFindById_NotFound() throws Exception
  {
    var maybeFound = mapRepository.findById(UUID.randomUUID()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeFound);
    assertTrue(maybeFound.isEmpty());
  }

  @Test
  public void testUpdate() throws Exception
  {
    var reservation = mapRepository.create(generateCreateBody()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);

    var update = generateUpdateBody(reservation);
    update.setStatus(Optional.of(CANCELED));

    var maybeUpdated = mapRepository.update(reservation.getId(), update).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeUpdated);
    assertTrue(maybeUpdated.isPresent());

    var updated = maybeUpdated.get();
    assertEquals(reservation.getId(), updated.getId());
    assertEquals(update.getArrivalDate().get(), updated.getArrivalDate());
    assertEquals(update.getDepartureDate().get(), updated.getDepartureDate());
    assertEquals(update.getStatus().get(), updated.getStatus());
  }

  @Test
  public void testUpdate_PeriodOnly() throws Exception
  {
    var reservation = mapRepository.create(generateCreateBody()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);

    var update = generateUpdateBody(reservation);

    var maybeUpdated = mapRepository.update(reservation.getId(), update).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeUpdated);
    assertTrue(maybeUpdated.isPresent());

    var updated = maybeUpdated.get();
    assertEquals(reservation.getId(), updated.getId());
    assertEquals(update.getArrivalDate().get(), updated.getArrivalDate());
    assertEquals(update.getDepartureDate().get(), updated.getDepartureDate());
    assertEquals(reservation.getStatus(), updated.getStatus());
  }

  @Test
  public void testUpdate_StatusOnly() throws Exception
  {
    var reservation = mapRepository.create(generateCreateBody()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);

    var update = ReservationUpdateBody.builder()
        .arrivalDate(Optional.empty())
        .departureDate(Optional.empty())
        .status(Optional.of(CANCELED))
        .build();

    var maybeUpdated = mapRepository.update(reservation.getId(), update).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeUpdated);
    assertTrue(maybeUpdated.isPresent());

    var updated = maybeUpdated.get();
    assertEquals(reservation.getId(), updated.getId());
    assertEquals(reservation.getArrivalDate(), updated.getArrivalDate());
    assertEquals(reservation.getDepartureDate(), updated.getDepartureDate());
    assertEquals(update.getStatus().get(), updated.getStatus());
  }

  @Test
  public void testUpdate_NotFound() throws Exception
  {
    var maybeUpdated = mapRepository.update(UUID.randomUUID(), ReservationUpdateBody.builder().build())
        .get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeUpdated);
    assertTrue(maybeUpdated.isEmpty());
  }

  @Test
  public void testCancel() throws Exception
  {
    var reservation = mapRepository.create(generateCreateBody()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(reservation);

    var maybeCanceled = mapRepository.cancel(reservation.getId()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeCanceled);
    assertTrue(maybeCanceled.isPresent());

    var canceled = maybeCanceled.get();
    assertEquals(reservation.getId(), canceled.getId());
    assertEquals(reservation.getArrivalDate(), canceled.getArrivalDate());
    assertEquals(reservation.getDepartureDate(), canceled.getDepartureDate());
    assertEquals(CANCELED, canceled.getStatus());
  }

  @Test
  public void testCancel_NotFound() throws Exception
  {
    var maybeCanceled = mapRepository.cancel(UUID.randomUUID()).get(TIMEOUT_MS, MILLISECONDS);
    assertNotNull(maybeCanceled);
    assertTrue(maybeCanceled.isEmpty());
  }

  @Test
  public void testFindFrom() throws Exception
  {
    var n = now();

    var past = generateCreateBody();
    past.setArrivalDate(now().minus(ofDays(2)));
    past.setDepartureDate(now().minus(ofDays(1)));

    var today = generateCreateBody();
    today.setArrivalDate(now());
    today.setDepartureDate(now().plus(ofDays(1)));

    var tomorrow = generateCreateBody();
    tomorrow.setArrivalDate(now().plus(ofDays(1)));
    tomorrow.setDepartureDate(now().plus(ofDays(2)));

    var inFutur = generateCreateBody();
    inFutur.setArrivalDate(now().plus(ofDays(5)));
    inFutur.setDepartureDate(now().plus(ofDays(6)));

    var bodies = List.of(past, today, tomorrow, inFutur);

    var reservationIds = new ArrayList<UUID>(bodies.size());

    var reservations = bodies.stream().map(body ->
    {
      try
      {
        return mapRepository.create(body).get(TIMEOUT_MS, MILLISECONDS);
      }
      catch (InterruptedException | ExecutionException | TimeoutException ex)
      {
      }
      return null;
    })
        .filter(Objects::nonNull)
        .peek(r -> reservationIds.add(r.getId()))
        .collect(toList());

    Function<ReservationCreateBody, UUID> findId = body -> reservations.stream()
        .filter(r -> r.getArrivalDate().isEqual(body.getArrivalDate()))
        .findFirst()
        .get()
        .getId();

    Function<LocalDate, List<UUID>> findAndGetIds = startAt ->
    {
      try
      {
        return mapRepository.findFrom(startAt).get(TIMEOUT_MS, MILLISECONDS)
            .stream()
            .map(Reservation::getId)
            .collect(toList());
      }
      catch (InterruptedException | ExecutionException | TimeoutException ex)
      {
        Logger.getLogger(ReservationMapRepositoryTest.class.getName()).log(Level.SEVERE, null, ex);
      }
      return emptyList();
    };

    var bodyIdsMap = bodies.stream().collect(toMap(identity(), findId));

    var from3DaysAgo = findAndGetIds.apply(now().minus(ofDays(3)));
    assertEquals(4, from3DaysAgo.size());
    assertTrue(from3DaysAgo.containsAll(reservationIds));

    var fromYesterday = findAndGetIds.apply(now().minus(ofDays(1)));
    assertEquals(4, fromYesterday.size()); // still 4 because 'past' departure is yesterday
    assertTrue(from3DaysAgo.containsAll(reservationIds));

    var fromToday = findAndGetIds.apply(now());
    assertEquals(3, fromToday.size());
    assertTrue(from3DaysAgo.containsAll(
        List.of(bodyIdsMap.get(today), bodyIdsMap.get(tomorrow), bodyIdsMap.get(inFutur))
    ));

    var fromTomorrow = findAndGetIds.apply(now().plus(ofDays(1)));
    assertEquals(3, fromTomorrow.size());
    assertTrue(from3DaysAgo.containsAll(
        List.of(bodyIdsMap.get(today), bodyIdsMap.get(tomorrow), bodyIdsMap.get(inFutur))
    ));

    var in2Days = findAndGetIds.apply(now().plus(ofDays(2)));
    assertEquals(2, in2Days.size());
    assertTrue(from3DaysAgo.containsAll(
        List.of(bodyIdsMap.get(tomorrow), bodyIdsMap.get(inFutur))
    ));

    var in3Days = findAndGetIds.apply(now().plus(ofDays(3)));
    assertEquals(1, in3Days.size());
    assertTrue(from3DaysAgo.contains(bodyIdsMap.get(inFutur)));

    var in1Month = findAndGetIds.apply(now().plus(ofMonths(1)));
    assertTrue(in1Month.isEmpty());
  }
}
