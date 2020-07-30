package org.example.crs.reservation.command.param;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.example.crs.reservation.utils.ReservationPeriodUtils.ReservationPeriod;

/**
 * Describes the data we need to create a reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationCreateBody implements ReservationPeriod
{
  private String clientEmail;
  private String clientName;
  private LocalDate arrivalDate;
  private LocalDate departureDate;
}
