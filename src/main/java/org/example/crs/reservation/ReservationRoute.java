package org.example.crs.reservation;

import static akka.http.javadsl.marshallers.jackson.Jackson.unmarshaller;
import static akka.http.javadsl.server.PathMatchers.segment;
import static org.example.crs.ReservationApp.OBJECT_MAPPER;
import static org.example.crs.ReservationApp.TO_JSON;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import lombok.RequiredArgsConstructor;

import org.example.crs.UnexpectedErrorResponse;
import org.example.crs.reservation.command.ReservationCommands.CancelReservationCmd;
import org.example.crs.reservation.command.ReservationCommands.CancelReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.Command;
import org.example.crs.reservation.command.ReservationCommands.CreateReservationCmd;
import org.example.crs.reservation.command.ReservationCommands.CreateReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.GetAvailabilitiesCmd;
import org.example.crs.reservation.command.ReservationCommands.GetAvailabilitiesResponse;
import org.example.crs.reservation.command.ReservationCommands.GetReservationCmd;
import org.example.crs.reservation.command.ReservationCommands.GetReservationResponse;
import org.example.crs.reservation.command.ReservationCommands.UpdateReservationCmd;
import org.example.crs.reservation.command.ReservationCommands.UpdateReservationResponse;
import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;

/**
 * Provides the reservations routing system.
 */
@RequiredArgsConstructor
public class ReservationRoute extends AllDirectives
{
  /**
   * The reservations Actor.
   */
  private final ActorRef<Command> registryActor;

  /**
   * The system scheduler.
   */
  private final Scheduler scheduler;

  /**
   * @return /reservations route
   */
  public Route getRoute()
  {
    return pathPrefix("reservations", () ->
        concat(
            createReservationRoute(),
            getAvailabilitiesRoute(),
            getReservationRoutes()
        )
    ).seal(RejectionHandler.defaultHandler(), getExceptionHandler());
  }

  /**
   * @return The POST /reservation route.
   */
  private Route createReservationRoute()
  {
    return post(() ->
        entity(
            unmarshaller(OBJECT_MAPPER, ReservationCreateBody.class),
            body -> onSuccess(createReservation(body), performed ->
                complete(performed.getStatus(), performed, TO_JSON)
            )
        )
    );
  }

  /**
   * @return The GET /reservation route (with optional from & to parameters)
   */
  private Route getAvailabilitiesRoute()
  {
    return pathEnd(() ->
        parameterOptional("from", from ->
            parameterOptional("to", to ->
            {
              var maybeFrom = from.map(LocalDate::parse);
              var maybeTo = to.map(LocalDate::parse);

              return onSuccess(getAvailabilities(maybeFrom, maybeTo), performed ->
                  complete(performed.getStatus(), performed, TO_JSON)
              );
            })
        )
    );
  }

  /**
   * Returns a specified reservation routes. GET to retrieve the reservation. PUT to update the
   * reservation. And DELETE to cancel the reservation.
   *
   * @return The /reservation/{id} routes.
   */
  private Route getReservationRoutes()
  {
    return path(segment(), idStr ->
    {
      var id = UUID.fromString(idStr);

      return concat(
          get(() ->
              onSuccess(getReservation(id), performed ->
                  complete(performed.getStatus(), performed, TO_JSON)
              )
          ),
          put(() ->
              entity(
                  unmarshaller(OBJECT_MAPPER, ReservationUpdateBody.class),
                  body -> onSuccess(updateReservation(id, body), performed ->
                      complete(performed.getStatus(), performed, TO_JSON)
                  )
              )
          ),
          delete(() ->
              onSuccess(cancelReservation(id), performed ->
                  complete(performed.getStatus(), performed, TO_JSON)
              )
          )
      );
    });
  }

  /**
   * Sends the create command.
   *
   * @param body The reservation create body.
   * @return The promise of the creation response.
   */
  private CompletionStage<CreateReservationResponse> createReservation(ReservationCreateBody body)
  {
    return AskPattern.ask(registryActor,
        ref -> new CreateReservationCmd(body, ref), Duration.ofSeconds(3), scheduler);
  }

  /**
   * Sends the update command.
   *
   * @param id The reservation id.
   * @param body The reservation update body.
   * @return The promise of the update response.
   */
  private CompletionStage<UpdateReservationResponse> updateReservation(UUID id, ReservationUpdateBody body)
  {
    return AskPattern.ask(registryActor,
        ref -> new UpdateReservationCmd(id, body, ref), Duration.ofSeconds(3), scheduler);
  }

  /**
   * Sends the retrieval command.
   *
   * @param id The reservation id.
   * @return The promise of the retrieval response.
   */
  private CompletionStage<GetReservationResponse> getReservation(UUID id)
  {
    return AskPattern.ask(registryActor,
        ref -> new GetReservationCmd(id, ref), Duration.ofSeconds(1), scheduler);
  }

  /**
   * Sends the cancel command.
   *
   * @param id The reservation id.
   * @return The promise of the cancel response.
   */
  private CompletionStage<CancelReservationResponse> cancelReservation(UUID id)
  {
    return AskPattern.ask(registryActor,
        ref -> new CancelReservationCmd(id, ref), Duration.ofSeconds(3), scheduler);
  }

  /**
   * Sends the check availabilities command.
   *
   * @param maybeFrom The optional date from which we're searching availabilities.
   * @param maybeTo The optional date to which we're searching availabilities.
   * @return The promise of the check availabilities response.
   */
  private CompletionStage<GetAvailabilitiesResponse> getAvailabilities(
      Optional<LocalDate> maybeFrom,
      Optional<LocalDate> maybeTo)
  {
    return AskPattern.ask(registryActor,
        ref -> new GetAvailabilitiesCmd(maybeFrom, maybeTo, ref), Duration.ofSeconds(3), scheduler);
  }

  /**
   * @return A custom exception handler for the routes.
   */
  private ExceptionHandler getExceptionHandler()
  {
    return ExceptionHandler.newBuilder()
        .matchAny(ex -> complete(StatusCodes.INTERNAL_SERVER_ERROR, new UnexpectedErrorResponse(ex), TO_JSON))
        .build();
  }
}
