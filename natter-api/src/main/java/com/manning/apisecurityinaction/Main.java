package com.manning.apisecurityinaction;

import java.nio.file.*;

import com.manning.apisecurityinaction.controller.*;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.*;
import spark.*;

import static spark.Spark.*;

public class Main {

    public static void main(String... args) throws Exception {
        var datasource = JdbcConnectionPool.create(
            "jdbc:h2:mem:natter", "natter", "password");
        var database = Database.forDataSource(datasource);
        createTables(database);
        datasource = JdbcConnectionPool.create(
            "jdbc:h2:mem:natter", "natter_api_user", "password");

        database = Database.forDataSource(datasource);
        var spaceController = new SpaceController(database);

        before(((request, response) -> {
            if (request.requestMethod().equals("POST") &&
            !"application/json".equals(request.contentType())) {
                halt(415, new JSONObject().put(
                    "error", "Only application/json supported"
                ).toString());
            }
        }));

        post("/spaces", spaceController::createSpace);
        post("/spaces/:spaceId/messages", spaceController::postMessage);
        get("/spaces/:spaceId/messages/:msgId",
            spaceController::readMessage);
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        var moderatorController =
            new ModeratorController(database);
        delete("/spaces/:spaceId/messages/:msgId",
            moderatorController::deletePost);

        afterAfter((request, response) -> {
            response.type("application/json; charset=utf-8");
            response.header("X-Content-Type-Options", "nosniff");
            response.header("X-Frame-Options", "deny");
            response.header("X-XSS-Protection", "1; mode=block");
            response.header("Cache-Control", "private, max-age=0");
            response.header("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; sandbox");
            response.header("Server", "");
        });

        internalServerError(new JSONObject()
            .put("error", "internal server error").toString());
        notFound(new JSONObject()
            .put("error", "not found").toString());

        exception(IllegalArgumentException.class, Main::badRequest);
        exception(JSONException.class, Main::badRequest);
        exception(EmptyResultException.class,
            (e, request, response) -> response.status(404));
    }

  private static void badRequest(Exception ex,
      Request request, Response response) {
    response.status(400);
    response.body(new JSONObject().put("error", ex.getMessage()).toString());
  }

    private static void createTables(Database database) throws Exception {
        var path = Paths.get(
                Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}