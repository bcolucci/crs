package org.example.crs.reservation;

import lombok.Data;

@Data
public class ErrorResponse
{
  private String error;
  private int statusCode;
}
