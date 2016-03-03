import com.typesafe.config.ConfigFactory;
import play.Application;
import play.Configuration;
import play.GlobalSettings;
import play.Logger;
import play.Mode;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.io.File;

import static play.mvc.Results.badRequest;
import static play.mvc.Results.notFound;

public class Global extends GlobalSettings {

  // TODO: load specific configuration file depending on execution mode.
  // Instructions for Play 2.4.6: http://stackoverflow
  // .com/questions/31294723/play-framework-2-3-8-java-overriding-default-configuration-load-with-mode-spec

  // If the framework doesn’t find an action method for a request, the onHandlerNotFound operation will be called:
  @Override
  public Promise<Result> onHandlerNotFound(Http.RequestHeader request) {
    return Promise.<Result>pure(notFound(request.uri()));
  }

  // The onBadRequest operation will be called if a route was found, but it was not possible to bind the request
  // parameters
  @Override
  public Promise<Result> onBadRequest(Http.RequestHeader request, String error) {
    return Promise.<Result>pure(badRequest(error));
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