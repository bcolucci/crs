package org.example.crs.reservation;

import static akka.http.javadsl.marshallers.jackson.Jackson.marshaller;
import static akka.http.javadsl.marshallers.jackson.Jackson.unmarshaller;
import static akka.http.javadsl.server.PathMatchers.segment;
import static org.example.crs.ReservationApp.OBJECT_MAPPER;

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
import org.example.crs.reservation.command.ReservationCommands.CommandResponse;
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
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;

@RequiredArgsConstructor
public class ReservationRoute extends AllDirectives
{
  private static final Marshaller<CommandResponse, RequestEntity> TO_JSON = marshaller(OBJECT_MAPPER);

  private final ActorRef<Command> registryActor;
  private final Scheduler scheduler;

  public ReservationRoute(ActorSystem<?> system, ActorRef<Command> registryActor)
  {
    this.registryActor = registryActor;
    this.scheduler = system.scheduler();
  }

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
   * @return The POST /reservation/{id} routes. GET to retrieve the reservation. PUT to update the
   * reservation.
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

  private ExceptionHandler getExceptionHandler()
  {
    return ExceptionHandler.newBuilder()
        .matchAny(ex -> complete(StatusCodes.INTERNAL_SERVER_ERROR, new UnexpectedErrorResponse(ex), TO_JSON))
        .build();
  }

  private CompletionStage<CreateReservationResponse> createReservation(ReservationCreateBody body)
  {
    return AskPattern.ask(registryActor,
        ref -> new CreateReservationCmd(body, ref), Duration.ofSeconds(3), scheduler);
  }

  private CompletionStage<UpdateReservationResponse> updateReservation(UUID id, ReservationUpdateBody body)
  {
    return AskPattern.ask(registryActor,
        ref -> new UpdateReservationCmd(id, body, ref), Duration.ofSeconds(3), scheduler);
  }

  private CompletionStage<GetReservationResponse> getReservation(UUID id)
  {
    return AskPattern.ask(registryActor,
        ref -> new GetReservationCmd(id, ref), Duration.ofSeconds(1), scheduler);
  }

  private CompletionStage<CancelReservationResponse> cancelReservation(UUID id)
  {
    return AskPattern.ask(registryActor,
        ref -> new CancelReservationCmd(id, ref), Duration.ofSeconds(3), scheduler);
  }

  private CompletionStage<GetAvailabilitiesResponse> getAvailabilities(
      Optional<LocalDate> maybeFrom,
      Optional<LocalDate> maybeTo)
  {
    return AskPattern.ask(registryActor,
        ref -> new GetAvailabilitiesCmd(maybeFrom, maybeTo, ref), Duration.ofSeconds(3), scheduler);
  }
}
