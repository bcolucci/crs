package org.example.crs.reservation.command.param;

import java.time.LocalDate;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.example.crs.reservation.Reservation.ReservationStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Describes the data we could have to update a reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationUpdateBody
{
  @Builder.Default
  private Optional<LocalDate> arrivalDate = Optional.empty();

  @Builder.Default
  private Optional<LocalDate> departureDate = Optional.empty();

  @Builder.Default
  private Optional<ReservationStatus> status = Optional.empty();

  /**
   * @return If the update impacts the reservation period.
   */
  @JsonIgnore
  public boolean isPeriodUpdate()
  {
    return arrivalDate.isPresent() && departureDate.isPresent();
  }
}
