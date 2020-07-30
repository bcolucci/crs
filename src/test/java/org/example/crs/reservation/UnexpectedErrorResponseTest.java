package org.example.crs.reservation;

import static akka.http.javadsl.model.StatusCodes.INTERNAL_SERVER_ERROR;
import static org.junit.Assert.assertEquals;

import org.example.crs.UnexpectedErrorResponse;
import org.junit.Test;

public class UnexpectedErrorResponseTest
{
  @Test
  public void testCreateFromThrowable()
  {
    var throwable = new RuntimeException("Some error message");
    var error = new UnexpectedErrorResponse(throwable);
    assertEquals(throwable, error.getMaybeException().get());
    assertEquals(throwable.getMessage(), error.getError());
    assertEquals(INTERNAL_SERVER_ERROR.intValue(), error.getStatusCode());
  }
}
