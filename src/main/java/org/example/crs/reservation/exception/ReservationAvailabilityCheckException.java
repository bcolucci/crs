package org.example.crs.reservation.exception;

/**
 * Contains exceptions on reservation (check) period.
 */
public class ReservationAvailabilityCheckException extends Exception
{
  public static final String ALREADY_PAST_MESSAGE = "Period already past.";
  public static final String TOO_SHORT_MESSAGE = "Period must contain at least one day.";
  public static final String TOO_SOON_MESSAGE = "Period must start at least tomorow.";
  public static final String TOO_FAR_MESSAGE = "Period must start the next month, at most.";

  private ReservationAvailabilityCheckException(String message)
  {
    super(message);
  }

  /**
   * Create an exception for the case of we have a period that is already past.
   *
   * @return A reservation (check) exception.
   */
  public static ReservationAvailabilityCheckException alreadyPast()
  {
    return new ReservationAvailabilityCheckException(ALREADY_PAST_MESSAGE);
  }

  /**
   * Create an exception for the case of we have a period that does not contain any days.
   *
   * @return A reservation (check) exception.
   */
  public static ReservationAvailabilityCheckException tooShort()
  {
    return new ReservationAvailabilityCheckException(TOO_SHORT_MESSAGE);
  }

  /**
   * Create an exception for the case of we have a period that start before tomorrow.
   *
   * @return A reservation (check) exception.
   */
  public static ReservationAvailabilityCheckException tooSoon()
  {
    return new ReservationAvailabilityCheckException(TOO_SOON_MESSAGE);
  }

  /**
   * Create an exception for the case of we have a period that start in more than one month.
   *
   * @return A reservation (check) exception.
   */
  public static ReservationAvailabilityCheckException tooFar()
  {
    return new ReservationAvailabilityCheckException(TOO_FAR_MESSAGE);
  }
}
