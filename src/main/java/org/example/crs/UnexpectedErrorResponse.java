package org.example.crs;

import static akka.http.javadsl.model.StatusCodes.INTERNAL_SERVER_ERROR;

import java.util.Optional;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.example.crs.reservation.command.ReservationCommands.CommandResponse;

/**
 * Used to wrap unexpected error so they can be sent back to the client.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UnexpectedErrorResponse extends CommandResponse
{
  public UnexpectedErrorResponse(Throwable ex)
  {
    setMaybeException(Optional.of(ex));
    setStatus(INTERNAL_SERVER_ERROR);
  }
}
