package de.mopsdom.adfs.http;

public class HttpException extends Exception{

  public String message;
  public String details;
  public int code;

  public HttpException(String details)
  {
    this.details=details;
  }

  @Override
  public String getMessage(){
    if (super.getMessage()==null||super.getMessage().equalsIgnoreCase("null"))
    {
      return this.details;
    }
    else
    {
      return this.details+" | "+super.getMessage();
    }
  }

  public HttpException(int code, String message, String details)
  {
    this.code=code;
    this.message=message;
    this.details=details;
  }

}
