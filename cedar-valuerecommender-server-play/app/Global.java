import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpStatus;
import play.*;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import utils.ErrorMsgBuilder;

import java.io.File;

import static play.mvc.Results.badRequest;
import static play.mvc.Results.notFound;
import static utils.Constants.NOT_FOUND_MSG;
import static utils.Constants.BAD_REQUEST_MSG;

public class Global extends GlobalSettings {

//  @Override
//  public Configuration onLoadConfig(Configuration config, File path, ClassLoader classloader, Mode mode) {
//    // Modifies the configuration according to the execution mode (DEV, TEST, PROD)
//    if (mode.name().compareTo("TEST") == 0) {
//      return new Configuration(ConfigFactory.load("application." + mode.name().toLowerCase() + ".conf"));
//    } else {
//      return onLoadConfig(config, path, classloader); // default implementation
//    }
//  }

  // If the framework doesn’t find an action method for a request, the onHandlerNotFound operation will be called:
  @Override
  public Promise<Result> onHandlerNotFound(Http.RequestHeader request) {
    return Promise.<Result>pure(notFound(ErrorMsgBuilder.build(HttpStatus.SC_BAD_REQUEST, NOT_FOUND_MSG, request.uri())));
  }

  // The onBadRequest operation will be called if a route was found, but it was not possible to bind the request
  // parameters
  @Override
  public Promise<Result> onBadRequest(Http.RequestHeader request, String error) {
    return Promise.<Result>pure(badRequest(ErrorMsgBuilder.build(HttpStatus.SC_BAD_REQUEST, BAD_REQUEST_MSG, error)));
  }

  @Override
  public void onStart(Application app) {
    Logger.info("Application has started");
  }

  @Override
  public void onStop(Application app) {
    Logger.info("Application shutdown...");
  }

  /* For CORS */

  private class ActionWrapper extends Action.Simple {
    public ActionWrapper(Action<?> action) {
      this.delegate = action;
    }

    @Override
    public Promise<Result> call(Http.Context ctx) throws java.lang.Throwable {
      Promise<Result> result = this.delegate.call(ctx);
      Http.Response response = ctx.response();
      response.setHeader("Access-Control-Allow-Origin", "*");
      return result;
    }
  }

  @Override
  public Action<?> onRequest(Http.Request request, java.lang.reflect.Method actionMethod) {
    return new ActionWrapper(super.onRequest(request, actionMethod));
  }
}