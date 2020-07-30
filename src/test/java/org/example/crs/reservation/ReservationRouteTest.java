package org.example.crs.reservation;

import static akka.http.javadsl.model.HttpRequest.DELETE;
import static akka.http.javadsl.model.HttpRequest.GET;
import static akka.http.javadsl.model.HttpRequest.POST;
import static akka.http.javadsl.model.HttpRequest.PUT;
import static akka.http.javadsl.model.MediaTypes.APPLICATION_JSON;
import static akka.http.javadsl.model.StatusCodes.BAD_REQUEST;
import static akka.http.javadsl.model.StatusCodes.CREATED;
import static akka.http.javadsl.model.StatusCodes.NOT_FOUND;
import static akka.http.javadsl.model.StatusCodes.OK;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;
import static org.example.crs.ReservationApp.OBJECT_MAPPER;
import static org.example.crs.reservation.Reservation.ReservationStatus.ACTIVE;
import static org.example.crs.reservation.Reservation.ReservationStatus.CANCELED;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateCreateBody;
import static org.example.crs.reservation.utils.ReservationGenerationUtils.generateUpdateBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.example.crs.reservation.command.ReservationCommands.CancelReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.Command;
import org.example.crs.reservation.command.ReservationCommands.CreateReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.GetAvailabilitiesResponse;
import org.example.crs.reservation.command.ReservationCommands.GetReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.UpdateReservationResponse;
import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;
import org.example.crs.reservation.exception.ReservationAvailabilityCheckException;
import org.example.crs.reservation.exception.ReservationException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import com.fasterxml.jackson.core.JsonProcessingException;

@Slf4j
public class ReservationRouteTest extends JUnitRouteTest
{
  @ClassRule
  public static TestKitJunitResource testkit = new TestKitJunitResource();

  private ActorRef<Command> registry;
  private TestRoute route;

  @Before
  public void before()
  {
    var mapRepository = new ReservationMapRepository();
    var service = new ReservationService(mapRepository);

    registry = testkit.spawn(ReservationRegistry.create(service));
    route = testRoute(new ReservationRoute(testkit.system(), registry).getRoute());
  }

  @After
  public void after()
  {
    testkit.stop(registry);
  }

  @Test
  public void testCreateReservation() throws JsonProcessingException
  {
    var body = generateCreateBody();

    var response = createReservation(body, StatusCodes.CREATED);
    assertNotNull(response.getReservation());

    var reservation = response.getReservation();
    assertNotNull(reservation);
    assertNotNull(reservation.getId());
    assertEquals(body.getClientEmail(), reservation.getClientEmail());
    assertEquals(body.getClientName(), reservation.getClientName());
    assertEquals(body.getArrivalDate(), reservation.getArrivalDate());
    assertEquals(body.getDepartureDate(), reservation.getDepartureDate());
  }

  @Test
  public void testCreateReservation_PastArrivalDate() throws JsonProcessingException
  {
    var body = generateCreateBody();
    body.setArrivalDate(now().minus(ofDays(1)));
    body.setDepartureDate(body.getArrivalDate());

    var response = route.run(
        HttpRequest.POST("/reservations")
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationAvailabilityCheckException.ALREADY_PAST_MESSAGE, error.getError());
  }

  @Test
  public void testCreateReservation_TooLong() throws JsonProcessingException
  {
    var body = generateCreateBody();
    body.setArrivalDate(now().plus(ofDays(1)));
    body.setDepartureDate(body.getArrivalDate().plus(ofDays(5)));

    var response = route.run(
        HttpRequest.POST("/reservations")
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationException.TOO_LONG_MESSAGE, error.getError());
  }

  @Test
  public void testCreateReservation_NotAvailable() throws JsonProcessingException
  {
    var existingReservationBody = generateCreateBody();
    existingReservationBody.setArrivalDate(now().plus(ofDays(5)));
    existingReservationBody.setDepartureDate(existingReservationBody.getArrivalDate().plus(ofDays(3)));

    var existingReservation = createReservation(existingReservationBody, StatusCodes.CREATED).getReservation();

    var body = generateCreateBody();
    body.setArrivalDate(existingReservation.getArrivalDate().minus(ofDays(1)));
    body.setDepartureDate(existingReservation.getDepartureDate().minus(ofDays(1)));

    var response = route.run(
        HttpRequest.POST("/reservations")
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationException.NOT_AVAILABLE_MESSAGE, error.getError());
  }

  @Test
  public void testUpdateReservation() throws JsonProcessingException
  {
    var reservation = createReservation(generateCreateBody(), CREATED).getReservation();
    assertEquals(reservation.getStatus(), ACTIVE);

    var body = generateUpdateBody(reservation);
    assertTrue(body.getStatus().isEmpty());

    var response = updateReservation(reservation.getId(), body, OK);
    assertNotNull(response.getReservation());

    var updated = response.getReservation();
    assertEquals(reservation.getId(), updated.getId());
    assertEquals(reservation.getClientEmail(), updated.getClientEmail());
    assertEquals(reservation.getClientName(), updated.getClientName());
    assertEquals(body.getArrivalDate().get(), updated.getArrivalDate());
    assertEquals(body.getDepartureDate().get(), updated.getDepartureDate());
    assertEquals(ACTIVE, updated.getStatus());

    var retrieved = getReservation(reservation.getId(), OK).getReservation();
    assertEquals(updated.getClientEmail(), retrieved.getClientEmail());
    assertEquals(updated.getClientName(), retrieved.getClientName());
    assertEquals(updated.getArrivalDate(), retrieved.getArrivalDate());
    assertEquals(updated.getDepartureDate(), retrieved.getDepartureDate());
    assertEquals(updated.getStatus(), retrieved.getStatus());
  }

  @Test
  public void testCancelReservationViaAnUpdate() throws JsonProcessingException
  {
    var reservation = createReservation(generateCreateBody(), CREATED).getReservation();
    assertEquals(reservation.getStatus(), ACTIVE);

    var body = generateUpdateBody(reservation);
    body.setStatus(Optional.of(CANCELED));

    var response = updateReservation(reservation.getId(), body, OK);
    assertNotNull(response.getReservation());

    var updated = response.getReservation();
    assertEquals(reservation.getId(), updated.getId());
    assertEquals(reservation.getClientEmail(), updated.getClientEmail());
    assertEquals(reservation.getClientName(), updated.getClientName());
    assertEquals(body.getArrivalDate().get(), updated.getArrivalDate());
    assertEquals(body.getDepartureDate().get(), updated.getDepartureDate());
    assertEquals(CANCELED, updated.getStatus());

    var retrieved = getReservation(reservation.getId(), OK).getReservation();
    assertEquals(updated, retrieved);
  }

  @Test
  public void testUpdateReservation_NotFound() throws JsonProcessingException
  {
    var body = ReservationUpdateBody.builder()
        .build();
    var response = updateReservation(UUID.randomUUID(), body, NOT_FOUND);
    assertNull(response.getReservation());
  }

  @Test
  public void testUpdateReservation_NotAvailable() throws JsonProcessingException
  {
    var existingReservationBody = generateCreateBody();
    existingReservationBody.setArrivalDate(now().plus(ofDays(20)));
    existingReservationBody.setDepartureDate(existingReservationBody.getArrivalDate().plus(ofDays(3)));

    var existingReservation = createReservation(existingReservationBody, CREATED).getReservation();

    var reservation = createReservation(generateCreateBody(), CREATED).getReservation();
    var body = generateUpdateBody(reservation);
    body.setArrivalDate(Optional.of(existingReservation.getArrivalDate()));
    body.setDepartureDate(Optional.of(existingReservation.getDepartureDate()));

    var response = route.run(
        HttpRequest.PUT(format("/reservations/%s", reservation.getId()))
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationException.NOT_AVAILABLE_MESSAGE, error.getError());
  }

  @Test
  public void testUpdateReservation_ReactivateWithoutPeriod() throws JsonProcessingException
  {
    var existingReservationBody = generateCreateBody();
    existingReservationBody.setArrivalDate(now().plus(ofDays(20)));
    existingReservationBody.setDepartureDate(existingReservationBody.getArrivalDate().plus(ofDays(3)));

    var reservation = createReservation(existingReservationBody, CREATED)
        .getReservation();
    var canceled = cancelReservation(reservation.getId(), OK)
        .getReservation();

    var body = generateUpdateBody(canceled);
    body.setArrivalDate(Optional.empty());
    body.setDepartureDate(Optional.empty());
    body.setStatus(Optional.of(ACTIVE));

    var response = route.run(
        HttpRequest.PUT(format("/reservations/%s", reservation.getId()))
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationException.NOT_REACTIVABLE_WITHOUT_PERIOD_MESSAGE, error.getError());
  }

  @Test
  public void testUpdateReservation_PastPeriod() throws JsonProcessingException
  {
    var existingReservationBody = generateCreateBody();
    existingReservationBody.setArrivalDate(now().plus(ofDays(20)));
    existingReservationBody.setDepartureDate(existingReservationBody.getArrivalDate().plus(ofDays(3)));

    var reservation = createReservation(existingReservationBody, CREATED)
        .getReservation();

    var body = generateUpdateBody(reservation);
    body.setArrivalDate(Optional.of(now().minus(ofDays(1))));

    var response = route.run(
        HttpRequest.PUT(format("/reservations/%s", reservation.getId()))
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationAvailabilityCheckException.ALREADY_PAST_MESSAGE, error.getError());
  }

  @Test
  public void testGetReservation() throws JsonProcessingException
  {
    var reservation = createReservation(generateCreateBody(), CREATED).getReservation();

    var response = getReservation(reservation.getId(), OK);
    assertNotNull(response.getReservation());

    var found = response.getReservation();
    assertEquals(reservation.getId(), found.getId());
    assertEquals(reservation.getClientEmail(), found.getClientEmail());
    assertEquals(reservation.getClientName(), found.getClientName());
    assertEquals(reservation.getArrivalDate(), found.getArrivalDate());
    assertEquals(reservation.getDepartureDate(), found.getDepartureDate());
  }

  @Test
  public void testGetReservation_NotFound() throws JsonProcessingException
  {
    var response = getReservation(UUID.randomUUID(), NOT_FOUND);
    assertNull(response.getReservation());
  }

  @Test
  public void testGetAvailabilities_NoReservations() throws JsonProcessingException
  {
    var response = getAvailabilities(OK);
    assertEquals(now().plus(ofDays(1)), response.getFrom());
    assertEquals(now().plus(ofDays(1)).plus(ofMonths(1)), response.getTo());

    var availabilities = response.getAvailabilities();
    assertEquals(1, availabilities.size());

    var availability = availabilities.get(0);
    assertEquals(response.getFrom(), availability.getFrom());
    assertEquals(response.getTo(), availability.getTo());
  }

  @Test
  public void testGetAvailabilities_PastArrivalDate() throws JsonProcessingException
  {
    var checkFrom = now().minus(ofDays(1));
    var checkTo = now();

    var response = route.run(GET(format("/reservations?from=%s&to=%s", checkFrom, checkTo)))
        .assertStatusCode(BAD_REQUEST)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    var error = OBJECT_MAPPER.readValue(response, ErrorResponse.class);
    assertEquals(ReservationAvailabilityCheckException.ALREADY_PAST_MESSAGE, error.getError());
  }

  @Test
  public void testGetAvailabilities_SomeReservations() throws JsonProcessingException
  {
    var checkFrom = now().plus(ofDays(5));
    var checkTo = checkFrom.plus(ofMonths(1)).minus(ofDays(1));

    log.info("Check availabilities from {} to {}", checkFrom, checkTo);

    var body1 = generateCreateBody();
    body1.setArrivalDate(checkFrom.minus(ofDays(1)));
    body1.setDepartureDate(checkFrom.plus(ofDays(1)));

    var body2 = generateCreateBody();
    body2.setArrivalDate(checkFrom.plus(ofDays(10)));
    body2.setDepartureDate(checkFrom.plus(ofDays(12)));

    List.of(body1, body2).forEach(body ->
    {
      try
      {
        createReservation(body, CREATED);
        log.info("Reservation from {} to {}", body.getArrivalDate(), body.getDepartureDate());
      }
      catch (JsonProcessingException ex)
      {
      }
    });

    var response = getAvailabilities(checkFrom, checkTo, OK);
    var availabilities = response.getAvailabilities();
    assertEquals(2, availabilities.size());

    availabilities.forEach(a -> log.info("Availability from {} to {}", a.getFrom(), a.getTo()));

    var availability1 = availabilities.get(0);
    assertEquals(checkFrom.plus(ofDays(1)), availability1.getFrom());
    assertEquals(checkFrom.plus(ofDays(10)), availability1.getTo());

    var availability2 = availabilities.get(1);
    assertEquals(checkFrom.plus(ofDays(12)), availability2.getFrom());
    assertEquals(checkTo, availability2.getTo());
  }

  @Test
  public void testCancelReservation() throws JsonProcessingException
  {
    var reservation = createReservation(generateCreateBody(), CREATED).getReservation();
    assertEquals(reservation.getStatus(), ACTIVE);

    var response = cancelReservation(reservation.getId(), OK);
    assertNotNull(response.getReservation());

    var canceled = response.getReservation();
    assertEquals(reservation.getId(), canceled.getId());
    assertEquals(reservation.getClientEmail(), canceled.getClientEmail());
    assertEquals(reservation.getClientName(), canceled.getClientName());
    assertEquals(reservation.getArrivalDate(), canceled.getArrivalDate());
    assertEquals(reservation.getDepartureDate(), canceled.getDepartureDate());
    assertEquals(CANCELED, canceled.getStatus());
  }

  @Test
  public void testCancelReservation_NotFound() throws JsonProcessingException
  {
    route.run(DELETE(format("/reservations/%s", UUID.randomUUID())))
        .assertStatusCode(NOT_FOUND)
        .assertMediaType(APPLICATION_JSON);
  }

  private CreateReservationResponse createReservation(ReservationCreateBody body, StatusCode status) throws JsonProcessingException
  {
    var response = route.run(
        POST("/reservations")
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, CreateReservationResponse.class);
  }

  private UpdateReservationResponse updateReservation(UUID id, ReservationUpdateBody body, StatusCode status) throws JsonProcessingException
  {
    var response = route.run(
        PUT(format("/reservations/%s", id))
            .withEntity(APPLICATION_JSON.toContentType(), OBJECT_MAPPER.writeValueAsString(body))
    )
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, UpdateReservationResponse.class);
  }

  private GetReservationResponse getReservation(UUID id, StatusCode status) throws JsonProcessingException
  {
    var response = route.run(GET(format("/reservations/%s", id)))
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, GetReservationResponse.class);
  }

  private GetAvailabilitiesResponse getAvailabilities(StatusCode status) throws JsonProcessingException
  {
    var response = route.run(GET("/reservations"))
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, GetAvailabilitiesResponse.class);
  }

  private GetAvailabilitiesResponse getAvailabilities(LocalDate checkFrom, LocalDate checkTo, StatusCode status) throws JsonProcessingException
  {
    var response = route.run(GET(format("/reservations?from=%s&to=%s", checkFrom, checkTo)))
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, GetAvailabilitiesResponse.class);
  }

  private CancelReservationResponse cancelReservation(UUID id, StatusCode status) throws JsonProcessingException
  {
    var response = route.run(DELETE(format("/reservations/%s", id)))
        .assertStatusCode(status)
        .assertMediaType(APPLICATION_JSON)
        .entityString();

    return OBJECT_MAPPER.readValue(response, CancelReservationResponse.class);
  }
}
