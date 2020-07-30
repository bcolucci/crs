package org.example.crs.reservation.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import org.example.crs.reservation.Reservation;
import org.example.crs.reservation.command.param.Availability;
import org.example.crs.reservation.command.param.ReservationCreateBody;
import org.example.crs.reservation.command.param.ReservationUpdateBody;

import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.fasterxml.jackson.annotation.JsonIgnore;

@UtilityClass
public class ReservationCommands
{
  /**
   * The main command interface.
   */
  public static interface Command
  {
  }

  /**
   * The main command response.
   */
  @Data
  public static class CommandResponse
  {
    @JsonIgnore
    private StatusCode status = StatusCodes.OK;

    @JsonIgnore
    private Optional<Throwable> maybeException = Optional.empty();

    /**
     * @return The status code int value.
     */
    public int getStatusCode()
    {
      return status.intValue();
    }

    /**
     * @return The optional error message, or null,
     */
    public String getError()
    {
      return maybeException.map(Throwable::getLocalizedMessage).orElse(null);
    }
  }

  /**
   * The create command.
   */
  @Getter
  @RequiredArgsConstructor
  public static class CreateReservationCmd implements Command
  {
    private final ReservationCreateBody body;
    private final ActorRef<CreateReservationResponse> replyTo;
  }

  /**
   * The create response.
   */
  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class CreateReservationResponse extends CommandResponse
  {
    private Reservation reservation;
  }

  /**
   * The check availabilities command.
   */
  @Getter
  @RequiredArgsConstructor
  public static class GetAvailabilitiesCmd implements Command
  {
    private final Optional<LocalDate> maybeCheckFrom;
    private final Optional<LocalDate> maybeCheckTo;
    private final ActorRef<GetAvailabilitiesResponse> replyTo;
  }

  /**
   * The check availabilities response.
   */
  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class GetAvailabilitiesResponse extends CommandResponse
  {
    private LocalDate from;
    private LocalDate to;
    private List<Availability> availabilities;
  }

  /**
   * The retrieve command.
   */
  @Getter
  @RequiredArgsConstructor
  public static class GetReservationCmd implements Command
  {
    private final UUID id;
    private final ActorRef<GetReservationResponse> replyTo;
  }

  /**
   * The retrieve response.
   */
  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class GetReservationResponse extends CommandResponse
  {
    private Reservation reservation;
  }

  /**
   * The update command.
   */
  @Getter
  @RequiredArgsConstructor
  public static class UpdateReservationCmd implements Command
  {
    private final UUID id;
    private final ReservationUpdateBody body;
    private final ActorRef<UpdateReservationResponse> replyTo;
  }

  /**
   * The update response.
   */
  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class UpdateReservationResponse extends CommandResponse
  {
    private Reservation reservation;
  }

  /**
   * The cancel command.
   */
  @Getter
  @RequiredArgsConstructor
  public static class CancelReservationCmd implements Command
  {
    private final UUID id;
    private final ActorRef<CancelReservationResponse> replyTo;
  }

  /**
   * The cancel response.
   */
  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class CancelReservationResponse extends CommandResponse
  {
    private Reservation reservation;
  }
}
