package de.mopsdom.adfs.http;

public class HttpException extends Exception{

  public String message;
  public String details;
  public int code;

  public HttpException(String details)
  {
    this.details=details;
  }

  public HttpException(int code, String message, String details)
  {
    this.code=code;
    this.message=message;
    this.details=details;
  }

}
