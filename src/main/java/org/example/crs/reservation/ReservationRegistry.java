package org.example.crs.reservation;

import static java.time.LocalDate.now;
import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;

import java.util.Optional;
import java.util.concurrent.CompletionException;

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
import org.example.crs.reservation.exception.ReservationAvailabilityCheckException;
import org.example.crs.reservation.exception.ReservationException;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.StatusCodes;

/**
 * Manages events from routing and call the service methods to do operations on reservations.
 */
public class ReservationRegistry extends AbstractBehavior<Command>
{
  /**
   * The reservations service.
   */
  private final ReservationService service;

  /**
   * @param context The context, given by the Behavior.setup() call.
   * @param service The reservations service.
   */
  public ReservationRegistry(ActorContext<Command> context, ReservationService service)
  {
    super(context);
    this.service = service;
  }

  /**
   * The builder we use the create this actor.
   *
   * @param service The reservation service.
   * @return The actor which manages events received from routing.
   */
  public static Behavior<Command> create(ReservationService service)
  {
    return Behaviors.setup(ctx -> new ReservationRegistry(ctx, service));
  }

  /**
   * @return The events receiver.
   */
  @Override
  public Receive<Command> createReceive()
  {
    return newReceiveBuilder()
        .onMessage(CreateReservationCmd.class, this::onCreateReservation)
        .onMessage(UpdateReservationCmd.class, this::onUpdateReservation)
        .onMessage(GetReservationCmd.class, this::onGetReservation)
        .onMessage(GetAvailabilitiesCmd.class, this::onGetAvailabilities)
        .onMessage(CancelReservationCmd.class, this::onCancelReservation)
        .build();
  }

  /**
   * Handles when a user want to create a reservation.
   *
   * @param command The create command.
   * @return The current actor.
   */
  private Behavior<Command> onCreateReservation(CreateReservationCmd command)
  {
    service.create(command.getBody()).whenCompleteAsync((reservation, ex) ->
    {
      var response = new CreateReservationResponse();
      if (ex != null)
      {
        populateResponseErrorFields(response, ex);
      }
      else
      {
        response.setReservation(reservation);
        response.setStatus(StatusCodes.CREATED);
      }

      command.getReplyTo().tell(response);
    });

    return this;
  }

  /**
   * Handles when a user want to update a reservation.
   *
   * @param command The update command.
   * @return The current actor.
   */
  private Behavior<Command> onUpdateReservation(UpdateReservationCmd command) throws Exception
  {
    var id = command.getId();
    var body = command.getBody();

    service.update(id, body).whenCompleteAsync((reservation, ex) ->
    {
      var response = new UpdateReservationResponse();
      if (ex != null)
      {
        populateResponseErrorFields(response, ex);
      }
      else
      {
        reservation.ifPresentOrElse(response::setReservation, () ->
        {
          response.setStatus(StatusCodes.NOT_FOUND);
        });
      }

      command.getReplyTo().tell(response);
    });

    return this;
  }

  /**
   * Handles when a user want to retrieve a reservation.
   *
   * @param command The retrieve command.
   * @return The current actor.
   */
  private Behavior<Command> onGetReservation(GetReservationCmd command) throws Exception
  {
    var id = command.getId();

    service.findById(id).whenCompleteAsync((reservation, ex) ->
    {
      var response = new GetReservationResponse();
      if (ex != null)
      {
        populateResponseErrorFields(response, ex);
      }
      else
      {
        reservation.ifPresentOrElse(response::setReservation, () ->
        {
          response.setStatus(StatusCodes.NOT_FOUND);
        });
      }

      command.getReplyTo().tell(response);
    });

    return this;
  }

  /**
   * Handles when a user want to retrieve availabilities.
   *
   * @param command The retrieve availabilities command.
   * @return The current actor.
   */
  private Behavior<Command> onGetAvailabilities(GetAvailabilitiesCmd command) throws Exception
  {
    var checkFrom = command.getMaybeCheckFrom().orElseGet(() -> now().plus(ofDays(1)));
    var checkTo = command.getMaybeCheckTo().orElseGet(() -> checkFrom.plus(ofMonths(1)));

    service.getAvailabilities(checkFrom, checkTo).whenCompleteAsync((availabilities, ex) ->
    {
      var response = new GetAvailabilitiesResponse();
      response.setFrom(checkFrom);
      response.setTo(checkTo);

      if (ex != null)
      {
        populateResponseErrorFields(response, ex);
      }
      else
      {
        response.setAvailabilities(availabilities);
      }

      command.getReplyTo().tell(response);
    });

    return this;
  }

  /**
   * Handles when a user want to cancel a reservation.
   *
   * @param command The cancel command.
   * @return The current actor.
   */
  private Behavior<Command> onCancelReservation(CancelReservationCmd command) throws Exception
  {
    var id = command.getId();

    service.cancelReservation(id).whenCompleteAsync((reservation, ex) ->
    {
      var response = new CancelReservationResponse();
      if (ex != null)
      {
        populateResponseErrorFields(response, ex);
      }
      else
      {
        reservation.ifPresentOrElse(response::setReservation, () ->
        {
          response.setStatus(StatusCodes.NOT_FOUND);
        });
      }

      command.getReplyTo().tell(response);
    });

    return this;
  }

  /**
   * @param response The actor response for a command.
   * @param ex An exception we must use to populate the response error attributes.
   */
  private void populateResponseErrorFields(CommandResponse response, Throwable ex)
  {
    if (ex instanceof CompletionException)
    {
      populateResponseErrorFields(response, ex.getCause());
      return;
    }

    if (ex instanceof ReservationAvailabilityCheckException ||
        ex instanceof ReservationException)
    {
      response.setStatus(StatusCodes.BAD_REQUEST);
    }
    else
    {
      response.setStatus(StatusCodes.INTERNAL_SERVER_ERROR);
    }

    response.setMaybeException(Optional.of(ex));
  }
}
