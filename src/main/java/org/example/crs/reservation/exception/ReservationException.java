package org.example.crs.reservation.exception;

/**
 * Contains exceptions on reservation.
 */
public class ReservationException extends Exception
{
  public static final String TOO_LONG_MESSAGE = "You can not reserve for more than three days.";
  public static final String NOT_AVAILABLE_MESSAGE = "This reservation period is not available.";
  public static final String NOT_REACTIVABLE_WITHOUT_PERIOD_MESSAGE = "The reservation can not be reactivated" +
      " if you do not specify a period (it may be not still available).";

  private ReservationException(String message)
  {
    super(message);
  }

  /**
   * Create an exception for the case of we have a period that exceed three days.
   *
   * @return A reservation exception.
   */
  public static ReservationException tooLong()
  {
    return new ReservationException(TOO_LONG_MESSAGE);
  }

  /**
   * Create an exception for the case of we have a period that is not available.
   *
   * @return A reservation exception.
   */
  public static ReservationException notAvailable()
  {
    return new ReservationException(NOT_AVAILABLE_MESSAGE);
  }

  /**
   * Create an exception for the case of we want to re-activate a reservation via an update, but
   * without any period specified.
   *
   * @return A reservation exception.
   */
  public static ReservationException notReactivableWithoutPeriod()
  {
    return new ReservationException(NOT_REACTIVABLE_WITHOUT_PERIOD_MESSAGE);
  }
}
